# Conversation Mailbox Interaction Model

本文档记录 Adjuva 在讨论 `ductor` 的 session / task / agent 模型后，形成的一套核心交互设计思路。

本文档不是最终 implementation spec，而是产品和架构层面的 design note。后续数据库 schema、API、worker state machine、CLI adapter、UI 细节应基于本文档继续展开。

---

## 1. 背景

Adjuva 的目标是构建一个长期运行的 Persistent Project Assistant。

它不自研 Agent Runtime，而是编排成熟的官方 CLI agent runtime：

- Codex CLI
- Gemini CLI
- Claude Code

这些 CLI runtime 已经具备强大的 coding、reasoning、tool use 和 workspace 操作能力。Adjuva 需要解决的是另一层问题：

```text
用户如何长期、异步、持续地和这些 CLI agent 协作？
```

讨论 `ductor` 后，我们确认了一个关键事实：

```text
CLI agent 的一次执行通常是一个 subprocess run。
run 结束后，通过 CLI 返回的 session_id / thread_id / resume id 继续下一轮。
```

也就是说，服务端不一定需要保持一个永不退出的 interactive CLI process。更现实、更稳定的模型是：

```text
User message
↓
Start subprocess run
↓
CLI executes
↓
Persist CLI session_id
↓
Subprocess exits
↓
Next message resumes the saved session_id
```

这个模型适合 server-side orchestration，也适合 background execution、scheduled jobs、webhook triggers 和 long-running project assistant。

---

## 2. 从 ductor 得到的启发

`ductor` 将 Telegram / Matrix 这类 chat transport 接到官方 CLI agent 上。

它内部区分了多种概念：

- foreground session
- named session
- TaskHub task
- cron / webhook task
- sub-agent

这些概念在 Telegram 形态下是合理的，因为 Telegram 的核心约束是：

```text
一个 bot 对一个用户通常只有一个 chat window。
```

为了在一个 chat window 里表达多个上下文，`ductor` 必须引入：

- `/session`
- `@session-name`
- Telegram topic
- parent session
- task result injection
- inter-agent routing

这些设计能工作，但用户心智模型会变复杂。

讨论后我们得出的判断是：

```text
很多复杂概念不是 agent collaboration 本身必须复杂，
而是 Telegram 单窗口 chat 形态造成的表达复杂。
```

如果 Adjuva 自己实现用户端，尤其是 GUI / TUI / Web UI，就不必把这些 transport workaround 暴露给用户。

---

## 3. 核心抽象决策

最终确定的核心交互抽象是：

```text
Chat Window = Conversation
Conversation = CLI session_id + mailbox + run history
Run = 一次 subprocess execution
Mailbox = 用户和 agent 在 run 中交换消息的通道
```

这套抽象将 ductor 中多个外显概念收敛为更自然的用户模型。

### Conversation

Conversation 是用户看到的一个 chat window。

它代表一个可持续的上下文，内部绑定：

- provider
- model
- CLI session_id / thread_id / resume id
- project / workspace
- mailbox
- run history
- status
- metadata

用户不需要理解 foreground session 和 named session 的区别。

在 Adjuva 中，它们都应该只是：

```text
一个 Conversation
```

用户想要新的上下文，就创建一个 new chat。

### Run

Run 是一次实际的 CLI subprocess execution。

例如：

```text
codex exec ...
gemini ...
claude -p ...
```

Run 可以由不同来源触发：

- 用户在 chat window 中发送消息
- background task 自动启动
- schedule 到点触发
- webhook 触发
- system follow-up 触发

无论来源是什么，底层都可以统一成：

```text
Conversation + Run
```

### Mailbox

Mailbox 是 Conversation 的异步消息通道。

它用于解决一个核心问题：

```text
当 agent subprocess 正在运行时，用户如何继续向它发送信息？
```

传统 subprocess-per-run 模式只能在 run 结束后 resume。

Mailbox 允许正在运行的 agent 主动读取用户消息，从而实现更接近 TUI 的协作体验。

---

## 4. 目标体验

目标体验是：

```text
我坐在 TUI 前
agent 在 yolo 模式下持续执行
它需要确认业务信息时问我
我在同一个 chat window 里回复
它读取 mailbox 后继续
如果它已经结束，我再发消息就 resume
```

这里的重点不是处理 provider CLI 原生 approval prompt。

本文讨论的范围明确限定为：

```text
不考虑非-yolo 模式中需要用户手动 approve tool call 和 write file 权限的情况。
```

也就是说，Adjuva 暂时不尝试拦截和恢复 CLI 内部的 permission approval flow。

本文关注的是业务层沟通：

- agent 需要用户补充参数
- agent 需要用户选择方案
- agent 需要用户验收阶段性结果
- agent 需要用户确认业务决策
- agent 需要用户提供外部信息

这些都可以通过 Conversation Mailbox 实现。

---

## 5. 交互流程

### 5.1 用户发起一个 Conversation

```text
User opens new chat
↓
Adjuva creates Conversation
↓
Conversation gets mailbox
↓
User sends first message
↓
Adjuva starts Run
↓
CLI subprocess executes in yolo mode
```

Run 启动前，Adjuva 注入必要环境变量：

```text
ADJUVA_CONVERSATION_ID
ADJUVA_RUN_ID
ADJUVA_MAILBOX_URL
ADJUVA_MAILBOX_TOKEN
ADJUVA_WORKSPACE
```

同时，workspace 中提供 mailbox tools，例如：

```text
send_message
wait_user
check_messages
ack_message
```

### 5.2 Agent 需要用户输入

当 agent 执行中需要用户输入时，它不应在最终回答里简单说 “我需要更多信息” 然后结束。

它应该调用 mailbox tool：

```text
send_message("我需要确认：是否包含 archived data？")
wait_user(timeout = 300 seconds)
```

此时 UI 显示：

```text
Agent is waiting for your reply
```

用户在同一个 chat window 里回复：

```text
包含 archived data，但不要处理 deleted records。
```

Adjuva 将该消息写入 Conversation mailbox。

正在运行的 subprocess 通过 `wait_user` 读取到消息，继续执行。

### 5.3 Agent 等不到用户

如果 `wait_user` 超时，有两种可选策略。

推荐策略：

```text
Run exits
Conversation status = waiting_for_user
Pending question remains visible
```

用户稍后回来回复时：

```text
Adjuva sees no active subprocess
↓
Adjuva starts a new Run
↓
New Run resumes saved CLI session_id
↓
User reply is passed as follow-up prompt
```

这样用户体验仍然像是在同一个 chat 里继续。

### 5.4 Run 已结束后用户继续对话

如果用户在 Run 结束后继续发送消息：

```text
Adjuva finds Conversation is idle/completed
↓
Adjuva starts a new subprocess Run
↓
CLI resumes Conversation's saved session_id
↓
Agent continues from previous context
```

这保留了 subprocess-per-run 的工程优势，同时给用户一个持续 conversation 的体验。

---

## 6. 状态模型

Adjuva 应尽量减少状态机数量。

真正需要状态机的是 Conversation，因为它直接决定用户消息如何路由、UI 如何展示、是否需要启动新的 CLI subprocess。

Run 不应该维护一套完整的业务状态机。Run 更适合作为 execution record，记录一次 subprocess execution 的生命周期和结束原因。

### Conversation status

Conversation 建议使用以下最小状态：

```text
idle
running
waiting_for_user
completed
failed
cancelled
```

含义：

```text
idle
    当前没有 active Run，也没有 pending question。

running
    当前存在 active Run，用户新消息应写入 mailbox。

waiting_for_user
    Conversation 有 pending question，下一条用户消息应作为回答处理。
    此时 active Run 可能仍然存在，也可能已经超时退出。

completed
    上一轮任务明确成功结束。用户继续发送消息时，应 resume 同一个 CLI session_id。

failed
    上一轮 Run 非正常失败。用户可以在同一个 Conversation 中继续恢复或改方案。

cancelled
    用户或系统主动停止了当前 Run。
```

### Run lifecycle

Run 只需要表达执行生命周期：

```text
running
ended
```

如果 MVP 创建 Run 后立即启动 subprocess，可以先不引入 `queued`。

只有出现这些需求时才需要增加 queue：

- runner resource pool
- remote worker
- provider rate limit
- priority scheduling
- retry policy
- delayed execution

### Run termination reason

Run 结束原因单独记录：

```text
null
completed
waiting_for_user
failed
timed_out
cancelled
unknown
```

推荐数据模型：

```text
Run:
  id
  conversation_id
  provider
  lifecycle
  termination_reason
  process_id
  cli_session_id_before
  cli_session_id_after
  started_at
  ended_at
  exit_code
  signal
  final_event_type
  timeout_kind
  error_message
```

其中：

```text
Run.lifecycle = running | ended
Run.termination_reason = completed | waiting_for_user | failed | timed_out | cancelled | unknown
```

不要把 `waiting_for_user` 建模为 Run 的主状态。

原因是：

```text
Conversation.waiting_for_user + active Run still running
Conversation.waiting_for_user + last Run already ended
```

这两种情况都合理。

因此：

```text
waiting_for_user 是 Conversation status。
Run 只记录 subprocess 是否还在，以及它最终为什么结束。
```

状态映射：

```text
Conversation.running
    active_run.lifecycle = running

Conversation.waiting_for_user
    active_run.lifecycle = running
    or last_run.lifecycle = ended + termination_reason = waiting_for_user/timed_out

Conversation.completed
    last_run.lifecycle = ended + termination_reason = completed

Conversation.failed
    last_run.lifecycle = ended + termination_reason = failed/unknown

Conversation.cancelled
    last_run.lifecycle = ended + termination_reason = cancelled
```

### Mailbox message

Mailbox message 建议至少表达：

```text
id
conversation_id
run_id
sender
type
body
created_at
read_at
ack_at
```

其中 `type` 可以包括：

```text
user_message
agent_message
question
answer
system_notice
decision
approval_note
```

即使 MVP 阶段先使用自由文本，也应保留类型字段，为后续 structured decision / approval 预留空间。

---

## 7. 用户消息路由规则

用户在某个 Conversation chat window 中发送消息时，Adjuva 应按 Conversation 当前状态决定如何处理。

```text
if Conversation has active Run:
    append message to mailbox

else:
    start new Run
    resume CLI session_id
    pass user message as prompt
```

更细分：

```text
running
    -> append mailbox

waiting_for_user + active Run exists
    -> append mailbox

waiting_for_user + no active Run
    -> save user message as pending answer
    -> start run with resume

idle / completed
    -> start run with resume

failed
    -> start run with resume if recoverable, otherwise ask user whether to retry

cancelled
    -> require explicit new run or resume decision
```

这条规则让用户不需要理解 subprocess 是否还活着。

用户只知道：

```text
我在这个 chat window 里继续说话。
```

---

## 8. Non-interactive mode 与 instruction injection

Adjuva 应继续使用 official agent CLI 的 Non-interactive mode 执行任务。

也就是说，底层执行仍然是：

```text
User message
↓
Start one subprocess Run
↓
Agent works
↓
Agent may use mailbox during the Run
↓
Run ends
↓
Next message resumes CLI session_id
```

Adjuva 不应把 MVP 建立在长期控制 provider CLI interactive TUI / PTY 上。

原因是：

- official CLI 的 TUI stdout/stderr 不是稳定 protocol
- interactive terminal automation 难测试、难恢复、难跨 provider 适配
- subprocess-per-run 更适合 server-side orchestration、audit log、timeout、cancel、retry
- official CLI 通常已经提供 session resume 能力

这里的核心区别是：

```text
CLI process mode:
    Non-interactive subprocess

User experience:
    interactive Conversation through mailbox
```

也就是说，Adjuva 不是让 official CLI process 永久活着，而是让 agent 在一次 Non-interactive Run 内把“等待用户”作为任务的一部分执行。

### Provider-specific injection

不同 provider CLI 暴露的 instruction injection method 不一样。Adjuva 需要为每个 provider 做 adapter，而不是假设所有 CLI 都支持同一个 `--system-prompt` flag。

推荐策略：

```text
Codex CLI
    Use AGENTS.md / per-run prompt / tools / MCP where available.

Claude Code
    Use CLAUDE.md and --append-system-prompt where available.

Gemini CLI
    Use GEMINI.md, .gemini/settings.json, initial prompt, and context files.
```

这些 injection method 的目标不是替换 provider 内部 system prompt，而是追加 Adjuva protocol：

```text
You are running inside Adjuva Conversation <conversation_id>.
When you need user input, call adjuva_ask_user or adjuva_wait_user.
When you complete the task, call adjuva_done.
When you cannot continue, call adjuva_fail.
Do not finish the run while a business decision is pending.
```

### Mailbox protocol tools

单靠 prompt 不够稳。

Adjuva 应提供真实 tools/scripts，让 agent 能用明确动作和 host 通信：

```text
adjuva_send_message
adjuva_ask_user
adjuva_wait_user
adjuva_check_messages
adjuva_ack_message
adjuva_done
adjuva_fail
```

这些 tool events 是 Adjuva 判断 Conversation status 和 Run termination reason 的主要依据。

推荐规则：

```text
agent calls adjuva_ask_user
    -> Conversation.status = waiting_for_user

agent calls adjuva_done
    -> Conversation.status = completed
    -> Run.termination_reason = completed

agent calls adjuva_fail
    -> Conversation.status = failed
    -> Run.termination_reason = failed

subprocess exits without terminal event
    -> Run.termination_reason = unknown
    -> UI should show unverified completion / needs review
```

这能避免完全依赖 agent 自觉输出文本。

实际产品中仍然需要 eval：

```text
Can Codex/Gemini/Claude reliably follow the Adjuva mailbox protocol?
When do they incorrectly final instead of wait_user?
Which injection method is most reliable per provider?
```

---

## 9. 为什么这比 ductor 的用户模型更简单

在 ductor / Telegram 模型中：

```text
普通消息 -> foreground session
@name -> named session
/session @name -> named session background follow-up
task asks parent -> parent asks user -> resume task
cron task -> isolated task folder result
```

在 Adjuva 模型中：

```text
所有可持续上下文都是 Conversation。
所有执行都是 Run。
所有运行中沟通都走 Mailbox。
```

因此：

```text
foreground session = Conversation
named session = Conversation
TaskHub task = Conversation + background Run
cron/webhook task = Conversation + scheduled/external Run
sub-agent = Agent with its own Conversations
```

用户看到的是统一的 chat window。

系统内部仍然可以保留不同 Run source：

- manual
- background
- scheduled
- webhook
- inter-agent

但这些不应成为用户必须理解的主要概念。

---

## 10. 与 yolo 模式的关系

本文设计假设 agent 在 yolo 模式下执行。

这意味着：

- tool call 不需要逐个请求用户批准
- file write 不需要逐个请求用户批准
- subprocess 可以持续推进任务
- 用户沟通主要发生在业务决策层

这和 “坐在 TUI 前看 agent 执行” 的体验更接近。

但 yolo 不意味着没有约束。

Adjuva 仍然需要在系统层提供：

- workspace sandbox
- project boundary
- run timeout
- cancellation
- audit log
- artifact tracking
- business-level approval policy

也就是说：

```text
yolo 是 CLI runtime 的 execution mode。
Mailbox 是 Human-Agent collaboration mode。
```

两者不是同一件事。

---

## 11. 工程实现建议

MVP 阶段不需要直接引入 WebSocket 或外部 message queue。

更简单的实现是：

```text
HTTP long-poll mailbox API
+
DB-backed message table
+
CLI-side mailbox tool scripts
```

示例 API：

```text
POST /conversations/{conversationId}/messages
GET  /conversations/{conversationId}/mailbox/wait?after={messageId}&timeout=300
POST /conversations/{conversationId}/mailbox/{messageId}/ack
```

Agent-side tools：

```text
send_message.py
wait_user.py --timeout 300
check_messages.py --after MESSAGE_ID
ack_message.py MESSAGE_ID
```

后续如果需要更实时体验，可以将 mailbox transport 升级为：

- WebSocket
- Server-Sent Events
- Redis Stream
- database polling with notification
- dedicated message queue

但抽象不应改变。

核心仍然是：

```text
Conversation owns a Mailbox.
Run reads and writes that Mailbox.
User interacts through the Conversation window.
```

---

## 12. 关键决策

本次讨论形成以下决策：

1. Adjuva 不应把 Telegram-style session workaround 直接暴露给用户。
2. 用户心智模型应统一为一个 session 一个 chat window。
3. `foreground session` 和 `named session` 在 Adjuva 中都应收敛为 Conversation。
4. background task、cron task、webhook task 也应映射为 Conversation + Run，而不是孤立的后台记录。
5. 每个 Conversation 应有自己的 mailbox。
6. active Run 中的用户消息进入 mailbox。
7. inactive Conversation 中的用户消息触发 CLI session resume。
8. Agent 需要业务信息时，应通过 mailbox tool 主动询问并等待。
9. 如果等待超时，Run 可以结束，但 Conversation 保持 waiting state。
10. Run 不应维护复杂业务状态机；Run 应作为 execution record，Conversation 才是主要状态机。
11. Adjuva 保持使用 official agent CLI 的 Non-interactive mode 执行任务。
12. Adjuva 需要为 Codex、Claude Code、Gemini CLI 分别适配 instruction injection method。
13. Adjuva 应通过 tools/scripts 获取 explicit terminal event，而不是只靠 prompt 文本推断。
14. Adjuva 暂不处理非-yolo 模式下的 provider CLI tool approval / file permission approval。

---

## 13. 最终模型

最终核心模型是：

```text
Agent
  └── Conversation
        ├── CLI session_id
        ├── Mailbox
        └── Run history
              └── Run = one subprocess execution
```

最终用户体验是：

```text
用户打开一个 chat window
↓
agent 执行任务
↓
agent 需要业务信息时直接在这个 chat 里问
↓
用户回复
↓
正在运行的 agent 通过 mailbox 读取并继续
↓
如果 agent 已经退出，下一条消息自动 resume 同一个 CLI session
```

这套模型让 Adjuva 既能保留 subprocess-per-run 的 server-side 稳定性，又能提供接近 TUI 的持续协作体验。

它也是 Adjuva 后续 Conversation、Task、Scheduling、Webhook、Approval 和 Messaging 设计的基础。
