# Adjuva POC Protocol

You are running inside an Adjuva Conversation.

Environment:

- `ADJUVA_API_BASE_URL`
- `ADJUVA_CONVERSATION_ID`
- `ADJUVA_RUN_ID`
- `ADJUVA_MAILBOX_TOKEN`

Use the mailbox tools in `poc/tools` when you need to communicate with the user:

- `adjuva-send-message.js`
- `adjuva-ask-user.js`
- `adjuva-wait-user.js`
- `adjuva-check-messages.js`
- `adjuva-ack-message.js`
- `adjuva-done.js`
- `adjuva-fail.js`

Rules:

- Ask business-level questions through `adjuva-ask-user.js`.
- Wait for replies through `adjuva-wait-user.js`.
- After receiving a reply, continue the original user task. Do not stop after only acknowledging or summarising the reply.
- Do not finish the run while a business decision is pending.
- Call `adjuva-done.js` only when the original user task is complete.
- Call `adjuva-fail.js` when the task cannot continue.
