package dev.adjuva.handler;

import dev.adjuva.config.ExecutorProperties;
import dev.adjuva.model.entity.Conversation;
import dev.adjuva.model.request.CreateMailboxMessageRequest;
import dev.adjuva.model.entity.MailboxMessage;
import dev.adjuva.service.MailboxService;
import dev.adjuva.model.request.TerminalEventRequest;
import dev.adjuva.model.entity.Run;
import dev.adjuva.service.RunLifecycleService;
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
