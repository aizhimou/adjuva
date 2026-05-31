import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
  appendMessage,
  createRecord,
  finalizeRun,
  findRecord,
  getActiveRun,
  makeId,
  markConversation,
  now,
  updateRecord
} from "./store.js";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const serverRoot = path.resolve(__dirname, "..");
const pocRoot = path.resolve(serverRoot, "..");
const defaultWorkspacesDir = path.join(serverRoot, "workspaces");

const children = new Map();

export function startRun(conversationId, { provider = null, prompt = "" } = {}) {
  const conversation = findRecord("conversations", conversationId);
  if (!conversation) {
    const error = new Error("Conversation not found");
    error.status = 404;
    throw error;
  }
  const selectedProvider = provider ?? conversation.provider ?? "mock";

  const activeRun = getActiveRun(conversation);
  if (activeRun) return activeRun;

  const timestamp = now();
  const run = createRecord("runs", {
    id: makeId("run"),
    conversation_id: conversationId,
    provider: selectedProvider,
    lifecycle: "running",
    termination_reason: null,
    process_id: null,
    cli_session_id_before: conversation.cli_session_id ?? null,
    cli_session_id_after: conversation.cli_session_id ?? null,
    started_at: timestamp,
    ended_at: null,
    exit_code: null,
    signal: null,
    final_event_type: null,
    timeout_kind: null,
    error_message: null,
    prompt
  });

  markConversation(conversationId, {
    status: "running",
    active_run_id: run.id
  });

  const command = buildCommand({
    provider: selectedProvider,
    prompt,
    conversation: { ...conversation, active_run_id: run.id },
    run
  });

  const child = spawn(command.bin, command.args, {
    cwd: command.cwd,
    env: command.env,
    stdio: ["ignore", "pipe", "pipe"]
  });

  children.set(run.id, child);
  updateRecord("runs", run.id, { process_id: child.pid });

  let stdoutBuffer = "";
  child.stdout.on("data", (chunk) => {
    const text = chunk.toString();
    process.stdout.write(`[${run.id}] ${text}`);
    if (selectedProvider === "codex") {
      stdoutBuffer = readCodexEvents({ runId: run.id, conversationId, buffer: stdoutBuffer + text });
    }
  });

  child.stderr.on("data", (chunk) => {
    process.stderr.write(`[${run.id} stderr] ${chunk}`);
  });

  child.on("exit", (exitCode, signal) => {
    children.delete(run.id);
    const latestRun = findRecord("runs", run.id);
    if (!latestRun || latestRun.lifecycle === "ended") return;

    finalizeRun(run.id, {
      termination_reason: exitCode === 0 ? "unknown" : "failed",
      exit_code: exitCode,
      signal,
      final_event_type: null,
      error_message: exitCode === 0 ? "Process exited without terminal event" : "Process failed"
    });

    markConversation(conversationId, {
      status: exitCode === 0 ? "failed" : "failed",
      active_run_id: null
    });
  });

  return findRecord("runs", run.id);
}

export function cancelRun(runId) {
  const child = children.get(runId);
  if (child) child.kill("SIGTERM");

  const run = findRecord("runs", runId);
  if (!run) return null;

  const finalized = finalizeRun(runId, {
    termination_reason: "cancelled",
    signal: "SIGTERM",
    final_event_type: "cancelled"
  });

  markConversation(run.conversation_id, {
    status: "cancelled",
    active_run_id: null
  });

  return finalized;
}

function buildCommand({ provider, prompt, conversation, run }) {
  const commonEnv = {
    ...process.env,
    ADJUVA_API_BASE_URL: process.env.ADJUVA_API_BASE_URL ?? "http://localhost:4100",
    ADJUVA_CONVERSATION_ID: conversation.id,
    ADJUVA_RUN_ID: run.id,
    ADJUVA_MAILBOX_TOKEN: process.env.ADJUVA_MAILBOX_TOKEN ?? "poc-token",
    ADJUVA_INITIAL_PROMPT: prompt
  };

  if (provider === "mock") {
    return {
      bin: process.execPath,
      args: [path.join(__dirname, "mock-agent.js")],
      cwd: serverRoot,
      env: commonEnv
    };
  }

  if (provider === "codex") {
    const workspace = ensureCodexWorkspace(conversation);
    const codexPrompt = buildCodexPrompt(prompt);
    const baseArgs = [
      "exec",
      "--json",
      "--skip-git-repo-check",
      "--sandbox",
      "danger-full-access"
    ];

    const args = conversation.cli_session_id
      ? [
          "exec",
          "resume",
          "--json",
          "--skip-git-repo-check",
          "--dangerously-bypass-approvals-and-sandbox",
          conversation.cli_session_id,
          codexPrompt
        ]
      : [...baseArgs, "-C", workspace, codexPrompt];

    return {
      bin: process.env.ADJUVA_CODEX_BIN ?? "codex",
      args,
      cwd: workspace,
      env: {
        ...commonEnv,
        PATH: `${path.join(pocRoot, "tools")}:${process.env.PATH ?? ""}`
      }
    };
  }

  const error = new Error(`Unsupported provider: ${provider}`);
  error.status = 400;
  throw error;
}

function ensureCodexWorkspace(conversation) {
  const workspace = conversation.workspace || path.join(defaultWorkspacesDir, conversation.id);
  fs.mkdirSync(workspace, { recursive: true });

  const template = fs.readFileSync(path.join(__dirname, "agents-template.md"), "utf8");
  const content = `${template}

## POC Runtime Details

- Conversation ID: ${conversation.id}
- Mailbox ID: ${conversation.mailbox_id}
- Provider: codex
- Tool directory: ${path.join(pocRoot, "tools")}

You can call tools either by script name if PATH is available, or by absolute path:

\`\`\`bash
node ${path.join(pocRoot, "tools", "adjuva-ask-user.js")} "Question text"
node ${path.join(pocRoot, "tools", "adjuva-wait-user.js")} --after MESSAGE_ID --timeout 300
node ${path.join(pocRoot, "tools", "adjuva-done.js")} "Done summary"
\`\`\`
`;

  fs.writeFileSync(path.join(workspace, "AGENTS.md"), content, "utf8");
  markConversation(conversation.id, { workspace });
  return workspace;
}

function buildCodexPrompt(prompt) {
  return `User task:

${prompt || "Help the user with this Adjuva conversation."}

Adjuva mailbox protocol:

1. Treat the user task above as the primary objective.
2. Send user-visible progress, questions, and final results through the mailbox tools documented in AGENTS.md.
3. If you need user input, call adjuva-ask-user, then adjuva-wait-user, then continue the original user task using the reply.
4. Do not stop after merely acknowledging or summarising the user's reply.
5. Call adjuva-done only after the original user task is complete.
6. Call adjuva-fail if you cannot complete the original user task, and explain why.

Do not just answer in final text. The mailbox is the user-visible conversation channel.`;
}

function readCodexEvents({ runId, conversationId, buffer }) {
  const lines = buffer.split("\n");
  const rest = lines.pop() ?? "";

  for (const line of lines) {
    if (!line.trim().startsWith("{")) continue;
    try {
      const event = JSON.parse(line);
      handleCodexEvent({ runId, conversationId, event });
    } catch {
      // Codex may emit non-JSON warnings around JSONL output.
    }
  }

  return rest;
}

function handleCodexEvent({ runId, conversationId, event }) {
  if (event.type === "thread.started" && event.thread_id) {
    updateRecord("runs", runId, { cli_session_id_after: event.thread_id });
    markConversation(conversationId, { cli_session_id: event.thread_id });
    return;
  }

  if (event.type === "item.completed" && event.item?.type === "agent_message") {
    const conversation = findRecord("conversations", conversationId);
    if (!conversation || !event.item.text) return;
    const run = findRecord("runs", runId);
    if (run?.lifecycle === "ended") return;
    appendMessage({
      conversationId,
      mailboxId: conversation.mailbox_id,
      runId,
      sender: "agent",
      type: "agent_message",
      body: event.item.text
    });
  }
}
