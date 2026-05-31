import express from "express";
import cors from "cors";
import {
  demoArticleHtml,
  getArticleDemoStatus,
  scheduleArticleDemoTask,
  triggerArticleDemoTask
} from "./article-demo.js";
import {
  appendMessage,
  createRecord,
  dataPaths,
  ensureStore,
  finalizeRun,
  findRecord,
  getActiveRun,
  listMessages,
  listRuns,
  makeId,
  markConversation,
  now,
  readCollection,
  updateRecord
} from "./store.js";
import { cancelRun, startRun } from "./runner.js";

const app = express();
const port = Number(process.env.PORT ?? 4100);

ensureStore();

app.use(cors());
app.use(express.json({ limit: "1mb" }));

app.get("/api/health", (_req, res) => {
  res.json({ ok: true, data: dataPaths() });
});

app.get("/demo/article-source", (_req, res) => {
  res.type("html").send(demoArticleHtml());
});

app.get("/api/demo/article-task", (_req, res) => {
  res.json(getArticleDemoStatus());
});

app.post("/api/demo/article-task/trigger", (_req, res) => {
  try {
    res.status(201).json(triggerArticleDemoTask());
  } catch (error) {
    res.status(error.status ?? 500).json({ error: error.message });
  }
});

app.post("/api/demo/article-task/schedule", (req, res) => {
  res.json(scheduleArticleDemoTask(req.body.delay_ms));
});

app.get("/api/conversations", (_req, res) => {
  const conversations = readCollection("conversations")
    .sort((a, b) => b.updated_at.localeCompare(a.updated_at))
    .map((conversation) => ({
      ...conversation,
      active_run: getActiveRun(conversation)
    }));
  res.json(conversations);
});

app.post("/api/conversations", (req, res) => {
  const timestamp = now();
  const conversation = createRecord("conversations", {
    id: makeId("conv"),
    title: req.body.title || "New conversation",
    provider: req.body.provider || "mock",
    model: req.body.model || (req.body.provider === "codex" ? "codex-cli" : "mock-agent"),
    workspace: req.body.workspace || null,
    mailbox_id: makeId("mbox"),
    cli_session_id: null,
    status: "idle",
    active_run_id: null,
    created_at: timestamp,
    updated_at: timestamp
  });

  res.status(201).json(conversation);
});

app.get("/api/conversations/:conversationId", (req, res) => {
  const conversation = findRecord("conversations", req.params.conversationId);
  if (!conversation) return res.status(404).json({ error: "Conversation not found" });

  res.json({
    conversation,
    messages: listMessages(conversation.id),
    runs: listRuns(conversation.id)
  });
});

app.post("/api/conversations/:conversationId/messages", (req, res) => {
  const conversation = findRecord("conversations", req.params.conversationId);
  if (!conversation) return res.status(404).json({ error: "Conversation not found" });
  if (!req.body.body) return res.status(400).json({ error: "body is required" });

  const activeRun = getActiveRun(conversation);
  const message = appendMessage({
    conversationId: conversation.id,
    mailboxId: conversation.mailbox_id,
    runId: activeRun?.id ?? null,
    sender: "user",
    type: conversation.status === "waiting_for_user" ? "answer" : "user_message",
    body: req.body.body
  });

  markConversation(conversation.id, {
    status: activeRun ? conversation.status : "idle"
  });

  const shouldAutoStart = req.body.auto_start !== false && !activeRun;
  const run = shouldAutoStart
    ? startRun(conversation.id, {
        provider: conversation.provider,
        prompt: req.body.body
      })
    : null;

  res.status(201).json({ message, run });
});

app.post("/api/conversations/:conversationId/runs", (req, res) => {
  try {
    const run = startRun(req.params.conversationId, {
      provider: req.body.provider,
      prompt: req.body.prompt ?? ""
    });
    res.status(201).json(run);
  } catch (error) {
    res.status(error.status ?? 500).json({ error: error.message });
  }
});

app.post("/api/runs/:runId/cancel", (req, res) => {
  const run = cancelRun(req.params.runId);
  if (!run) return res.status(404).json({ error: "Run not found" });
  res.json(run);
});

app.get("/api/conversations/:conversationId/mailbox/messages", (req, res) => {
  const conversation = findRecord("conversations", req.params.conversationId);
  if (!conversation) return res.status(404).json({ error: "Conversation not found" });

  const messages = messagesAfter(listMessages(conversation.id), req.query.after);
  res.json(messages);
});

app.post("/api/conversations/:conversationId/mailbox/messages", (req, res) => {
  const conversation = findRecord("conversations", req.params.conversationId);
  if (!conversation) return res.status(404).json({ error: "Conversation not found" });
  if (!req.body.body) return res.status(400).json({ error: "body is required" });

  const message = appendMessage({
    conversationId: conversation.id,
    mailboxId: conversation.mailbox_id,
    runId: req.body.run_id ?? conversation.active_run_id ?? null,
    sender: req.body.sender || "agent",
    type: req.body.type || "agent_message",
    body: req.body.body
  });

  if (message.sender === "agent" && message.type === "question") {
    markConversation(conversation.id, { status: "waiting_for_user" });
  }

  res.status(201).json(message);
});

app.get("/api/conversations/:conversationId/mailbox/wait", async (req, res) => {
  const conversation = findRecord("conversations", req.params.conversationId);
  if (!conversation) return res.status(404).json({ error: "Conversation not found" });

  const timeoutMs = Math.min(Number(req.query.timeout ?? 30), 300) * 1000;
  const sender = req.query.sender ? String(req.query.sender) : null;
  const deadline = Date.now() + timeoutMs;

  while (Date.now() <= deadline) {
    const messages = messagesAfter(listMessages(conversation.id), req.query.after);
    const message = messages.find((item) => !sender || item.sender === sender);
    if (message) {
      const timestamp = now();
      updateRecord("messages", message.id, { read_at: timestamp });
      if (message.sender === "user" && conversation.active_run_id) {
        markConversation(conversation.id, { status: "running" });
      }
      return res.json({ ...message, read_at: timestamp });
    }
    await sleep(500);
  }

  res.json(null);
});

app.post("/api/conversations/:conversationId/mailbox/messages/:messageId/ack", (req, res) => {
  const message = updateRecord("messages", req.params.messageId, { ack_at: now() });
  if (!message || message.conversation_id !== req.params.conversationId) {
    return res.status(404).json({ error: "Message not found" });
  }
  res.json(message);
});

app.post("/api/conversations/:conversationId/mailbox/events/done", (req, res) => {
  const conversation = findRecord("conversations", req.params.conversationId);
  if (!conversation) return res.status(404).json({ error: "Conversation not found" });

  const runId = req.body.run_id ?? conversation.active_run_id;
  if (!runId) return res.status(400).json({ error: "run_id is required" });

  const message = appendMessage({
    conversationId: conversation.id,
    mailboxId: conversation.mailbox_id,
    runId,
    sender: "agent",
    type: "terminal_event",
    body: req.body.body || "done"
  });

  const run = finalizeRun(runId, {
    termination_reason: "completed",
    exit_code: 0,
    signal: null,
    final_event_type: "done"
  });

  markConversation(conversation.id, {
    status: "completed",
    active_run_id: null
  });

  res.json({ message, run });
});

app.post("/api/conversations/:conversationId/mailbox/events/fail", (req, res) => {
  const conversation = findRecord("conversations", req.params.conversationId);
  if (!conversation) return res.status(404).json({ error: "Conversation not found" });

  const runId = req.body.run_id ?? conversation.active_run_id;
  if (!runId) return res.status(400).json({ error: "run_id is required" });

  const message = appendMessage({
    conversationId: conversation.id,
    mailboxId: conversation.mailbox_id,
    runId,
    sender: "agent",
    type: "terminal_event",
    body: req.body.body || "failed"
  });

  const run = finalizeRun(runId, {
    termination_reason: "failed",
    exit_code: 1,
    signal: null,
    final_event_type: "fail",
    error_message: req.body.body || "Agent failed"
  });

  markConversation(conversation.id, {
    status: "failed",
    active_run_id: null
  });

  res.json({ message, run });
});

app.listen(port, () => {
  console.log(`Adjuva POC server listening on http://localhost:${port}`);
});

function messagesAfter(messages, afterId) {
  if (!afterId) return messages;
  const index = messages.findIndex((message) => message.id === afterId);
  if (index === -1) return messages;
  return messages.slice(index + 1);
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
