package com.adjuva.backend.handler;

import com.adjuva.backend.config.ExecutorProperties;
import com.adjuva.backend.model.dto.CodexCommand;
import com.adjuva.backend.model.entity.Conversation;
import com.adjuva.backend.model.request.CreateMailboxMessageRequest;
import com.adjuva.backend.service.MailboxService;
import com.adjuva.backend.model.entity.Run;
import com.adjuva.backend.service.RunLauncher;
import com.adjuva.backend.service.RunLifecycleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CodexExecutor implements RunExecutor {

    private final MailboxService mailboxService;
    private final RunLifecycleService runLifecycleService;
    private final RunLauncher runLauncher;
    private final ExecutorProperties properties;
    private final ObjectMapper objectMapper;

    public CodexExecutor(
            MailboxService mailboxService,
            RunLifecycleService runLifecycleService,
            RunLauncher runLauncher,
            ExecutorProperties properties,
            ObjectMapper objectMapper
    ) {
        this.mailboxService = mailboxService;
        this.runLifecycleService = runLifecycleService;
        this.runLauncher = runLauncher;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return "codex";
    }

    @Override
    public void execute(Conversation conversation, Run run) {
        try {
            CodexCommand command = buildCommand(conversation, run);
            ProcessBuilder builder = new ProcessBuilder(command.bin());
            builder.command().addAll(command.args());
            builder.directory(command.cwd().toFile());
            builder.environment().putAll(command.env());
            Process process = builder.start();
            runLauncher.registerProcess(run.getId(), process);
            runLifecycleService.markProcessStarted(run.getId(), String.valueOf(process.pid()), command.displayCommand());

            Thread stderr = new Thread(() -> drainStderr(process), "codex-stderr-" + run.getId());
            stderr.setDaemon(true);
            stderr.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    handleJsonLine(conversation, run, line);
                }
            }
            int exitCode = process.waitFor();
            runLifecycleService.processExited(run.getId(), exitCode, null);
        } catch (Exception e) {
            runLifecycleService.fail(run.getId(), e.getMessage());
        } finally {
            runLauncher.unregisterProcess(run.getId());
        }
    }

    public CodexCommand buildCommand(Conversation conversation, Run run) throws IOException {
        Path workspace = Path.of(conversation.getWorkspacePath()).toAbsolutePath().normalize();
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("AGENTS.md"), agentsFile(conversation, run), StandardCharsets.UTF_8);

        String prompt = buildPrompt(run.getPrompt());
        List<String> args = new ArrayList<>();
        if (run.getExternalSessionIdBefore() != null && !run.getExternalSessionIdBefore().isBlank()) {
            args.addAll(List.of(
                    "exec", "resume",
                    "--json",
                    "--skip-git-repo-check",
                    "--dangerously-bypass-approvals-and-sandbox",
                    run.getExternalSessionIdBefore(),
                    prompt
            ));
        } else {
            args.addAll(List.of(
                    "exec",
                    "--json",
                    "--skip-git-repo-check",
                    "--dangerously-bypass-approvals-and-sandbox",
                    "-C", workspace.toString(),
                    prompt
            ));
        }

        Map<String, String> env = new HashMap<>();
        env.put("ADJUVA_API_BASE_URL", properties.getApiBaseUrl());
        env.put("ADJUVA_CONVERSATION_ID", conversation.getId());
        env.put("ADJUVA_RUN_ID", run.getId());
        env.put("ADJUVA_WORKSPACE", workspace.toString());
        return new CodexCommand(properties.getCodex().getBin(), args, workspace, env,
                properties.getCodex().getBin() + " " + String.join(" ", args.subList(0, Math.min(args.size(), 7))) + " ...");
    }

    public void handleJsonLine(Conversation conversation, Run run, String line) {
        if (line == null || !line.trim().startsWith("{")) {
            return;
        }
        try {
            JsonNode event = objectMapper.readTree(line);
            String type = event.path("type").asText();
            if ("thread.started".equals(type) && event.hasNonNull("thread_id")) {
                runLifecycleService.updateExternalSession(run.getId(), event.path("thread_id").asText());
                return;
            }
            JsonNode item = event.path("item");
            if ("item.completed".equals(type)
                    && "agent_message".equals(item.path("type").asText())
                    && item.hasNonNull("text")) {
                mailboxService.append(conversation.getId(), new CreateMailboxMessageRequest(
                        run.getId(), "agent", "agent_message", item.path("text").asText()));
            }
        } catch (Exception ignored) {
            // Codex may emit non-JSON diagnostics around JSONL output.
        }
    }

    private void drainStderr(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) {
                // Keep the process pipe drained; stderr is not user-visible in MVP.
            }
        } catch (IOException ignored) {
        }
    }

    private String buildPrompt(String prompt) {
        return """
                User task:

                %s

                Adjuva mailbox protocol:

                1. Treat the user task above as the primary objective.
                2. Send user-visible progress, questions, and final results through the mailbox curl examples documented in AGENTS.md.
                3. If you need user input, send a question message, wait for a user message, then continue the original task using the reply.
                4. Do not stop after merely acknowledging or summarising the user's reply.
                5. Send a terminal event with eventType=done only when the original user task is complete.
                6. Send a terminal event with eventType=fail if you cannot complete the original user task, and explain why.

                Do not just answer in final text. The mailbox is the user-visible conversation channel.
                """.formatted(prompt == null || prompt.isBlank() ? "Help the user with this Adjuva conversation." : prompt);
    }

    private String agentsFile(Conversation conversation, Run run) {
        String base = properties.getApiBaseUrl();
        String conversationUrl = base + "/api/v1/conversations/" + conversation.getId();
        return """
                # Adjuva Conversation Runtime

                You are running inside an Adjuva Conversation.

                - Conversation ID: %s
                - Run ID: %s
                - Workspace: %s

                Use these curl examples to communicate with the user through the Adjuva mailbox.

                Send a normal message:

                ```bash
                curl -sS -X POST %s/mailbox/messages \\
                  -H 'content-type: application/json' \\
                  -d '{"runId":"%s","sender":"agent","messageType":"agent_message","body":"Message text"}'
                ```

                Ask a question:

                ```bash
                curl -sS -X POST %s/mailbox/messages \\
                  -H 'content-type: application/json' \\
                  -d '{"runId":"%s","sender":"agent","messageType":"question","body":"Question text"}'
                ```

                Wait for a user reply after a message:

                ```bash
                curl -sS '%s/mailbox/wait?sender=user&after=MESSAGE_ID&timeout=300'
                ```

                Acknowledge a message:

                ```bash
                curl -sS -X POST %s/mailbox/messages/MESSAGE_ID/ack
                ```

                Mark done:

                ```bash
                curl -sS -X POST %s/mailbox/terminal-events \\
                  -H 'content-type: application/json' \\
                  -d '{"runId":"%s","eventType":"done","body":"Done summary"}'
                ```

                Mark failed:

                ```bash
                curl -sS -X POST %s/mailbox/terminal-events \\
                  -H 'content-type: application/json' \\
                  -d '{"runId":"%s","eventType":"fail","body":"Failure reason"}'
                ```
                """.formatted(
                conversation.getId(),
                run.getId(),
                conversation.getWorkspacePath(),
                conversationUrl, run.getId(),
                conversationUrl, run.getId(),
                conversationUrl,
                conversationUrl,
                conversationUrl, run.getId(),
                conversationUrl, run.getId()
        );
    }
}
