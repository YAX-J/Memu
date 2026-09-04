//! Memu Tauri 桌面壳入口
//!
//! 职责边界：Tauri 只做系统采集、通知、UI 渲染，以及作为宿主通过 Supervisor
//! 拉起并监控 Java 后端与 Python 内核。业务逻辑在 Java，AI 在 Python。

mod supervisor;

use std::sync::Arc;

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
