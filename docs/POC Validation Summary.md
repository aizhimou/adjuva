# Adjuva POC 验证总结

本文档总结当前 POC 已经实际验证过的路径、场景、核心数据模型、状态模型和关键业务流程。它用于指导后续 MVP 产品实现。

本文档描述的是已经跑通的实现事实，不是完整产品 spec。

---

## 1. POC 结论

当前 POC 已经验证：

```text
Conversation
+
Mailbox
+
Run
+
Non-interactive Codex subprocess
+
Mailbox tools
```

可以支撑 Adjuva 的核心交互模型：

```text
用户不需要盯着 terminal。
Agent 可以在后台 run 中通过 mailbox 向用户提问。
用户在同一个 Conversation 中回复。
正在运行的 agent 可以通过 long polling 读取回复并继续原始任务。
Agent 完成后通过 explicit terminal event 更新 Run 和 Conversation 状态。
```

更重要的是，POC 已经验证：

- Conversation 可以作为用户心智模型中的 chat window。
- Mailbox 可以作为 user 和 agent 的异步消息通道。
- Run 可以保持为一次 subprocess execution record。
- Codex CLI 可以通过 non-interactive mode 加入这个模型。
- Codex session id 可以保存到 Conversation，并用于后续 resume。
- 系统自动触发的 background / scheduled task 也可以映射为 `Conversation + Run`。

这说明 Conversation Mailbox Interaction Model 是可行的，可以作为 MVP 的基础。

---

## 2. 已验证场景

### 2.1 手动创建 Conversation

已验证路径：

```text
用户点击创建 Conversation
↓
POST /api/conversations
↓
Server 创建 Conversation
↓
Server 自动创建 mailbox_id
↓
Conversation 出现在 UI conversation list 中
```

当前实现：

- UI: `poc/ui/src/main.jsx`
- API: `POST /api/conversations`
- Store: `poc/server/data/conversations.json`

结论：

`Conversation` 创建时自动绑定 `mailbox_id` 是足够简单且有效的。POC 阶段没有单独的 Mailbox table，MVP 可以继续把 Mailbox 作为 Conversation-owned logical channel，也可以后续独立成 table。

### 2.2 用户消息自动启动 Run

已验证路径：

```text
用户在 Conversation 中发送消息
↓
POST /api/conversations/:conversationId/messages
↓
Server 将用户消息写入 mailbox
↓
如果没有 active Run，Server 启动新的 Run
↓
Runner 启动 mock agent 或 Codex subprocess
```

关键规则：

```text
if active Run exists:
    只把用户消息写入 mailbox

else:
    写入用户消息
    启动新的 Run
```

结论：

用户不需要理解 subprocess 是否存在。用户只是在同一个 Conversation 中继续发消息。

### 2.3 Agent 在运行中等待用户回复

已验证路径：

```text
Agent 调用 adjuva-ask-user
↓
Server 写入 question message
↓
Conversation.status = waiting_for_user
↓
Agent 调用 adjuva-wait-user --after <question_id>
↓
用户在 UI 中回复
↓
用户回复被写入 mailbox
↓
wait-user 返回用户回复
↓
Conversation.status = running
↓
Agent 继续原始任务
```

结论：

Agent 端使用 HTTP long polling 很适合这个模型。它天然表达“当前 Run 等待用户输入”，同时不需要长驻 interactive CLI process。

### 2.4 Codex fresh run

已验证路径：

```text
Conversation.provider = codex
↓
startRun()
↓
写入 AGENTS.md 到 conversation workspace
↓
codex exec --json ...
↓
runner 解析 thread.started
↓
Conversation.cli_session_id = thread_id
↓
Codex 使用 mailbox tools
↓
Codex 调用 adjuva-done
```

当前 fresh run command：

```text
codex exec
  --json
  --skip-git-repo-check
  --sandbox danger-full-access
  -C <conversation_workspace>
  <run_prompt>
```

结论：

Codex CLI 可以作为 non-interactive executor 接入。POC 中需要 `danger-full-access`，因为 Codex subprocess 内部的 mailbox tools 需要访问本地 `localhost:4100` API。

### 2.5 Codex resume run

已验证路径：

```text
Conversation 已有 cli_session_id
↓
用户发送 follow-up
↓
Server 启动新的 Run
↓
codex exec resume <cli_session_id> <run_prompt>
↓
Codex 保留之前的 session context
↓
新的 Run 记录 cli_session_id_before / cli_session_id_after
```

当前 resume command：

```text
codex exec resume
  --json
  --skip-git-repo-check
  --dangerously-bypass-approvals-and-sandbox
  <cli_session_id>
  <run_prompt>
```

结论：

`Conversation.cli_session_id` 是 Conversation 和 Codex session 的关键绑定字段。一个 Conversation 可以拥有多个 Runs，但这些 Runs 可以 resume 同一个 Codex session。

### 2.6 系统自动创建 Conversation 并启动任务

已验证路径：

```text
Node timer 触发
↓
Server 创建 Conversation
↓
Conversation 获得 mailbox_id
↓
Server 启动 Codex Run
↓
Agent 向用户索要文章 URL
↓
用户回复
↓
Agent 抓取 URL
↓
Agent 总结内容
↓
Agent 询问反馈
↓
用户回复
↓
Agent 修改总结
↓
Agent 调用 done
```

当前 demo：

- API: `POST /api/demo/article-task/schedule`
- API: `POST /api/demo/article-task/trigger`
- Module: `poc/server/src/article-demo.js`
- 本地文章源: `GET /demo/article-source`

结论：

Background / scheduled work 不需要引入新的 user-facing 抽象。它也可以是：

```text
Conversation + Run + Mailbox
```

系统自动创建的 Conversation 会出现在 conversation list 中，用户进入后直接按普通 Conversation 回复即可。

---

## 3. 当前核心数据模型

当前 POC 使用 JSON 文件模拟 future DB tables：

```text
poc/server/data/conversations.json
poc/server/data/runs.json
poc/server/data/mailbox_messages.json
```

### 3.1 Conversation

当前实际字段：

```json
{
  "id": "conv_...",
  "title": "Daily Article Review ...",
  "provider": "codex",
  "model": "codex-cli",
  "workspace": "/absolute/path/to/workspace",
  "mailbox_id": "mbox_...",
  "cli_session_id": "019e...",
  "status": "completed",
  "active_run_id": null,
  "created_at": "2026-05-30T00:00:00.000Z",
  "updated_at": "2026-05-30T00:00:00.000Z",
  "source": "scheduled_article_demo"
}
```

字段含义：

- `id`: Adjuva Conversation id。
- `title`: UI list 中显示的标题。
- `provider`: 当前 executor provider，已验证 `mock` 和 `codex`。
- `model`: provider model label，POC 中主要是 `mock-agent` / `codex-cli`。
- `workspace`: Conversation 对应 workspace。Codex provider 会写入 `AGENTS.md`。
- `mailbox_id`: Conversation-owned mailbox id。
- `cli_session_id`: Codex thread/session id，用于 resume。
- `status`: Conversation 主状态。
- `active_run_id`: 当前 active subprocess Run id。无 active Run 时为 `null`。
- `source`: 可选字段。用于标记系统任务来源，POC 中用 `scheduled_article_demo`。

MVP 建议：

- Conversation 应继续作为主业务实体。
- `mailbox_id` 可以继续由 Conversation 自动创建。
- `source` 建议保留，用于区分 manual / scheduled / webhook / system-created conversation。
- `cli_session_id` 后续可能需要 provider-specific session mapping，但 POC 中单字段已足够验证模型。

### 3.2 Run

当前实际字段：

```json
{
  "id": "run_...",
  "conversation_id": "conv_...",
  "provider": "codex",
  "lifecycle": "ended",
  "termination_reason": "completed",
  "process_id": 92431,
  "cli_session_id_before": "019e...",
  "cli_session_id_after": "019e...",
  "started_at": "2026-05-30T00:00:00.000Z",
  "ended_at": "2026-05-30T00:00:00.000Z",
  "exit_code": 0,
  "signal": null,
  "final_event_type": "done",
  "timeout_kind": null,
  "error_message": null,
  "prompt": "..."
}
```

字段含义：

- `id`: Run id。
- `conversation_id`: Run 所属 Conversation。
- `provider`: 本次执行使用的 provider。
- `lifecycle`: subprocess 生命周期。
- `termination_reason`: Run 结束原因。
- `process_id`: OS process id。
- `cli_session_id_before`: Run 开始前 Conversation 保存的 provider session id。
- `cli_session_id_after`: Run 获取或更新后的 provider session id。
- `started_at` / `ended_at`: 执行时间。
- `exit_code` / `signal`: subprocess 退出信息。
- `final_event_type`: agent 显式发出的 terminal event，例如 `done` / `fail`。
- `timeout_kind`: 预留字段。
- `error_message`: 失败说明。
- `prompt`: 本次传给 executor 的 run prompt。

MVP 建议：

- Run 应保持 execution record，不要承载复杂业务状态机。
- `lifecycle` 保持小集合即可：`running | ended`。
- 完成与失败应主要依赖 explicit terminal event，而不是自然语言 final answer。
- `prompt` 在 MVP 中很有用，方便 audit 和 debugging。

### 3.3 Mailbox Message

当前实际字段：

```json
{
  "id": "msg_...",
  "conversation_id": "conv_...",
  "mailbox_id": "mbox_...",
  "run_id": "run_...",
  "sender": "agent",
  "type": "question",
  "body": "Please send one URL for today's selected article.",
  "created_at": "2026-05-30T00:00:00.000Z",
  "read_at": null,
  "ack_at": null
}
```

字段含义：

- `id`: message id。
- `conversation_id`: 所属 Conversation。
- `mailbox_id`: 所属 mailbox。
- `run_id`: 关联 Run。用户在无 active Run 时发出的第一条消息可能是 `null`。
- `sender`: `user | agent | system`。
- `type`: message type。
- `body`: 消息内容。
- `created_at`: 创建时间。
- `read_at`: 被 agent `wait-user` 读取的时间。
- `ack_at`: ack 时间，当前只实现基础 API。

当前已实际使用的 message type：

```text
user_message
answer
agent_message
question
terminal_event
```

MVP 建议：

- `question` 和 `answer` 很关键，应该保留为显式类型。
- `terminal_event` 是 Run 状态判断的重要依据。
- 后续 approval 可以在这个模型上扩展 `decision` / `approval_note`。

---

## 4. 当前状态模型

### 4.1 Conversation status

当前已实现：

```text
idle
running
waiting_for_user
completed
failed
cancelled
```

已验证流转：

```text
idle
  -> running
  -> waiting_for_user
  -> running
  -> completed
```

取消流转：

```text
running
  -> cancelled
```

失败流转：

```text
running
  -> failed
```

关键规则：

- `running`: 当前存在 active Run。
- `waiting_for_user`: agent 已提出问题，正在等用户回复。
- `completed`: agent 已通过 `adjuva-done` 完成任务。
- `failed`: agent 或 subprocess 失败。
- `cancelled`: 用户或系统取消 Run。

MVP 建议：

- Conversation status 应继续作为 UI routing 和 user message routing 的核心状态。
- `waiting_for_user` 应属于 Conversation，而不是 Run。
- UI 应突出显示 `waiting_for_user`，这是用户最需要响应的状态。

### 4.2 Run lifecycle

当前已实现：

```text
running
ended
```

当前 termination reason：

```text
completed
failed
cancelled
unknown
```

POC 中保留但未深入实现：

```text
waiting_for_user
timed_out
```

关键规则：

- Run 不进入 `waiting_for_user` lifecycle。
- Run 是否还活着由 `lifecycle` 表示。
- 等用户属于 Conversation status。
- Run 最终为什么结束由 `termination_reason` 表示。

MVP 建议：

- 不要给 Run 增加复杂业务状态机。
- 如果后续引入 queue，再增加 `queued`，否则先保持 `running | ended`。

---

## 5. 当前关键业务流程

### 5.1 手动 Conversation 流程

```text
用户创建 Conversation
↓
用户发送初始消息
↓
Server 写入 user_message
↓
Server 启动 Run
↓
Runner 启动 Codex subprocess
↓
Codex 通过 mailbox tools 发送 agent messages
↓
Codex 通过 adjuva-ask-user 向用户提问
↓
用户回复
↓
Codex wait-user 收到 answer
↓
Codex 继续原始任务
↓
Codex 调用 adjuva-done
↓
Conversation.status = completed
```

### 5.2 Scheduled Article Review 流程

```text
Schedule 触发
↓
System 创建 Conversation，并设置 source = scheduled_article_demo
↓
System 启动 Codex Run
↓
Agent 索要文章 URL
↓
用户在同一个 Conversation 中发送 URL
↓
Agent 抓取 URL
↓
Agent 发送中文总结
↓
Agent 询问反馈
↓
用户要求改短总结
↓
Agent 发送修订版总结
↓
Agent 调用 done
```

这个流程验证了：

- system-created Conversation
- 用户可以响应 background task
- 一个 active Run 内可以发生多轮 user-agent 沟通
- Codex subprocess 可以抓取 URL
- task completion 之前可以有 feedback loop

### 5.3 Resume 流程

```text
Conversation completed
↓
用户发送 follow-up
↓
Server 启动新的 Run
↓
Runner 调用 codex exec resume <cli_session_id>
↓
Codex 在同一个 provider session 中继续
↓
Run 记录 cli_session_id_before 和 cli_session_id_after
```

这个流程验证了：

- 一个 Conversation 可以有多个 Runs。
- 多个 Runs 可以共享同一个 Codex session id。
- Conversation 仍然是持久的 user-facing context。

---

## 6. Prompt Injection 经验

当前 prompt sources：

```text
AGENTS.md
  稳定的 Adjuva mailbox protocol

buildCodexPrompt()
  每次 Run 的 user task + protocol reminder
```

POC 得到的重要经验：

per-run prompt 不能把每个任务都意外变成 mailbox demo。

已经观察到的错误行为：

```text
向用户提问
↓
等待回复
↓
只总结用户回复
↓
Call done
```

修正方式：

当前 prompt 明确要求：

```text
把用户任务作为 primary objective。
收到用户回复后，继续完成原始用户任务。
不要只 acknowledge 或 summarise 用户回复就停止。
只有原始用户任务完成后，才可以 call done。
```

对 MVP 的含义：

- Prompt design 是产品 protocol 的一部分。
- Mailbox protocol 应稳定且显式。
- Task-specific instructions 必须清晰定义真正的 completion condition。

---

## 7. 用户 UI 更新模型

当前 POC UI 使用简单的 fixed interval polling：

```text
Every 1.5s:
  GET /api/conversations
  GET /api/conversations/:selectedId
```

这已经足够验证：

- system-created Conversation 会出现在 conversation list 中
- agent mailbox messages 不需要手动刷新页面也会出现
- status changes 会变得可见

MVP 建议：

升级为：

```text
initial snapshot fetch
+
SSE notification stream
+
event-triggered refetch
```

SSE 只应该作为 notification。Source of truth 仍应是 database state。

---

## 8. Executor 集成经验

### 8.1 Subprocess model 已验证可行

POC 不需要：

- interactive TUI control
- PTY bridge
- long-lived Codex process
- stdin/stdout chat loop

已验证的模型是：

```text
一个 Run
↓
一个 non-interactive subprocess
↓
显式 mailbox tools
↓
Process 退出
↓
下一条用户消息启动新的 Run 或 resume session
```

### 8.2 Codex JSONL parsing 有用，但不是 source of truth

Runner 解析：

```text
thread.started
item.completed agent_message
```

用途：

- `thread.started` 用于保存 `Conversation.cli_session_id`。
- `agent_message` 在 Run active 时追加到 mailbox，方便 observability。

但 completion 不从 Codex final message 推断。

Completion 的 source of truth：

```text
adjuva-done
adjuva-fail
```

这对 MVP 很重要。

### 8.3 Sandbox mode 很关键

POC 发现：

Codex subprocess 需要访问本地 mailbox API：

```text
http://localhost:4100
```

在 POC 中，这需要：

```text
--sandbox danger-full-access
```

MVP 阶段需要重新认真设计这一点。更好的长期方案可能包括：

- 在 sandbox 内通过 local file/socket API 暴露 mailbox tool
- dedicated MCP server
- explicit network permission model
- controlled internal API endpoint

---

## 9. MVP 应该继承什么

应该继承：

- `Conversation` 作为 user-facing chat window。
- `Mailbox` 作为 async communication channel。
- `Run` 作为 subprocess execution record。
- Conversation-owned status model。
- Explicit mailbox tools。
- `done` 和 `fail` 使用 explicit terminal events。
- Provider session id 存在 Conversation 上，用于 resume。
- System-created tasks 也表示为 Conversations。
- Prompt rule：收到用户回复后继续原始任务。

不要盲目继承：

- JSON files 作为 storage。
- In-memory timer 作为 scheduler。
- `danger-full-access` 作为 production 默认 sandbox。
- Fixed interval polling UI。
- Hardcoded article demo task。
- 如果多个 providers 有不同 session semantics，不要强行只用单个 `cli_session_id` 字段。

推荐的 MVP storage tables：

```text
conversations
runs
mailbox_messages
scheduled_tasks
executor_sessions or provider_sessions
```

后续可选：

```text
artifacts
approvals
decision_policies
events
```

---

## 10. 最终总结

POC 已经验证了 Adjuva 的核心模型：

```text
Conversation 是 user-facing context。
Mailbox 是异步 user-agent communication channel。
Run 是一次 executor subprocess。
Agent 通过 explicit tools 读写 mailbox。
User 通过 UI 读写 mailbox。
Scheduled/background tasks 可以创建 Conversations，并复用同一套流程。
Codex CLI 可以通过 non-interactive subprocess runs 和 session resume 执行这个模型。
```

这已经足够支撑我们用同一套 conceptual architecture 推进 MVP，同时把 POC shortcuts 替换成真正的 persistence、scheduler、event delivery 和更安全的 executor isolation。
