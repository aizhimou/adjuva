CREATE TABLE projects (
  id VARCHAR(64) NOT NULL,
  name VARCHAR(200) NOT NULL,
  slug VARCHAR(120) NOT NULL,
  description CLOB,
  status VARCHAR(32) NOT NULL,
  workspace_path VARCHAR(1000) NOT NULL,
  default_provider VARCHAR(64),
  default_model VARCHAR(128),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_projects_slug ON projects (slug);
CREATE INDEX ix_projects_status ON projects (status);

CREATE TABLE conversations (
  id VARCHAR(64) NOT NULL,
  project_id VARCHAR(64) NOT NULL,
  title VARCHAR(300) NOT NULL,
  source_type VARCHAR(64) NOT NULL,
  source_ref VARCHAR(200),
  status VARCHAR(32) NOT NULL,
  mailbox_id VARCHAR(64) NOT NULL,
  workspace_path VARCHAR(1000) NOT NULL,
  default_provider VARCHAR(64) NOT NULL,
  default_model VARCHAR(128) NOT NULL,
  active_run_id VARCHAR(64),
  pending_question_message_id VARCHAR(64),
  last_activity_at TIMESTAMP NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_conversations_mailbox_id ON conversations (mailbox_id);
CREATE INDEX ix_conversations_project_status ON conversations (project_id, status);
CREATE INDEX ix_conversations_project_activity ON conversations (project_id, last_activity_at);
CREATE INDEX ix_conversations_active_run_id ON conversations (active_run_id);
CREATE INDEX ix_conversations_pending_question ON conversations (pending_question_message_id);

CREATE TABLE provider_sessions (
  id VARCHAR(64) NOT NULL,
  conversation_id VARCHAR(64) NOT NULL,
  provider VARCHAR(64) NOT NULL,
  model VARCHAR(128) NOT NULL,
  external_session_id VARCHAR(300),
  status VARCHAR(32) NOT NULL,
  metadata_json CLOB,
  last_seen_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE UNIQUE INDEX ux_provider_sessions_conversation_provider_model
  ON provider_sessions (conversation_id, provider, model);
CREATE INDEX ix_provider_sessions_external_session
  ON provider_sessions (provider, external_session_id);
CREATE INDEX ix_provider_sessions_conversation
  ON provider_sessions (conversation_id);

CREATE TABLE runs (
  id VARCHAR(64) NOT NULL,
  conversation_id VARCHAR(64) NOT NULL,
  provider_session_id VARCHAR(64),
  trigger_message_id VARCHAR(64),
  trigger_type VARCHAR(64) NOT NULL,
  provider VARCHAR(64) NOT NULL,
  model VARCHAR(128) NOT NULL,
  lifecycle VARCHAR(32) NOT NULL,
  termination_reason VARCHAR(64),
  process_id VARCHAR(64),
  external_session_id_before VARCHAR(300),
  external_session_id_after VARCHAR(300),
  started_at TIMESTAMP NOT NULL,
  ended_at TIMESTAMP,
  exit_code INTEGER,
  signal VARCHAR(64),
  final_event_type VARCHAR(64),
  timeout_kind VARCHAR(64),
  error_message CLOB,
  prompt CLOB NOT NULL,
  command_line CLOB,
  metadata_json CLOB,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX ix_runs_conversation_started ON runs (conversation_id, started_at);
CREATE INDEX ix_runs_lifecycle ON runs (lifecycle);
CREATE INDEX ix_runs_provider_session ON runs (provider_session_id);
CREATE INDEX ix_runs_trigger_message ON runs (trigger_message_id);

CREATE TABLE mailbox_messages (
  id VARCHAR(64) NOT NULL,
  conversation_id VARCHAR(64) NOT NULL,
  mailbox_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64),
  sender VARCHAR(32) NOT NULL,
  message_type VARCHAR(64) NOT NULL,
  body CLOB NOT NULL,
  reply_to_message_id VARCHAR(64),
  metadata_json CLOB,
  read_at TIMESTAMP,
  ack_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX ix_mailbox_messages_conversation_created
  ON mailbox_messages (conversation_id, created_at);
CREATE INDEX ix_mailbox_messages_mailbox_created
  ON mailbox_messages (mailbox_id, created_at);
CREATE INDEX ix_mailbox_messages_run_created
  ON mailbox_messages (run_id, created_at);
CREATE INDEX ix_mailbox_messages_reply_to
  ON mailbox_messages (reply_to_message_id);

CREATE TABLE automation_schedules (
  id VARCHAR(64) NOT NULL,
  project_id VARCHAR(64) NOT NULL,
  title VARCHAR(300) NOT NULL,
  status VARCHAR(32) NOT NULL,
  schedule_kind VARCHAR(32) NOT NULL,
  schedule_expression VARCHAR(300) NOT NULL,
  timezone VARCHAR(100) NOT NULL,
  provider VARCHAR(64) NOT NULL,
  model VARCHAR(128) NOT NULL,
  conversation_title_template VARCHAR(300) NOT NULL,
  prompt_template CLOB NOT NULL,
  last_triggered_at TIMESTAMP,
  metadata_json CLOB,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX ix_automation_schedules_project_status
  ON automation_schedules (project_id, status);
CREATE INDEX ix_automation_schedules_status
  ON automation_schedules (status);

CREATE TABLE artifacts (
  id VARCHAR(64) NOT NULL,
  project_id VARCHAR(64) NOT NULL,
  conversation_id VARCHAR(64),
  run_id VARCHAR(64),
  artifact_type VARCHAR(64) NOT NULL,
  title VARCHAR(300) NOT NULL,
  uri VARCHAR(1000) NOT NULL,
  mime_type VARCHAR(200),
  size_bytes BIGINT,
  checksum VARCHAR(200),
  metadata_json CLOB,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX ix_artifacts_project_created
  ON artifacts (project_id, created_at);
CREATE INDEX ix_artifacts_conversation_created
  ON artifacts (conversation_id, created_at);
CREATE INDEX ix_artifacts_run
  ON artifacts (run_id);

CREATE TABLE outbox_events (
  id VARCHAR(64) NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(100) NOT NULL,
  payload_json CLOB NOT NULL,
  status VARCHAR(32) NOT NULL,
  attempts INTEGER NOT NULL,
  available_at TIMESTAMP NOT NULL,
  published_at TIMESTAMP,
  error_message CLOB,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX ix_outbox_events_status_available
  ON outbox_events (status, available_at);
CREATE INDEX ix_outbox_events_aggregate
  ON outbox_events (aggregate_type, aggregate_id);
