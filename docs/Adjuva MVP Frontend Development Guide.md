# Adjuva MVP Frontend Development Guide

本文档规定 Adjuva MVP 阶段 frontend 的开发原则、功能边界、信息架构和 layout 方向。

它的目标是指导 AI agent 从零开始实现 frontend 时保持聚焦、稳定、可维护，不偏离 Adjuva 的核心产品主题，也不过度发散到 admin dashboard、generic chat app 或 coding workspace。

本文档不是 pixel-perfect design spec，也不是 API spec。具体 component props、route path、API response schema 和测试细节应在实现阶段基于 backend 进度补充。

相关文档：

- [Adjuva Core Positioning.md](Adjuva%20Core%20Positioning.md)
- [Conversation Mailbox Interaction Model.md](Conversation%20Mailbox%20Interaction%20Model.md)
- [Adjuva Technical Choices.md](Adjuva%20Technical%20Choices.md)

---

## 1. 核心产品方向

Adjuva frontend 的 MVP 不是 landing page，不是 admin console，也不是普通 real-time chatbot。

MVP frontend 应服务这个核心体验：

```text
用户打开 Adjuva
↓
在 desktop 看到 Projects / Conversations / Mailbox 三栏
在 mobile 看到按 backend 排序的 Conversations
↓
进入一个 Conversation
↓
查看 agent 当前状态、消息、pending question 和 run result
↓
回复或继续推进
↓
离开 app 后通过 notification 回到需要处理的 Conversation
```

用户心智模型应保持简单：

```text
我在这个 Conversation 里继续说话。
Adjuva 会把消息交给正在运行的 agent。
如果 agent 已经结束，Adjuva 会 resume 对应 CLI session 后继续。
```

Frontend 不应暴露这些底层概念作为主要用户心智：

- subprocess
- CLI resume id
- mailbox token
- provider adapter
- scheduler metadata
- db-scheduler task
- worker claim

这些可以在 debug / admin 功能中出现，但不属于 MVP 主体验。

---

## 2. MVP 开发原则

### 2.1 Core workflow first

优先实现能闭环的核心 workflow：

```text
Conversation list
↓
Conversation detail
↓
Message timeline
↓
Reply / compose
↓
Status update
↓
Notification entry point
```

不要先做外围配置、复杂 dashboard 或大而全的 project management UI。

### 2.2 Desktop 和 mobile 同步交付

MVP 不采用 mobile-only。

每完成一个核心 capability，都必须同时覆盖：

- desktop layout
- mobile layout
- loading state
- empty state
- error state
- basic interaction state

不要先只做 desktop，再把 mobile 留到以后；也不要先只做 mobile，再把 desktop 留到以后。

### 2.3 平台交互不互相硬搬

Desktop 和 mobile 必须共享业务能力，但 UI component 不要求复用。

Desktop 应利用：

- 更高信息密度
- multi-pane review
- persistent navigation
- wider message and artifact preview area

Mobile 应强调：

- low navigation depth
- clear hierarchy
- touch target
- fast status scan
- fast reply
- predictable back navigation

不要把 desktop 的 split pane 强行压缩进 mobile，也不要把 mobile 的 single-column navigation 原样搬到 desktop。

Keyboard shortcut 只覆盖关键输入场景，不把 Adjuva 做成面向 coder 的 keyboard-heavy product。

MVP 只需要基础 shortcut：

- search input 中 Enter 执行搜索
- message composer 中 Enter 发送消息
- message composer 中 Shift+Enter 换行

### 2.4 Backend-first，real API only

Frontend 应把 backend API 视为 source of truth。

Adjuva 是 solo develop 项目，开发顺序明确是 backend first、frontend second。

原则：

- frontend 必须直接对接真实 backend API
- 不使用 mock API
- 不使用长期 dev fixture 模拟 backend behavior
- 不为了绕过缺失 API 在 frontend 编造数据模型
- 如果某个 frontend 功能需要 API 但当前找不到，应明确记录缺失 API，并回到 backend 补齐
- server state 使用 TanStack Query 管理
- local UI state 只保存 transient interaction state
- 不在 frontend 复制 backend state machine
- 不在 component 内散落 fetch / SSE / Web Push 逻辑
- API client、query keys、event handling 应集中管理

这条规则的目的不是追求形式上的纯粹，而是避免 solo develop 中把时间浪费在 frontend mock、mock drift 和重复调试上。缺 API 就补 API，别绕远路。

### 2.5 Shared non-UI logic

Desktop 和 mobile 不共用 UI component，但应共用非 UI 逻辑：

- API client
- TanStack Query setup
- query keys
- mutation hooks
- SSE event handling
- auth/session handling
- notification registration logic
- theme tokens / Mantine theme config
- shared utility functions

不要为 desktop 和 mobile 各自维护一套 API/state/auth 逻辑。

### 2.6 Mantine first

UI 应优先使用 Mantine UI v9 components。

编写 Mantine 代码时必须遵循 Mantine v9 官方文档和指引。

原则：

- 使用 Mantine v9，不使用 v6 / v7 / v8 的历史 API 或示例
- 实现前应查阅当前 Mantine v9 docs，尤其是 provider setup、styles import、theme、responsive props 和 component API
- 不依赖记忆中的 Mantine 写法
- 不把不同 Mantine 历史版本的 API 混在一起
- 如果 docs 与记忆不一致，以 Mantine v9 docs 为准

允许自定义 CSS，但应克制：

- 不从零写 design system
- 不引入额外大型 UI component library
- 不用大量 custom card / panel 样式模拟已有 Mantine component
- 不为 MVP 做复杂 animation、decorative visual 或 marketing hero

整体视觉应偏向工作工具：

- 清晰
- 稳定
- 信息密度适中
- 状态可扫读
- 交互路径短

---

## 3. MVP 功能边界

### 3.1 In scope

MVP frontend 只聚焦以下能力：

- desktop project / conversation / mailbox three-column main layout
- mobile flat conversation list
- Conversation navigation
- Conversation mailbox detail
- message timeline
- compose / reply
- pending question display
- `waiting_for_user` 状态
- run status display
- unread / updated indication
- failed / cancelled / completed indication
- basic notification permission onboarding
- SSE foreground updates
- Web Push outside-app notification entry point
- reconnect / refetch handling

### 3.2 Out of scope

以下功能不进入 MVP 主界面：

- project registry UI
- project creation wizard
- workspace file browser
- artifact browser
- environment configuration UI
- executor/provider configuration UI
- model selector
- decision policy editor
- approval policy editor
- user/team management
- billing
- admin console
- scheduler management UI
- webhook management UI
- raw run log viewer
- diff viewer
- IDE-like code navigation
- PR review workflow

如果实现过程中必须临时暴露某个 out-of-scope 能力，只能作为 dev-only/debug-only 入口，不应成为主 navigation 的一部分。

---

## 4. 信息架构

MVP 的 desktop 和 mobile 信息架构不同。

两端共享同一套 backend API、server state、auth、theme 和 notification logic，但 UI component 和 layout 分开实现。

### 4.1 Desktop：三栏主布局

Desktop 主布局直接采用类似 email app 的三栏结构：

```text
Projects
↓
Conversations
↓
Mailbox
```

这不是额外 navigation complexity，而是 desktop 上最高效的 scan / switch / read 结构。

Desktop columns：

- `Projects`：展示 Project 列表和每个 Project 的基础状态 / unread indicator
- `Conversations`：展示当前 Project 下的 Conversations
- `Mailbox`：展示选中 Conversation 的 mailbox detail、message timeline、pending question 和 composer

Desktop 行为：

- 选择 Project 后，中间栏切换到该 Project 的 Conversations
- 选择 Conversation 后，右侧栏展示 Mailbox
- 右侧 Mailbox 是主要工作区
- Project 和 Conversation 列表应保持紧凑、可扫读
- 不需要把 Project registry / configuration 混入主三栏
- 不需要为三栏增加复杂 drag/drop、resizable panel 或 workspace-style layout

### 4.2 Mobile：flat conversation list

Mobile 主入口直接是 Conversation list。

Mobile 暂时不做 Project grouping，也不做三层 drill-down。

示例：

```text
Conversations

Update core positioning...
adjuva · waiting · 7m

梳理项目目的与优缺点
ductor · completed · 12h

交互模型 POC
adjuva · running · 5h
```

Mobile conversation item 使用两行结构：

- 第一行：conversation title or latest meaningful message
- 第二行：project、status、time、unread / updated marker 等辅助信息

Mobile list 排序：

- 直接使用 backend API 返回的排序
- frontend 不重新实现复杂排序策略
- 如需改变优先级，优先调整 backend API

Mobile 行为：

- 点击 Conversation 进入 full-screen Mailbox detail
- detail page 顶部提供返回 Conversation list
- 不单独提供 Project list 页面
- 不把 Project filter 作为 MVP 必需功能

### 4.3 Conversation row / item 内容

Conversation item 至少应表达：

- conversation title or latest meaningful message
- project name
- latest activity time
- status
- unread / updated marker
- whether user action is needed

需要用户处理的 Conversation 应通过 status / marker 清楚表达：

- `waiting_for_user`
- unread user-relevant update
- `run_failed`
- recently completed

### 4.4 Conversation detail

点击 Conversation 后进入 Conversation detail。

Conversation detail 应包含：

- conversation title
- project context
- current status
- run status summary
- pending question if any
- message timeline
- composer / reply input
- basic retry / continue affordance when failed or completed

不要把 provider、CLI session id、process id 等 runtime details 放在主要视觉层级中。

---

## 5. Page 和 Layout 要求

### 5.1 Desktop Main App

用途：

```text
帮助用户在 Projects、Conversations 和 Mailbox 之间快速切换，并在一个工作界面内完成阅读和回复。
```

Layout：

- 使用 persistent app shell
- 主体采用三栏：Projects -> Conversations -> Mailbox
- Projects 栏保持窄而稳定
- Conversations 栏展示当前 Project 下的 conversation items
- Mailbox 栏展示 selected conversation detail
- 三栏都应支持 loading / empty / error 状态
- 不做过大的 marketing-style cards
- 不做 IDE-style workspace shell

Required states：

- loading
- empty
- error with retry
- no projects yet
- no conversations in selected project
- all caught up

### 5.2 Mobile Conversation List

用途：

```text
帮助用户在 mobile 上快速扫读最近 Conversations，并进入需要处理的 Mailbox。
```

Layout：

- single-column list
- 不做 Project grouping
- 不做 Project list page
- Conversation item 使用两行结构
- 第一行展示 conversation title or latest meaningful message
- 第二行展示 project、status、time、unread / updated marker 等辅助信息
- Conversation row 可点击区域足够大
- 列表顺序使用 backend API 返回顺序

Required states：

- loading
- empty
- error with retry
- no conversations yet
- all caught up

### 5.3 Mailbox / Conversation Detail

用途：

```text
让用户查看 agent 进展、理解当前等待事项，并继续回复。
```

Desktop layout：

- Mailbox 是三栏 layout 的右侧主工作区
- message timeline 和 composer 应在同一个 detail pane 中
- pending question / waiting state 应在 detail pane 顶部清楚展示
- 不把 runtime debug details 放在默认视图中

Mobile layout：

- single-column detail page
- 顶部提供返回 Conversation list 的 navigation
- status summary 靠近顶部
- message timeline 占主要空间
- composer 靠近底部，适合快速回复
- pending question 应比普通 message 更醒目，但不应像 blocking modal 一样打断阅读

Required states：

- loading messages
- no messages yet
- sending message
- send failed with retry
- SSE disconnected / reconnecting
- run failed
- waiting for user

### 5.4 Notification Permission Onboarding

用途：

```text
让用户理解为什么 Adjuva 需要 notification，并完成基本 permission setup。
```

这不应是大型 onboarding flow。

MVP 只需要：

- 在合适时机提示开启 notification
- 说明 notification 只用于关键事件
- 支持 skip / later
- permission granted 后不重复打扰

关键事件：

- `waiting_for_user`
- `run_completed`
- `run_failed`
- `conversation_updated`

---

## 6. 状态展示规则

Conversation status 应优先使用 [Conversation Mailbox Interaction Model.md](Conversation%20Mailbox%20Interaction%20Model.md) 中定义的最小状态：

```text
idle
running
waiting_for_user
completed
failed
cancelled
```

UI 展示原则：

- `waiting_for_user` 是最高优先级用户行动状态
- `running` 应表现为 agent 正在处理，不要求用户盯着等待
- `completed` 应表现为上一轮已结束，用户可以继续对话
- `failed` 应说明失败并提供 retry / continue path
- `cancelled` 应说明已停止，恢复需要明确用户动作
- `idle` 不应制造紧迫感

不要引入过多自定义 frontend-only status。

如果 API 返回细节不足，UI 应退回到明确但保守的显示，而不是编造状态。

---

## 7. Realtime 和 Notification 行为

Foreground app 使用 SSE。

SSE 应用于：

- conversation list refresh trigger
- conversation detail message updates
- run status updates
- unread / updated state

Web Push 用于 app 外关键提醒。

Web Push 不用于同步完整状态。用户点击 notification 回到 app 后，应通过 API fetch 获取最新 source of truth。

实现要求：

- SSE event handler 不应直接改写复杂业务对象
- event 可以触发 targeted query invalidation / refetch
- reconnecting 状态应可见但不吓人
- offline / network error 应有清晰 fallback
- notification permission 不应阻塞核心功能

---

## 8. Component 和 UX 约束

### 8.1 Component boundary

Desktop 和 mobile 不共用 UI component。

推荐结构是按平台拆 UI，按业务共享非 UI logic。

Desktop UI component 可以从以下开始：

- `AppShell`
- `DesktopMainLayout`
- `ProjectColumn`
- `DesktopConversationColumn`
- `DesktopConversationItem`
- `MailboxPanel`
- `MessageTimeline`
- `MessageBubble`
- `PendingQuestion`
- `RunStatusSummary`
- `MessageComposer`
- `NotificationPrompt`

Mobile UI component 可以从以下开始：

- `MobileConversationListPage`
- `MobileConversationItem`
- `MobileMailboxPage`
- `MobileTopBar`
- `MessageTimeline`
- `MessageBubble`
- `PendingQuestion`
- `RunStatusSummary`
- `MessageComposer`
- `NotificationPrompt`

Shared non-UI modules 可以从以下开始：

- `apiClient`
- `queryClient`
- `queryKeys`
- `useConversations`
- `useConversationMessages`
- `useSendMessage`
- `useConversationEvents`
- `useNotificationRegistration`
- `auth`
- `theme`

保持 component API 小而明确。

不要过早抽象 generic dashboard framework、generic resource table 或 plugin system。

### 8.2 Message timeline

Message timeline 应支持：

- user message
- agent message
- question
- answer
- system notice
- decision / approval note placeholder

MVP 可以先使用文本消息，但 component 结构应允许后续增加 structured message type。

### 8.3 Composer

Composer 应支持：

- text input
- submit
- sending state
- disabled state when required
- send failure retry
- Enter 发送消息
- Shift+Enter 换行

MVP 不需要：

- rich text editor
- markdown toolbar
- file attachment
- voice input
- command palette

### 8.4 Empty / error states

Empty state 和 error state 必须具体。

不要写泛泛的：

```text
Something went wrong
```

应表达用户下一步能做什么：

- retry
- return to list
- wait for reconnect
- continue conversation

---

## 9. 推荐实现顺序

Adjuva 的实现顺序是 backend first、frontend second。

AI agent 从零开始实现 frontend 时，应按以下顺序推进：

1. 创建 Vite + React + JavaScript app shell。
2. 安装并接入 Mantine UI v9、MantineProvider 和基础 theme。
3. 建立 API client、query keys 和 TanStack Query provider。
4. 对照真实 backend API 实现 conversations / projects / messages data access。
5. 如果缺 API，停止该功能的 frontend 实现，明确列出缺失 endpoint / response field / behavior，回到 backend 补齐。
6. 实现 desktop 三栏 main layout：Projects -> Conversations -> Mailbox。
7. 实现 mobile flat Conversation list。
8. 实现 desktop 和 mobile 各自的 Mailbox / Conversation Detail。
9. 实现 composer 和发送消息 flow。
10. 接入 conversation/run status display。
11. 接入 SSE event source 和 query invalidation。
12. 接入 basic notification permission onboarding。
13. 补齐 loading / empty / error / reconnecting states。
14. 用 desktop 和 mobile viewport 做人工验证。

每一步都应保持可运行。

不要在核心页面可用前实现 out-of-scope 功能。

不要为缺失 backend API 写 mock。缺什么 API 就补什么 API，这是本项目的默认工作方式。

---

## 10. MVP 验收标准

MVP frontend 达到可验收状态时，应满足：

- 用户在 desktop 打开 app 后能看到 Projects -> Conversations -> Mailbox 三栏
- 用户在 mobile 打开 app 后能看到 flat Conversation list
- 用户能识别哪些 Conversation 需要处理
- 用户能进入 Conversation detail
- 用户能看到 message timeline、run status 和 pending question
- 用户能发送 reply
- 用户能看到 sending / sent / failed 状态
- frontend 直接对接真实 backend API，不依赖 mock API
- foreground 更新通过 SSE 反映到 UI
- app 外关键事件可以通过 Web Push 提醒
- desktop 和 mobile 都有明确、可用、非临时拼凑的 layout
- loading、empty、error、offline / reconnecting 状态都有处理
- UI 没有把 Adjuva 误导成 generic chatbot、admin dashboard 或 IDE

---

## 11. 明确禁止的发散方向

实现过程中不要做：

- landing page / marketing homepage
- decorative hero section
- generic SaaS dashboard
- full project management suite
- Kanban board
- calendar view
- workflow builder
- low-code automation builder
- file explorer
- code editor
- provider configuration center
- admin console
- complex onboarding wizard
- social/chat app features such as reactions, presence, typing indicator, read receipts

这些能力未来可能有价值，但不属于 MVP frontend 的主线。

MVP frontend 的主线只有一个：

```text
让用户稳定、高效地通过 Conversation Mailbox 和长期运行的 project assistant 协作。
```
