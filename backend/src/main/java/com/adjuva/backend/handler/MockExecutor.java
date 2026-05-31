package com.adjuva.backend.handler;

import com.adjuva.backend.config.ExecutorProperties;
import com.adjuva.backend.model.entity.Conversation;
import com.adjuva.backend.model.request.CreateMailboxMessageRequest;
import com.adjuva.backend.model.entity.MailboxMessage;
import com.adjuva.backend.service.MailboxService;
import com.adjuva.backend.model.request.TerminalEventRequest;
import com.adjuva.backend.model.entity.Run;
import com.adjuva.backend.service.RunLifecycleService;
import org.springframework.stereotype.Component;

@Component
public class MockExecutor implements RunExecutor {

    private final MailboxService mailboxService;
    private final RunLifecycleService runLifecycleService;
    private final ExecutorProperties properties;

    public MockExecutor(MailboxService mailboxService, RunLifecycleService runLifecycleService, ExecutorProperties properties) {
        this.mailboxService = mailboxService;
        this.runLifecycleService = runLifecycleService;
        this.properties = properties;
    }

    @Override
    public String provider() {
        return "mock";
    }

    @Override
    public void execute(Conversation conversation, Run run) {
        runLifecycleService.markProcessStarted(run.getId(), "mock-" + run.getId(), "mock");
        MailboxMessage start = mailboxService.append(conversation.getId(), new CreateMailboxMessageRequest(
                run.getId(),
                "agent",
                "agent_message",
                "Mock agent started. I will ask one mailbox question before completing."
        ));
        MailboxMessage question = mailboxService.append(conversation.getId(), new CreateMailboxMessageRequest(
                run.getId(),
                "agent",
                "question",
                "Mock question: should I include archived data while handling \"" + run.getPrompt() + "\"?"
        ));
        MailboxMessage reply = mailboxService.waitForMessage(
                conversation.getId(), question.getId(), "user", properties.getMock().getWaitTimeoutSeconds());
        if (reply == null) {
            runLifecycleService.processExited(run.getId(), 0, null);
            return;
        }
        mailboxService.append(conversation.getId(), new CreateMailboxMessageRequest(
                run.getId(),
                "agent",
                "agent_message",
                "I received your reply: " + reply.getBody()
        ));
        mailboxService.terminalEvent(conversation.getId(), new TerminalEventRequest(
                run.getId(),
                "done",
                "Mock run completed after reading user reply. Last startup message: " + start.getId()
        ));
    }
}
