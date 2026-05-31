# Adjuva Database and State Machine Design

本文档定义 Adjuva MVP 阶段的 Control Plane DB 表结构和核心状态机。

设计依据：

- [Adjuva Core Positioning.md](Adjuva%20Core%20Positioning.md)
- [POC Validation Summary.md](../poc/POC%20Validation%20Summary.md)
- [Conversation Mailbox Interaction Model.md](Conversation%20Mailbox%20Interaction%20Model.md)
- [Adjuva Technical Choices.md](Adjuva%20Technical%20Choices.md)

本文档关注 Adjuva 自己管理的业务表。`db-scheduler` 自带的基础设施表由 db-scheduler 负责，不在本文档中展开。

---

## 1. 设计范围

Adjuva 的核心模型是：

```text
Project
  -> Conversation
      -> Mailbox Messages
      -> Runs
      -> Provider Session
  -> Artifacts
```

核心原则：

- `Conversation` 是用户看到的 chat window，也是 user message routing 的主状态载体。
- `Mailbox Message` 是 user / agent / system 之间的异步消息流。
- `Run` 是一次 executor subprocess execution record，不承载复杂业务状态。
- `Provider Session` 保存 Codex / Gemini / Claude 等 runtime 的 external session id，用于 resume。
- `Artifact` 只保存 metadata。真实内容优先放在 Project Workspace 中。
- `Outbox Event` 用于可靠发送 UI notification、SSE notification 和后续 webhook 集成。

---

## 2. 数据库设计原则

### 2.1 SQL 可迁移性

MVP 当前使用 H2 file database，但 schema 应尽量保持通用 SQL：

- 不使用 H2-specific function、identity syntax、computed column、JSON column、array type。
- ID 由 application 生成，使用 `VARCHAR(64)`，不依赖 database sequence。
- 时间字段使用 `TIMESTAMP`，application 统一写入 UTC 时间。
- 枚举值使用 `VARCHAR`，由 application validation 控制。
- 结构化扩展字段使用 `CLOB` 保存 JSON text，而不是 database JSON type。
- schema migration 使用 Flyway，但 migration SQL 不绑定 H2 方言。

### 2.2 逻辑外键

所有关联字段都是 logical foreign key，不创建 physical foreign key constraint。

原因：

- MVP 单人开发阶段需要更灵活的 migration 和 data repair。
- 后续迁移到 PostgreSQL / MySQL 时减少 vendor behavior 差异。
- background worker 和 executor failure 可能留下中间态，application 更适合做恢复和校验。

约束方式：

- application service 在写入前校验 referenced record 是否存在。
- 使用 index 保证查询效率。
- 定期 maintenance job 扫描 orphan logical references。

### 2.3 审计字段

每张业务表必须包含：

```sql
created_at TIMESTAMP NOT NULL,
updated_at TIMESTAMP NOT NULL
```

约定：

- application 写入 UTC。
- insert 时 `created_at = updated_at`。
- update 时只更新 `updated_at` 和实际变化字段。
- append-only event 表也保留 `updated_at`，通常等于 `created_at`。

### 2.4 冗余字段策略

默认避免冗余字段。允许保留少量 routing pointer 和 list optimization：

- `conversations.status`: UI routing 和 user message routing 的 source of truth，不从 run/message 临时推导。
- `conversations.active_run_id`: 当前 active subprocess 的 routing pointer，避免每次用户发消息都扫 `runs`。
- `conversations.pending_question_message_id`: 当前待回答问题的 routing pointer，避免多 question 场景歧义。
- `conversations.last_activity_at`: conversation list 排序优化。

这些字段必须由同一个 transaction 维护。不要在多个表维护同一个 status 值。

---

## 3. 实体关系概览

```text
projects
  logical 1 - n conversations
  logical 1 - n automation_schedules

conversations
  logical 1 - n runs
  logical 1 - n mailbox_messages
  logical 1 - n provider_sessions
  logical 1 - n artifacts

provider_sessions
  logical 1 - n runs

runs
  logical 1 - n mailbox_messages
  logical 1 - n artifacts

outbox_events
  drives SSE / notification / integration delivery
```

所有表都不使用 physical `FOREIGN KEY`。

---

## 4. 核心表

### 4.1 projects

`projects` 是 Adjuva 的长期协作单位。Project Workspace 中保存 human-editable project truth，例如 memory、decision records、reports、drafts 和 artifacts。

```sql
CREATE TABLE projects (
  id VARCHAR(64) NOT NULL,
  name VARCHAR(200) NOT NULL,
  slug VARCHAR(120) NOT NULL,
  description CLOB,
  status VARCHAR(32) NOT NULL,
  workspace_path VARCHAR(1000) NOT NULL,
  default_provider VARCHAR(64),
  default_model VARCHAR(128),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_projects_slug ON projects (slug);
CREATE INDEX ix_projects_status ON projects (status);
```

字段说明：

- `status`: `active | archived`.
- `workspace_path`: server-local path to the git-backed Project Workspace。
- `default_provider` / `default_model`: 只作为新 Conversation 默认值，不代表所有 Conversation 必须使用它们。

---

### 4.2 conversations

`conversations` 是用户心智模型中的 chat window，也是核心状态机所在表。

```sql
CREATE TABLE conversations (
  id VARCHAR(64) NOT NULL,
  project_id VARCHAR(64) NOT NULL,
  title VARCHAR(300) NOT NULL,
  source_type VARCHAR(64) NOT NULL,
  source_ref VARCHAR(200),
  status VARCHAR(32) NOT NULL,
  mailbox_id VARCHAR(64) NOT NULL,
  workspace_path VARCHAR(1000) NOT NULL,
  default_provider VARCHAR(64) NOT NULL,
  default_model VARCHAR(128) NOT NULL,
  active_run_id VARCHAR(64),
  pending_question_message_id VARCHAR(64),
  last_activity_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_conversations_mailbox_id ON conversations (mailbox_id);
CREATE INDEX ix_conversations_project_status ON conversations (project_id, status);
CREATE INDEX ix_conversations_project_activity ON conversations (project_id, last_activity_at);
CREATE INDEX ix_conversations_active_run_id ON conversations (active_run_id);
CREATE INDEX ix_conversations_pending_question ON conversations (pending_question_message_id);
```

字段说明：

- `source_type`: `manual | schedule | webhook | system | demo`.
- `source_ref`: source-specific reference，例如 `automation_schedules.id` 或 webhook id。
- `status`: `idle | running | waiting_for_user | completed | failed | cancelled`.
- `mailbox_id`: opaque logical mailbox channel id。MVP 不需要单独建 `mailboxes` 表。
- `workspace_path`: conversation-specific workspace path。后续可以改成 project-relative path。
- `active_run_id`: logical reference to current active run。
- `pending_question_message_id`: logical reference to current unresolved question。
- `last_activity_at`: list ordering optimization，由 message append / run terminal event 更新。

不变量：

- `status = running` 时通常存在 `active_run_id`。
- `status = waiting_for_user` 时必须存在 `pending_question_message_id`。
- `status in (idle, completed, failed, cancelled)` 时 `active_run_id` 必须为 `NULL`。
- 同一个 Conversation 同一时间最多一个 active Run。

---

### 4.3 provider_sessions

`provider_sessions` 保存 provider-specific resume handle。不要把 `cli_session_id` 直接绑死在 `conversations` 上，否则后续支持多个 provider 会难受。

```sql
CREATE TABLE provider_sessions (
  id VARCHAR(64) NOT NULL,
  conversation_id VARCHAR(64) NOT NULL,
  provider VARCHAR(64) NOT NULL,
  model VARCHAR(128) NOT NULL,
  external_session_id VARCHAR(300),
  status VARCHAR(32) NOT NULL,
  metadata_json CLOB,
  last_seen_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_provider_sessions_conversation_provider_model
  ON provider_sessions (conversation_id, provider, model);
CREATE INDEX ix_provider_sessions_external_session
  ON provider_sessions (provider, external_session_id);
CREATE INDEX ix_provider_sessions_conversation
  ON provider_sessions (conversation_id);
```

字段说明：

- `provider`: `codex | gemini | claude | mock`.
- `external_session_id`: Codex thread id / Claude session id / Gemini resume id。
- `status`: `active | unavailable | expired`.
- `metadata_json`: provider adapter specific metadata。

Run 仍然记录 `external_session_id_before` / `external_session_id_after`，用于 audit。`provider_sessions` 是当前可 resume state。

---

### 4.4 runs

`runs` 是一次 subprocess execution record。它只表达 execution lifecycle 和 termination reason。

```sql
CREATE TABLE runs (
  id VARCHAR(64) NOT NULL,
  conversation_id VARCHAR(64) NOT NULL,
  provider_session_id VARCHAR(64),
  trigger_message_id VARCHAR(64),
  trigger_type VARCHAR(64) NOT NULL,
  provider VARCHAR(64) NOT NULL,
  model VARCHAR(128) NOT NULL,
  lifecycle VARCHAR(32) NOT NULL,
  termination_reason VARCHAR(64),
  process_id VARCHAR(64),
  external_session_id_before VARCHAR(300),
  external_session_id_after VARCHAR(300),
  started_at TIMESTAMP NOT NULL,
  ended_at TIMESTAMP,
  exit_code INTEGER,
  signal VARCHAR(64),
  final_event_type VARCHAR(64),
  timeout_kind VARCHAR(64),
  error_message CLOB,
  prompt CLOB NOT NULL,
  command_line CLOB,
  metadata_json CLOB,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX ix_runs_conversation_started ON runs (conversation_id, started_at);
CREATE INDEX ix_runs_lifecycle ON runs (lifecycle);
CREATE INDEX ix_runs_provider_session ON runs (provider_session_id);
CREATE INDEX ix_runs_trigger_message ON runs (trigger_message_id);
```

字段说明：

- `trigger_type`: `user_message | schedule | webhook | system | retry | resume`.
- `lifecycle`: `running | ended`.
- `termination_reason`: `completed | waiting_for_user | failed | timed_out | cancelled | unknown`.
- `process_id`: string for portability. Some runtimes may not expose a normal numeric OS pid.
- `final_event_type`: explicit agent terminal event, e.g. `done | fail`。
- `prompt`: exact prompt sent to provider runtime。MVP 阶段对 debugging 很值。
- `command_line`: sanitized command line。不要写入 secret / token。

不变量：

- `lifecycle = running` 时 `termination_reason` 应为 `NULL`。
- `lifecycle = ended` 时 `ended_at` 和 `termination_reason` 必须存在。
- `runs.lifecycle` 不出现 `waiting_for_user`。等待用户是 `conversations.status`。

---

### 4.5 mailbox_messages

`mailbox_messages` 是 Conversation-owned mailbox 的 durable message stream。

```sql
CREATE TABLE mailbox_messages (
  id VARCHAR(64) NOT NULL,
  conversation_id VARCHAR(64) NOT NULL,
  mailbox_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64),
  sender VARCHAR(32) NOT NULL,
  message_type VARCHAR(64) NOT NULL,
  body CLOB NOT NULL,
  reply_to_message_id VARCHAR(64),
  metadata_json CLOB,
  read_at TIMESTAMP,
  ack_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX ix_mailbox_messages_conversation_created
  ON mailbox_messages (conversation_id, created_at);
CREATE INDEX ix_mailbox_messages_mailbox_created
  ON mailbox_messages (mailbox_id, created_at);
CREATE INDEX ix_mailbox_messages_run_created
  ON mailbox_messages (run_id, created_at);
CREATE INDEX ix_mailbox_messages_reply_to
  ON mailbox_messages (reply_to_message_id);
```

字段说明：

- `sender`: `user | agent | system`.
- `message_type`: `user_message | agent_message | question | answer | terminal_event | system_notice`.
- `reply_to_message_id`: logical reference for answer-to-question。
- `read_at`: agent / system read marker。主要给 `wait_user` / `check_messages` 用。
- `ack_at`: UI 或 agent ack marker，MVP 可先弱化。

Message 规则：

- 用户普通输入写入 `user_message`。
- 用户回复 pending question 时写入 `answer`，并设置 `reply_to_message_id = pending_question_message_id`。
- agent 提问写入 `question`，同时更新 `conversations.status = waiting_for_user`。
- agent 完成或失败写入 `terminal_event`，同时触发 run / conversation terminal transition。

---

## 5. 工作流与产物表

### 5.1 automation_schedules

Adjuva 使用 db-scheduler 作为 timer infrastructure，但业务 schedule definition 放在 `automation_schedules`。不要把业务状态塞进 db-scheduler 的 infrastructure table，别把 jandals 当 tramping boots。

```sql
CREATE TABLE automation_schedules (
  id VARCHAR(64) NOT NULL,
  project_id VARCHAR(64) NOT NULL,
  title VARCHAR(300) NOT NULL,
  status VARCHAR(32) NOT NULL,
  schedule_kind VARCHAR(32) NOT NULL,
  schedule_expression VARCHAR(300) NOT NULL,
  timezone VARCHAR(100) NOT NULL,
  provider VARCHAR(64) NOT NULL,
  model VARCHAR(128) NOT NULL,
  conversation_title_template VARCHAR(300) NOT NULL,
  prompt_template CLOB NOT NULL,
  last_triggered_at TIMESTAMP,
  metadata_json CLOB,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX ix_automation_schedules_project_status
  ON automation_schedules (project_id, status);
CREATE INDEX ix_automation_schedules_status
  ON automation_schedules (status);
```

字段说明：

- `status`: `active | paused | disabled`.
- `schedule_kind`: `cron | interval | one_time`.
- `schedule_expression`: app-defined expression。MVP 可先支持 simple cron / interval text。
- `timezone`: IANA timezone，例如 `Pacific/Auckland`。

Trigger 规则：

```text
db-scheduler fires infrastructure job
↓
handler loads automation_schedules by id
↓
if status != active, no-op
↓
create system Conversation
↓
create Run
↓
update last_triggered_at
```

---

### 5.2 artifacts

`artifacts` 保存 generated output 的 metadata。真实文件优先放在 Project Workspace。

```sql
CREATE TABLE artifacts (
  id VARCHAR(64) NOT NULL,
  project_id VARCHAR(64) NOT NULL,
  conversation_id VARCHAR(64),
  run_id VARCHAR(64),
  artifact_type VARCHAR(64) NOT NULL,
  title VARCHAR(300) NOT NULL,
  uri VARCHAR(1000) NOT NULL,
  mime_type VARCHAR(200),
  size_bytes BIGINT,
  checksum VARCHAR(200),
  metadata_json CLOB,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX ix_artifacts_project_created
  ON artifacts (project_id, created_at);
CREATE INDEX ix_artifacts_conversation_created
  ON artifacts (conversation_id, created_at);
CREATE INDEX ix_artifacts_run
  ON artifacts (run_id);
```

字段说明：

- `artifact_type`: `report | draft | file | link | log | export`.
- `uri`: workspace-relative path or external URL。
- `checksum`: optional content hash，由 application 计算。

---

## 6. Outbox 表

`outbox_events` 用于 transactional outbox。API / worker 在同一个 DB transaction 内写业务状态和 outbox event，异步 publisher 再推送 SSE、notification 或 webhook。

```sql
CREATE TABLE outbox_events (
  id VARCHAR(64) NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  payload_json CLOB NOT NULL,
  status VARCHAR(32) NOT NULL,
  attempts INTEGER NOT NULL,
  available_at TIMESTAMP NOT NULL,
  published_at TIMESTAMP,
  error_message CLOB,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX ix_outbox_events_status_available
  ON outbox_events (status, available_at);
CREATE INDEX ix_outbox_events_aggregate
  ON outbox_events (aggregate_type, aggregate_id);
```

字段说明：

- `status`: `pending | published | failed`.
- `attempts`: retry counter。

---

## 7. Conversation 状态机

### 7.1 状态集合

```text
idle
running
waiting_for_user
completed
failed
cancelled
```

状态含义：

- `idle`: 当前没有 active Run，也没有 pending question。
- `running`: 当前存在 active subprocess Run。
- `waiting_for_user`: 当前存在 pending question。active Run 可能仍在运行，也可能已经因为等待超时而退出。
- `completed`: 上一轮 task 已通过 explicit terminal event 成功结束。
- `failed`: 上一轮 task 失败，或 subprocess 非正常结束。
- `cancelled`: 用户或系统取消了当前工作。

### 7.2 状态转移矩阵

| 当前状态 | 事件 | Guard | 下一状态 | transaction 内动作 |
|---|---|---|---|---|
| new | `conversation.create` | project 存在 | `idle` | insert conversation，生成 `mailbox_id`，创建 outbox event |
| `idle` | `user.message` | 没有 active Run | `running` | insert `user_message`，create run，设置 `active_run_id`，更新 `last_activity_at` |
| `idle` | `schedule.trigger` | schedule 为 active | `running` | 必要时 create system conversation，create run，设置 `active_run_id` |
| `running` | `user.message` | active Run 存在 | `running` | append `user_message`，更新 `last_activity_at` |
| `running` | `agent.question` | active Run 存在 | `waiting_for_user` | insert `question`，设置 `pending_question_message_id`，更新 `last_activity_at` |
| `running` | `run.done` | terminal event 为 `done` | `completed` | 将 run 结束为 `completed`，清空 `active_run_id` 和 pending question |
| `running` | `run.fail` | terminal event 为 `fail` 或 process failure | `failed` | 将 run 结束为 `failed`，清空 `active_run_id`，保存 error |
| `running` | `run.timeout` | watchdog timeout | `failed` | 必要时 kill process，将 run 结束为 `timed_out`，清空 `active_run_id` |
| `running` | `user.cancel` | active Run 存在 | `cancelled` | terminate process，将 run 结束为 `cancelled`，清空 `active_run_id` |
| `waiting_for_user` | `user.answer` | active Run 存在 | `running` | insert `answer`，清空 `pending_question_message_id`，保留 `active_run_id` |
| `waiting_for_user` | `user.answer` | 没有 active Run | `running` | insert `answer`，create resume run，设置 `active_run_id`，清空 `pending_question_message_id` |
| `waiting_for_user` | `run.exits.waiting` | agent 等待用户时主动退出 | `waiting_for_user` | 将 run 结束为 `waiting_for_user`，清空 `active_run_id`，保留 pending question |
| `waiting_for_user` | `run.fail` | active Run 失败 | `failed` | 将 run 结束为 `failed`，清空 `active_run_id`，问题仍保留在 message history 中 |
| `waiting_for_user` | `user.cancel` | pending question 存在 | `cancelled` | 如有 active run 则 cancel，清空 `active_run_id` 和 pending question |
| `completed` | `user.message` | provider session 可 resume | `running` | insert `user_message`，create resume run，设置 `active_run_id` |
| `failed` | `user.message` | 用户选择 recovery 或发送新指令 | `running` | insert `user_message`，create resume run，设置 `active_run_id` |
| `cancelled` | `user.message` | 用户明确继续 | `running` | insert `user_message`，create resume run，设置 `active_run_id` |

非法状态转移应返回 domain error。只有对 debugging 有价值时，才记录对应事件。

### 7.3 关键规则

用户消息路由：

```text
if conversation.active_run_id is not null:
    append message to mailbox
else:
    append message to mailbox
    start new Run using provider_sessions.external_session_id when available
```

Pending question 处理：

```text
if conversation.status = waiting_for_user
and conversation.pending_question_message_id is not null:
    user message becomes answer
    answer.reply_to_message_id = pending_question_message_id
else:
    user message becomes user_message
```

Terminal event 处理：

```text
adjuva_done -> run.termination_reason = completed -> conversation.completed
adjuva_fail -> run.termination_reason = failed -> conversation.failed
natural language final answer alone is not completion
```

---

## 8. Run 状态机

### 8.1 状态集合

Run lifecycle 应刻意保持小集合：

```text
running
ended
```

Run termination reason：

```text
completed
waiting_for_user
failed
timed_out
cancelled
unknown
```

### 8.2 状态转移矩阵

| 当前 lifecycle | 事件 | Guard | 下一 lifecycle | termination reason | 动作 |
|---|---|---|---|---|---|
| new | `run.start` | conversation 可以启动 run | `running` | `NULL` | insert run，spawn subprocess，设置 `conversations.active_run_id` |
| `running` | `provider.session.started` | provider 返回 session id | `running` | `NULL` | 更新 `external_session_id_after` 和 provider session |
| `running` | `agent.done` | explicit terminal event | process 退出前保持 `running`；如 process 已退出则为 `ended` | `completed` | insert terminal mailbox message |
| `running` | `agent.fail` | explicit terminal event | process 退出前保持 `running`；如 process 已退出则为 `ended` | `failed` | insert terminal mailbox message，保存 error |
| `running` | `process.exit.0` | final event 为 `done` | `ended` | `completed` | 设置 `ended_at` 和 `exit_code` |
| `running` | `process.exit.waiting` | agent 在 wait timeout 后退出 | `ended` | `waiting_for_user` | 清空 active run，让 conversation 保持 waiting |
| `running` | `process.exit.nonzero` | 没有 explicit recoverable reason | `ended` | `failed` | 设置 error message |
| `running` | `watchdog.timeout` | 超过配置的执行上限 | `ended` | `timed_out` | terminate process，保存 timeout kind |
| `running` | `cancel` | 用户或系统 cancel | `ended` | `cancelled` | terminate process，保存 signal |
| `running` | `process.exit.unknown` | 无法分类 | `ended` | `unknown` | 保存原始 exit details |
| `ended` | any terminal event | run 已结束 | invalid | 不变 | 忽略 idempotent duplicate，或记录 warning |

### 8.3 结束原因分类规则

Completion 的 source of truth：

```text
final_event_type = done
and process exit is successful or already observed as terminal
=> completed
```

Failure 的 source of truth：

```text
final_event_type = fail
or non-zero process exit without explicit waiting/cancel reason
=> failed
```

等待用户：

```text
conversation.status = waiting_for_user
and process exits intentionally after wait timeout
=> run.termination_reason = waiting_for_user
=> conversation remains waiting_for_user
```

不要设置 `runs.lifecycle = waiting_for_user`。

---

## 9. Automation Schedule 状态机

### 9.1 状态集合

```text
active
paused
disabled
```

### 9.2 状态转移矩阵

| 当前状态 | 事件 | Guard | 下一状态 | 动作 |
|---|---|---|---|---|
| new | `schedule.create` | expression 合法 | `active` 或 `paused` | insert schedule，register db-scheduler job |
| `active` | `schedule.pause` | user action | `paused` | update schedule，disable future triggers |
| `paused` | `schedule.resume` | expression 仍合法 | `active` | update schedule，register next trigger |
| `active` | `schedule.disable` | user action | `disabled` | update schedule，remove/disable infrastructure trigger |
| `paused` | `schedule.disable` | user action | `disabled` | update schedule，remove/disable infrastructure trigger |
| `active` | `scheduler.fire` | 仍为 active | `active` | create Conversation + Run，更新 `last_triggered_at` |
| `paused` | `scheduler.fire` | stale trigger | `paused` | no-op，必要时写入 outbox event 通知 UI |
| `disabled` | `scheduler.fire` | stale trigger | `disabled` | no-op |

---

## 10. Transaction 边界

状态转移必须通过明确的 service method 执行。不要通过 generic CRUD 直接更新核心 status。

推荐的 transaction 边界：

### 10.1 用户发送消息

```text
开始 transaction
  load conversation，做 update-equivalent conditional check
  if status = waiting_for_user:
      创建 answer message
      清空 pending_question_message_id
      设置 status = running
      if no active_run_id:
          创建 resume run
          设置 active_run_id
  else:
      insert user_message
      if no active_run_id:
          创建 run
          设置 conversation.status = running
          设置 active_run_id
  更新 last_activity_at
  insert outbox_event
提交 transaction

commit 后再 spawn subprocess
```

为保持 portability，尽量使用 conditional update，而不是 database-specific row locking：

```sql
UPDATE conversations
SET status = ?, active_run_id = ?, updated_at = ?
WHERE id = ? AND status = ?;
```

如果 affected row count 为 0，说明状态已被其他流程改变，应 reload 后 retry，或返回 conflict。

### 10.2 Agent 向用户提问

```text
开始 transaction
  验证 run is active
  insert question message
  设置 conversation.status = waiting_for_user
  设置 pending_question_message_id
  更新 last_activity_at
  insert outbox_event
提交 transaction
```

### 10.3 Run 结束

```text
开始 transaction
  load run
  classify termination_reason
  更新 run lifecycle = ended
  如有变化，更新 provider_session.external_session_id
  根据 termination reason 更新 conversation status
  清空 active_run_id
  根据 reason 保留或清空 pending_question_message_id
  insert outbox_event
提交 transaction
```

### 10.4 Outbox 发布

```text
publisher 按 status/available_at 加载 pending outbox_events
发布 notification
标记为 published
失败时增加 attempts 并重新安排发送
```

---

## 11. MVP Migration 说明

推荐的初始 Flyway 顺序：

1. `projects`
2. `conversations`
3. `provider_sessions`
4. `runs`
5. `mailbox_messages`
6. `automation_schedules`
7. `artifacts`
8. `outbox_events`
9. db-scheduler infrastructure tables

实现说明：

- 主键 ID 由 MyBatis-Plus `ASSIGN_UUID` 统一生成；非主键业务引用 ID 如 `mailbox_id` 使用同样的 32 位 UUID 字符串形态。
- 所有状态转移代码都应放在 dedicated service 中，不放在 controller 或 generic mapper method 中。
- 在增加 provider-specific 复杂度之前，先为这些状态转移矩阵补 integration tests。
- Project Memory 内容优先保存在 workspace files 中。只有 UI/search 明确需要时，才增加 DB metadata。
- H2 足够支撑 MVP，但 schema 仍应保持 boring、portable SQL，避免未来 migration 变成 hard yakka。
