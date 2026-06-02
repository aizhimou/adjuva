# Adjuva Frontend Visual Design Tokens

This document defines Adjuva's target visual style and design token direction.

It is the visual companion to [Adjuva Frontend UI Contract.md](Adjuva%20Frontend%20UI%20Contract.md). The UI contract defines product layout and behavior. This document defines how Adjuva should look and feel before implementation details are written.

Current scope:

- desktop web app
- visual style
- design token direction
- Mantine UI v9 theme baseline

Mobile visual tokens should be reviewed separately after the desktop direction is stable.

---

## 1. Visual Goal

Adjuva should feel like a quiet, professional mailbox for long-running project assistants.

The target experience is:

```text
Apple Mail workflow structure
+
ChatGPT reading calm
+
Linear professional restraint
+
Codex app minimal chrome
```

The UI should help users read, scan, and reply without feeling chased by alerts.

The page should feel:

- calm
- professional
- text-first
- focused
- stable
- low-noise
- slightly spacious
- production-ready

The UI should not feel:

- decorative
- playful
- sales-oriented
- dashboard-like
- IDE-like
- customer-support-like
- project-management-like
- visually loud

---

## 2. Product References

Use these references as product-level direction, not pixel-perfect targets.

| Reference | What to borrow | What not to borrow |
| --- | --- | --- |
| Apple Mail | Three-column mailbox mental model | Native macOS chrome or exact Apple visual styling |
| ChatGPT | Calm text reading, simple message rhythm | Generic chatbot product framing |
| Linear | Quiet density, restrained status treatment | Issue tracker or project-management workflow |
| Codex app | Minimal navigation chrome, clean typography | Two-column session tree structure |

Adjuva's desktop layout remains:

```text
Projects -> Conversations -> Mailbox
```

---

## 3. Mantine Baseline

Use Mantine UI v9 default styling as the base.

Mantine should provide the default:

- typography rhythm
- component sizing
- input/button behavior
- color scheme support
- CSS variables
- radius scale
- spacing scale

Do not build a custom design system from scratch.

Do not introduce another UI component library.

Use `MantineProvider` with `defaultColorScheme="auto"` so the app can follow the system light/dark preference. For a Vite SPA, the implementation should still avoid hand-rolled color-scheme state unless there is a specific product need.

Prefer Mantine CSS variables and theme tokens in custom CSS, for example:

```css
color: var(--mantine-color-text);
background: var(--mantine-color-white);
border-color: var(--mantine-color-gray-3);
```

---

## 4. Visual Principles

### 4.1 Text First

Adjuva is a reading and reply surface.

Text should be the dominant visual element. UI chrome should support scan and navigation without competing with message content.

Use large typography only for primary mailbox title. Avoid oversized headings in columns, rows, banners, or empty states.

### 4.2 Quiet Structure

Use clear columns, light borders, and subtle selected states.

Prefer structure from layout and spacing instead of heavy cards, shadows, or colored blocks.

### 4.3 Low-Saturation Attention

Status color is allowed only when it helps users decide what needs attention.

`Needs reply` should be visible, but it must still feel calm.

Avoid bright warning colors unless the user is blocked.

### 4.4 Stable Density

The UI should be more spacious than a dense admin table, but not empty like a landing page.

Target density is closer to Linear: comfortable rows, readable metadata, no clutter.

### 4.5 No Decorative Layer

Do not use:

- gradients
- decorative illustrations
- bokeh/orb backgrounds
- animated flourish
- colorful dashboard cards
- heavy shadows
- marketing hero treatment

---

## 5. Color Direction

Default palette:

```text
black / white / grey first
soft status colors second
```

Adjuva should not have a strong brand-color wash.

Avoid:

- purple AI-product gradients
- saturated blue dashboards
- beige/brown editorial palettes
- bright red/orange warning surfaces by default
- multi-color badge noise

### 5.1 Semantic Color Tokens

These are semantic token names for design discussion. Implementation can map them to Mantine variables.

| Token | Purpose | Light mapping candidate | Dark mapping candidate |
| --- | --- | --- | --- |
| `surface.app` | full app background | `--mantine-color-white` | `--mantine-color-dark-8` |
| `surface.sidebar` | Projects column background | `--mantine-color-gray-0` | `--mantine-color-dark-7` |
| `surface.panel` | Conversations/Mailbox background | `--mantine-color-white` | `--mantine-color-dark-8` |
| `surface.selected` | selected project/conversation row | `--mantine-color-gray-2` | `--mantine-color-dark-5` |
| `surface.hover` | row hover state | `--mantine-color-gray-1` | `--mantine-color-dark-6` |
| `surface.userMessage` | user message bubble | `--mantine-color-gray-1` | `--mantine-color-dark-6` |
| `surface.systemNotice` | system/terminal notice | `--mantine-color-gray-0` | `--mantine-color-dark-7` |
| `border.subtle` | column and header borders | `--mantine-color-gray-3` | `--mantine-color-dark-4` |
| `text.primary` | primary readable text | `--mantine-color-text` | `--mantine-color-text` |
| `text.muted` | metadata and secondary labels | `--mantine-color-dimmed` | `--mantine-color-dimmed` |

### 5.2 Status Color Tokens

Status colors should be low-saturation and mostly use Mantine `light` or `outline` variants.

| Status | UI label | Color direction | Mantine candidate |
| --- | --- | --- | --- |
| `waiting_for_user` | `Needs reply` | calm positive attention | `teal` light |
| `failed` | `Failed` | visible but not alarming | `red` light |
| unread / updated | `Updated` | subtle activity | `blue` or `gray` light |
| `running` | `Working` | quiet progress | `blue` light |
| `completed` | `Completed` | low priority | `gray` outline |
| `idle` | `Idle` | low priority | `gray` outline |
| `cancelled` | `Cancelled` | low priority | `gray` outline |

Do not show low-priority statuses as loud badges in conversation rows.

---

## 6. Typography Direction

Use system UI fonts via Mantine theme:

```text
-apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif
```

The typography should feel close to ChatGPT/Codex app:

- readable body text
- restrained headings
- clear but subtle metadata
- no decorative font treatment

### 6.1 Typography Tokens

| Token | Purpose | Direction |
| --- | --- | --- |
| `font.family.base` | all UI text | system UI font |
| `font.weight.heading` | mailbox title and important row titles | `650` candidate |
| `font.weight.body` | normal text | Mantine default |
| `font.size.columnTitle` | column header titles | Mantine `sm`/`md`, not hero scale |
| `font.size.rowTitle` | conversation title | readable, compact |
| `font.size.metadata` | project/status/time metadata | small and muted |
| `font.size.messageBody` | timeline message body | comfortable reading size |

Avoid viewport-scaled type. Text should not resize based on viewport width.

---

## 7. Spacing And Density

Adjuva should feel slightly spacious, not dense.

Spacing should support scan:

- compact Projects column rows
- comfortable Conversation rows
- generous Mailbox reading area
- clear separation between messages
- composer always easy to find

### 7.1 Desktop Layout Tokens

| Token | Value | Notes |
| --- | --- | --- |
| `layout.projectsWidth` | `240px` | stable left column |
| `layout.conversationsWidth` | `380px` | stable middle column |
| `layout.mailboxWidth` | `1fr` | remaining space |
| `layout.desktopMinWidth` | `1024px` | desktop contract starts here |
| `layout.mailboxContentWidth` | `min(860px, calc(100% - 48px))` | comfortable reading measure |

### 7.2 Row Density Tokens

| Token | Direction |
| --- | --- |
| `row.projectMinHeight` | compact, around mid-30px |
| `row.conversationMinHeight` | comfortable, around 72-84px |
| `row.horizontalPadding` | enough to scan without crowding |
| `row.metadataGap` | small but clear |

These values can be refined after screenshot review.

---

## 8. Radius, Borders, And Shadow

Default radius should be small.

Use Mantine `sm` radius as the baseline.

Avoid:

- large pill surfaces
- rounded marketing cards
- nested cards
- heavy shadows

Use borders for structure:

- column dividers
- column headers
- composer boundary
- subtle pending-question callout

Shadows should generally be absent in the main shell.

### 8.1 Shape Tokens

| Token | Direction |
| --- | --- |
| `radius.default` | Mantine `sm` |
| `radius.messageBubble` | small/medium, not pill |
| `radius.rowSelected` | small |
| `shadow.mainShell` | none |
| `shadow.card` | none by default |

---

## 9. Message Visual Treatment

Message timeline follows a ChatGPT-like reading model.

User messages:

- align right
- subtle grey background
- max width `70%`
- small/medium radius
- no strong border

Agent messages:

- align left
- no bubble background
- main reading width
- text-first

System/terminal notices:

- centered or lightly separated
- muted text
- subtle background only if needed

Pending question:

- low-saturation callout near top of Mailbox pane
- visually stronger than normal agent message
- not modal
- not bright warning banner

---

## 10. Icon Direction

Use outline/stroke icons.

Current frontend stack includes `@tabler/icons-react`. This is acceptable for an operational work app because coverage is broad and Mantine-adjacent usage is common.

Icon rules:

- use icons sparingly
- prefer familiar symbols for actions
- keep icon buttons subtle
- do not create dense toolbars
- do not use icons as decoration

Primary icon use cases:

- search
- new conversation
- settings
- retry
- notification permission

---

## 11. Light And Dark Mode

Light mode is the primary acceptance target.

Dark mode must be coherent and usable:

- no washed-out text
- no overly bright status surfaces
- borders remain visible
- selected rows remain clear
- user message background remains subtle

Use Mantine color-scheme support and `light-dark(...)` CSS where custom CSS needs scheme-aware values.

---

## 12. Implementation Direction

Start with Mantine defaults plus a small theme override.

Recommended initial theme direction:

```js
const theme = createTheme({
  primaryColor: 'gray',
  defaultRadius: 'sm',
  fontFamily:
    '-apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif',
  headings: {
    fontFamily:
      '-apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif',
    fontWeight: '650',
  },
})
```

Custom CSS should be limited to:

- three-column shell dimensions
- column dividers
- row selected/hover states
- mailbox reading width
- message alignment
- pending-question callout
- sticky composer boundary

Do not customize every Mantine component globally before screenshot review proves it is necessary.

---

## 13. Visual Acceptance Checklist

The desktop UI passes this visual contract when:

- first impression is Apple Mail-like workflow, not dashboard
- page is quiet and text-first
- no decorative gradients or marketing visuals exist
- columns feel stable and intentional
- selected row state is clear but subtle
- status badges are sparse and calm
- user messages are right-aligned with subtle background
- agent messages are plain left-aligned text
- mailbox content width is comfortable for reading
- empty states are small and specific
- icons are functional, not decorative
- light mode looks polished
- dark mode remains readable and coherent

---

## 14. Open Decisions

Decide after screenshot review:

1. Whether `primaryColor: 'gray'` is enough or if Adjuva needs a soft accent color.
2. Whether `Needs reply` should use `teal` or a quieter `blue/gray` treatment.
3. Whether conversation row height should target closer to `72px` or `84px`.
4. Whether pending question callout should use a tinted background or a neutral border-only treatment.
5. Whether dark mode needs custom dark surface tokens beyond Mantine defaults.

