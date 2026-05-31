package com.adjuva.backend.controller;

import com.adjuva.backend.model.entity.Conversation;
import com.adjuva.backend.model.response.ConversationDetail;
import com.adjuva.backend.model.response.ConversationMessageResult;
import com.adjuva.backend.model.request.CreateConversationRequest;
import com.adjuva.backend.model.request.CreateMailboxMessageRequest;
import com.adjuva.backend.model.entity.MailboxMessage;
import com.adjuva.backend.model.request.SendMessageRequest;
import com.adjuva.backend.model.request.TerminalEventRequest;
import com.adjuva.backend.model.response.TerminalEventResponse;
import com.adjuva.backend.model.entity.Run;
import com.adjuva.backend.model.request.StartRunRequest;
import com.adjuva.backend.service.ConversationService;
import com.adjuva.backend.service.MailboxService;
import com.adjuva.backend.service.RunLifecycleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ConversationController {

    private final ConversationService conversationService;
    private final MailboxService mailboxService;
    private final RunLifecycleService runLifecycleService;

    public ConversationController(
            ConversationService conversationService,
            MailboxService mailboxService,
            RunLifecycleService runLifecycleService
    ) {
        this.conversationService = conversationService;
        this.mailboxService = mailboxService;
        this.runLifecycleService = runLifecycleService;
    }

    @GetMapping("/projects/{projectId}/conversations")
    public List<Conversation> listConversations(@PathVariable String projectId) {
        return conversationService.listByProject(projectId);
    }

    @PostMapping("/projects/{projectId}/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public Conversation createConversation(@PathVariable String projectId, @RequestBody CreateConversationRequest request) {
        return conversationService.create(projectId, request);
    }

    @GetMapping("/conversations/{conversationId}")
    public ConversationDetail getConversation(@PathVariable String conversationId) {
        return conversationService.detail(conversationId);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationMessageResult sendMessage(
            @PathVariable String conversationId,
            @RequestBody SendMessageRequest request
    ) {
        return conversationService.sendUserMessage(conversationId, request);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<MailboxMessage> listMessages(
            @PathVariable String conversationId,
            @RequestParam(required = false) String after
    ) {
        return mailboxService.list(conversationId, after);
    }

    @PostMapping("/conversations/{conversationId}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    public Run startRun(@PathVariable String conversationId, @RequestBody StartRunRequest request) {
        return runLifecycleService.startManualRun(conversationId, request);
    }

    @PostMapping("/runs/{runId}/cancel")
    public Run cancelRun(@PathVariable String runId) {
        return runLifecycleService.cancel(runId);
    }

    @GetMapping("/conversations/{conversationId}/mailbox/messages")
    public List<MailboxMessage> listMailboxMessages(
            @PathVariable String conversationId,
            @RequestParam(required = false) String after
    ) {
        return mailboxService.list(conversationId, after);
    }

    @PostMapping("/conversations/{conversationId}/mailbox/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MailboxMessage appendMailboxMessage(
            @PathVariable String conversationId,
            @RequestBody CreateMailboxMessageRequest request
    ) {
        return mailboxService.append(conversationId, request);
    }

    @GetMapping("/conversations/{conversationId}/mailbox/wait")
    public MailboxMessage waitMailbox(
            @PathVariable String conversationId,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) String sender,
            @RequestParam(defaultValue = "30") long timeout
    ) {
        return mailboxService.waitForMessage(conversationId, after, sender, timeout);
    }

    @PostMapping("/conversations/{conversationId}/mailbox/messages/{messageId}/ack")
    public MailboxMessage ackMailboxMessage(@PathVariable String conversationId, @PathVariable String messageId) {
        return mailboxService.markAck(conversationId, messageId);
    }

    @PostMapping("/conversations/{conversationId}/mailbox/terminal-events")
    public TerminalEventResponse terminalEvent(
            @PathVariable String conversationId,
            @RequestBody TerminalEventRequest request
    ) {
        return mailboxService.terminalEvent(conversationId, request);
    }
}
