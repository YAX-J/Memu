# Memu

> 主动式助手：构建本地知识图谱记录历史、偏好与习惯，不等指令，主动提出建议。
> 全部数据本地运行，靠优化信息处理降低成本。

## 架构

三进程协同。**进程管理收口到 Tauri，不让 Java 拉起 Python。**

| 进程 | 路径 | 端口 | 职责 |
|---|---|---|---|
| Tauri 桌面壳 | `app/` | — | 系统采集、通知托盘、UI、**宿主拉起并监控另两个进程** |
| Java 业务后端 | `backend-java/` | 8080 | 事件总线、建议生命周期、反馈闭环、集成适配 |
| Python AI 内核 | `kernel-python/` | 8765 | 抽取、嵌入、图谱(Kùzu)、检索、**主动引擎**、LLM 调度 |

通信：
- Tauri ↔ Java：HTTP + WebSocket（`/ws` → `/topic/suggestions`）
- Java ↔ Python：HTTP REST（`127.0.0.1:8765`）
- Python ↔ Ollama：HTTP（`127.0.0.1:11434`）

```
memu/
├─ app/                 # Tauri 2 + Vue 3 + TS
│  ├─ src/              # 前端：建议 / 时间线 / 图谱 / 设置
│  └─ src-tauri/        # Rust：Supervisor 进程管理 + 命令
├─ backend-java/        # Spring Boot 3.3.5 + Java 21
│  └─ src/main/java/com/memu/{config,client,event,suggestion,feedback,integration}
├─ kernel-python/       # FastAPI AI 内核
│  ├─ main.py           # 4 个契约接口
│  └─ schema.cypher     # Kùzu 图谱 DDL
└─ docs/DESIGN.md       # 完整架构设计
```

## 前置依赖

- **Node** 18+（前端 + Tauri CLI）
- **Rust** stable（cargo，Tauri 编译）
- **Java** 21 + **Maven** 3.9
- **Python** 3.11+
- **Ollama** 已安装，并拉取模型：`ollama pull qwen2.5:7b`

## 启动

### 方式一：各自手动（联调最直观，推荐先用这个）

```bash
# 1) Python 内核
cd D:/project/memu/kernel-python
pip install -r requirements.txt
ollama serve            # 另开一个终端
uvicorn main:app --host 127.0.0.1 --port 8765 --reload

# 2) Java 后端
cd D:/project/memu/backend-java
mvn spring-boot:run     # 首次会下载依赖，较慢

# 3) Tauri 前端（dev）
cd D:/project/memu/app
npm install
npm run tauri dev       # 需要 Rust 工具链；会编译并打开窗口
```

### 方式二：Tauri 统一拉起（开发完成后再用）

`npm run tauri dev` 启动后，Rust 侧 Supervisor 会自动拉起 Python 与 Java 两个子进程，
进程退出或健康探测失败自动重启，应用退出时回收。需要先：

- Java 打成 jar：`cd backend-java && mvn package -DskipTests`
- 设置环境变量（见下），或用默认相对路径（dev 时相对 `app/src-tauri`）

### 环境变量（仅方式二需要，方式一可忽略）

| 变量 | 默认 | 说明 |
|---|---|---|
| `MEMU_ROOT` | `../..` | 项目根（相对 src-tauri） |
| `MEMU_KERNEL_CWD` | `$MEMU_ROOT/kernel-python` | Python 内核工作目录 |
| `MEMU_JAVA_JAR` | `$MEMU_ROOT/backend-java/target/memu-backend-0.1.0.jar` | Java jar 路径 |
| `MEMU_PYTHON` | `python` | Python 可执行 |
| `MEMU_JAVA` | `java` | Java 可执行 |

## P0 验收链路

三端起来后，按顺序验证：

```bash
# 1) 内核健康
curl http://127.0.0.1:8765/health
# → {"ok":true,"service":"memu-kernel",...}

# 2) Java 健康与 CORS
curl http://127.0.0.1:8080/actuator/health

# 3) 经 Java 入图一段含人名和事项的文字
curl -X POST http://127.0.0.1:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{"eventId":"e-1","source":"manual","type":"note","rawText":"和张三确认下周五的版本评审"}'

# 4) 检索
curl "http://127.0.0.1:8080/api/retrieve?q=张三"

# 5) 图谱子图
curl "http://127.0.0.1:8080/api/graph/subgraph?depth=2"
```

预期：第 3 步返回的 `entities` 里出现 `张三`（Person），第 4 步能检索到该事件。

## 端口速查

| 端口 | 服务 |
|---|---|
| 1420 | Tauri 前端 dev server |
| 8080 | Java 业务后端 |
| 8765 | Python AI 内核 |
| 11434 | Ollama |

## 关键设计原则（务必保持）

- **主动引擎不调 LLM**：规律学习用 Cypher 聚合 + 统计，这是「成本更低」的主要来源。
- **反馈闭环是必需件**：没有它，主动式助手会在三周内被用户关掉通知。
- **Java 不拉起 Python**：进程生命周期收口在 Tauri Supervisor。
- 详见 `docs/DESIGN.md`。

## 状态

- [x] Python 内核骨架（P0 可跑，P1 引擎已成型）
- [x] Java 后端骨架（契约已接通，编排逻辑 P1 补）
- [x] Tauri 壳骨架（进程管理 + 四视图占位）
- [ ] P1：Suggestion 独立表、embedding 缓存、实体消歧、定时触发
- [ ] P2：日历/邮件集成、图谱可视化、云端兜底接入、打包分发
