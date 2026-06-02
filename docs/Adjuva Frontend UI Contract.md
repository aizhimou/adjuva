# Adjuva Frontend UI Contract

This document defines executable UI constraints for the Adjuva frontend.

It complements [Adjuva MVP Frontend Development Guide.md](Adjuva%20MVP%20Frontend%20Development%20Guide.md). The development guide defines product scope and architecture direction. This contract defines the visual, layout, state, and interaction decisions that AI agents must follow when implementing the app.

Visual style and design token direction are defined in [Adjuva Frontend Visual Design Tokens.md](Adjuva%20Frontend%20Visual%20Design%20Tokens.md).

This document is not a marketing design brief. It is a practical implementation contract for building a quiet, production-friendly work app.

---

## 1. Scope

Current scope:

- Desktop web app only
- Main app shell
- Projects / Conversations / Mailbox three-column layout
- Conversation detail
- Message timeline
- Composer
- Basic notification and reconnecting surfaces

Mobile UI is intentionally out of scope for this version of the contract. Do not infer mobile behavior from desktop constraints. Mobile must be specified separately.

---

## 2. Product References

Adjuva desktop should feel closest to:

- Apple Mail for the three-column mailbox workflow
- ChatGPT for the calm, text-first conversation reading experience
- Linear for quiet professional density and restrained status treatment
- The Codex app for minimal navigation chrome and clean typography

Adjuva must not look like:

- admin panel
- IDE
- project management tool
- landing page
- generic SaaS dashboard
- online customer support inbox with noisy badges, alerts, or colorful urgency
- AI toy app with decorative gradients or animated flourishes

The user should feel:

- calm
- professional
- focused
- text-first
- not rushed
- not overloaded by buttons, icons, badges, or panels

---

## 3. Visual Direction

Use Mantine UI v9 default styling as the base.

Do not create a custom design system from scratch. Do not introduce another UI component library.

The default visual language is black, white, and grey, with low-saturation status colors only when state needs attention.

Avoid:

- gradients
- decorative backgrounds
- large rounded cards
- heavy shadows
- colorful dashboard widgets
- prominent metric cards
- animated attention-seeking elements
- dense icon toolbars

Use a quiet work-tool aesthetic:

- light borders
- restrained background contrast
- readable text hierarchy
- stable column structure
- subtle selected states
- status color only where it helps the user decide what needs attention

Cards should not dominate the page. The main UI is column-based, not card-based.

---

## 4. Theme

Support:

- light mode
- dark mode
- automatic system color scheme

Light mode is the primary visual acceptance target. Dark mode must be usable and visually coherent, but should not drive the first implementation pass.

Use Mantine color scheme support rather than hand-rolled theme switching.

The app should rely on Mantine theme tokens where possible:

- background
- text
- muted text
- borders
- selected states
- radius
- spacing

Status colors should be soft and low saturation.

---

## 5. Desktop Layout

Desktop layout follows an Apple Mail-like three-column structure:

```text
Projects -> Conversations -> Mailbox
```

There should be no heavy global top bar.

Each column owns its own lightweight header.

Baseline desktop widths:

- Projects column: `240px`
- Conversations column: `380px`
- Mailbox column: remaining width

Desktop target starts at `>= 1024px`.

Column resizing is out of scope for MVP. Keep column widths stable.

Do not add:

- resizable panels
- drag/drop layout
- workspace tabs
- IDE-like panes
- project management board views
- all-inbox aggregation

---

## 6. Projects Column

Purpose:

```text
Let the user switch between projects quickly without turning the app into a project manager.
```

Header:

- Show `Adjuva`
- Include a search icon
- Include a new conversation icon

Footer:

- Include Settings

Project data:

- Project list comes directly from backend API.
- Project names are user/backend-defined and must be displayed as returned.
- Frontend must not assume fixed project names or fixed project count.

Project item content:

- project name
- subtle needs-action indicator/count if provided or derivable from backend response

Do not show long project descriptions in the project column.

Do not include an `All conversations`, `Inbox`, or similar aggregate entry in MVP.

---

## 7. Conversations Column

Purpose:

```text
Let the user scan conversations in the selected project and identify what needs attention.
```

Conversation row content:

- first line: conversation title
- second line: latest user-relevant message snippet
- right side: relative time
- status marker only when it matters

Conversation title:

- Use backend `title`.
- If title is missing, empty, or not yet generated, display `Untitled conversation`.
- The first conversation may begin untitled until backend generates a title after initial interaction.

Snippet:

- Display the latest user-relevant message.
- Do not show debug, system runtime, provider, scheduler, CLI, or internal metadata messages as the primary snippet.

Sorting:

- Frontend should use backend API order.
- Product intent is:

```text
needs action first, then recent activity
```

If sorting needs to change, adjust backend API behavior rather than implementing complex frontend sorting.

Search:

- Backend search API does not exist yet.
- Build the search icon/input UI, but keep the input disabled.
- Disabled hint text: `Coming Soon`
- Do not fake full-text search in frontend.

---

## 8. Status Priority And Labels

When multiple states could be shown, use this priority:

```text
waiting_for_user > failed > unread updated > running > completed > idle > cancelled
```

Status labels:

| Backend status | UI label | Visual priority |
| --- | --- | --- |
| `waiting_for_user` | `Needs reply` | highest |
| `failed` | `Failed` | high |
| unread / updated | `Updated` or unread marker | medium |
| `running` | `Working` | medium-low |
| `completed` | `Completed` | low |
| `idle` | `Idle` | low |
| `cancelled` | `Cancelled` | low |

Use low-saturation colors. Avoid bright red/orange unless the user cannot proceed.

`Needs reply` should be the clearest action state, but should still feel calm.

---

## 9. Mailbox Pane

Purpose:

```text
Let the user read the conversation, understand current agent state, and reply.
```

Mailbox header:

- conversation title
- project name
- status
- agent name
- last activity

Supported agent name examples:

- `codex`
- `gemini`
- `claude code`
- `open code`

Do not show these in the primary mailbox header:

- run id
- mailbox id
- provider session id
- CLI session id
- subprocess id
- scheduler metadata
- worker claim data

Action buttons:

- `completed`: primary action `Continue`
- `failed`: primary action `Retry`
- `failed`: optional subtle text action `Continue`

Only show action buttons when there is a clear next action.

---

## 10. Pending Question

Pending user questions are important, but must not block the whole interface.

Use a low-saturation callout near the top of the Mailbox pane.

Do not use:

- modal
- blocking overlay
- aggressive toast
- bright warning banner

The callout should make the next action clear without interrupting reading.

---

## 11. Message Timeline

Message timeline should follow a ChatGPT-like reading model.

Message types for MVP:

- `user_message`
- `agent_message`
- `question`
- `answer`
- `system_notice`
- `terminal_event`

User messages:

- align right
- use a very subtle background
- max width: `70%` of the Mailbox reading area
- keep text readable and wrap long content

Agent messages:

- align left
- no message bubble background
- use the main reading width
- prioritize text readability

Message metadata:

- Do not show avatars.
- Do not show per-message metadata by default.
- Mailbox-level metadata belongs in the Mailbox header.

Time display:

- Conversation row uses relative time, such as `7m`, `2h`, `Yesterday`.
- Timeline may show subtle relative time.
- Absolute time can be placed in `title`/hover attributes.

---

## 12. Composer

Composer placement:

- Sticky at the bottom of the Mailbox pane.
- Always available when conversation state allows user input.

Behavior:

- `Enter` sends message.
- `Shift+Enter` inserts newline.
- Trimmed empty input cannot be submitted.
- Submit button is disabled when input is empty.
- Composer is disabled while sending.
- On send failure, preserve the text and show a clear retry affordance.

Do not add in MVP:

- rich text editor
- markdown toolbar
- file attachment
- voice input
- command palette

---

## 13. Initial Selection

On desktop app load:

1. Select the first project returned by backend API.
2. Select the first conversation for that selected project.
3. If the selected project has no conversations, show an empty Mailbox state.

If backend returns no projects, show a clear no-projects state in the main layout.

---

## 14. New Conversation

The new conversation action creates a conversation under the currently selected project.

If no project is selected:

- disable the action
- show hint text: `Select a project first`

Do not create project-selection workflows in the new conversation action for MVP.

---

## 15. Reconnecting State

SSE reconnecting state should be visible but calm.

Display a small `Reconnecting...` indicator in the bottom-right corner of the app.

Do not use:

- toast
- modal
- red warning
- blocking overlay

SSE is a notification channel, not source of truth. After reconnect/refetch, backend API remains authoritative.

---

## 16. Notification Permission

Notification permission is important for Adjuva because the workflow is asynchronous.

Show notification permission prompt as a high-priority, one-time authorization surface.

Placement:

- app top-right

Behavior:

- explain briefly that notifications are for important conversation updates
- allow user to defer
- do not repeatedly nag after the user dismisses or grants permission

Do not build a large onboarding flow for notification permission.

---

## 17. Empty And Error States

Empty and error states must be specific.

Avoid:

```text
Something went wrong
```

Use direct copy:

- `No projects yet`
- `No conversations in this project`
- `Select a conversation`
- `Could not load projects`
- `Could not load conversations`
- `Could not load messages`

Every error state should include a retry action when retry is possible.

Tone:

- user-facing
- specific
- calm
- not technical unless the detail helps the user act

---

## 18. API And Data Rules

Frontend must use real backend API as source of truth.

Do not add runtime mock APIs or long-lived fixture-driven UI behavior.

Use [Adjuva Frontend API Response Examples.md](Adjuva%20Frontend%20API%20Response%20Examples.md) as the API shape reference when implementing frontend data access.

However, documentation may include example API responses for implementation and visual validation. These examples are for agent understanding and screenshot testing only; they must not become frontend runtime data.

Frontend should not invent:

- custom status values
- frontend-only state machine
- project names
- conversation titles
- agent names
- message metadata

If the UI needs a field that backend does not provide, document the missing field and update backend API.

---

## 19. Desktop Acceptance Checklist

An implementation is acceptable only when all of these are true:

- Desktop opens into Projects / Conversations / Mailbox three-column layout.
- The layout feels closer to Apple Mail than to an admin dashboard.
- UI mood is close to ChatGPT / Linear / Codex app: quiet, text-first, low-noise.
- There is no heavy global top bar.
- Projects column is stable and compact.
- Conversations column supports title, snippet, relative time, and meaningful status.
- Mailbox header shows conversation title, project name, status, agent name, and last activity.
- User messages are right-aligned with subtle background and max 70% width.
- Agent messages are left-aligned without bubble background.
- Composer is sticky at the bottom of the Mailbox pane.
- `Needs reply` is visibly the highest-priority user action state.
- Failed state shows `Retry` and optional subtle `Continue`.
- Completed state supports `Continue`.
- Search UI is present but disabled with `Coming Soon` until backend API exists.
- SSE reconnecting appears as a subtle bottom-right `Reconnecting...` indicator.
- Notification permission prompt appears in the top-right and is not a large onboarding flow.
- Empty and error states are specific and include retry where useful.
- Light mode screenshot passes primary visual review.
- Dark mode is coherent and usable.
- No landing page, admin dashboard, IDE, kanban, calendar, file explorer, or generic SaaS dashboard patterns appear in the MVP main UI.
