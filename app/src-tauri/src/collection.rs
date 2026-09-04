//! 系统采集：轮询剪贴板，把新文本推给 Java 后端 `/api/events`。
//!
//! 职责边界：Tauri 只做采集，不做抽取/建模。事件推给 Java 后，
//! 由 Java → Python 内核完成抽取入图，主动引擎据此学习习惯。
//!
//! 去重策略：只有「成功推送」后才记为本条已见；推送失败（后端未就绪）不记，
//! 下一个轮询周期会重试，从而容忍启动竞态（Java 起来得比采集器晚）。

use std::collections::hash_map::DefaultHasher;
use std::collections::HashSet;
use std::hash::{Hash, Hasher};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::time::Duration;

use serde::Serialize;

use notify::Watcher;

/// 推给 Java `/api/events` 的载荷，字段与 EventPayload 对齐。
#[derive(Serialize)]
struct IngestEvent<'a> {
    source: &'a str,
    #[serde(rename = "type")]
    event_type: &'a str,
    #[serde(rename = "rawText")]
    raw_text: &'a str,
}

/// 剪贴板采集器。后台轮询，去重后推给 Java。
pub struct Collector {
    http: reqwest::Client,
    ingest_url: String,
    last_text: Mutex<String>,
}

impl Collector {
    pub fn new(base_url: String) -> Self {
        let http = reqwest::Client::builder()
            .timeout(Duration::from_secs(5))
            .build()
            .expect("build reqwest client");
        Self {
            http,
            ingest_url: format!("{}/api/events", base_url.trim_end_matches('/')),
            last_text: Mutex::new(String::new()),
        }
    }

    /// 启动后台采集循环（剪贴板每 3 秒轮询一次）。
    pub fn start(self: Arc<Self>) {
        tokio::spawn(async move {
            loop {
                self.poll_once().await;
                tokio::time::sleep(Duration::from_secs(3)).await;
            }
        });
    }

    async fn poll_once(&self) {
        // arboard 的 Clipboard 可能非 Send，且读取属阻塞 IO，放 spawn_blocking 里做
        let text = tokio::task::spawn_blocking(read_clipboard)
            .await
            .unwrap_or(None);
        let Some(text) = text else { return };

        // 过滤过短文本，避免把单字符/图标等噪声灌进图谱
        if text.chars().count() < 4 {
            return;
        }

        if *self.last_text.lock().unwrap() == text {
            return;
        }

        // 成功推送后才去重标记，失败则下轮重试
        if self.post_event("clipboard", "note", &text).await {
            *self.last_text.lock().unwrap() = text;
        }
    }

    /// 推送一条事件到 Java，返回是否成功。
    async fn post_event(&self, source: &str, event_type: &str, text: &str) -> bool {
        let body = IngestEvent {
            source,
            event_type,
            raw_text: text,
        };
        match self.http.post(&self.ingest_url).json(&body).send().await {
            Ok(r) if r.status().is_success() => true,
            Ok(r) => {
                eprintln!("[collector] 后端拒绝事件: {}", r.status());
                false
            }
            Err(e) => {
                eprintln!("[collector] 推送事件失败: {e}");
                false
            }
        }
    }

    /// 启动目录监听：把 `dir` 下新增/变动的文本文件推给 Java（source=file）。
    ///
    /// notify 的回调跑在它自己的内部线程里，这里用 tokio 无界通道把文件文本
    /// 桥接到异步任务统一走 reqwest 推送；`watch` 句柄必须保持存活，故本线程 park 住。
    pub fn start_file_watcher(self: Arc<Self>, dir: PathBuf) {
        let ingest_url = self.ingest_url.clone();
        let http = self.http.clone();

        let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel::<String>();

        std::thread::spawn(move || {
            let mut watcher = match notify::recommended_watcher(
                move |res: notify::Result<notify::Event>| {
                    if let Ok(event) = res {
                        for path in event.paths {
                            if let Some(text) = read_text_file(&path) {
                                let _ = tx.send(text);
                            }
                        }
                    }
                },
            ) {
                Ok(w) => w,
                Err(e) => {
                    eprintln!("[collector] 文件监听初始化失败: {e}");
                    return;
                }
            };
            if let Err(e) = watcher.watch(&dir, notify::RecursiveMode::Recursive) {
                eprintln!("[collector] 监听目录失败 {}: {e}", dir.display());
                return;
            }
            // 保持 watcher 存活（notify 事件在它自己的线程里继续回调）
            loop {
                std::thread::park();
            }
        });

        tokio::spawn(async move {
            let mut seen: HashSet<u64> = HashSet::new();
            while let Some(text) = rx.recv().await {
                let h = content_hash(&text);
                if seen.insert(h) {
                    let body = IngestEvent {
                        source: "file",
                        event_type: "note",
                        raw_text: &text,
                    };
                    let _ = http.post(&ingest_url).json(&body).send().await;
                }
            }
        });
    }
}

/// 读剪贴板文本；空串 / 纯空白视为无内容。
fn read_clipboard() -> Option<String> {
    let mut cb = arboard::Clipboard::new().ok()?;
    let text = cb.get_text().ok()?;
    let trimmed = text.trim().to_string();
    if trimmed.is_empty() {
        None
    } else {
        Some(trimmed)
    }
}

/// 文件监听支持的文本扩展名。
const TEXT_EXTS: [&str; 6] = ["txt", "md", "csv", "json", "log", "rst"];

fn is_text_file(path: &Path) -> bool {
    path.extension()
        .and_then(|e| e.to_str())
        .map(|e| TEXT_EXTS.contains(&e.to_ascii_lowercase().as_str()))
        .unwrap_or(false)
}

/// 读取文本文件内容；非文本 / 过大 / 空 / 读取失败都返回 None。
fn read_text_file(path: &Path) -> Option<String> {
    if !is_text_file(path) {
        return None;
    }
    let meta = std::fs::metadata(path).ok()?;
    if meta.len() > 100 * 1024 {
        return None; // 只读 <100KB，避免把大文件灌进图谱
    }
    let text = std::fs::read_to_string(path).ok()?;
    let trimmed = text.trim().to_string();
    if trimmed.is_empty() {
        None
    } else {
        Some(trimmed)
    }
}

/// 内容哈希，用于文件去重（同一内容不重复入图）。
fn content_hash(s: &str) -> u64 {
    let mut h = DefaultHasher::new();
    s.hash(&mut h);
    h.finish()
}
