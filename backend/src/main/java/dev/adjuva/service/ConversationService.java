package dev.adjuva.service;

import dev.adjuva.util.Text;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import dev.adjuva.mapper.ConversationMapper;
import dev.adjuva.model.entity.Conversation;
import dev.adjuva.model.response.ConversationDetail;
import dev.adjuva.model.response.ConversationMessageResult;
import dev.adjuva.model.response.ConversationSummary;
import dev.adjuva.model.request.CreateConversationRequest;
import dev.adjuva.model.entity.MailboxMessage;
import dev.adjuva.mapper.MailboxMessageMapper;
import dev.adjuva.model.entity.Project;
import dev.adjuva.model.entity.Run;
import dev.adjuva.mapper.RunMapper;
import dev.adjuva.model.request.SendMessageRequest;
import dev.adjuva.model.entity.ProviderSession;
import dev.adjuva.mapper.ProviderSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final MailboxMessageMapper messageMapper;
    private final RunMapper runMapper;
    private final ProviderSessionMapper providerSessionMapper;
    private final ProjectService projectService;
    private final MailboxService mailboxService;
    private final RunLifecycleService runLifecycleService;
    private final OutboxService outboxService;
    private final Clock clock;

    public ConversationService(
            ConversationMapper conversationMapper,
            MailboxMessageMapper messageMapper,
            RunMapper runMapper,
            ProviderSessionMapper providerSessionMapper,
            ProjectService projectService,
            MailboxService mailboxService,
            RunLifecycleService runLifecycleService,
            OutboxService outboxService,
            Clock clock
    ) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.runMapper = runMapper;
        this.providerSessionMapper = providerSessionMapper;
        this.projectService = projectService;
        this.mailboxService = mailboxService;
        this.runLifecycleService = runLifecycleService;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    public List<Conversation> listByProject(String projectId) {
        projectService.getActive(projectId);
        return conversationMapper.selectByProject(projectId);
    }

    public List<ConversationSummary> listSummaries() {
        return conversationMapper.selectSummaries();
    }

    public ConversationDetail detail(String conversationId) {
        Conversation conversation = conversationMapper.selectForDetail(conversationId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Conversation not found"));
        return new ConversationDetail(
                conversation,
                messageMapper.selectByConversation(conversationId),
                runMapper.selectByConversation(conversationId)
        );
    }

    @Transactional
    public Conversation create(String projectId, CreateConversationRequest request) {
        request = request == null ? new CreateConversationRequest(null, null, null, null, null, null) : request;
        Project project = projectService.getActive(projectId);
        String provider = Text.valueOrDefault(request.provider(), Text.valueOrDefault(project.getDefaultProvider(), "mock"));
        String model = Text.valueOrDefault(request.model(), defaultModel(provider, project.getDefaultModel()));
        validateProvider(provider);

        Instant now = clock.instant();
        Conversation conversation = new Conversation();
        conversation.setProjectId(projectId);
        conversation.setTitle(Text.valueOrDefault(request.title(), "New conversation"));
        conversation.setSourceType(Text.valueOrDefault(request.sourceType(), "manual"));
        conversation.setSourceRef(Text.trimToNull(request.sourceRef()));
        conversation.setStatus("idle");
        conversation.setMailboxId(IdWorker.get32UUID());
        conversation.setWorkspacePath(Text.valueOrDefault(request.workspacePath(), project.getWorkspacePath()));
        conversation.setDefaultProvider(provider);
        conversation.setDefaultModel(model);
        conversation.setLastActivityAt(now);
        conversationMapper.insert(conversation);

        ProviderSession providerSession = new ProviderSession();
        providerSession.setConversationId(conversation.getId());
        providerSession.setProvider(provider);
        providerSession.setModel(model);
        providerSession.setStatus("active");
        providerSessionMapper.insert(providerSession);

        outboxService.append("conversation", conversation.getId(), "conversation.created", Map.of(
                "conversationId", conversation.getId(),
                "projectId", projectId
        ));
        return conversation;
    }

    @Transactional
    public ConversationMessageResult sendUserMessage(String conversationId, SendMessageRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "body is required");
        }
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new ResponseStatusException(NOT_FOUND, "Conversation not found");
        }
        String body = Text.required(request.body(), "body");
        String messageType = "waiting_for_user".equals(conversation.getStatus()) ? "answer" : "user_message";
        String replyTo = "answer".equals(messageType) ? conversation.getPendingQuestionMessageId() : null;
        String runId = conversation.getActiveRunId();
        MailboxMessage message = mailboxService.appendMessage(conversation, runId, "user", messageType, body, replyTo);
        Instant now = clock.instant();

        Run run = null;
        if (conversation.getActiveRunId() != null) {
            if ("answer".equals(messageType)) {
                conversationMapper.clearQuestionAndMarkRunning(conversationId, conversation.getActiveRunId(), now, now);
            } else {
                conversation.setLastActivityAt(now);
                conversationMapper.updateById(conversation);
            }
        } else if (request.autoStart() == null || request.autoStart()) {
            conversation.setActiveRunId(null);
            run = runLifecycleService.createRun(conversation, resumeTriggerType(conversation), message.getId(),
                    conversation.getDefaultProvider(), conversation.getDefaultModel(), body);
            if ("answer".equals(messageType)) {
                conversationMapper.clearQuestionAndMarkRunning(conversationId, run.getId(), now, now);
            }
        } else {
            conversation.setLastActivityAt(now);
            conversationMapper.updateById(conversation);
        }

        return new ConversationMessageResult(message, run);
    }

    private String resumeTriggerType(Conversation conversation) {
        return switch (conversation.getStatus()) {
            case "completed", "failed", "cancelled", "waiting_for_user" -> "resume";
            default -> "user_message";
        };
    }

    private String defaultModel(String provider, String projectDefault) {
        if (projectDefault != null && !projectDefault.isBlank()) {
            return projectDefault.trim();
        }
        return "codex".equals(provider) ? "codex-cli" : "mock-agent";
    }

    private void validateProvider(String provider) {
        if (!"mock".equals(provider) && !"codex".equals(provider)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported provider: " + provider);
        }
    }
}
