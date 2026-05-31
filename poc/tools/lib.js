function env(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} is required`);
  }
  return value;
}

function apiBaseUrl() {
  return process.env.ADJUVA_API_BASE_URL || "http://localhost:4100";
}

function conversationId() {
  return env("ADJUVA_CONVERSATION_ID");
}

function runId() {
  return process.env.ADJUVA_RUN_ID || null;
}

function readBodyArg() {
  const body = process.argv.slice(2).join(" ").trim();
  if (!body) throw new Error("message body is required");
  return body;
}

function argValue(name, fallback = null) {
  const prefix = `${name}=`;
  const match = process.argv.slice(2).find((arg) => arg.startsWith(prefix));
  if (match) return match.slice(prefix.length);

  const index = process.argv.indexOf(name);
  if (index !== -1 && process.argv[index + 1]) return process.argv[index + 1];

  return fallback;
}

async function request(path, { method = "GET", body = null } = {}) {
  const response = await fetch(`${apiBaseUrl()}${path}`, {
    method,
    headers: body ? { "content-type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined
  });

  if (!response.ok) {
    throw new Error(`${response.status} ${await response.text()}`);
  }

  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

function printJson(value) {
  process.stdout.write(`${JSON.stringify(value, null, 2)}\n`);
}

async function main(fn) {
  try {
    const value = await fn();
    printJson(value);
  } catch (error) {
    process.stderr.write(`${error.message}\n`);
    process.exit(1);
  }
}

module.exports = {
  argValue,
  conversationId,
  main,
  readBodyArg,
  request,
  runId
};
