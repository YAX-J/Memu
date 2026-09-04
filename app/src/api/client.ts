// 前端统一调用 Java 业务后端（:8080）。Java 再调 Python 内核，前端不直连内核。
// 这样图谱/模型的访问权限收口在 Java，前端只看到业务 API。

const BASE = "http://127.0.0.1:8080";

export interface SuggestionAction {
  type: string;
  payload: Record<string, unknown>;
}

export interface Suggestion {
  id: string;
  title: string;
  reason: string;
  confidence: number;
  actions: SuggestionAction[];
}

export interface SuggestResponse {
  suggestions: Suggestion[];
  coldStart: boolean;
}

export interface ServiceStatus {
  name: string;
  alive: boolean;
}

export interface GraphNode {
  id: string;
  type: string;
  label: string;
}
export interface GraphEdge {
  source: string;
  target: string;
  type: string;
}
export interface Subgraph {
  nodes: GraphNode[];
  edges: GraphEdge[];
}

async function jsonOrThrow<T>(r: Response): Promise<T> {
  if (!r.ok) throw new Error(`${r.status} ${r.statusText}`);
  return (await r.json()) as T;
}

export async function refreshSuggestions(): Promise<SuggestResponse> {
  return jsonOrThrow(await fetch(`${BASE}/api/suggestions/refresh`, { method: "POST" }));
}

export async function sendFeedback(
  suggestionId: string,
  action: "accepted" | "snoozed" | "dismissed"
): Promise<void> {
  await fetch(`${BASE}/api/feedback`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ suggestionId, action }),
  });
}

export async function fetchSubgraph(): Promise<Subgraph> {
  // 经 Java 代理转发到内核 /kernel/graph/subgraph
  return jsonOrThrow(await fetch(`${BASE}/api/graph/subgraph?depth=2`));
}

// 进程状态走 Tauri 命令（Rust 侧 Supervisor），不在 Java
export async function fetchServiceStatus(): Promise<ServiceStatus[]> {
  try {
    const { invoke } = await import("@tauri-apps/api/core");
    return await invoke<ServiceStatus[]>("service_status");
  } catch {
    // 非 Tauri 环境（纯浏览器调试）时返回空
    return [];
  }
}

export async function restartService(name: string): Promise<void> {
  const { invoke } = await import("@tauri-apps/api/core");
  await invoke("restart_service", { name });
}
