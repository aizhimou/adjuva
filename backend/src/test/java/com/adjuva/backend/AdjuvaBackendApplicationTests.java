package com.adjuva.backend;

import com.adjuva.backend.model.entity.Conversation;
import com.adjuva.backend.model.response.ConversationDetail;
import com.adjuva.backend.model.response.ConversationMessageResult;
import com.adjuva.backend.service.ConversationService;
import com.adjuva.backend.model.request.CreateConversationRequest;
import com.adjuva.backend.model.request.SendMessageRequest;
import com.adjuva.backend.model.dto.CodexCommand;
import com.adjuva.backend.handler.CodexExecutor;
import com.adjuva.backend.handler.MockExecutor;
import com.adjuva.backend.model.request.CreateMailboxMessageRequest;
import com.adjuva.backend.model.entity.MailboxMessage;
import com.adjuva.backend.service.MailboxService;
import com.adjuva.backend.model.request.TerminalEventRequest;
import com.adjuva.backend.model.request.CreateProjectRequest;
import com.adjuva.backend.model.entity.Project;
import com.adjuva.backend.service.ProjectService;
import com.adjuva.backend.model.entity.Run;
import com.adjuva.backend.service.RunLifecycleService;
import com.adjuva.backend.mapper.RunMapper;
import com.adjuva.backend.model.entity.AutomationSchedule;
import com.adjuva.backend.service.AutomationScheduleService;
import com.adjuva.backend.model.request.CreateAutomationScheduleRequest;
import com.adjuva.backend.mapper.ProviderSessionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AdjuvaBackendApplicationTests {

    @Autowired
    private ProjectService projectService;
    @Autowired
    private ConversationService conversationService;
    @Autowired
    private MailboxService mailboxService;
    @Autowired
    private RunLifecycleService runLifecycleService;
    @Autowired
    private RunMapper runMapper;
    @Autowired
    private ProviderSessionMapper providerSessionMapper;
    @Autowired
    private AutomationScheduleService automationScheduleService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MockExecutor mockExecutor;
    @Autowired
    private CodexExecutor codexExecutor;
    @LocalServerPort
    private int port;

    @Test
    void contextLoadsAndPersistsProjectThroughMyBatisPlus() {
        Project project = createProject("project-baseline", "codex", "gpt-5");

        assertUuid(project.getId());
        assertThat(projectService.getActive(project.getId()).getWorkspacePath())
                .isEqualTo("/srv/adjuva/projects/adjuva");
        assertThat(projectService.listActive()).extracting(Project::getId).contains(project.getId());
    }

    @Test
    void conversationCreateInitializesProviderSessionAndIdleState() {
        Project project = createProject("conversation-create", "mock", "mock-agent");

        Conversation conversation = conversationService.create(project.getId(), new CreateConversationRequest(
                "MVP chat", null, null, null, null, null));

        assertUuid(conversation.getId());
        assertUuid(conversation.getMailboxId());
        assertThat(conversation.getStatus()).isEqualTo("idle");
        assertThat(providerSessionMapper.selectByConversationProviderModel(
                conversation.getId(), "mock", "mock-agent")).isPresent();
    }

    @Test
    void userMessagesRouteThroughConversationStateMachine() {
        Conversation conversation = createConversation("routing");

        ConversationMessageResult first = conversationService.sendUserMessage(
                conversation.getId(), new SendMessageRequest("start", true));
        Conversation running = conversationService.detail(conversation.getId()).conversation();
        assertThat(first.message().getMessageType()).isEqualTo("user_message");
        assertUuid(first.run().getId());
        assertThat(running.getStatus()).isEqualTo("running");
        assertThat(running.getActiveRunId()).isEqualTo(first.run().getId());

        ConversationMessageResult second = conversationService.sendUserMessage(
                conversation.getId(), new SendMessageRequest("additional context", true));
        assertThat(second.run()).isNull();
        assertThat(conversationService.detail(conversation.getId()).conversation().getActiveRunId())
                .isEqualTo(first.run().getId());

        MailboxMessage question = mailboxService.append(conversation.getId(), new CreateMailboxMessageRequest(
                first.run().getId(), "agent", "question", "Need a choice?"));
        Conversation waiting = conversationService.detail(conversation.getId()).conversation();
        assertThat(waiting.getStatus()).isEqualTo("waiting_for_user");
        assertThat(waiting.getPendingQuestionMessageId()).isEqualTo(question.getId());

        ConversationMessageResult answer = conversationService.sendUserMessage(
                conversation.getId(), new SendMessageRequest("yes", true));
        Conversation resumed = conversationService.detail(conversation.getId()).conversation();
        assertThat(answer.message().getMessageType()).isEqualTo("answer");
        assertThat(answer.message().getReplyToMessageId()).isEqualTo(question.getId());
        assertThat(answer.run()).isNull();
        assertThat(resumed.getStatus()).isEqualTo("running");
        assertThat(resumed.getPendingQuestionMessageId()).isNull();
    }

    @Test
    void waitingConversationWithoutActiveRunResumesOnAnswer() {
        Conversation conversation = createConversation("resume-answer");
        Run run = conversationService.sendUserMessage(
                conversation.getId(), new SendMessageRequest("start", true)).run();
        MailboxMessage question = mailboxService.append(conversation.getId(), new CreateMailboxMessageRequest(
                run.getId(), "agent", "question", "Need input?"));

        runLifecycleService.processExited(run.getId(), 0, null);
        Conversation waiting = conversationService.detail(conversation.getId()).conversation();
        assertThat(waiting.getStatus()).isEqualTo("waiting_for_user");
        assertThat(waiting.getActiveRunId()).isNull();
        assertThat(waiting.getPendingQuestionMessageId()).isEqualTo(question.getId());

        ConversationMessageResult answer = conversationService.sendUserMessage(
                conversation.getId(), new SendMessageRequest("reply", true));
        Conversation resumed = conversationService.detail(conversation.getId()).conversation();
        assertThat(answer.message().getMessageType()).isEqualTo("answer");
        assertThat(answer.run()).isNotNull();
        assertThat(answer.run().getTriggerType()).isEqualTo("resume");
        assertThat(resumed.getStatus()).isEqualTo("running");
        assertThat(resumed.getActiveRunId()).isEqualTo(answer.run().getId());
    }

    @Test
    void terminalEventsAndCancelUpdateRunAndConversation() {
        Conversation doneConversation = createConversation("terminal-done");
        Run doneRun = conversationService.sendUserMessage(
                doneConversation.getId(), new SendMessageRequest("finish", true)).run();

        mailboxService.terminalEvent(doneConversation.getId(), new TerminalEventRequest(
                doneRun.getId(), "done", "complete"));
        ConversationDetail doneDetail = conversationService.detail(doneConversation.getId());
        assertThat(doneDetail.conversation().getStatus()).isEqualTo("completed");
        assertThat(runMapper.selectById(doneRun.getId()).getTerminationReason()).isEqualTo("completed");

        Conversation failConversation = createConversation("terminal-fail");
        Run failRun = conversationService.sendUserMessage(
                failConversation.getId(), new SendMessageRequest("fail", true)).run();
        mailboxService.terminalEvent(failConversation.getId(), new TerminalEventRequest(
                failRun.getId(), "fail", "nope"));
        assertThat(conversationService.detail(failConversation.getId()).conversation().getStatus()).isEqualTo("failed");
        assertThat(runMapper.selectById(failRun.getId()).getTerminationReason()).isEqualTo("failed");

        Conversation cancelConversation = createConversation("terminal-cancel");
        Run cancelRun = conversationService.sendUserMessage(
                cancelConversation.getId(), new SendMessageRequest("cancel", true)).run();
        runLifecycleService.cancel(cancelRun.getId());
        assertThat(conversationService.detail(cancelConversation.getId()).conversation().getStatus()).isEqualTo("cancelled");
        assertThat(runMapper.selectById(cancelRun.getId()).getTerminationReason()).isEqualTo("cancelled");
    }

    @Test
    void mailboxWaitAndAckMarkMessages() {
        Conversation conversation = createConversation("mailbox");
        MailboxMessage first = mailboxService.append(conversation.getId(), new CreateMailboxMessageRequest(
                null, "agent", "agent_message", "hello"));
        MailboxMessage user = conversationService.sendUserMessage(
                conversation.getId(), new SendMessageRequest("reply", false)).message();

        MailboxMessage waited = mailboxService.waitForMessage(conversation.getId(), first.getId(), "user", 1);
        assertThat(waited.getId()).isEqualTo(user.getId());
        assertThat(waited.getReadAt()).isNotNull();

        MailboxMessage acked = mailboxService.markAck(conversation.getId(), user.getId());
        assertThat(acked.getAckAt()).isNotNull();
    }

    @Test
    void scheduleTriggerHandlerCreatesSystemConversationAndRun() {
        Project project = createProject("schedule", "mock", "mock-agent");
        AutomationSchedule schedule = automationScheduleService.create(project.getId(), new CreateAutomationScheduleRequest(
                "Daily report",
                "active",
                "one_time",
                null,
                null,
                "mock",
                "mock-agent",
                "Scheduled report",
                "Create a daily report",
                null
        ));

        automationScheduleService.handleTrigger(schedule.getId());

        AutomationSchedule updated = automationScheduleService.get(schedule.getId());
        List<Conversation> conversations = conversationService.listByProject(project.getId());
        assertThat(updated.getLastTriggeredAt()).isNotNull();
        assertThat(conversations).hasSize(1);
        assertThat(conversations.getFirst().getSourceType()).isEqualTo("schedule");
        assertThat(conversationService.detail(conversations.getFirst().getId()).runs()).hasSize(1);
    }

    @Test
    void mockExecutorCompletesAskWaitDoneFlow() throws Exception {
        Conversation conversation = createConversation("mock-executor");
        Run run = conversationService.sendUserMessage(
                conversation.getId(), new SendMessageRequest("start mock", true)).run();

        CompletableFuture<Void> execution = CompletableFuture.runAsync(() -> mockExecutor.execute(conversation, run));

        MailboxMessage question = waitUntilQuestion(conversation.getId());
        conversationService.sendUserMessage(conversation.getId(), new SendMessageRequest("include archived", true));

        execution.get(3, TimeUnit.SECONDS);
        ConversationDetail detail = conversationService.detail(conversation.getId());
        assertThat(detail.conversation().getStatus()).isEqualTo("completed");
        assertThat(detail.messages()).extracting(MailboxMessage::getMessageType)
                .contains("agent_message", "question", "answer", "terminal_event");
        assertThat(runMapper.selectById(run.getId()).getTerminationReason()).isEqualTo("completed");
        assertUuid(question.getId());
    }

    @Test
    void codexCommandAndJsonlParserHandleSessionAndMessages() throws Exception {
        Project project = createProject("codex", "codex", "gpt-5");
        String workspace = Path.of("target", "test-workspaces", UUID.randomUUID().toString()).toAbsolutePath().toString();
        Conversation conversation = conversationService.create(project.getId(), new CreateConversationRequest(
                "Codex", "codex", "gpt-5", workspace, null, null));
        Run run = conversationService.sendUserMessage(
                conversation.getId(), new SendMessageRequest("do work", true)).run();

        CodexCommand command = codexExecutor.buildCommand(conversation, run);
        assertThat(command.args()).containsExactly(
                "exec",
                "--json",
                "--skip-git-repo-check",
                "--dangerously-bypass-approvals-and-sandbox",
                "-C",
                Path.of(workspace).toAbsolutePath().normalize().toString(),
                command.args().getLast()
        );
        assertThat(command.env()).containsEntry("ADJUVA_CONVERSATION_ID", conversation.getId());
        assertThat(Path.of(workspace, "AGENTS.md")).exists();

        codexExecutor.handleJsonLine(conversation, run,
                "{\"type\":\"thread.started\",\"thread_id\":\"session-123\"}");
        codexExecutor.handleJsonLine(conversation, run,
                "{\"type\":\"item.completed\",\"item\":{\"type\":\"agent_message\",\"text\":\"hello from codex\"}}");
        codexExecutor.handleJsonLine(conversation, run, "not json");

        Run updatedRun = runMapper.selectById(run.getId());
        assertThat(updatedRun.getExternalSessionIdAfter()).isEqualTo("session-123");
        assertThat(mailboxService.list(conversation.getId(), null)).extracting(MailboxMessage::getBody)
                .contains("hello from codex");

        mailboxService.terminalEvent(conversation.getId(), new TerminalEventRequest(run.getId(), "done", "done"));
        Run resumeRun = conversationService.sendUserMessage(
                conversation.getId(), new SendMessageRequest("continue", true)).run();
        CodexCommand resume = codexExecutor.buildCommand(
                conversationService.detail(conversation.getId()).conversation(), resumeRun);
        assertThat(resume.args()).startsWith("exec", "resume", "--json");
        assertThat(resume.args()).contains("session-123");
    }

    @Test
    void v1ApiCreatesProjectAndAcceptsConversationMessage() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String slug = "api-" + UUID.randomUUID();
        HttpResponse<String> projectResponse = client.send(jsonPost("/api/v1/projects", """
                {"name":"API","slug":"%s","workspacePath":"/tmp/adjuva-api","defaultProvider":"mock","defaultModel":"mock-agent"}
                """.formatted(slug)), HttpResponse.BodyHandlers.ofString());
        assertThat(projectResponse.statusCode()).isEqualTo(201);
        JsonNode project = objectMapper.readTree(projectResponse.body());

        HttpResponse<String> conversationResponse = client.send(jsonPost(
                "/api/v1/projects/" + project.path("id").asText() + "/conversations",
                "{\"title\":\"API conversation\"}"), HttpResponse.BodyHandlers.ofString());
        assertThat(conversationResponse.statusCode()).isEqualTo(201);
        JsonNode conversation = objectMapper.readTree(conversationResponse.body());

        HttpResponse<String> messageResponse = client.send(jsonPost(
                "/api/v1/conversations/" + conversation.path("id").asText() + "/messages",
                "{\"body\":\"hello\",\"autoStart\":true}"), HttpResponse.BodyHandlers.ofString());
        assertThat(messageResponse.statusCode()).isEqualTo(201);
        JsonNode message = objectMapper.readTree(messageResponse.body());
        assertThat(message.path("message").path("messageType").asText()).isEqualTo("user_message");
        assertUuid(message.path("run").path("id").asText());
    }

    private void assertUuid(String id) {
        assertThat(id).matches("[0-9a-f]{32}");
    }

    private HttpRequest jsonPost(String path, String body) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    private Conversation createConversation(String name) {
        Project project = createProject(name, "mock", "mock-agent");
        return conversationService.create(project.getId(), new CreateConversationRequest(
                name, null, null, null, null, null));
    }

    private Project createProject(String slugPrefix, String provider, String model) {
        return projectService.create(new CreateProjectRequest(
                "Adjuva " + slugPrefix,
                slugPrefix + "-" + UUID.randomUUID(),
                "Persistent project assistant",
                "/srv/adjuva/projects/adjuva",
                provider,
                model
        ));
    }

    private MailboxMessage waitUntilQuestion(String conversationId) throws Exception {
        long deadline = System.currentTimeMillis() + 1500;
        while (System.currentTimeMillis() < deadline) {
            List<MailboxMessage> messages = mailboxService.list(conversationId, null);
            for (MailboxMessage message : messages) {
                if ("question".equals(message.getMessageType())) {
                    return message;
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for mock question");
    }
}
