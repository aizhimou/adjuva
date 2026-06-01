# Adjuva Technical Choices

本文档记录 Adjuva 当前阶段已经确定的技术选型和关键设计边界。

本文档是总览性质文件，不是 implementation spec。具体 schema、状态机、字段、接口、worker 流程、subprocess 细节，应在后续实际设计和开发过程中逐步补充。

本文档基于 [Adjuva Core Positioning.md](Adjuva%20Core%20Positioning.md) 中定义的产品定位。

---

## 1. 总体技术方向

Adjuva 是一个 async project assistant。

它不是 real-time chatbot，不是 Coding Workspace，也不是 Agent Runtime。

技术栈需要支持：

- 长期运行的 server
- durable state transition
- background execution
- conversation mailbox
- git-backed Project Workspace
- controlled subprocess execution

核心架构方向是：

```text
Git-backed Project Workspace
+
Control Plane DB
+
Spring Boot Backend
+
Background Workers
+
Agent Runtime CLI
```

---

## 2. Backend 技术栈

Backend 暂定使用：

- Java 21
- Spring Boot 4
- Spring MVC
- H2
- Flyway
- MyBatis
- MyBatis Plus
- db-scheduler
- JDK `ProcessBuilder`
- JDK `ProcessHandle`

选择这套技术栈的主要原因：

- 主要开发者熟悉 Java 和 Spring Boot 生态
- 适合长期运行的 server-side application
- 适合构建 REST API、Conversation Mailbox、background workers 和 scheduler
- 适合实现 transaction-based control plane
- 可以较自然地集成本机 CLI runtime
- 不引入额外的大型技术学习成本和外部基础设施依赖

Backend 应按 async control plane 设计，而不是按普通 REST CRUD application 设计。

核心模式是：

```text
API receives intent
↓
DB records intent
↓
Worker executes intent
↓
Outbox reports result
```

---

## 3. 持久化模型

Adjuva 使用 hybrid persistence model：

```text
Git-backed Project Workspace
+
Control Plane DB
```

### Git-backed Project Workspace

Project Workspace 存放 human-editable 和 agent-editable 的 project truth。

适合放在 workspace 中的内容包括：

- memory
- decision records
- decision policy
- reports
- drafts
- artifacts
- project-level context
- reusable background knowledge

这些内容应尽量使用 plain text 或其他易 review 的文件格式存储，并通过 git 获得 version history、diff 和 rollback 能力。

### Control Plane DB

Control Plane DB 存放 machine-managed runtime state。

适合放在 DB 中的内容包括：

- task state
- executor session state
- approval state
- schedule state
- mailbox message state
- outbox state
- artifact metadata
- event / audit data

基本原则：

```text
Plain text / git = Project truth
Control Plane DB = Runtime state truth
```

---

## 4. 数据库选型

Control Plane DB 暂定使用：

```text
H2
```

默认部署形态应使用 H2 file database，而不是 in-memory database。

选择 H2 的原因：

- Adjuva MVP 是 single-server control plane，不需要过早引入外部 DB server
- H2 对当前阶段的 runtime state、mailbox message state、schedule state 和 audit data 足够轻量
- H2 与 Spring Boot、Flyway、MyBatis、MyBatis Plus 集成简单
- H2 可以通过同一个 `DataSource` 同时服务业务表和 db-scheduler 表
- H2 支持当前阶段需要的 transaction、index、relational schema 和基本 locking model
- 本地开发、测试、demo 和单机部署体验更简单

H2 使用边界：

- 使用 file mode 保存 durable state
- 不把 H2 当作 multi-node、high-concurrency production database
- 不依赖 H2-specific features 设计业务 schema
- 对重要数据建立明确 backup / export 策略
- schema migration 继续由 Flyway 管理
- db-scheduler 的 `scheduled_tasks` 表也由 Flyway migration 管理，并尽早用 H2 integration test 验证

PostgreSQL 或 MySQL 仍然是未来可选项。只有出现以下需求时才考虑迁移：

- 多实例部署
- 更高并发写入
- 更强 observability / backup / replication 需求
- H2 locking、性能或运维边界成为明确瓶颈

数据库设计原则：

- relational first
- 重要状态使用 transaction 管理
- retryable operation 需要考虑 idempotency
- worker claim 需要避免重复处理
- 核心状态变化应显式、可审计
- 不把 db-scheduler metadata 当作业务状态的唯一 source of truth
- 尽量避免过早依赖 vendor-specific database features

这些原则用于指导后续 schema 和 state machine 设计，但不在本文档中提前展开具体表结构和字段。

---

## 5. Data Access 选型

Data access 暂定使用：

```text
MyBatis
+
MyBatis Plus
```

基本原则：

- MyBatis Plus 用于简单 CRUD，减少样板代码
- MyBatis 用于需要显式 SQL 的核心状态操作
- 核心 state transition 不应被 generic CRUD abstraction 隐藏

具体 mapper、SQL、transaction boundary 后续在实现阶段定义。

---

## 6. Background Execution / Scheduling

Background scheduling 暂定使用：

```text
db-scheduler
```

db-scheduler 适合用于：

- scheduled triggers
- recurring jobs
- delayed jobs
- maintenance jobs
- timeout/watchdog jobs
- lightweight DB-backed execution

核心边界：

- db-scheduler 是 infrastructure
- Adjuva 自己的 Control Plane DB 才是 orchestration truth
- scheduled task 只负责触发业务动作，不保存业务 truth
- task handler 应从 DB 读取当前业务状态后再执行
- task handler 需要能安全 retry
- long-running Agent work 应建模为 Adjuva `Run`，而不是隐藏在 scheduler metadata 中
- scheduler metadata 只用于 timer、claim、retry 和 execution bookkeeping

选择 db-scheduler 的原因：

- 比 Quartz、Temporal 这类外部调度 / workflow 框架更轻
- 与 Spring Boot 和 JDBC `DataSource` 集成直接
- Spring Boot 4 可使用 `db-scheduler-spring-boot-4-starter`
- 可以复用 H2 Control Plane DB，不需要额外部署 Redis、worker service 或独立 scheduler server
- 足够覆盖 MVP 需要的 scheduled task、background trigger、timeout watchdog 和 maintenance job
- 保持 Adjuva 的 orchestration model 简单、可观察、可替换

Adjuva 不应过早引入重量级外部调度框架。

后续只有出现以下需求时才重新评估：

- 多节点 worker coordination
- 复杂 workflow DAG
- 长时间 human-in-the-loop workflow engine
- 大规模 job retry / rate limiting / priority queue
- 独立 scheduler cluster 运维需求

具体 job 类型、retry 策略和 worker 流程后续再定义。

---

## 7. Subprocess Execution 选型

Adjuva 暂定使用 JDK 原生能力调用外部 Agent Runtime CLI：

```text
JDK ProcessBuilder
+
JDK ProcessHandle
```

目标 runtime 包括：

- Codex CLI
- Gemini CLI
- Claude Code

基本原则：

- 使用 non-interactive CLI mode 作为主要集成方式
- 不把 PTY / tmux bridge 作为核心协议
- 不直接在业务代码中散落 raw `ProcessBuilder`
- 需要封装内部 process supervision 能力
- 大输出不应全量读入内存
- stdout / stderr / artifacts 应有清晰的存储策略
- timeout、cancel、exit code、failure 都应成为可记录状态

具体 `ProcessSupervisor` 设计、输出解析、日志存储、cancel 策略后续单独设计。

---

## 8. Spring Boot Application Shape

应用形态暂定为：

```text
Modular Monolith
```

原因：

- 当前阶段不需要 microservices
- 单体应用更容易开发、部署、调试和演进
- 仍然可以通过清晰 module boundary 控制复杂度

预期模块方向包括：

- project
- workspace
- task
- session
- executor
- scheduler
- approval
- decision policy
- message
- artifact
- outbox
- admin

这些模块是方向性划分，不是最终 package structure。

具体 module boundary 后续随实现演进。

---

## 9. Frontend 选型

Frontend 暂定使用：

```text
Responsive Web App / PWA
+
Vite
+
React
+
JavaScript
+
Mantine UI v9
+
TanStack Query
+
SSE
+
Web Push
```

Frontend 不使用后端 template engine。

原因：

- 前端需要成熟的 app shell、responsive layout、PWA、SSE、Web Push 和 client-side state 管理能力
- 后端应保持 API-first，专注 control plane、executor orchestration 和 persistence
- 前后端可以独立迭代，避免把 Spring MVC template 作为长期 UI 基础

开发语言使用 JavaScript，不使用 TypeScript。

原因：

- MVP 是小型工具，过早引入 TypeScript 类型建模和抽象会增加启动成本
- 业务模型仍在快速变化，JavaScript 更利于快速调整
- 通过清晰 API contract、集中 data access layer 和 focused tests 控制风险

Mantine UI v9 是 MVP 的 UI 组件和 app shell 基础。

原因：

- Mantine 是成熟的 React component library，覆盖 layout、form、navigation、overlay、feedback、data display 等常用 UI primitives
- Mantine v9 支持 Vite + React 的直接集成，并通过 `MantineProvider`、theme 和 color scheme 管理应用级 UI 基础
- Mantine 的 responsive primitives 和 component props 更适合同时构建 desktop 与 mobile 页面
- 不需要从 unstyled primitives 自己设计视觉系统
- 默认视觉质量稳定，可在 MVP 阶段减少 design system 成本
- 与 TanStack Query、SSE、Web Push 等前端 infrastructure 没有架构冲突

Realtime 更新策略：

- desktop 和 mobile app foreground 都使用 SSE 接收 Conversation / Mailbox / Run 状态变化
- desktop 和 mobile app background / outside-app 都使用 Web Push 提醒重要事件
- Web Push 只用于关键事件，不用于同步完整状态
- 重新打开 app 后以 API fetch 为 source of truth

关键 notification event 包括：

- `waiting_for_user`
- `run_completed`
- `run_failed`
- `conversation_updated`

Approval 相关提醒在 MVP 中不单独引入新的 Conversation status。若需要提醒用户确认业务决策，应先通过 `waiting_for_user` status 和 mailbox message type 表达。

Web Push 在 MVP 中作为 web app / PWA 能力处理，不引入额外 native app 平台规划和设备能力抽象。

MVP frontend 的功能边界、信息架构、layout 和 agent 开发原则，单独记录在 [Adjuva MVP Frontend Development Guide.md](Adjuva%20MVP%20Frontend%20Development%20Guide.md)。
