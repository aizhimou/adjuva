# Adjuva Core Positioning

## 1. 一句话定义

Adjuva 是一个运行在 Linux Server 上的 Persistent Project Assistant。

它以 Project 为核心组织单位，利用 Codex CLI、Gemini CLI、Claude Code 等 Frontier Agent Runtime 提供执行能力，自身专注于长期人机协作所需的 Task、Memory、Session、Scheduling、Approval、Conversation Mailbox 和 Artifact 管理。

Adjuva 的目标不是让 Agent 替代用户，而是让 Agent 成为一个长期在线、持续跟进、主动汇报、在授权范围内主动决策、在关键事项上请求批准，并能继续推进项目的 trusted aide。

---

## 2. 核心问题

当前主流 AI 工具已经能很好地处理单次任务或 coding workflow，但个人开发者和小团队仍缺少一个长期协作层。

典型现状是：

```text
Open IDE
↓
Ask Agent
↓
Agent works
↓
Close window
```

这个模式的问题是：

- Agent 不会长期观察项目
- Agent 不会主动汇报进展
- Agent 不会定时执行 recurring work
- Agent 不会维护显式 project memory
- Agent 不会围绕 business approval 等待用户决策
- Agent 不会在用户离开后继续保持项目上下文

因此，Agent 仍然更像一个工具，而不是一个长期协作者。

Adjuva 要解决的问题是：

```text
Human ↔ Agent
```

长期、异步、持续的项目协作。

---

## 3. 明确不做什么

Adjuva 不做 Coding Workspace，也不进入完整的 coding engineering 赛道。

因此以下不是核心目标：

- IDE integration
- Code navigation
- Diff review
- PR review
- Worktree management
- Parallel coding agents
- Agent ↔ Code productivity
- 大规模 software engineering workflow

Adjuva 也不做 Agent Runtime。

因此以下不是核心目标：

- Agent Loop
- Reasoning engine
- Planning engine
- Tool calling framework
- Multi-agent orchestration framework
- 自研 autonomous agent runtime

这些能力应交给 Codex CLI、Gemini CLI、Claude Code 等成熟 runtime。

Adjuva 不竞争它们，而是编排它们。

这不代表 Adjuva 拒绝 repository。

恰恰相反，Adjuva 的 Project 很大概率会通过 git repository 管理，因为 git 已经提供了成熟的 version control、history、diff、branch、remote sync 和 collaboration infrastructure。

关键区别是：

```text
Project repository
≠
Code repository
```

Adjuva 可以大量复用 git 基础设施，但 repository 中的主要内容通常是 project memory、documents、tasks、decisions、reports、drafts 和 artifacts，而不一定是 application source code。

因此，Adjuva 不排斥 repo。Adjuva 排斥的是把产品定位拖入复杂、竞争激烈、重工程投入的 coding engineering 平台方向。

---

## 4. Adjuva 做什么

Adjuva 专注 Human ↔ Agent 协作层。

核心职责是：

- Project management
- Task management
- Memory management
- Conversation management
- Session management
- Scheduling
- Approval workflow
- Conversation mailbox
- Artifact management
- Executor adapter

Adjuva 关心的是：

```text
这个项目现在是什么状态？
有哪些待处理事项？
哪些信息应该被长期记住？
哪些任务应该定时执行？
哪些结果需要用户审批？
哪些事项可以在授权范围内直接推进？
用户通过哪个 Conversation 反馈？
Agent 如何继续推进？
```

而不是：

```text
如何实现最强 Agent Loop？
如何替代 IDE？
如何让多个 coding agents 同时写代码？
```

---

## 5. 产品形态

Adjuva 的理想使用方式是：

```text
User
↓
Adjuva Conversation UI / TUI
↓
Adjuva Server
↓
Project Workspace
↓
Codex / Gemini / Claude
```

用户不需要打开 IDE，也不需要持续盯着 terminal。

Agent 在 Linux Server 上长期运行，围绕具体 Project 做持续协作：

- 定时生成 Daily Report / Weekly Summary
- 观察 feedback、issue、文档或外部信息变化
- 起草内容、邮件、issue、report
- 对授权范围内的事务直接推进
- 对超出授权范围的事务请求 approval
- 根据用户反馈继续执行
- 把重要结论写入 project memory

这种体验应该像一个靠谱的项目助理：事情不会因为用户离线就断掉，项目会持续 keep ticking along。

---

## 6. 交互形式与用户心智模型

Adjuva 的核心交互形式是自主实现的 Conversation Mailbox，而不是外部即时聊天集成。

用户看到的是一个个 Conversation。每个 Conversation 是一个持续的工作上下文，内部绑定：

- Project / workspace
- provider 和 model
- CLI session id / thread id / resume id
- mailbox
- run history
- status
- metadata

核心抽象是：

```text
Chat Window = Conversation
Conversation = CLI session id + mailbox + run history
Run = 一次 subprocess execution
Mailbox = 用户和 agent 在 run 中交换消息的异步通道
```

因此，Adjuva 的用户心智模型应该是：

```text
在一个 Conversation 中和项目助理持续协作
```

而不是：

```text
和即时 chatbot 做一问一答
```

用户不需要理解 foreground session、named session、subprocess、resume id 或 background task。用户只需要理解：

```text
我在这个 Conversation 里继续说话。
Adjuva 会把消息交给正在运行的 agent。
如果 agent 已经结束，Adjuva 会 resume 对应 CLI session 后继续。
```

典型流程是：

```text
User sends message in Conversation
↓
Adjuva appends message to Mailbox
↓
If no active Run exists, Adjuva starts a new Run
↓
CLI subprocess works in non-interactive mode
↓
Agent may ask questions through Mailbox
↓
User replies in the same Conversation
↓
Agent reads the reply and continues
↓
Run ends with explicit terminal event
```

这个模型同时支持手动任务和系统自动任务：

- 用户创建的 Conversation：用户发起消息后启动 Run。
- 系统创建的 Conversation：scheduled / webhook / background work 自动创建 Conversation 并启动 Run。
- 运行中的 Conversation：用户消息写入 mailbox，active Run 读取后继续。
- 已结束的 Conversation：用户继续发送消息时，Adjuva resume saved CLI session id 并启动新 Run。

真正的执行发生在 background worker 中。任务可能几秒完成，也可能几分钟、几小时后完成。用户可以离开，Adjuva 会继续执行、记录状态，并在需要 clarification、approval、feedback 或完成汇报时回到同一个 Conversation。

Conversation 是 user-facing context，也是用户和 agent 协作的稳定入口。Mailbox 是 Conversation 内部的异步沟通机制。Run 是 execution record，不承载复杂业务状态机。

典型交互节奏是：

- 接收任务：将用户消息写入 Conversation mailbox，必要时启动 Run。
- 异步执行：CLI subprocess 在 background worker 中执行任务。
- 运行中沟通：Agent 通过 mailbox 提问、汇报阶段性结果或请求补充信息。
- 用户回复：用户在同一个 Conversation 中回复，不需要知道 Run 是否仍在运行。
- 请求 approval：遇到 decision boundary 时，Conversation 进入 `waiting_for_user`。
- 继续推进：用户回复后，active Run 继续；若 Run 已结束，则 resume saved CLI session。
- 明确结束：Agent 通过 explicit terminal event 标记 completed / failed / cancelled。

这个模型的关键区别是：

```text
Chatbot = message in, answer out
Adjuva = Conversation + Mailbox + Run
```

这种交互形式需要产品明确建立用户预期：

- Adjuva 不保证每条消息都有即时完整回答。
- Adjuva 会把用户消息保存到 Conversation mailbox。
- Adjuva 会在后台持续推进 Run。
- Adjuva 会在完成、失败、需要 approval、需要 clarification 或需要 feedback 时回到同一个 Conversation。
- Adjuva 的 Conversation 应该可恢复、可追踪、可继续。
- 用户不需要理解底层 subprocess 是否还活着。

---

## 7. Project 优先，而不是 Code Repo 优先

Adjuva 的核心对象是：

```text
Project
Task
Memory
Conversation
Approval
Artifact
Session
```

而不是：

```text
Code Repository
Branch
Diff
Pull Request
Worktree
Build
Deploy
```

一个 Project 可以是：

- SaaS product
- startup
- personal brand
- job search
- research initiative
- learning journey
- immigration knowledge base
- content strategy
- community

很多 Project 会用 git repository 管理，但这不等于它们是 code repository。

在 Adjuva 中，repository 更像 Project Workspace 的存储和协作载体：

- 保存 project memory
- 保存 tasks 和 decisions
- 保存 reports 和 drafts
- 保存 artifacts
- 提供 version history
- 支持 remote backup 和 sync
- 复用成熟的 git ecosystem

因此，Adjuva 的系统边界必须以 Project 为中心，而不是以 coding engineering workflow 为中心。

Repo 是基础设施，不是产品定位。

---

## 8. 显式 Memory

Adjuva 不应依赖隐藏上下文作为长期记忆。

但 Adjuva 也不应该复制 Codex CLI、Gemini CLI、Claude Code 等 Agent Runtime 在单个 session 内已经维护的 short-term memory。

在 Adjuva 的 subprocess 模型中，原则上一个 task 对应一个 executor session。用户围绕该 task 的后续沟通，可以通过 resume 回到同一个 task session，让 runtime 自己维护该 task 的局部上下文。

因此，Adjuva 的 memory 重点不是记录每一个 task 的完整过程细节，而是沉淀跨 session、跨 task、跨时间周期仍然重要的 project memory。

Adjuva 负责的是：

- 长期性 memory
- 关键性 memory
- 摘要性 memory
- decision record
- project-level context
- reusable background knowledge

Executor session 负责的是：

- 当前 task 的 working context
- task 内部推理过程
- task-specific files and edits
- 用户针对该 task 的 follow-up
- runtime 自身的 session state

长期项目上下文应该显式文件化，存放在 Project Workspace 中。

示例：

```text
/projects/
  pigeonpod/
    project.md
    memory.md
    tasks.md
    inbox.md
    decisions.md
    reports/
    drafts/

  personal-brand/
    strategy.md
    ideas.md
    content-calendar.md
    drafts/
    reports/
```

这个原则很重要：

- 用户可以直接阅读和编辑 memory
- Agent 可以稳定恢复 project-level context
- 系统行为更可解释
- 关键 Project state 不被锁在某个 runtime session 里
- task-level context 仍由 executor session 承担
- 后续可以更容易迁移 executor 或 user interface

---

## 9. 技术路线

Adjuva 的技术路线是：

```text
Official Runtime
+
Thin Orchestration Layer
```

Adjuva 不重新发明 Agent Runtime。

它通过 Executor Adapter 调用现有 CLI：

- Codex CLI
- Gemini CLI
- Claude Code

核心 adapter capability 可以保持小而稳定：

- run task
- resume session
- cancel execution
- get status

不同 runtime 的差异被隔离在 adapter layer。

Adjuva 上层只关心：

- task 是否开始
- session 是否存在
- execution 是否完成
- result 在哪里
- 是否需要 user approval
- 后续该如何继续

---

## 10. CLI 集成原则

优先采用一次性 subprocess 模式：

```text
Task
↓
spawn process
↓
codex exec / gemini / claude
↓
result
↓
process exit
```

原则上：

```text
One task
+
One executor session
```

用户围绕该 task 的反馈和追问，优先通过 resume 回到对应 session。

Adjuva 只在必要时从 task session 中提炼长期性、关键性、摘要性的 memory，写回 Project Workspace。

原因：

- 最稳定
- 最容易观察
- 最容易记录 stdout / stderr / exit code
- 最适合 Linux Server
- failure boundary 清晰
- 不容易产生长期 state drift

不优先采用：

- 长驻 subprocess
- stdin / stdout 持续交互
- tmux / PTY bridge

这些方案可以作为兼容手段，但不应成为核心协议。

---

## 11. Approval 模型

Adjuva 的 Approval 是 business approval，而不是 terminal approval。

它关心的是：

- 是否发送邮件
- 是否发布内容
- 是否创建 issue
- 是否更新公开文档
- 是否同步外部知识库
- 是否代表用户联系他人
- 是否执行有业务影响的 action

它不重点关注：

- 是否运行 `npm test`
- 是否执行 `ls`
- 是否读取文件

也就是说，Adjuva 的 approval boundary 应围绕真实世界影响和用户决策权设计。

---

## 12. Decision Scope 模型

Adjuva 不应该完全不做决策。

如果所有事情都等待用户批准，Adjuva 会变得过于被动，无法成为长期项目助理。

更合理的模型是：

```text
Delegated Decision Scope
```

也就是像传统组织架构一样，每个角色都有明确的 decision authority。

Adjuva 应该能在授权范围内主动判断和推进，在超出范围时请求用户 approval。

一个 practical 的权限分层可以是：

- Auto: 可以直接执行，例如整理 inbox、生成 report、更新内部 memory
- Draft: 可以准备草稿，但不能发布，例如邮件、文章、issue proposal
- Approval Required: 必须用户确认，例如发送邮件、公开发布、创建外部 issue、调用付费服务
- Forbidden: 永远不允许执行，例如删除关键数据、代表用户做高风险承诺

Decision Scope 应该成为 Adjuva 的核心能力之一，而不是附属功能。

它需要控制：

- 哪些 action 可以自动执行
- 哪些 action 只能生成 draft
- 哪些 action 必须请求 approval
- 哪些 action 被明确禁止
- 不同 Project 是否有不同权限
- 不同 Task 是否有临时授权范围
- 不同 Conversation source 是否允许 approval

这使 Adjuva 既不会变成完全被动的 chatbot，也不会变成失控的 autonomous agent。

目标是：

```text
主动推进
+
清晰授权
+
关键事项保留用户 decision 权
```

---

## 13. 系统边界

Adjuva 自身负责：

- Project Registry
- Task Scheduler
- Session Registry
- Executor Adapter Layer
- Memory Store
- Approval Store
- Decision Policy Store
- Conversation Store
- Artifact Store
- Mailbox Service
- Conversation UI / API

Executor 负责：

- reasoning
- research
- writing
- tool execution
- task-specific implementation
- artifact generation

Conversation UI / Mailbox 负责：

- 用户触达
- 用户反馈
- approval response
- status notification

这种边界可以让 Adjuva 保持简单、可维护，并随着 Frontier Agent Runtime 进步而自然变强。

---

## 14. 典型场景

### Product Feedback

```text
过去 24 小时新增 12 条用户反馈。

我已整理出 3 个主题：

- 用户希望支持 Pocket Cast
- 用户希望批量导入频道
- 用户希望增加更新频率控制

是否创建对应 issue？
```

### Job Search

```text
本周发现 4 个适合的软件工程岗位。

是否需要我整理定制化申请材料？
```

---

## 15. 后续技术讨论基准

后续任何技术选型、架构设计或 roadmap 讨论，都应回到以下判断标准：

1. 这个设计是否强化 Human ↔ Agent 长期协作？
2. 这个设计是否以 Project 为中心，而不是被 coding engineering workflow 牵引？
3. 这个设计是否避免重新实现 Agent Runtime？
4. 这个设计是否让 memory、task、approval 和 artifact 更显式？
5. 这个设计是否适合 Linux Server 上长期稳定运行？
6. 这个设计是否保持 thin orchestration layer，而不是变成复杂平台？
7. 这个设计是否清晰定义 Adjuva 的 decision scope？
8. 这个设计是否复用 git 等成熟基础设施，而不是重新发明 project storage？

如果某个设计主要服务于 Agent ↔ Code productivity，它大概率不属于 Adjuva 的核心。

如果某个设计能让项目在用户离线时仍能被观察、整理、汇报、起草并等待决策，它大概率属于 Adjuva 的核心。

---

## 16. 最终定位

Adjuva 是一个 Persistent Project Assistant。

它长期在线，围绕 Project 管理 memory、task、session、schedule、approval、decision scope、conversation 和 artifact。

它通过 Codex CLI、Gemini CLI、Claude Code 等 Frontier Agent Runtime 完成具体执行。

它不替代用户在关键事项上的 decision 权，但会在明确授权范围内主动判断和推进。

它不重做 Agent Runtime，也不试图成为 Coding Workspace 或 coding engineering platform。

它的价值在于让人和 Agent 可以围绕真实项目长期、异步、持续协作。
