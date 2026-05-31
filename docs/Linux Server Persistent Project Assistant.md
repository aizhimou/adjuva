# Linux Server Persistent Project Assistant

## 基于 Codex / Gemini / Claude CLI 的长期异步人机协作平台

---

# 1. 项目背景

随着：

* Codex CLI
* Gemini CLI
* Claude Code

等 Frontier Agent Runtime 的快速发展，Agent 的推理、规划、工具调用、长任务执行能力已经取得了巨大进步。

然而，对于个人开发者和小团队而言，真正的痛点已经逐渐从：

```text
Agent 不够聪明
```

转变为：

```text
Agent 如何长期与人协作
```

当前主流 Agent 产品主要分为两类：

## Coding Workspace

例如：

* Cursor
* Zed
* JetBrains Junie
* Windsurf
* Superset

它们解决的问题是：

```text
Agent ↔ Code
```

即：

```text
如何让多个 Agent 更高效地写代码
```

---

## Agent Runtime

例如：

* Codex CLI
* Gemini CLI
* Claude Code
* OpenClaw
* OpenDevin

它们解决的问题是：

```text
Agent 如何完成任务
```

---

但对于个人项目而言，真正缺失的是：

```text
Human ↔ Agent
```

长期协作层。

---

# 2. 核心问题

当前用户与 Agent 的协作模式通常是：

```text
打开 IDE
↓
提出问题
↓
Agent 工作
↓
关闭窗口
```

Agent 不会：

* 长期观察项目
* 主动汇报进展
* 定时执行工作
* 等待用户审批
* 根据反馈继续推进

这导致：

```text
Agent 更像工具
而不是协作者
```

---

# 3. 项目愿景

构建一个：

# Persistent Project Assistant

即：

```text
长期在线
长期记忆
长期协作
```

的项目助理。

Agent：

* 24/7 运行于 Linux Server
* 持续观察项目状态
* 主动执行定时任务
* 主动向用户汇报
* 接收用户反馈
* 继续推进工作

用户：

无需打开 IDE。

通过：

* Telegram
* Matrix
* Discord
* Slack

即可与 Agent 长期协作。

---

# 4. 项目定位

## 我们不做什么

明确排除：

### Coding Workspace

例如：

* Cursor
* Superset
* Zed
* Junie

所关注的问题：

```text
PR Review
Diff Review
Worktree Management
Parallel Coding Agents
IDE Integration
Code Navigation
```

不是本项目目标。

---

### Agent Runtime

例如：

* OpenClaw
* OpenDevin
* AutoGen
* CrewAI

所关注的问题：

```text
Agent Loop
Tool Calling
Reasoning
Planning
```

也不是本项目目标。

---

# 我们做什么

我们专注：

```text
Human ↔ Agent
```

协作层。

即：

```text
Task
Memory
Approval
Conversation
Scheduling
Project Context
```

---

# 5. 核心理念

Agent 不应该只是聊天机器人。

Agent 应该成为：

```text
长期项目助理
```

---

典型场景：

## PigeonPod

Agent：

```text
过去24小时新增12条用户反馈

我已整理如下：

- 用户希望支持 Pocket Cast
- 用户希望批量导入频道
- 用户希望增加更新频率控制

是否创建对应 Issue？
```

---

## NZ Immigration Assistant

Agent：

```text
我发现 Immigration NZ 更新了相关政策页面

可能影响以下知识库内容：

...

是否需要重新同步？
```

---

## Personal Brand

Agent：

```text
本周尚未发布技术内容

我已经起草了三篇选题：

...
```

---

## Job Search

Agent：

```text
本周发现 4 个适合的软件工程岗位

是否需要我整理定制化申请材料？
```

---

# 6. 项目模型

系统中的核心对象：

```text
Project
Task
Memory
Conversation
Approval
Artifact
```

而不是：

```text
Repo
Branch
Diff
Pull Request
```

---

# 7. Project Workspace

每个项目拥有独立工作空间：

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

---

Agent 与用户共享这些文件。

Agent 不依赖隐藏上下文。

所有长期记忆：

```text
显式文件化
```

---

# 8. 技术路线

## 基本原则

不重新发明 Agent Runtime。

直接利用：

* Codex CLI
* Gemini CLI
* Claude Code

作为：

```text
Executor
```

---

系统自身只负责：

```text
Task
Session
Memory
Scheduling
Approval
Messaging
```

---

# 9. 系统架构

```text
Telegram / Matrix / Discord
                ↓
Project Assistant Server
                ↓
Project Registry
                ↓
Task Scheduler
                ↓
Session Registry
                ↓
Executor Adapter Layer
                ↓
Codex / Gemini / Claude
                ↓
Project Workspace
```

---

# 10. Executor Adapter

系统核心：

```python
class ExecutorAdapter:

    run_task()

    resume_task()

    cancel_task()

    get_status()
```

实现：

```text
CodexAdapter
GeminiAdapter
ClaudeAdapter
```

---

目标：

统一：

```text
session
execution
resume
status
```

差异。

---

# 11. 为什么选择 CLI Adapter

原因：

Agentic 能力变化极快。

未来：

* Codex
* Gemini
* Claude

能力都会持续增强。

重新实现 Agent Loop：

```text
投入巨大
收益有限
```

---

因此：

采用：

```text
Official Runtime
+
Thin Orchestration Layer
```

路线。

---

# 12. CLI 集成方案

当前主要有三种方式：

## 方案A：一次性 Subprocess（推荐）

```text
Task
 ↓
spawn process
 ↓
codex exec
 ↓
result
 ↓
process exit
```

优势：

* 最稳定
* 最容易观察
* 最适合 Linux Server

也是本项目推荐路线。

---

## 方案B：长驻 Subprocess

```text
stdin/stdout
持续交互
```

优势：

* 更实时

缺点：

* 状态漂移
* 调试困难

不推荐。

---

## 方案C：tmux/PTTY Bridge

```text
模拟真人操作终端
```

优势：

* 兼容性最好

缺点：

* 极度脆弱
* 不适合作为长期协议

不推荐。

---

# 13. Approval 模型

本项目中的 Approval：

不是：

```text
是否执行 npm test
```

而是：

```text
是否发送邮件
是否发布内容
是否创建任务
是否调用外部服务
```

即：

```text
Business Approval
```

而非：

```text
Terminal Approval
```

---

# 14. 定时任务模型

支持：

```text
Daily Report
Weekly Summary
Market Research
Feedback Analysis
Knowledge Sync
Content Draft
```

执行流程：

```text
Scheduler
 ↓
Executor
 ↓
Draft/Report
 ↓
User Review
 ↓
Continue
```

---

# 15. Ductor 的启发

当前最接近本项目方向的开源项目：

Ductor

其正确之处：

* 不重做 Agent Runtime
* 使用官方 CLI
* Session Persistence
* IM Communication
* Background Tasks
* Cron

但：

仍偏：

```text
CLI Session Supervisor
```

而非：

```text
Project Assistant Platform
```

---

# 16. 长期演进路线

Phase 1

```text
Telegram
+
Project Workspace
+
Codex/Gemini Adapter
```

---

Phase 2

```text
Project Memory
Approval Workflow
Artifact Management
```

---

Phase 3

```text
Multi Project
Multi Agent
Cross Project Collaboration
```

---

Phase 4

```text
Personal Operating System
```

Agent：

* 观察
* 执行
* 汇报
* 协作

用户：

只负责：

```text
决策
```

---

# 17. 最终定义

本项目不是：

```text
Coding IDE
```

不是：

```text
Agent Runtime
```

不是：

```text
Multi-Agent Coding Platform
```

而是：

# Persistent Project Assistant

一个运行在 Linux Server 上的长期在线 Agent。

以 Project 为核心组织单位。

利用：

* Codex CLI
* Gemini CLI
* Claude Code

提供 Frontier Agent 能力。

自身不实现 Agent Loop。

只负责：

```text
Task
Session
Memory
Approval
Messaging
Scheduling
```

从而实现：

```text
长期在线
主动汇报
等待批准
继续执行
```

的人机异步协作模式。
