//! Memu Tauri 桌面壳入口
//!
//! 职责边界：Tauri 只做系统采集、通知、UI 渲染，以及作为宿主通过 Supervisor
//! 拉起并监控 Java 后端与 Python 内核。业务逻辑在 Java，AI 在 Python。

mod collection;
mod supervisor;

use std::sync::Arc;

use collection::Collector;
use supervisor::{default_specs, Supervisor};
use tauri::Manager;

#[tauri::command]
async fn service_status(
    state: tauri::State<'_, Arc<Supervisor>>,
) -> Result<Vec<(String, bool)>, String> {
    Ok(state.status().await)
}

#[tauri::command]
async fn restart_service(
    name: String,
    state: tauri::State<'_, Arc<Supervisor>>,
) -> Result<(), String> {
    state.restart(&name).await
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let supervisor = Arc::new(Supervisor::new(default_specs()));

    tauri::Builder::default()
        .setup({
            let sup = supervisor.clone();
            move |app| {
                app.manage(sup.clone());
                tauri::async_runtime::spawn(async move {
                    sup.start_all().await;
                });

                // 系统采集：剪贴板轮询推给 Java（Java 未就绪时推送失败会下轮重试）
                let java_base_url = std::env::var("MEMU_JAVA_BASE_URL")
                    .unwrap_or_else(|_| "http://127.0.0.1:8080".to_string());
                let collector = Arc::new(Collector::new(java_base_url));
                collector.clone().start();

                // 文件监听（可选）：设置 MEMU_WATCH_DIR 才启用，监听目录下新增文本文件
                if let Ok(watch_dir) = std::env::var("MEMU_WATCH_DIR") {
                    if !watch_dir.trim().is_empty() {
                        let path = std::path::PathBuf::from(watch_dir);
                        if path.is_dir() {
                            collector.start_file_watcher(path);
                        } else {
                            eprintln!(
                                "[collector] MEMU_WATCH_DIR 不是目录，跳过文件监听: {}",
                                path.display()
                            );
                        }
                    }
                }

                Ok(())
            }
        })
        .invoke_handler(tauri::generate_handler![service_status, restart_service])
        .build(tauri::generate_context!())
        .expect("error while building tauri application")
        .run(|app_handle, event| {
            // 应用退出时回收所有子进程，避免 Python/Java 成为孤儿
            if let tauri::RunEvent::Exit = event {
                if let Some(sup) = app_handle.try_state::<Arc<Supervisor>>() {
                    let sup = sup.inner().clone();
                    tauri::async_runtime::block_on(async move {
                        sup.stop_all().await;
                    });
                }
            }
        });
}
