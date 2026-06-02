# Adjuva Frontend API Response Examples

This document records the backend API shapes that the frontend should build against.

These examples are for implementation guidance, UI validation, and agent alignment. They are not frontend runtime fixtures. The frontend must call the real backend API.

The API base path is:

```text
http://localhost:8080/api/v1
```

---

## 1. How To Refresh These Examples

When the backend is running locally, refresh response examples with:

```bash
scripts/dev-backend-start.sh --executor-enabled false
scripts/dev-backend-health.sh

curl -sS http://localhost:8080/api/v1/projects
curl -sS http://localhost:8080/api/v1/conversations
curl -sS http://localhost:8080/api/v1/projects/{projectId}/conversations
curl -sS http://localhost:8080/api/v1/conversations/{conversationId}
curl -sS http://localhost:8080/api/v1/conversations/{conversationId}/messages
curl -sS -N http://localhost:8080/api/v1/events
```

If local data does not cover all UI states, do not add mock runtime data to the frontend. Instead, document the representative response shape here and add backend seed/test data separately if needed.

---

## 2. Project List

Endpoint:

```text
GET /api/v1/projects
```

Current response shape:

```json
[
  {
    "id": "018f7b7f3b1c4a2ab9d0f2f62e0a1001",
    "name": "adjuva",
    "slug": "adjuva",
    "description": "Long-running project assistant for Adjuva.",
    "status": "active",
    "workspacePath": "/Users/asimov/Developer/adjuva",
    "defaultProvider": "codex",
    "defaultModel": "codex-cli",
    "createdAt": "2026-06-02T00:00:00Z",
    "updatedAt": "2026-06-02T00:00:00Z"
  }
]
```

Frontend usage:

- `id`: project selection and API routing
- `name`: visible project label
- `status`: only active projects are listed by current backend behavior
- `defaultProvider`: fallback agent/provider label if conversation-level value is unavailable
- `defaultModel`: subtle metadata only if needed

Do not show in the primary project column:

- `description`
- `workspacePath`
- raw timestamps

Known frontend gaps:

- Project list does not currently include `needsActionCount`.
- Project list does not currently include `unreadCount`.
- If the project column needs attention indicators, backend should add summary counts rather than making frontend infer them from every conversation.

Suggested future fields:

```json
{
  "needsActionCount": 2,
  "unreadCount": 5,
  "lastActivityAt": "2026-06-02T00:00:00Z"
}
```

---

## 3. Project Conversations

Endpoint:

```text
GET /api/v1/projects/{projectId}/conversations
```

Current response shape:

```json
[
  {
    "id": "018f7b7f3b1c4a2ab9d0f2f62e0a2001",
    "projectId": "018f7b7f3b1c4a2ab9d0f2f62e0a1001",
    "title": "Review frontend UI contract",
    "sourceType": "manual",
    "sourceRef": null,
    "status": "waiting_for_user",
    "mailboxId": "018f7b7f3b1c4a2ab9d0f2f62e0a3001",
    "workspacePath": "/Users/asimov/Developer/adjuva",
    "defaultProvider": "codex",
    "defaultModel": "codex-cli",
    "activeRunId": null,
    "pendingQuestionMessageId": "018f7b7f3b1c4a2ab9d0f2f62e0a4001",
    "lastActivityAt": "2026-06-02T00:25:00Z",
    "createdAt": "2026-06-02T00:10:00Z",
    "updatedAt": "2026-06-02T00:25:00Z"
  }
]
```

Frontend usage:

- `id`: conversation selection and API routing
- `title`: conversation row title
- `status`: status badge/label
- `defaultProvider`: agent name fallback, such as `codex`
- `defaultModel`: optional subtle metadata
- `pendingQuestionMessageId`: indicates pending question exists
- `lastActivityAt`: relative time in row

Frontend display rules:

- If `title` is missing, blank, or eventually becomes nullable, show `Untitled conversation`.
- Current backend creates new conversations with title `New conversation`. This conflicts with the desired frontend fallback behavior and should be revisited when backend title generation is implemented.
- Do not show `mailboxId`, `activeRunId`, `workspacePath`, or raw provider/session internals in primary UI.

Known frontend gaps:

- Conversation row needs latest user-relevant snippet, but this endpoint does not include one.
- Conversation row needs unread/updated marker, but this endpoint does not include unread fields.

Suggested future fields:

```json
{
  "latestUserRelevantMessage": "Can you check the desktop layout before I wire SSE?",
  "latestMessageAt": "2026-06-02T00:25:00Z",
  "unread": true,
  "updatedSinceLastView": true,
  "needsReply": true
}
```

---

## 4. Global Conversation Summaries

Endpoint:

```text
GET /api/v1/conversations
```

Current response shape:

```json
[
  {
    "id": "018f7b7f3b1c4a2ab9d0f2f62e0a2001",
    "projectId": "018f7b7f3b1c4a2ab9d0f2f62e0a1001",
    "projectName": "adjuva",
    "projectSlug": "adjuva",
    "title": "Review frontend UI contract",
    "status": "waiting_for_user",
    "lastActivityAt": "2026-06-02T00:25:00Z",
    "activeRunId": null,
    "pendingQuestionMessageId": "018f7b7f3b1c4a2ab9d0f2f62e0a4001",
    "updatedAt": "2026-06-02T00:25:00Z"
  }
]
```

Frontend usage:

- This is useful for mobile flat conversation list in the future.
- For desktop, the current contract selects a project first and then lists that project's conversations.
- It may also be useful for notification entry routing.

Known frontend gaps:

- Does not include `defaultProvider` or agent name.
- Does not include latest user-relevant snippet.
- Does not include unread/updated fields.

Do not infer unread state purely from `updatedAt` without a backend-supported last-read marker.

---

## 5. Conversation Detail

Endpoint:

```text
GET /api/v1/conversations/{conversationId}
```

Current response shape:

```json
{
  "conversation": {
    "id": "018f7b7f3b1c4a2ab9d0f2f62e0a2001",
    "projectId": "018f7b7f3b1c4a2ab9d0f2f62e0a1001",
    "title": "Review frontend UI contract",
    "sourceType": "manual",
    "sourceRef": null,
    "status": "waiting_for_user",
    "mailboxId": "018f7b7f3b1c4a2ab9d0f2f62e0a3001",
    "workspacePath": "/Users/asimov/Developer/adjuva",
    "defaultProvider": "codex",
    "defaultModel": "codex-cli",
    "activeRunId": null,
    "pendingQuestionMessageId": "018f7b7f3b1c4a2ab9d0f2f62e0a4001",
    "lastActivityAt": "2026-06-02T00:25:00Z",
    "createdAt": "2026-06-02T00:10:00Z",
    "updatedAt": "2026-06-02T00:25:00Z"
  },
  "messages": [
    {
      "id": "018f7b7f3b1c4a2ab9d0f2f62e0a4000",
      "conversationId": "018f7b7f3b1c4a2ab9d0f2f62e0a2001",
      "mailboxId": "018f7b7f3b1c4a2ab9d0f2f62e0a3001",
      "runId": "018f7b7f3b1c4a2ab9d0f2f62e0a5001",
      "sender": "user",
      "messageType": "user_message",
      "body": "请检查 desktop layout 是否符合 Apple Mail 三栏心智模型。",
      "replyToMessageId": null,
      "metadataJson": null,
      "readAt": null,
      "ackAt": null,
      "createdAt": "2026-06-02T00:12:00Z",
      "updatedAt": "2026-06-02T00:12:00Z"
    },
    {
      "id": "018f7b7f3b1c4a2ab9d0f2f62e0a4001",
      "conversationId": "018f7b7f3b1c4a2ab9d0f2f62e0a2001",
      "mailboxId": "018f7b7f3b1c4a2ab9d0f2f62e0a3001",
      "runId": "018f7b7f3b1c4a2ab9d0f2f62e0a5001",
      "sender": "agent",
      "messageType": "question",
      "body": "Do you want the search input visible but disabled until backend search exists?",
      "replyToMessageId": null,
      "metadataJson": null,
      "readAt": null,
      "ackAt": null,
      "createdAt": "2026-06-02T00:25:00Z",
      "updatedAt": "2026-06-02T00:25:00Z"
    }
  ],
  "runs": [
    {
      "id": "018f7b7f3b1c4a2ab9d0f2f62e0a5001",
      "conversationId": "018f7b7f3b1c4a2ab9d0f2f62e0a2001",
      "providerSessionId": "018f7b7f3b1c4a2ab9d0f2f62e0a6001",
      "triggerMessageId": "018f7b7f3b1c4a2ab9d0f2f62e0a4000",
      "triggerType": "user_message",
      "provider": "codex",
      "model": "codex-cli",
      "lifecycle": "running",
      "terminationReason": null,
      "processId": null,
      "externalSessionIdBefore": null,
      "externalSessionIdAfter": null,
      "startedAt": "2026-06-02T00:12:01Z",
      "endedAt": null,
      "exitCode": null,
      "signal": null,
      "finalEventType": null,
      "timeoutKind": null,
      "errorMessage": null,
      "prompt": "请检查 desktop layout 是否符合 Apple Mail 三栏心智模型。",
      "commandLine": null,
      "metadataJson": null,
      "createdAt": "2026-06-02T00:12:01Z",
      "updatedAt": "2026-06-02T00:12:01Z"
    }
  ]
}
```

Frontend usage:

- `conversation.title`: Mailbox header title
- `conversation.status`: Mailbox status
- `conversation.defaultProvider`: Mailbox agent name fallback
- `conversation.defaultModel`: optional subtle metadata
- `conversation.lastActivityAt`: Mailbox header relative time
- `conversation.pendingQuestionMessageId`: points to the pending question message
- `messages`: timeline source of truth
- `runs`: run status summary source of truth

Do not show by default:

- `mailboxId`
- `workspacePath`
- `providerSessionId`
- `processId`
- `externalSessionIdBefore`
- `externalSessionIdAfter`
- `commandLine`
- raw prompt text outside the message timeline

Pending question rule:

- If `conversation.pendingQuestionMessageId` matches a message in `messages`, show that message in the pending-question callout.
- If the id is present but the message is missing from the payload, show a conservative `Needs reply` state and refetch detail.

---

## 6. Conversation Messages

Endpoint:

```text
GET /api/v1/conversations/{conversationId}/messages
GET /api/v1/conversations/{conversationId}/messages?after={messageId}
```

Current response shape:

```json
[
  {
    "id": "018f7b7f3b1c4a2ab9d0f2f62e0a4000",
    "conversationId": "018f7b7f3b1c4a2ab9d0f2f62e0a2001",
    "mailboxId": "018f7b7f3b1c4a2ab9d0f2f62e0a3001",
    "runId": "018f7b7f3b1c4a2ab9d0f2f62e0a5001",
    "sender": "user",
    "messageType": "user_message",
    "body": "The desktop page should feel like Apple Mail, not a SaaS dashboard.",
    "replyToMessageId": null,
    "metadataJson": null,
    "readAt": null,
    "ackAt": null,
    "createdAt": "2026-06-02T00:12:00Z",
    "updatedAt": "2026-06-02T00:12:00Z"
  }
]
```

Timeline mapping:

| `sender` | `messageType` | UI treatment |
| --- | --- | --- |
| `user` | `user_message` | right-aligned subtle bubble |
| `user` | `answer` | right-aligned subtle bubble, reply to pending question |
| `agent` | `agent_message` | left-aligned plain text |
| `agent` | `question` | left-aligned message and pending-question callout when active |
| `agent` | `terminal_event` | subtle system-style terminal note |
| `system` | `system_notice` | subtle system notice |

Do not show `mailboxId` or raw runtime ids in the timeline.

---

## 7. Send User Message

Endpoint:

```text
POST /api/v1/conversations/{conversationId}/messages
```

Request:

```json
{
  "body": "Yes, keep search visible but disabled with Coming Soon.",
  "autoStart": true
}
```

Response shape:

```json
{
  "message": {
    "id": "018f7b7f3b1c4a2ab9d0f2f62e0a4002",
    "conversationId": "018f7b7f3b1c4a2ab9d0f2f62e0a2001",
    "mailboxId": "018f7b7f3b1c4a2ab9d0f2f62e0a3001",
    "runId": null,
    "sender": "user",
    "messageType": "answer",
    "body": "Yes, keep search visible but disabled with Coming Soon.",
    "replyToMessageId": "018f7b7f3b1c4a2ab9d0f2f62e0a4001",
    "metadataJson": null,
    "readAt": null,
    "ackAt": null,
    "createdAt": "2026-06-02T00:30:00Z",
    "updatedAt": "2026-06-02T00:30:00Z"
  },
  "run": {
    "id": "018f7b7f3b1c4a2ab9d0f2f62e0a5002",
    "conversationId": "018f7b7f3b1c4a2ab9d0f2f62e0a2001",
    "providerSessionId": "018f7b7f3b1c4a2ab9d0f2f62e0a6001",
    "triggerMessageId": "018f7b7f3b1c4a2ab9d0f2f62e0a4002",
    "triggerType": "resume",
    "provider": "codex",
    "model": "codex-cli",
    "lifecycle": "running",
    "terminationReason": null,
    "processId": null,
    "externalSessionIdBefore": null,
    "externalSessionIdAfter": null,
    "startedAt": "2026-06-02T00:30:01Z",
    "endedAt": null,
    "exitCode": null,
    "signal": null,
    "finalEventType": null,
    "timeoutKind": null,
    "errorMessage": null,
    "prompt": "Yes, keep search visible but disabled with Coming Soon.",
    "commandLine": null,
    "metadataJson": null,
    "createdAt": "2026-06-02T00:30:01Z",
    "updatedAt": "2026-06-02T00:30:01Z"
  }
}
```

Frontend behavior:

- Disable composer while sending.
- Do not submit trimmed empty `body`.
- Preserve composer text on failed request.
- Invalidate/refetch conversation detail after success.
- Invalidate/refetch conversation list after success.

Notes:

- When conversation status is `waiting_for_user`, backend writes the message as `answer`.
- Otherwise backend writes it as `user_message`.
- `run` can be `null` when a message is appended to an existing active run or when `autoStart` is false.

---

## 8. Create Conversation

Endpoint:

```text
POST /api/v1/projects/{projectId}/conversations
```

Request:

```json
{
  "title": null,
  "provider": null,
  "model": null,
  "workspacePath": null,
  "sourceType": "manual",
  "sourceRef": null
}
```

Current response shape:

```json
{
  "id": "018f7b7f3b1c4a2ab9d0f2f62e0a2002",
  "projectId": "018f7b7f3b1c4a2ab9d0f2f62e0a1001",
  "title": "New conversation",
  "sourceType": "manual",
  "sourceRef": null,
  "status": "idle",
  "mailboxId": "018f7b7f3b1c4a2ab9d0f2f62e0a3002",
  "workspacePath": "/Users/asimov/Developer/adjuva",
  "defaultProvider": "codex",
  "defaultModel": "codex-cli",
  "activeRunId": null,
  "pendingQuestionMessageId": null,
  "lastActivityAt": "2026-06-02T00:35:00Z",
  "createdAt": "2026-06-02T00:35:00Z",
  "updatedAt": "2026-06-02T00:35:00Z"
}
```

Known product/API mismatch:

- Product decision: new conversation without generated title should display `Untitled conversation`.
- Current backend behavior: null title becomes `New conversation`.

Recommended backend change:

- Allow new conversation title to be null or empty until backend title generation exists.
- Or return a separate `titleGenerated` / `displayTitle` field if persistence title must remain non-null.

Frontend should not hard-code `New conversation` as a product label.

---

## 9. SSE Events

Endpoint:

```text
GET /api/v1/events
```

Heartbeat response:

```text
event:heartbeat
retry:2000
data:ok
```

Signal response:

```text
id:018f7b7f3b1c4a2ab9d0f2f62e0a7001
event:mailbox.message.appended
retry:2000
data:{"id":"018f7b7f3b1c4a2ab9d0f2f62e0a7001","eventType":"mailbox.message.appended","aggregateType":"conversation","aggregateId":"018f7b7f3b1c4a2ab9d0f2f62e0a2001","createdAt":"2026-06-02T00:25:00Z"}
```

Current event data shape:

```json
{
  "id": "018f7b7f3b1c4a2ab9d0f2f62e0a7001",
  "eventType": "mailbox.message.appended",
  "aggregateType": "conversation",
  "aggregateId": "018f7b7f3b1c4a2ab9d0f2f62e0a2001",
  "createdAt": "2026-06-02T00:25:00Z"
}
```

Frontend behavior:

- Treat SSE as a notification/refetch trigger only.
- Do not use SSE payload as complete source of truth.
- On conversation-related events, invalidate/refetch:
  - conversation list
  - selected conversation detail when `aggregateId` matches current conversation
- Show subtle bottom-right `Reconnecting...` only while EventSource is disconnected/retrying.

---

## 10. Error Response Shape

Spring error responses may vary by Spring Boot configuration. Frontend should not depend on a highly specific error body for MVP.

Representative error:

```json
{
  "timestamp": "2026-06-02T00:40:00.000+00:00",
  "status": 404,
  "error": "Not Found",
  "path": "/api/v1/conversations/missing"
}
```

Frontend behavior:

- Show specific UI copy based on the failed operation, not raw backend message.
- Examples:
  - `Could not load projects`
  - `Could not load conversations`
  - `Could not load messages`
  - `Could not send message`
- Include retry when retry is possible.

---

## 11. Fields Needed For High-Quality Desktop UI

The current API is close, but a polished desktop UI needs these backend additions to avoid frontend inference and duplication:

1. Project summary counts:

```json
{
  "needsActionCount": 2,
  "unreadCount": 5,
  "lastActivityAt": "2026-06-02T00:25:00Z"
}
```

2. Conversation row preview:

```json
{
  "latestUserRelevantMessage": "Can you check the desktop layout before I wire SSE?",
  "latestMessageAt": "2026-06-02T00:25:00Z"
}
```

3. Conversation unread/update state:

```json
{
  "unread": true,
  "updatedSinceLastView": true
}
```

4. Conversation agent display:

```json
{
  "agentName": "codex"
}
```

Until backend provides these fields:

- do not fake unread state
- do not compute project counts by loading every conversation detail
- use `defaultProvider` as agent name fallback where available
- leave snippets blank or use a conservative fallback only if the API already supplies enough message data in the selected context

