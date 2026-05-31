# Adjuva Conversation Mailbox POC

本文档定义 Adjuva POC 的目标、范围、关键决策和实现步骤。后续 POC 实现应优先服从本文档，避免偏离 Conversation Mailbox Interaction Model 的核心原则。

## 1. POC 目标

用最简单的 Node.js + React 前后端，验证 Adjuva 的最小核心闭环：

```text
Create Conversation
↓
Conversation automatically owns a Mailbox
↓
User sends message
↓
Backend starts one non-interactive Run
↓
Agent uses mailbox tools to ask user
↓
User replies in same Conversation
↓
Agent reads mailbox and continues
↓
Agent calls done / fail
↓
Conversation status is updated
```

本 POC 重点验证交互模型，不追求 production completeness。

## 2. 核心决策

- 使用 `Conversation` 作为用户看到的 chat window。
- 每个 `Conversation` 创建时自动创建并绑定一个 `Mailbox`。
- `Run` 表示一次 non-interactive subprocess execution。
- `Conversation` 是主要业务状态机。
- `Run` 只记录 execution lifecycle 和 termination reason。
- active Run 存在时，用户消息写入 mailbox。
- 没有 active Run 时，用户消息可以触发新的 Run，并 resume 同一个 Conversation。
- Agent 和 host 之间通过 explicit mailbox tools 通信，不从自然语言输出里猜状态。
- Prompt injection 第一版只使用 `AGENTS.md` 文本。
- 所有状态用本地 JSON 文件持久化，不引入数据库。
- 不做交互式配置，前置条件允许手动配置。
- 先用 mock agent 验证 mailbox protocol，再接真实 Codex CLI。

## 3. 明确不做

POC 阶段不做：

- 数据库
- auth / user system
- multi-tenant
- provider tool approval / file permission approval
- Telegram / Slack / Matrix integration
- WebSocket / external message queue
- complex scheduler
- distributed runner
- production-grade concurrency control
- sophisticated artifact management
- full Codex / Gemini / Claude adapter abstraction

这些都可以后续扩展。POC 只保留最小 happy path，先把模型跑通，sweet as。

## 4. 目录结构

建议目录：

```text
poc/
  POC.md
  server/
    package.json
    src/
      index.js
      store.js
      runner.js
      mock-agent.js
      article-demo.js
      agents-template.md
    data/
      conversations.json
      runs.json
      mailbox_messages.json
  ui/
    package.json
    src/
      App.jsx
      api.js
      main.jsx
  tools/
    adjuva-send-message.js
    adjuva-ask-user.js
    adjuva-wait-user.js
    adjuva-check-messages.js
    adjuva-ack-message.js
    adjuva-done.js
    adjuva-fail.js
```

`server/data/*.json` 可以由 server 启动时自动创建。

## 5. 数据模型

JSON 文件先按 future DB table 的形状设计。

### Conversation

```json
{
  "id": "conv_...",
  "title": "New conversation",
  "provider": "mock",
  "model": "mock-agent",
  "workspace": "/absolute/path",
  "mailbox_id": "mbox_...",
  "cli_session_id": null,
  "status": "idle",
  "active_run_id": null,
  "created_at": "2026-05-30T00:00:00.000Z",
  "updated_at": "2026-05-30T00:00:00.000Z"
}
```

Conversation status:

```text
idle
running
waiting_for_user
completed
failed
cancelled
```

### Run

```json
{
  "id": "run_...",
  "conversation_id": "conv_...",
  "provider": "mock",
  "lifecycle": "running",
  "termination_reason": null,
  "process_id": 12345,
  "cli_session_id_before": null,
  "cli_session_id_after": null,
  "started_at": "2026-05-30T00:00:00.000Z",
  "ended_at": null,
  "exit_code": null,
  "signal": null,
  "final_event_type": null,
  "timeout_kind": null,
  "error_message": null
}
```

Run lifecycle:

```text
running
ended
```

Run termination reason:

```text
completed
waiting_for_user
failed
timed_out
cancelled
unknown
```

### Mailbox Message

```json
{
  "id": "msg_...",
  "conversation_id": "conv_...",
  "mailbox_id": "mbox_...",
  "run_id": "run_...",
  "sender": "user",
  "type": "user_message",
  "body": "Hello",
  "created_at": "2026-05-30T00:00:00.000Z",
  "read_at": null,
  "ack_at": null
}
```

Message sender:

```text
user
agent
system
```

Message type:

```text
user_message
agent_message
question
answer
system_notice
decision
approval_note
terminal_event
```

## 6. API Scope

### Conversation API

```text
GET  /api/conversations
POST /api/conversations
GET  /api/conversations/:conversationId
POST /api/conversations/:conversationId/messages
POST /api/conversations/:conversationId/runs
```

Expected behavior:

- `POST /api/conversations` creates a Conversation and mailbox together.
- `POST /api/conversations/:id/messages` appends user message to mailbox.
- If Conversation has an active Run, user message is only appended.
- If Conversation has no active Run, backend may start a new Run for the message.
- `POST /api/conversations/:id/runs` manually starts a Run for testing.

### Scheduled Demo API

The POC includes a Node timer based scheduled-task simulation:

```text
GET  /api/demo/article-task
POST /api/demo/article-task/schedule
POST /api/demo/article-task/trigger
GET  /demo/article-source
```

Expected behavior:

- `schedule` uses `setTimeout` to create a system-owned Conversation after `delay_ms`.
- `trigger` creates the same task immediately for manual testing.
- The task always uses the `codex` provider.
- The task prompt is hardcoded in `server/src/article-demo.js`.
- `/demo/article-source` serves a local HTML article so Codex can fetch a real URL during tests.

Scheduled article workflow:

```text
System timer fires
↓
Create Conversation + mailbox
↓
Start Codex Run
↓
Agent asks user for today's article URL
↓
User replies with URL
↓
Agent fetches URL content
↓
Agent sends Chinese summary
↓
Agent asks whether summary needs changes
↓
User replies with feedback
↓
Agent revises if needed
↓
Agent calls done
```

### Mailbox API

```text
GET  /api/conversations/:conversationId/mailbox/messages
POST /api/conversations/:conversationId/mailbox/messages
GET  /api/conversations/:conversationId/mailbox/wait?after=:messageId&timeout=:seconds
POST /api/conversations/:conversationId/mailbox/messages/:messageId/ack
POST /api/conversations/:conversationId/mailbox/events/done
POST /api/conversations/:conversationId/mailbox/events/fail
```

Expected behavior:

- Agent tools call mailbox APIs using env vars.
- `wait` can use simple HTTP long polling backed by JSON file polling.
- `done` sets Conversation to `completed` and current Run termination reason to `completed`.
- `fail` sets Conversation to `failed` and current Run termination reason to `failed`.
- `ask_user` is implemented as an agent message with type `question`, and sets Conversation to `waiting_for_user`.

## 7. Agent Tool Scripts

Tools should be simple Node.js scripts that read env vars:

```text
ADJUVA_API_BASE_URL
ADJUVA_CONVERSATION_ID
ADJUVA_RUN_ID
ADJUVA_MAILBOX_TOKEN
```

Initial tools:

```text
adjuva-send-message
adjuva-ask-user
adjuva-wait-user
adjuva-check-messages
adjuva-ack-message
adjuva-done
adjuva-fail
```

Tool behavior:

- `send-message`: append `agent_message`.
- `ask-user`: append `question`, set Conversation `waiting_for_user`.
- `wait-user`: long-poll for new user messages after a message id.
- `check-messages`: list mailbox messages after a message id.
- `ack-message`: mark message acknowledged.
- `done`: emit terminal event and complete current Run.
- `fail`: emit terminal event and fail current Run.

## 8. Prompt Injection

POC uses `AGENTS.md` only.

At Run start, backend should prepare a temporary workspace or write/update an `AGENTS.md` file containing the Adjuva protocol:

```text
You are running inside Adjuva.
Use mailbox tools when you need user input.
Do not finish the run while a business decision is pending.
Call adjuva-done when the task is complete.
Call adjuva-fail when the task cannot continue.
```

Provider-specific adapter design is deferred. First target:

```text
provider = mock
```

Second target:

```text
provider = codex
```

## 9. Runner Plan

Phase 1 uses `mock-agent.js`.

Mock agent should:

1. Read `ADJUVA_CONVERSATION_ID` and `ADJUVA_RUN_ID`.
2. Send an agent message saying it started.
3. Ask one question through mailbox.
4. Wait for user reply.
5. Send a summary message.
6. Call done.

This proves the Conversation Mailbox loop independently from Codex CLI behavior.

Phase 2 replaces mock command with `codex exec` and `AGENTS.md` injection.

Current Codex POC behavior:

- New Codex runs use `codex exec --json --skip-git-repo-check --sandbox danger-full-access`.
- Resumed Codex runs use `codex exec resume --json --skip-git-repo-check --dangerously-bypass-approvals-and-sandbox`.
- `danger-full-access` is required in this POC because Codex-run mailbox tools must call the local HTTP API at `localhost:4100`.
- Runner parses `thread.started` from Codex JSONL and stores it as `Conversation.cli_session_id`.
- Runner appends Codex `agent_message` JSONL events into the mailbox for observability.
- Mailbox terminal events from `adjuva-done` / `adjuva-fail` remain the source of truth for Run completion.
- Codex workspaces are generated under `server/workspaces/{conversation_id}` with an injected `AGENTS.md`.

## 10. UI Scope

UI should be intentionally plain:

- left panel: Conversation list
- create Conversation button
- selected Conversation detail
- status badge
- mailbox message list
- message input
- start Run button for manual testing
- active run / last run summary

No polished design work is required for POC. The UI exists to inspect and drive the model.

## 11. Key Happy Path Test

Manual acceptance test:

1. Start server.
2. Start UI.
3. Create Conversation.
4. Send first user message.
5. Start mock Run, or let send message auto-start it.
6. Confirm agent message appears.
7. Confirm agent question appears and Conversation becomes `waiting_for_user`.
8. Reply in same Conversation.
9. Confirm agent reads reply and sends summary.
10. Confirm agent calls done.
11. Confirm Conversation becomes `completed`.
12. Confirm Run lifecycle is `ended` and termination reason is `completed`.

If this passes, the POC has validated the most important interaction model.

## 12. Implementation Steps

1. Create backend server with JSON store helpers.
2. Implement Conversation API and automatic mailbox creation.
3. Implement Mailbox API and long-poll wait.
4. Implement Run creation and mock runner.
5. Implement agent tool scripts.
6. Build minimal React UI.
7. Run happy path manually.
8. Add Codex CLI execution behind a simple provider switch.
9. Validate AGENTS.md prompt injection with Codex.
10. Update this document with findings and any changed decisions.

## 13. Guardrails

- Keep code small and explicit.
- Prefer boring functions over abstractions.
- Do not add dependencies unless they remove real complexity.
- Do not build generic provider architecture before mock + Codex work.
- Do not introduce queue/state-machine complexity until the POC proves the need.
- Keep JSON schema close to future DB tables.
- Keep UI as an inspection surface, not a product-grade app.
- Treat explicit mailbox terminal events as source of truth.
