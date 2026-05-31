import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const dataDir = path.resolve(__dirname, "../data");

const files = {
  conversations: path.join(dataDir, "conversations.json"),
  runs: path.join(dataDir, "runs.json"),
  messages: path.join(dataDir, "mailbox_messages.json")
};

export function ensureStore() {
  fs.mkdirSync(dataDir, { recursive: true });
  for (const file of Object.values(files)) {
    if (!fs.existsSync(file)) {
      fs.writeFileSync(file, "[]\n", "utf8");
    }
  }
}

export function now() {
  return new Date().toISOString();
}

export function makeId(prefix) {
  const random = Math.random().toString(36).slice(2, 10);
  return `${prefix}_${Date.now().toString(36)}_${random}`;
}

export function readCollection(name) {
  ensureStore();
  return JSON.parse(fs.readFileSync(files[name], "utf8"));
}

export function writeCollection(name, records) {
  ensureStore();
  fs.writeFileSync(files[name], `${JSON.stringify(records, null, 2)}\n`, "utf8");
}

export function createRecord(name, record) {
  const records = readCollection(name);
  records.push(record);
  writeCollection(name, records);
  return record;
}

export function updateRecord(name, id, patch) {
  const records = readCollection(name);
  const index = records.findIndex((record) => record.id === id);
  if (index === -1) return null;
  const next = { ...records[index], ...patch };
  records[index] = next;
  writeCollection(name, records);
  return next;
}

export function findRecord(name, id) {
  return readCollection(name).find((record) => record.id === id) ?? null;
}

export function listMessages(conversationId) {
  return readCollection("messages")
    .filter((message) => message.conversation_id === conversationId)
    .sort((a, b) => a.created_at.localeCompare(b.created_at));
}

export function listRuns(conversationId) {
  return readCollection("runs")
    .filter((run) => run.conversation_id === conversationId)
    .sort((a, b) => a.started_at.localeCompare(b.started_at));
}

export function appendMessage({
  conversationId,
  mailboxId,
  runId = null,
  sender,
  type,
  body
}) {
  const timestamp = now();
  return createRecord("messages", {
    id: makeId("msg"),
    conversation_id: conversationId,
    mailbox_id: mailboxId,
    run_id: runId,
    sender,
    type,
    body,
    created_at: timestamp,
    read_at: null,
    ack_at: null
  });
}

export function getActiveRun(conversation) {
  if (!conversation?.active_run_id) return null;
  const run = findRecord("runs", conversation.active_run_id);
  if (!run || run.lifecycle !== "running") return null;
  return run;
}

export function finalizeRun(runId, patch) {
  const run = findRecord("runs", runId);
  if (!run || run.lifecycle === "ended") return run;

  return updateRecord("runs", runId, {
    lifecycle: "ended",
    ended_at: now(),
    ...patch
  });
}

export function markConversation(conversationId, patch) {
  return updateRecord("conversations", conversationId, {
    ...patch,
    updated_at: now()
  });
}

export function dataPaths() {
  return { dataDir, ...files };
}
