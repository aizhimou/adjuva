import { createRecord, makeId, now } from "./store.js";
import { startRun } from "./runner.js";

const DEFAULT_DELAY_MS = 5000;

let timer = null;
let scheduledFor = null;
let lastConversationId = null;
let lastStartedAt = null;

export function getArticleDemoStatus() {
  return {
    scheduled: Boolean(timer),
    scheduled_for: scheduledFor,
    last_conversation_id: lastConversationId,
    last_started_at: lastStartedAt
  };
}

export function scheduleArticleDemoTask(delayMs = DEFAULT_DELAY_MS) {
  clearArticleDemoTimer();

  const safeDelayMs = Math.max(1000, Number(delayMs) || DEFAULT_DELAY_MS);
  scheduledFor = new Date(Date.now() + safeDelayMs).toISOString();

  timer = setTimeout(() => {
    timer = null;
    scheduledFor = null;
    try {
      triggerArticleDemoTask();
    } catch (error) {
      console.error("[article-demo] scheduled task failed", error);
    }
  }, safeDelayMs);

  return getArticleDemoStatus();
}

export function triggerArticleDemoTask() {
  const timestamp = now();
  const conversation = createRecord("conversations", {
    id: makeId("conv"),
    title: `Daily Article Review ${new Date().toLocaleString("en-NZ", {
      dateStyle: "medium",
      timeStyle: "short"
    })}`,
    provider: "codex",
    model: "codex-cli",
    workspace: null,
    mailbox_id: makeId("mbox"),
    cli_session_id: null,
    status: "idle",
    active_run_id: null,
    created_at: timestamp,
    updated_at: timestamp,
    source: "scheduled_article_demo"
  });

  lastConversationId = conversation.id;
  lastStartedAt = timestamp;

  const run = startRun(conversation.id, {
    provider: "codex",
    prompt: buildArticleDemoPrompt()
  });

  return { conversation, run };
}

export function demoArticleHtml() {
  return `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <title>Small Teams Need Explicit Memory for AI Agents</title>
  </head>
  <body>
    <article>
      <h1>Small Teams Need Explicit Memory for AI Agents</h1>
      <p>As AI agents move from one-off assistants into persistent project collaborators, small teams need a different operating model.</p>
      <p>The most reliable pattern is to keep project memory explicit, readable, and versioned. Hidden chat context works for a single session, but it is weak when work spans days or weeks.</p>
      <p>A practical project assistant should track tasks, decisions, artifacts, approvals, and conversation state. It should ask for business decisions when needed, then continue the original task after receiving the answer.</p>
      <p>Teams should avoid building a custom reasoning engine too early. A thin orchestration layer around strong CLI agents is easier to ship and easier to replace later.</p>
      <p>The key product question is not whether the agent can answer one prompt. It is whether the project keeps moving while the user is away, and whether the result is auditable when the user returns.</p>
    </article>
  </body>
</html>`;
}

function clearArticleDemoTimer() {
  if (timer) {
    clearTimeout(timer);
    timer = null;
  }
  scheduledFor = null;
}

function buildArticleDemoPrompt() {
  return `This is a system-scheduled Adjuva task.

Task: collect today's curated article from the user, summarize it, ask for feedback, and revise if needed.

Required workflow:

1. Send a short mailbox message saying the scheduled daily article review task has started.
2. Ask the user to send one URL for today's selected article.
3. Wait for the user's reply through the mailbox.
4. Extract the first URL from the reply. If there is no URL, ask once more for a URL and wait again.
5. Fetch the URL content using normal command-line tools.
6. Summarize the article in the mailbox in Chinese with:
   - title/source if discoverable
   - 5 key points
   - why it matters
   - one suggested follow-up action
7. Ask the user whether the summary needs changes.
8. Wait for the user's feedback through the mailbox.
9. If the user asks for changes, send one revised summary through the mailbox.
10. If the user says no changes are needed, acknowledge briefly.
11. Call adjuva-done only after the feedback step is handled.

For local POC testing, a valid article URL is:

http://localhost:4100/demo/article-source`;
}
