import { execFile } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const apiBaseUrl = requiredEnv("ADJUVA_API_BASE_URL");
const conversationId = requiredEnv("ADJUVA_CONVERSATION_ID");
const runId = requiredEnv("ADJUVA_RUN_ID");
const initialPrompt = process.env.ADJUVA_INITIAL_PROMPT ?? "";
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const toolsDir = path.resolve(__dirname, "../../tools");

async function main() {
  const lastMessage = await tool("adjuva-send-message.js", [
    "Mock agent started. I will ask one mailbox question before completing."
  ]);

  const question = await tool("adjuva-ask-user.js", [
    `POC question: should I include archived data while handling "${initialPrompt || "this task"}"?`
  ]);

  const reply = await tool("adjuva-wait-user.js", ["--after", question.id, "--timeout", "300"]);
  const answer = reply?.body ?? "(no reply)";

  await tool("adjuva-send-message.js", [`I received your reply: ${answer}`]);
  await tool("adjuva-done.js", [
    `Mock run completed after reading user reply. Last startup message: ${lastMessage.id}`
  ]);
}

function tool(scriptName, args) {
  return new Promise((resolve, reject) => {
    execFile(process.execPath, [path.join(toolsDir, scriptName), ...args], {
      env: {
        ...process.env,
        ADJUVA_API_BASE_URL: apiBaseUrl,
        ADJUVA_CONVERSATION_ID: conversationId,
        ADJUVA_RUN_ID: runId
      }
    }, (error, stdout, stderr) => {
      if (error) {
        reject(new Error(stderr || error.message));
        return;
      }

      resolve(stdout.trim() ? JSON.parse(stdout) : null);
    });
  });
}

async function post(path, payload) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(payload)
  });
  if (!response.ok) throw new Error(`${response.status} ${await response.text()}`);
  return response.json();
}

function requiredEnv(name) {
  const value = process.env[name];
  if (!value) throw new Error(`${name} is required`);
  return value;
}

main().catch(async (error) => {
  try {
    await post(`/api/conversations/${conversationId}/mailbox/events/fail`, {
      run_id: runId,
      body: error.message
    });
  } catch {
    // Ignore secondary failure. The parent process will mark the run failed.
  }
  console.error(error);
  process.exit(1);
});
