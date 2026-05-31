---
name: backend-agent-debug-workflow
description: Run Adjuva backend runtime debug scenarios locally. Use when verifying backend behavior after implementation by starting the Spring Boot backend, calling APIs with curl, checking H2 database state, inspecting logs, fixing issues, and rerunning the same scenario.
---

# Backend Agent Debug Workflow

Use this skill for Adjuva backend runtime verification after code changes and unit/integration tests.

The goal is to prove a real backend scenario works end to end:

```text
Runtime readiness -> API scenario -> H2 DB state verification -> related log check -> fix/rerun if needed
```

## Project Context

- Backend module: `backend`
- Runtime: Spring Boot 4, Java 21 target, Maven
- Default port: `8080`
- Local DB: H2 file database at `data/adjuva` under the repository root
- Default JDBC URL used by scripts: `jdbc:h2:file:<repo-root>/data/adjuva;AUTO_SERVER=TRUE;DATABASE_TO_UPPER=false`
- Default DB user/password: `adjuva` / `Adjuva@666`
- API base path: `/api/v1`
- The runtime executor is enabled by default. Disable it with `--executor-enabled false` when debugging pure API/database behavior.

## Required Scripts

Use the project scripts instead of rewriting ad hoc commands:

- `scripts/dev-backend-start.sh`
- `scripts/dev-backend-health.sh`
- `scripts/dev-backend-stop.sh`
- `scripts/dev-backend-log.sh`
- `scripts/dev-h2-query.sh`

## Runtime Workflow

Start backend in background:

```bash
scripts/dev-backend-start.sh
scripts/dev-backend-health.sh
```

In a Codex-managed foreground terminal, use:

```bash
scripts/dev-backend-start.sh --foreground
```

Stop before finishing:

```bash
scripts/dev-backend-stop.sh
```

If running with `--foreground`, stop the foreground terminal with `Ctrl-C`; `dev-backend-stop.sh` only manages background runs started by `dev-backend-start.sh`.

If the backend does not stop cleanly:

```bash
scripts/dev-backend-stop.sh --force
```

## Scenario Artifact Rules

Create one artifact directory per runtime scenario:

```text
var/dev/runs/<scenario-name>-<timestamp>/
```

Save:

- `scenario.vars` for generated local IDs and non-secret inputs.
- request and response files for each API step.
- H2 verification output.
- filtered related logs.
- a short `summary.md` or `summary.json`.

Do not store reusable secrets or real tokens in artifacts. Local generated IDs and local H2 evidence are fine.

## API Verification Rules

Use `curl` against `http://localhost:8080/api/v1`.

Minimum scenario shape:

1. Create or locate a project.
2. Exercise the endpoint or workflow under test.
3. Assert HTTP status and response fields.
4. Verify persisted DB state with H2.
5. Inspect logs for unexplained `ERROR`, stack traces, failed scheduler runs, or executor errors.

When testing async run/executor behavior, wait for the implied state transition before judging the result.

## H2 Verification Rules

Use `scripts/dev-h2-query.sh` for DB checks.

Before querying a table deeply, inspect its shape:

```bash
scripts/dev-h2-query.sh 'show columns from projects;'
```

Common checks:

```bash
scripts/dev-h2-query.sh 'show tables;'
scripts/dev-h2-query.sh 'select id, name, created_at, updated_at from projects order by created_at desc limit 5;'
scripts/dev-h2-query.sh 'select id, status, active_run_id from conversations order by created_at desc limit 5;'
scripts/dev-h2-query.sh 'select id, lifecycle, termination_reason from runs order by created_at desc limit 5;'
scripts/dev-h2-query.sh 'select id, sender, message_type, body from mailbox_messages order by created_at desc limit 10;'
```

Validate business facts in DB, not just HTTP response shape.

## Log Verification Rules

Use:

```bash
scripts/dev-backend-log.sh --tail 200
scripts/dev-backend-log.sh --tail 500 --grep 'ERROR|Exception|WARN'
```

An `ERROR` is not automatically a failure if it is expected by the scenario, but explain it. Unexpected stack traces must be investigated before calling the scenario passed.

## Completion Gate

A runtime debug scenario passes only when:

- API status and response fields match expected behavior.
- H2 state confirms the business result.
- Required side effects are present, especially outbox, mailbox, run, scheduler, or provider session changes.
- Related logs have no unexplained `ERROR` or exception.
- Any failure was fixed and the same scenario was rerun successfully.

Final response should summarize the scenario, DB evidence, log evidence, files changed, and residual risk.
