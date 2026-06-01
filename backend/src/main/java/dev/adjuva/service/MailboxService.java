package dev.adjuva.service;

import dev.adjuva.util.Text;
import dev.adjuva.model.entity.Conversation;
import dev.adjuva.mapper.ConversationMapper;
import dev.adjuva.model.request.CreateMailboxMessageRequest;
import dev.adjuva.mapper.MailboxMessageMapper;
import dev.adjuva.model.entity.MailboxMessage;
import dev.adjuva.model.entity.Run;
import dev.adjuva.model.request.TerminalEventRequest;
import dev.adjuva.model.response.TerminalEventResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class MailboxService {

    private final MailboxMessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final RunLifecycleService runLifecycleService;
    private final OutboxService outboxService;
    private final Clock clock;

    public MailboxService(
            MailboxMessageMapper messageMapper,
            ConversationMapper conversationMapper,
            RunLifecycleService runLifecycleService,
            OutboxService outboxService,
            Clock clock
    ) {
        this.messageMapper = messageMapper;
        this.conversationMapper = conversationMapper;
        this.runLifecycleService = runLifecycleService;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    public List<MailboxMessage> list(String conversationId, String afterMessageId) {
        requireConversation(conversationId);
        if (afterMessageId == null || afterMessageId.isBlank()) {
            return messageMapper.selectByConversation(conversationId);
        }
        MailboxMessage after = messageMapper.selectOwned(conversationId, afterMessageId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "after message not found"));
        return messageMapper.selectAfter(conversationId, after.getCreatedAt(), after.getId());
    }

    @Transactional
    public MailboxMessage append(String conversationId, CreateMailboxMessageRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "body is required");
        }
        Conversation conversation = requireConversation(conversationId);
        String sender = Text.valueOrDefault(request.sender(), "agent");
        String type = Text.valueOrDefault(request.messageType(), "agent_message");
        if (!"user".equals(sender) && !"agent".equals(sender) && !"system".equals(sender)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported sender: " + sender);
        }
        MailboxMessage message = appendMessage(conversation, request.runId(), sender, type,
                Text.required(request.body(), "body"), null);
        if ("agent".equals(sender) && "question".equals(type)) {
            Instant now = clock.instant();
            conversationMapper.markWaitingForUser(conversationId, message.getId(), now, now);
        }
        return message;
    }

    public MailboxMessage waitForMessage(String conversationId, String afterMessageId, String sender, long timeoutSeconds) {
        Instant deadline = clock.instant().plus(Duration.ofSeconds(Math.min(Math.max(timeoutSeconds, 1), 300)));
        while (!clock.instant().isAfter(deadline)) {
            List<MailboxMessage> messages = list(conversationId, afterMessageId);
            for (MailboxMessage message : messages) {
                if (sender == null || sender.isBlank() || sender.equals(message.getSender())) {
                    markRead(message.getConversationId(), message.getId());
                    MailboxMessage read = messageMapper.selectById(message.getId());
                    if ("user".equals(read.getSender())) {
                        Conversation conversation = conversationMapper.selectById(conversationId);
                        if (conversation != null && conversation.getActiveRunId() != null) {
                            Instant now = clock.instant();
                            conversationMapper.clearQuestionAndMarkRunning(
                                    conversationId, conversation.getActiveRunId(), now, now);
                        }
                    }
                    return read;
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    @Transactional
    public MailboxMessage markAck(String conversationId, String messageId) {
        MailboxMessage message = messageMapper.selectOwned(conversationId, messageId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Message not found"));
        Instant now = clock.instant();
        messageMapper.markAck(messageId, now, now);
        return messageMapper.selectById(message.getId());
    }

    @Transactional
    public MailboxMessage markRead(String conversationId, String messageId) {
        MailboxMessage message = messageMapper.selectOwned(conversationId, messageId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Message not found"));
        Instant now = clock.instant();
        messageMapper.markRead(messageId, now, now);
        return messageMapper.selectById(message.getId());
    }

    @Transactional
    public TerminalEventResponse terminalEvent(String conversationId, TerminalEventRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "eventType is required");
        }
        Conversation conversation = requireConversation(conversationId);
        String eventType = Text.required(request.eventType(), "eventType");
        if (!"done".equals(eventType) && !"fail".equals(eventType)) {
            throw new ResponseStatusException(BAD_REQUEST, "eventType must be done or fail");
        }
        String runId = Text.valueOrDefault(request.runId(), conversation.getActiveRunId());
        if (runId == null) {
            throw new ResponseStatusException(BAD_REQUEST, "runId is required");
        }
        String body = Text.valueOrDefault(request.body(), eventType);
        MailboxMessage message = appendMessage(conversation, runId, "agent", "terminal_event", body, null);
        Run run = "done".equals(eventType)
                ? runLifecycleService.complete(runId, body)
                : runLifecycleService.fail(runId, body);
        return new TerminalEventResponse(message, run);
    }

    public MailboxMessage appendMessage(Conversation conversation, String runId, String sender,
                                        String messageType, String body, String replyToMessageId) {
        MailboxMessage message = new MailboxMessage();
        message.setConversationId(conversation.getId());
        message.setMailboxId(conversation.getMailboxId());
        message.setRunId(runId);
        message.setSender(sender);
        message.setMessageType(messageType);
        message.setBody(body);
        message.setReplyToMessageId(replyToMessageId);
        messageMapper.insert(message);
        outboxService.append("conversation", conversation.getId(), "mailbox.message.appended", Map.of(
                "conversationId", conversation.getId(),
                "messageId", message.getId(),
                "sender", sender,
                "messageType", messageType
        ));
        return message;
    }

    private Conversation requireConversation(String conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new ResponseStatusException(NOT_FOUND, "Conversation not found");
        }
        return conversation;
    }
}
