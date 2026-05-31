const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:4100";

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "content-type": "application/json",
      ...(options.headers || {})
    }
  });

  if (!response.ok) {
    throw new Error(`${response.status} ${await response.text()}`);
  }

  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

export function listConversations() {
  return request("/api/conversations");
}

export function createConversation(payload) {
  return request("/api/conversations", {
    method: "POST",
    body: JSON.stringify(payload)
  });
}

export function getConversation(id) {
  return request(`/api/conversations/${id}`);
}

export function sendUserMessage(id, body) {
  return request(`/api/conversations/${id}/messages`, {
    method: "POST",
    body: JSON.stringify({ body })
  });
}

export function startRun(id, prompt = "") {
  return request(`/api/conversations/${id}/runs`, {
    method: "POST",
    body: JSON.stringify({ prompt })
  });
}

export function getArticleTaskStatus() {
  return request("/api/demo/article-task");
}

export function triggerArticleTask() {
  return request("/api/demo/article-task/trigger", {
    method: "POST",
    body: JSON.stringify({})
  });
}

export function scheduleArticleTask(delayMs = 5000) {
  return request("/api/demo/article-task/schedule", {
    method: "POST",
    body: JSON.stringify({ delay_ms: delayMs })
  });
}
