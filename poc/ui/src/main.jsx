import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import {
  createConversation,
  getArticleTaskStatus,
  getConversation,
  listConversations,
  scheduleArticleTask,
  sendUserMessage,
  startRun,
  triggerArticleTask
} from "./api.js";
import "./styles.css";

function App() {
  const [conversations, setConversations] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [detail, setDetail] = useState(null);
  const [draft, setDraft] = useState("");
  const [provider, setProvider] = useState("mock");
  const [articleTask, setArticleTask] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const selectedConversation = detail?.conversation ?? null;
  const messages = detail?.messages ?? [];
  const runs = detail?.runs ?? [];
  const lastRun = runs.at(-1) ?? null;

  useEffect(() => {
    refreshList();
    refreshArticleTask({ silent: true });
    const timer = setInterval(() => {
      refreshList({ silent: true });
      refreshArticleTask({ silent: true });
    }, 1500);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    if (!selectedId) return undefined;
    refreshDetail(selectedId);
    const timer = setInterval(() => {
      refreshList({ silent: true });
      refreshDetail(selectedId, { silent: true });
    }, 1500);
    return () => clearInterval(timer);
  }, [selectedId]);

  const title = useMemo(() => {
    if (!selectedConversation) return "No conversation selected";
    return selectedConversation.title || selectedConversation.id;
  }, [selectedConversation]);

  async function refreshList({ silent = false } = {}) {
    try {
      const next = await listConversations();
      setConversations(next);
      if (!selectedId && next[0]) setSelectedId(next[0].id);
      if (!silent) setError("");
    } catch (err) {
      setError(err.message);
    }
  }

  async function refreshDetail(id, { silent = false } = {}) {
    try {
      const next = await getConversation(id);
      setDetail(next);
      if (!silent) setError("");
    } catch (err) {
      setError(err.message);
    }
  }

  async function refreshArticleTask({ silent = false } = {}) {
    try {
      const next = await getArticleTaskStatus();
      setArticleTask(next);
      if (!silent) setError("");
    } catch (err) {
      setError(err.message);
    }
  }

  async function handleCreate() {
    setBusy(true);
    try {
      const conversation = await createConversation({
        title: `POC Conversation ${conversations.length + 1}`,
        provider,
        model: provider === "codex" ? "codex-cli" : "mock-agent"
      });
      await refreshList();
      setSelectedId(conversation.id);
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function handleSend(event) {
    event.preventDefault();
    if (!selectedId || !draft.trim()) return;

    const body = draft.trim();
    setDraft("");
    setBusy(true);
    try {
      await sendUserMessage(selectedId, body);
      await refreshDetail(selectedId);
      await refreshList({ silent: true });
      setError("");
    } catch (err) {
      setDraft(body);
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function handleStartRun() {
    if (!selectedId) return;
    setBusy(true);
    try {
      await startRun(selectedId, "Manual POC run");
      await refreshDetail(selectedId);
      await refreshList({ silent: true });
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function handleTriggerArticleTask() {
    setBusy(true);
    try {
      const result = await triggerArticleTask();
      await refreshArticleTask();
      await refreshList();
      setSelectedId(result.conversation.id);
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function handleScheduleArticleTask() {
    setBusy(true);
    try {
      await scheduleArticleTask(5000);
      await refreshArticleTask();
      await refreshList({ silent: true });
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="shell">
      <aside className="sidebar">
        <div className="sidebarHeader">
          <div>
            <p className="eyebrow">Adjuva POC</p>
            <h1>Conversations</h1>
          </div>
          <button className="iconButton" onClick={handleCreate} disabled={busy} title="Create conversation">
            +
          </button>
        </div>
        <label className="field">
          <span>Provider</span>
          <select value={provider} onChange={(event) => setProvider(event.target.value)}>
            <option value="mock">mock</option>
            <option value="codex">codex</option>
          </select>
        </label>

        <div className="demoPanel">
          <p className="eyebrow">Scheduled Demo</p>
          <button onClick={handleScheduleArticleTask} disabled={busy}>
            Schedule Article Task
          </button>
          <button onClick={handleTriggerArticleTask} disabled={busy}>
            Trigger Now
          </button>
          {articleTask?.scheduled && (
            <p className="meta">Scheduled for {new Date(articleTask.scheduled_for).toLocaleTimeString()}</p>
          )}
          {articleTask?.last_conversation_id && (
            <p className="meta">Last: {articleTask.last_conversation_id}</p>
          )}
        </div>

        <div className="conversationList">
          {conversations.map((conversation) => (
            <button
              className={`conversationItem ${conversation.id === selectedId ? "selected" : ""}`}
              key={conversation.id}
              onClick={() => setSelectedId(conversation.id)}
            >
              <span className="conversationTitle">{conversation.title}</span>
              <span className={`status ${conversation.status}`}>{conversation.status}</span>
            </button>
          ))}
          {conversations.length === 0 && <p className="empty">No conversations yet.</p>}
        </div>
      </aside>

      <section className="panel">
        <header className="topbar">
          <div>
            <h2>{title}</h2>
            {selectedConversation && (
              <p className="meta">
                {selectedConversation.provider} / {selectedConversation.model} / {selectedConversation.id}
              </p>
            )}
          </div>
          <div className="actions">
            {selectedConversation && (
              <span className={`status large ${selectedConversation.status}`}>
                {selectedConversation.status}
              </span>
            )}
            <button onClick={handleStartRun} disabled={!selectedId || busy}>
              Start Run
            </button>
          </div>
        </header>

        {error && <div className="error">{error}</div>}

        <div className="content">
          <section className="messages">
            {messages.map((message) => (
              <article className={`message ${message.sender}`} key={message.id}>
                <div className="messageHeader">
                  <span>{message.sender}</span>
                  <span>{message.type}</span>
                </div>
                <p>{message.body}</p>
                <time>{new Date(message.created_at).toLocaleTimeString()}</time>
              </article>
            ))}
            {selectedConversation && messages.length === 0 && (
              <p className="empty">Mailbox is empty. Send a message to start the mock run.</p>
            )}
          </section>

          <aside className="runPanel">
            <h3>Run</h3>
            {lastRun ? (
              <dl>
                <dt>ID</dt>
                <dd>{lastRun.id}</dd>
                <dt>Lifecycle</dt>
                <dd>{lastRun.lifecycle}</dd>
                <dt>Termination</dt>
                <dd>{lastRun.termination_reason || "none"}</dd>
                <dt>Process</dt>
                <dd>{lastRun.process_id || "none"}</dd>
              </dl>
            ) : (
              <p className="empty">No runs yet.</p>
            )}
          </aside>
        </div>

        <form className="composer" onSubmit={handleSend}>
          <input
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="Send a user message to this conversation"
            disabled={!selectedId || busy}
          />
          <button disabled={!selectedId || !draft.trim() || busy}>Send</button>
        </form>
      </section>
    </main>
  );
}

createRoot(document.getElementById("root")).render(<App />);
