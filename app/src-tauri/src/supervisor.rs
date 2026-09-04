//! 进程管理 Supervisor
//!
//! 职责：启动并监控 Python 内核与 Java 后端两个子进程；
//! 进程退出或健康探测失败时自动拉起；应用退出时回收全部子进程。
//! 这是「进程管理收口到 Tauri」决策的落点 —— Java 不负责拉起 Python。

use std::collections::HashMap;
use std::process::Stdio;
use std::sync::Arc;
use std::time::Duration;

use tokio::process::{Child, Command};
use tokio::sync::Mutex;

#[derive(Clone)]
pub struct ServiceSpec {
    pub name: &'static str,
    pub program: String,
    pub args: Vec<String>,
    pub cwd: Option<String>,
    pub health_url: Option<String>,
}

pub struct Supervisor {
    specs: Vec<ServiceSpec>,
    children: Arc<Mutex<HashMap<String, Child>>>,
    http: reqwest::Client,
}

impl Supervisor {
    pub fn new(specs: Vec<ServiceSpec>) -> Self {
        let http = reqwest::Client::builder()
            .timeout(Duration::from_secs(3))
            .build()
            .expect("reqwest client");
        Self {
            specs,
            children: Arc::new(Mutex::new(HashMap::new())),
            http,
        }
    }

    /// 启动全部服务，并起一个常驻监控任务。
    pub async fn start_all(&self) {
        for spec in &self.specs {
            let _ = self.spawn(spec).await;
        }

        let children = self.children.clone();
        let specs = self.specs.clone();
        let http = self.http.clone();
        tokio::spawn(async move {
            loop {
                tokio::time::sleep(Duration::from_secs(10)).await;
                for spec in &specs {
                    let needs_restart = !is_healthy(&children, spec, &http).await;
                    if needs_restart {
                        let _ = spawn_into(spec, &children).await;
                    }
                }
            }
        });
    }

    async fn spawn(&self, spec: &ServiceSpec) -> Result<(), String> {
        spawn_into(spec, &self.children).await
    }

    pub async fn restart(&self, name: &str) -> Result<(), String> {
        {
            let mut g = self.children.lock().await;
            if let Some(mut c) = g.remove(name) {
                let _ = c.start_kill();
                let _ = c.wait().await;
            }
        }
        match self.specs.iter().find(|s| s.name == name) {
            Some(spec) => self.spawn(spec).await,
            None => Err(format!("未知服务: {}", name)),
        }
    }

    pub async fn stop_all(&self) {
        let mut g = self.children.lock().await;
        for (_, mut c) in g.drain() {
            let _ = c.start_kill();
            let _ = c.wait().await;
        }
    }

    pub async fn status(&self) -> Vec<(String, bool)> {
        let mut g = self.children.lock().await;
        let mut out = Vec::with_capacity(self.specs.len());
        for s in &self.specs {
            let alive = match g.get_mut(s.name) {
                Some(c) => match c.try_wait() {
                    Ok(Some(_)) => {
                        g.remove(s.name);
                        false
                    }
                    _ => true,
                },
                None => false,
            };
            out.push((s.name.to_string(), alive));
        }
        out
    }
}

async fn is_healthy(
    children: &Arc<Mutex<HashMap<String, Child>>>,
    spec: &ServiceSpec,
    http: &reqwest::Client,
) -> bool {
    // 1) 进程仍在
    let alive = {
        let mut g = children.lock().await;
        match g.get_mut(spec.name) {
            Some(c) => match c.try_wait() {
                Ok(Some(_)) => {
                    g.remove(spec.name);
                    false
                }
                _ => true,
            },
            None => false,
        }
    };
    if !alive {
        return false;
    }
    // 2) 健康端点可达（进程可能在但还没起来 / 卡死）
    if let Some(url) = &spec.health_url {
        match http.get(url).send().await {
            Ok(r) => r.status().is_success(),
            Err(_) => false,
        }
    } else {
        true
    }
}

async fn spawn_into(
    spec: &ServiceSpec,
    children: &Arc<Mutex<HashMap<String, Child>>>,
) -> Result<(), String> {
    let mut cmd = Command::new(&spec.program);
    cmd.args(&spec.args).stdout(Stdio::null()).stderr(Stdio::piped());
    if let Some(cwd) = &spec.cwd {
        cmd.current_dir(cwd);
    }
    match cmd.spawn() {
        Ok(child) => {
            children.lock().await.insert(spec.name.to_string(), child);
            Ok(())
        }
        Err(e) => Err(format!("启动 {} 失败: {}", spec.name, e)),
    }
}

/// 默认服务规格。路径由环境变量覆盖；dev 时相对 src-tauri 的工作目录。
pub fn default_specs() -> Vec<ServiceSpec> {
    // 默认 MEMU_ROOT = ../.. （相对 src-tauri → 项目根 D:\project\memu）
    let root = std::env::var("MEMU_ROOT").unwrap_or_else(|_| "../..".to_string());

    let python_cwd =
        std::env::var("MEMU_KERNEL_CWD").unwrap_or_else(|_| format!("{}/kernel-python", root));
    let java_jar = std::env::var("MEMU_JAVA_JAR")
        .unwrap_or_else(|_| format!("{}/backend-java/target/memu-backend-0.1.0.jar", root));

    let python_program = std::env::var("MEMU_PYTHON").unwrap_or_else(|_| "python".to_string());
    let java_program = std::env::var("MEMU_JAVA").unwrap_or_else(|_| "java".to_string());

    vec![
        ServiceSpec {
            name: "python-kernel",
            program: python_program,
            args: vec![
                "-m".into(),
                "uvicorn".into(),
                "main:app".into(),
                "--host".into(),
                "127.0.0.1".into(),
                "--port".into(),
                "8765".into(),
            ],
            cwd: Some(python_cwd),
            health_url: Some("http://127.0.0.1:8765/health".into()),
        },
        ServiceSpec {
            name: "java-backend",
            program: java_program,
            args: vec!["-jar".into(), java_jar],
            cwd: None,
            health_url: Some("http://127.0.0.1:8080/actuator/health".into()),
        },
    ]
}
