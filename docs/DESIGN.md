# Memu 架构设计

> 定位：**主动式助手**。构建本地知识图谱记录历史、偏好与习惯，不等指令，基于对你的了解主动提出建议。
> 全部数据本地运行，通过优化信息处理降低运行成本。

---

## 1. 进程边界与职责

三进程协同。**核心原则：AI 与图谱只在 Python，业务编排与集成只在 Java，系统采集与触达只在 Tauri。**

| 进程 | 技术 | 职责 | 不做什么 |
|---|---|---|---|
| **Tauri 桌面壳** | Rust + Vue 3 + TS | 系统级采集（文件/剪贴板/活跃窗口）、系统通知与托盘、UI 渲染、**作为宿主通过 sidecar 拉起并监控另外两个进程** | 不做业务逻辑、不碰图谱 |
| **Java 业务后端** | Spring Boot 3 | 事件总线、建议生命周期管理、反馈闭环、集成适配（日历/邮件）、配置与权限 | 不做模型推理、不直接读写图谱 |
| **Python AI 内核** | FastAPI | 实体/关系抽取、嵌入、图谱读写（Kùzu）、向量检索、**主动引擎**、LLM 调度（本地 + 云端兜底） | 不做 UI、不做业务编排 |

### 通信方式

| 链路 | 协议 | 说明 |
|---|---|---|
| Tauri ↔ Java | HTTP REST + WebSocket | WebSocket 用于主动建议实时推送 |
| Java ↔ Python | HTTP REST（localhost） | 同机调用，JSON 足矣；性能瓶颈出现再换 gRPC |
| Python ↔ Ollama | HTTP（`localhost:11434`） | 本地模型，零 API 成本 |
| Python ↔ 云端 LLM | HTTPS | 仅在置信度不足或用户显式请求时调用 |

**建议**：进程管理放 Tauri（sidecar），不要放 Java。Java 不负责拉起 Python，只做 HTTP 调用 —— 否则进程生命周期耦合，排障会很难受。

---

## 2. 目录结构

```
memu/
├─ app/                          # Tauri 桌面壳
│  ├─ src-tauri/                 # Rust：系统采集 / 托盘 / 通知 / sidecar 进程管理
│  └─ src/                       # Vue 3 前端：建议卡片流 / 时间线 / 图谱可视化 / 设置
├─ backend-java/                 # Spring Boot 3 业务后端
│  └─ src/main/java/com/memu/
│     ├─ event/                  # 事件总线（采集入口 → 内核）
│     ├─ suggestion/             # 建议生命周期（生成/推送/采纳/忽略/静默）
│     ├─ feedback/               # 反馈闭环与权重回流
│     └─ integration/            # 日历 / 邮件等外部集成
├─ kernel-python/                # FastAPI AI 内核
│  ├─ main.py                    # API 入口（4 个契约接口）
│  ├─ schema.cypher              # 图谱 DDL
│  ├─ graph/                     # Kùzu 读写封装
│  ├─ extract/                   # 实体/关系抽取（Ollama + 云端兜底）
│  ├─ engine/                    # 主动引擎：规律学习 / 偏好建模 / 触发
│  └─ requirements.txt
└─ docs/
   └─ DESIGN.md
```

---

## 3. 图谱 Schema

### 节点

| 节点 | 关键属性 | 说明 |
|---|---|---|
| `Person` | `id, name, role, relation` | 人（同事、家人、联系人） |
| `Project` | `id, name, status` | 项目 |
| `Task` | `id, title, status, dueAt` | 任务 |
| `Topic` | `id, name` | 主题 / 兴趣 |
| `Event` | `id, type, source, rawText, occurredAt` | 原始事件（一切的输入） |
| `Preference` | `id, key, value, weight` | 偏好，带权重 |
| `Habit` | `id, pattern, period, confidence` | 习惯（规律学习产出） |

所有节点统一带 `createdAt / updatedAt / confidence`，支持时间切片查询。

### 边

| 边 | 语义 |
|---|---|
| `(Person)-[:PARTICIPATES_IN]->(Event)` | 人参与事件 |
| `(Person)-[:HAS_PREFERENCE]->(Preference)` | 人持有偏好 |
| `(Person)-[:FOLLOWS]->(Habit)` | 人遵循习惯 |
| `(Event)-[:RELATES_TO]->(Topic \| Project \| Task)` | 事件关联对象 |
| `(Event)-[:PRECEDES]->(Event)` | 事件时序（用于序列模式） |
| `(Task)-[:BELONGS_TO]->(Project)` | 任务归属 |

> 完整 DDL 见 `kernel-python/schema.cypher`。

---

## 4. Java ↔ Python 接口契约

统一前缀 `http://127.0.0.1:8765`，JSON。

### 4.1 事件入图 `POST /kernel/ingest`

```jsonc
// request
{ "eventId": "e-1024", "source": "clipboard", "type": "note",
  "occurredAt": "2026-09-04T09:30:00+08:00",
  "rawText": "和张三确认下周五的版本评审", "meta": {} }

// response
{ "eventId": "e-1024",
  "entities":  [{ "id": "p-zhangsan", "type": "Person", "name": "张三" }],
  "relations": [{ "from": "p-zhangsan", "to": "e-1024", "type": "PARTICIPATES_IN" }],
  "confidence": 0.82, "llmUsed": "local" }
```

### 4.2 混合检索 `POST /kernel/retrieve`

```jsonc
// request  { "query": "最近和张三聊过什么", "topK": 10, "timeRange": ["2026-08-01", "2026-09-04"] }
// response { "results": [{ "nodeId": "e-1024", "type": "Event", "score": 0.87, "snippet": "..." }] }
```

### 4.3 生成候选建议 `POST /kernel/suggest`

```jsonc
// response
{ "suggestions": [{
    "id": "s-77", "title": "要不要现在约张三做版本评审？",
    "reason": "你通常在周四下午安排评审，且上次评审已过 7 天",
    "confidence": 0.71,
    "actions": [{ "type": "open_app", "payload": {} }]
}]}
```

### 4.4 反馈回流 `POST /kernel/feedback`

```jsonc
// request { "suggestionId": "s-77", "action": "dismissed", "at": "..." }
// response { "ok": true, "patternMuted": false, "newWeight": 0.58 }
```

---

## 5. 主动引擎设计（核心，也是最难的部分）

> 关键设计：**规律学习用图查询做统计，不调 LLM。** 这是"成本更低"的主要来源。

### 5.1 规律学习 — 周期性检测

对事件按 `(type, 关联实体)` 分组，取时间戳序列，计算：

```
intervals  = 相邻事件时间差序列
consistency = 1 - (std(intervals) / mean(intervals))     # 越稳定越接近 1
freqScore   = min(1, log(1 + count) / log(1 + 20))        # 频次饱和
recency     = exp(-λ · daysSinceLast), λ ≈ 0.05           # 近期性衰减
period      = median(intervals) → 归一到 {日 / 周 / 月}
```

产出 `Habit` 节点，并预测下次发生时间：`nextAt = lastAt + period`。

### 5.2 偏好建模 — EWMA 加权

每条 `Preference` 边带 `weight`，随反馈平滑更新：

```
w_new = (1 - α) · w_old + α · reward,  α = 0.2
reward: accepted = 1.0, snoozed = 0.5, dismissed = 0.0
```

### 5.3 触发与置信度

```
confidence = clamp(0.35·consistency + 0.25·freqScore
                 + 0.25·recency + 0.15·feedbackScore, 0, 1)
```

- **双触发**：定时扫描（临近 `nextAt`） + 事件驱动（新事件命中已有 `Habit` 模式）
- **推送阈值** `confidence ≥ 0.60`（用户可调）

### 5.4 反馈闭环与防打扰（必需件，不是可选项）

| 用户动作 | 处理 |
|---|---|
| accepted | 权重 +，记录为正样本 |
| snoozed | 权重微增，延后提醒 |
| dismissed | 权重 − |
| **同一 pattern 连续 dismissed 3 次** | **静默该 pattern，不再推送** |

没有这条回路，主动式助手三周内就会被用户关掉通知。

---

## 6. 成本优化策略（"运行成本更低"如何兑现）

| # | 策略 | 效果 |
|---|---|---|
| 1 | 本地 Ollama（Qwen2.5:3b/7b）扛全部高频抽取 | 云端调用量降一个数量级 |
| 2 | Embedding 相似度缓存命中 → 跳过 LLM | 重复/近似输入零推理 |
| 3 | 增量抽取，只处理新增/变更事件 | 避免全量重算 |
| 4 | 规律统计用 Cypher 聚合，**不用 LLM** | 主动引擎主体零 token |
| 5 | 批量合并抽取请求 | 摊薄单次开销 |
| 6 | 云端调用配额 + 置信度门控 | 兜底可控，成本有上限 |

---

## 7. 分阶段落地计划

### P0 · 骨架打通（记忆底座）

- Tauri 壳起来，sidecar 拉起 Java + Python，健康检查通过
- 采集：剪贴板 + 指定目录文件监听
- `POST /kernel/ingest` 抽取入图（先接 Ollama）
- Vue 界面能看到实体与最近事件

**验收**：复制一段含人名和事项的文字 → 图谱出现对应实体 → UI 检索得到结果。

### P1 · 主动引擎 MVP（差异化）

- 规律学习 + 周期性检测 → 生成 `Habit`
- 定时触发 + 系统通知推送建议卡片
- 反馈闭环（采纳/忽略 → 权重回流）

**验收**：连续几天重复同一行为 → 收到预测建议 → 连续忽略 3 次后该建议不再出现。

### P2 · 集成与分发

- 日历 / 邮件集成，图谱可视化，云端 LLM 兜底接入，打包分发

---

## 8. 风险与坑

| 风险 | 等级 | 应对 |
|---|---|---|
| **主动变打扰** | P0 | 置信度阈值 + 反馈静默机制，见 5.4 |
| 三进程复杂度（排障/打包/启停） | P1 | 进程管理收口到 Tauri sidecar；统一日志与端口配置 |
| 图谱漂移（实体冲突、重复节点） | P1 | 实体消歧：先按 embedding 相似度合并，再落人工确认队列 |
| 冷启动无数据 → 无建议 | P2 | 冷启动期只做检索不做主动推送，积累 N 天后再开启引擎 |
| Java ↔ Python 联调成本 | P2 | 契约先定（见第 4 节），用 mock 并行开发 |
