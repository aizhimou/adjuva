package dev.adjuva.service;

import dev.adjuva.util.Text;
import dev.adjuva.model.entity.Conversation;
import dev.adjuva.mapper.ConversationMapper;
import dev.adjuva.model.entity.Run;
import dev.adjuva.mapper.RunMapper;
import dev.adjuva.model.entity.ProviderSession;
import dev.adjuva.mapper.ProviderSessionMapper;
import dev.adjuva.model.request.StartRunRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class RunLifecycleService {

    private final RunMapper runMapper;
    private final ConversationMapper conversationMapper;
    private final ProviderSessionMapper providerSessionMapper;
    private final OutboxService outboxService;
    private final RunLauncher runLauncher;
    private final Clock clock;

    public RunLifecycleService(
            RunMapper runMapper,
            ConversationMapper conversationMapper,
            ProviderSessionMapper providerSessionMapper,
            OutboxService outboxService,
            RunLauncher runLauncher,
            Clock clock
    ) {
        this.runMapper = runMapper;
        this.conversationMapper = conversationMapper;
        this.providerSessionMapper = providerSessionMapper;
        this.outboxService = outboxService;
        this.runLauncher = runLauncher;
        this.clock = clock;
    }

    @Transactional
    public Run createRun(Conversation conversation, String triggerType, String triggerMessageId,
                         String provider, String model, String prompt) {
        if (!"mock".equals(provider) && !"codex".equals(provider)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported provider: " + provider);
        }
        if (conversation.getActiveRunId() != null) {
            Run active = runMapper.selectById(conversation.getActiveRunId());
            if (active != null && "running".equals(active.getLifecycle())) {
                return active;
            }
        }

        ProviderSession providerSession = ensureProviderSession(conversation.getId(), provider, model);
        Instant now = clock.instant();
        Run run = new Run();
        run.setConversationId(conversation.getId());
        run.setProviderSessionId(providerSession.getId());
        run.setTriggerMessageId(triggerMessageId);
        run.setTriggerType(Text.valueOrDefault(triggerType, "user_message"));
        run.setProvider(provider);
        run.setModel(model);
        run.setLifecycle("running");
        run.setExternalSessionIdBefore(providerSession.getExternalSessionId());
        run.setExternalSessionIdAfter(providerSession.getExternalSessionId());
        run.setStartedAt(now);
        run.setPrompt(prompt == null ? "" : prompt);
        runMapper.insert(run);

        conversationMapper.markRunning(conversation.getId(), run.getId(), now, now);
        outboxService.append("run", run.getId(), "run.started", Map.of(
                "runId", run.getId(),
                "conversationId", conversation.getId(),
                "provider", provider
        ));
        runLauncher.launchAfterCommit(run.getId());
        return run;
    }

    @Transactional
    public Run startManualRun(String conversationId, StartRunRequest request) {
        request = request == null ? new StartRunRequest(null, null, null, null) : request;
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new ResponseStatusException(NOT_FOUND, "Conversation not found");
        }
        String provider = Text.valueOrDefault(request.provider(), conversation.getDefaultProvider());
        String model = Text.valueOrDefault(request.model(), conversation.getDefaultModel());
        return createRun(conversation, Text.valueOrDefault(request.triggerType(), "user_message"), null,
                provider, model, request.prompt());
    }

    @Transactional
    public Run cancel(String runId) {
        runLauncher.terminateProcess(runId);
        Run run = runMapper.selectById(runId);
        if (run == null) {
            throw new ResponseStatusException(NOT_FOUND, "Run not found");
        }
        return end(runId, "cancelled", null, "SIGTERM", "cancelled", null, null);
    }

    @Transactional
    public Run complete(String runId, String body) {
        return end(runId, "completed", 0, null, "done", null, null);
    }

    @Transactional
    public Run fail(String runId, String body) {
        return end(runId, "failed", 1, null, "fail", null, Text.valueOrDefault(body, "Agent failed"));
    }

    @Transactional
    public Run processExited(String runId, Integer exitCode, String signal) {
        Run run = runMapper.selectById(runId);
        if (run == null || "ended".equals(run.getLifecycle())) {
            return run;
        }
        Conversation conversation = conversationMapper.selectById(run.getConversationId());
        if (conversation != null && "waiting_for_user".equals(conversation.getStatus())) {
            return end(runId, "waiting_for_user", exitCode, signal, null, null, null);
        }
        String reason = exitCode != null && exitCode == 0 ? "failed" : "failed";
        String error = exitCode != null && exitCode == 0
                ? "Process exited without terminal event"
                : "Process failed";
        return end(runId, reason, exitCode, signal, null, null, error);
    }

    @Transactional
    public void markProcessStarted(String runId, String processId, String commandLine) {
        runMapper.markProcessStarted(runId, processId, commandLine, clock.instant());
    }

    @Transactional
    public void updateExternalSession(String runId, String externalSessionId) {
        Run run = runMapper.selectById(runId);
        if (run == null || "ended".equals(run.getLifecycle())) {
            return;
        }
        Instant now = clock.instant();
        runMapper.updateExternalSession(runId, externalSessionId, now);
        if (run.getProviderSessionId() != null) {
            ProviderSession providerSession = providerSessionMapper.selectById(run.getProviderSessionId());
            if (providerSession != null) {
                providerSession.setExternalSessionId(externalSessionId);
                providerSession.setLastSeenAt(now);
                providerSessionMapper.updateById(providerSession);
            }
        }
    }

    private Run end(String runId, String terminationReason, Integer exitCode, String signal,
                    String finalEventType, String timeoutKind, String errorMessage) {
        Run run = runMapper.selectById(runId);
        if (run == null) {
            throw new ResponseStatusException(NOT_FOUND, "Run not found");
        }
        if ("ended".equals(run.getLifecycle())) {
            return run;
        }
        Instant now = clock.instant();
        int updated = runMapper.endRun(runId, terminationReason, exitCode, signal, finalEventType,
                timeoutKind, errorMessage, now, now);
        if (updated == 0) {
            throw new ResponseStatusException(CONFLICT, "Run already ended");
        }

        if ("waiting_for_user".equals(terminationReason)) {
            conversationMapper.clearActiveRunKeepWaiting(run.getConversationId(), now, now);
        } else {
            conversationMapper.markTerminal(run.getConversationId(), conversationStatus(terminationReason), now, now);
        }
        outboxService.append("run", runId, "run.ended", Map.of(
                "runId", runId,
                "conversationId", run.getConversationId(),
                "terminationReason", terminationReason
        ));
        return runMapper.selectById(runId);
    }

    private String conversationStatus(String terminationReason) {
        return switch (terminationReason) {
            case "completed" -> "completed";
            case "cancelled" -> "cancelled";
            default -> "failed";
        };
    }

    private ProviderSession ensureProviderSession(String conversationId, String provider, String model) {
        return providerSessionMapper.selectByConversationProviderModel(conversationId, provider, model)
                .orElseGet(() -> {
                    ProviderSession session = new ProviderSession();
                    session.setConversationId(conversationId);
                    session.setProvider(provider);
                    session.setModel(model);
                    session.setStatus("active");
                    providerSessionMapper.insert(session);
                    return session;
                });
    }
}
