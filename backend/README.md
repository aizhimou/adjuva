# Adjuva Backend

Spring Boot control-plane backend for Adjuva.

## Stack

- Java 21 bytecode target
- Spring Boot 4.0.6
- Spring MVC via `spring-boot-starter-web`
- H2 2.4.240, managed by Spring Boot
- Flyway 11.14.1 via `spring-boot-starter-flyway`
- MyBatis 3.5.19 via MyBatis-Plus
- MyBatis-Plus 3.5.16 Boot 4 starter
- db-scheduler 16.12.0 Boot 4 starter

Spring Boot manages H2 and Flyway versions. Maven Central currently lists newer Flyway releases, but this project keeps Boot-managed versions to preserve Spring Boot 4 compatibility.

## Commands

```bash
mvn test
mvn spring-boot:run
```

The default datasource uses an H2 file database at `./data/adjuva`.

## MVP API

The supported API surface is versioned under `/api/v1`.

```bash
curl -s http://localhost:8080/api/v1/projects
```

Core resources:

- `GET/POST /api/v1/projects`
- `GET/POST /api/v1/projects/{projectId}/conversations`
- `GET /api/v1/conversations/{conversationId}`
- `POST /api/v1/conversations/{conversationId}/messages`
- `GET /api/v1/conversations/{conversationId}/messages?after=...`
- `POST /api/v1/conversations/{conversationId}/runs`
- `POST /api/v1/runs/{runId}/cancel`
- `GET/POST /api/v1/conversations/{conversationId}/mailbox/messages`
- `GET /api/v1/conversations/{conversationId}/mailbox/wait`
- `POST /api/v1/conversations/{conversationId}/mailbox/messages/{messageId}/ack`
- `POST /api/v1/conversations/{conversationId}/mailbox/terminal-events`
- `GET/POST /api/v1/projects/{projectId}/automation-schedules`
- `POST /api/v1/automation-schedules/{scheduleId}/trigger`

## Executors

Runs are launched asynchronously after the API transaction commits.

Supported providers:

- `mock`: deterministic in-JVM executor used for local smoke tests and integration tests.
- `codex`: launches the Codex CLI with JSONL output, writes a lightweight `AGENTS.md` mailbox protocol file into the conversation workspace, and appends Codex agent messages into mailbox messages.

Unsupported providers return `400 Bad Request`.

Useful local config:

```yaml
adjuva:
  api-base-url: http://localhost:8080
  executor:
    enabled: true
  codex:
    bin: codex
  mock:
    wait-timeout-seconds: 300
```

## Smoke Test

```bash
mvn package

java -jar target/adjuva-backend-0.1.0-SNAPSHOT.jar \
  --spring.datasource.url='jdbc:h2:file:./target/smoke-mvp/adjuva-control-plane;AUTO_SERVER=FALSE;DATABASE_TO_UPPER=false'
```

Create a mock project, conversation, and user message:

```bash
curl -s -X POST http://localhost:8080/api/v1/projects \
  -H 'content-type: application/json' \
  -d '{"name":"Smoke","provider":"mock","model":"mock-agent","workspaceRoot":"./target/adjuva-workspaces"}'
```

Then create a conversation under the returned project ID and post a user message with `{"autoStart":true}` to exercise the mock executor flow.
