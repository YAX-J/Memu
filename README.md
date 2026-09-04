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
│  └─ src/main/java/com/memu/{kernel,config,event,suggestion,feedback,integration}
├─ kernel-python/       # FastAPI AI 内核
│  ├─ main.py           # 应用装配（lifespan 建库）
│  ├─ api/ engine/ extract/ graph/   # 路由 / 主动引擎 / 抽取 / 图谱
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
| `MEMU_JAVA_BASE_URL` | `http://127.0.0.1:8080` | 采集器推送事件的 Java 地址 |
| `MEMU_WATCH_DIR` | 空（不启用） | 文件监听目录；设置后监听该目录下新增的文本文件（.txt/.md/.csv/.json/.log/.rst）自动入图 |

### 内核配置（Python :8765，见 `config.py`）

| 变量 | 默认 | 说明 |
|---|---|---|
| `MEMU_OLLAMA_URL` | `http://127.0.0.1:11434` | 本地 Ollama 地址 |
| `MEMU_OLLAMA_MODEL` | `qwen2.5:7b` | 本地抽取模型 |
| `MEMU_CLOUD_URL` | 空（不启用） | 云端兜底地址（DashScope 或 OpenAI 兼容端点） |
| `MEMU_CLOUD_API_KEY` | 空 | 云端 API Key；`URL`+`KEY` 都配了才启用云端兜底 |
| `MEMU_CLOUD_MODEL` | `qwen-plus` | 云端模型名 |
| `MEMU_EMBEDDING_MODEL` | `paraphrase-multilingual-MiniLM-L12-v2` | 语义向量模型（装 sentence-transformers 后生效，否则回落 n-gram） |
| `MEMU_SUGGEST_THRESHOLD` | `0.60` | 主动建议置信度阈值 |

> 抽取降级链：**缓存 → 本地 Ollama → 云端 → 离线规则**。本地 Ollama 不可用时，
> 配了 `MEMU_CLOUD_URL`+`MEMU_CLOUD_API_KEY` 会自动回落云端（响应兼容 DashScope 原生
> `output.choices` 与 OpenAI 兼容 `choices` 两种格式），再不行才降级到零成本规则。

### 自动采集（Tauri 侧）

- **剪贴板**：每 3 秒轮询一次，发现新文本（≥4 字）自动推给 Java `/api/events`（source=clipboard）。
- **文件监听**：设置 `MEMU_WATCH_DIR` 后，监听目录下新增/变动的文本文件（source=file）。
- 两者都只做采集，抽取/建模在 Python 内核。

### 日历集成（Java 侧）

- 读本地 `.ics` 日历文件（任何日历都能「导出 .ics」），把日程转成事件入图（`source=calendar`）。
- 配置：`application.yml` 的 `memu.calendar.ics-path`（留空则禁用）。
- 同步：`IntegrationScheduler` 每 5 分钟自动拉增量，也可手动 `POST /api/integration/sync`。
- 增量：按日程 `UID` 去重，只摄入新出现的日程；重复同步不会重复入图。

### 邮件集成（Java 侧）

- 读本地 `.eml` 目录（任何邮件客户端都能「导出 .eml」），把邮件转成事件入图（`source=mail`）。
- 配置：`application.yml` 的 `memu.mail.eml-dir`（留空则禁用）。
- 解析：From/To/Subject/Date/正文，支持 Subject 的 `=?UTF-8?B?...?=` base64 编码与正文 base64。
- 幂等：日历用 `calendar:<UID>`、邮件用 `mail:<Message-ID>` 作稳定 eventId，内核按此去重，
  即使 Java 重启导致增量游标丢失，也不会重复入图。

## P0 验收链路

三端起来后，按顺序验证：

```bash
# 1) 内核健康
curl http://127.0.0.1:8765/health
# → {"ok":true,"service":"memu-kernel",...}

# 2) Java 健康（含内核可达性）
curl http://127.0.0.1:8080/api/health
# → {"backend":"ok","kernel":true}

# 3) 经 Java 入图一段含人名和事项的文字（eventId 由 Java 生成）
curl -X POST http://127.0.0.1:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{"source":"manual","type":"note","rawText":"和张三确认下周五的版本评审"}'

# 4) 检索（中文务必 URL 编码）
curl -G "http://127.0.0.1:8080/api/retrieve" --data-urlencode "q=张三"

# 5) 图谱子图
curl "http://127.0.0.1:8080/api/graph/subgraph?depth=2"
```

预期：第 3 步返回的 `entities` 里出现 `张三`（Person，需 Ollama 在线），第 4 步能检索到该事件（无需 Ollama）。

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

- [x] Python 内核（模块化，六条端点已运行时验证）
- [x] Java 后端（模块化 + 契约接通，已编译通过并端到端跑通）
- [x] Tauri 壳骨架（进程管理 + 四视图 + 剪贴板/文件自动采集）
- [x] P1 主动引擎：规律学习 + 建议 + 反馈闭环（含 feedbackScore 回流、Suggestion 持久化、实体消歧）
- [x] P1 双触发：定时轮询（SuggestionScheduler）+ 事件驱动（新事件命中习惯立即扫描，已运行时验证）
- [x] P1 混合检索：retrieve 升级为 embedding 向量召回 + 图谱扩散（n-gram 哈希兜底，语义模型接口已留）
- [x] P1 抽取缓存：持久化精确 hash（跨重启复用跳过 LLM）
- [x] P2 图谱可视化：subgraph 返回 Person/Topic/Event + 两类边，前端 d3-force 力导向图 + 离线 demo
- [ ] P1 可选：接入 sentence-transformers 语义向量（替代 n-gram，模糊相似度缓存需此）
- [x] P2 云端兜底：call_cloud 兼容 DashScope/OpenAI 两类响应，降级链已 mock 端到端验证
- [x] P2 日历集成：本地 ICS 文件解析 → 日程入图（source=calendar），增量 UID 去重已验证
- [x] P2 邮件集成：本地 .eml 目录解析 → 邮件入图（source=mail），稳定 eventId 幂等
- [x] P2：打包分发（图标已生成、bundle 已启用，产物：`app/src-tauri/target/release/bundle/nsis/Memu_0.1.0_x64-setup.exe` + `.msi`）

## 打包分发

产物位于 `app/src-tauri/target/release/`：NSIS 安装器（`bundle/nsis/*-setup.exe`，推荐）、
MSI（`bundle/msi/*.msi`）、原始可执行（`memu.exe`，免安装直跑）。

> ⚠️ **当前安装包只携带 Tauri 壳**（WebView + Rust 采集/进程管理），**不含** Python 内核
> 解释器与依赖、Java jar、JRE。双击安装后，壳会按 `MEMU_PYTHON` / `MEMU_JAVA` /
> `MEMU_JAVA_JAR` 拉起两个 sidecar——目标机需满足：
>
> 1. Java 21+（或设 `MEMU_JAVA` 指向）；
> 2. 带内核依赖的解释器（kuzu / fastapi / uvicorn / numpy 等），经 `MEMU_PYTHON` 指定
>    （默认裸 `python` 通常缺这些依赖，需用内核 venv，见「故障排查」）；
> 3. jar 经 `MEMU_JAVA_JAR` 指定（`mvn package -DskipTests` 产出）。
>
> 若要做「双击即用、零依赖」的独立安装包，需把内核 venv + jar + JRE 一并 bundle
> （内核用 PyInstaller 打成独立 exe，jar/JRE 走 Tauri bundle resources），属后续架构项。

## 测试

```bash
cd kernel-python
pip install pytest
python -m pytest -q        # 25 个用例：实体消歧 / 规则抽取 / 习惯评分 / 嵌入 / LLM 调度
```

## 故障排查

- **Tauri 拉起内核时反复重启 / 内核起不来**：`Supervisor` 默认用裸 `python`（PATH 上的），
  通常缺 `kuzu`/`fastapi`/`uvicorn`。把 `MEMU_PYTHON` 指向装好依赖的内核 venv 即可：
  ```bash
  export MEMU_PYTHON="C:/Users/Administrator/.workbuddy/binaries/python/envs/default/Scripts/python.exe"
  # 或自行 venv：python -m venv .venv && .venv/Scripts/pip install -r kernel-python/requirements.txt
  ```
  同理，`MEMU_JAVA` 指向 Java 21、`MEMU_JAVA_JAR` 指向 `mvn package` 产出的 jar。
- **`mvn` 报 `org.codehaus.plexus.classworlds.launcher.Launcher` ClassNotFound**：
  本机 PATH 上的 maven 损坏。可改用 wrapper 版 Maven，或直接用 java 调 classworlds 启动器：
  ```bash
  MVN_HOME="C:/Users/Administrator/.m2/wrapper/dists/apache-maven-3.9.16-bin/5grr65jo27hi51sujmtcldfovl/apache-maven-3.9.16"
  JAVA_HOME="C:/Program Files/Java/jdk-21.0.11" \
  java -cp "$MVN_HOME/boot/plexus-classworlds-2.11.0.jar" \
    "-Dclassworlds.conf=$MVN_HOME/bin/m2.conf" "-Dmaven.home=$MVN_HOME" \
    "-Dmaven.multiModuleProjectDirectory=$PWD" \
    org.codehaus.plexus.classworlds.launcher.Launcher -q -DskipTests package
  ```
