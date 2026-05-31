package com.adjuva.backend.service;

import com.adjuva.backend.util.Text;
import com.adjuva.backend.config.SchedulerConfig;
import com.adjuva.backend.mapper.AutomationScheduleMapper;
import com.adjuva.backend.model.entity.AutomationSchedule;
import com.adjuva.backend.model.entity.Conversation;
import com.adjuva.backend.model.request.CreateAutomationScheduleRequest;
import com.adjuva.backend.model.request.CreateConversationRequest;
import com.adjuva.backend.model.entity.Project;
import com.adjuva.backend.model.response.TriggerScheduleResponse;
import com.github.kagkarlsson.scheduler.SchedulerClient;
import org.springframework.beans.factory.ObjectProvider;
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
public class AutomationScheduleService {

    private final AutomationScheduleMapper scheduleMapper;
    private final ProjectService projectService;
    private final ConversationService conversationService;
    private final RunLifecycleService runLifecycleService;
    private final ObjectProvider<SchedulerClient> schedulerClient;
    private final OutboxService outboxService;
    private final Clock clock;

    public AutomationScheduleService(
            AutomationScheduleMapper scheduleMapper,
            ProjectService projectService,
            ConversationService conversationService,
            RunLifecycleService runLifecycleService,
            ObjectProvider<SchedulerClient> schedulerClient,
            OutboxService outboxService,
            Clock clock
    ) {
        this.scheduleMapper = scheduleMapper;
        this.projectService = projectService;
        this.conversationService = conversationService;
        this.runLifecycleService = runLifecycleService;
        this.schedulerClient = schedulerClient;
        this.outboxService = outboxService;
        this.clock = clock;
    }

    public List<AutomationSchedule> list(String projectId) {
        projectService.getActive(projectId);
        return scheduleMapper.selectByProject(projectId);
    }

    @Transactional
    public AutomationSchedule create(String projectId, CreateAutomationScheduleRequest request) {
        request = request == null ? new CreateAutomationScheduleRequest(null, null, null, null, null, null, null, null, null, null) : request;
        Project project = projectService.getActive(projectId);
        String provider = Text.valueOrDefault(request.provider(), Text.valueOrDefault(project.getDefaultProvider(), "mock"));
        if (!"mock".equals(provider) && !"codex".equals(provider)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported provider: " + provider);
        }
        Instant now = clock.instant();
        AutomationSchedule schedule = new AutomationSchedule();
        schedule.setProjectId(projectId);
        schedule.setTitle(Text.valueOrDefault(request.title(), "Automation"));
        schedule.setStatus(Text.valueOrDefault(request.status(), "active"));
        schedule.setScheduleKind(Text.valueOrDefault(request.scheduleKind(), "one_time"));
        schedule.setScheduleExpression(Text.valueOrDefault(request.scheduleExpression(), now.toString()));
        schedule.setTimezone(Text.valueOrDefault(request.timezone(), "Pacific/Auckland"));
        schedule.setProvider(provider);
        schedule.setModel(Text.valueOrDefault(request.model(), "codex".equals(provider) ? "codex-cli" : "mock-agent"));
        schedule.setConversationTitleTemplate(Text.valueOrDefault(request.conversationTitleTemplate(), schedule.getTitle()));
        schedule.setPromptTemplate(Text.required(request.promptTemplate(), "promptTemplate"));
        schedule.setMetadataJson(Text.trimToNull(request.metadataJson()));
        scheduleMapper.insert(schedule);
        outboxService.append("automation_schedule", schedule.getId(), "automation_schedule.created", Map.of(
                "scheduleId", schedule.getId(),
                "projectId", projectId
        ));
        return schedule;
    }

    public TriggerScheduleResponse trigger(String scheduleId) {
        Instant when = clock.instant();
        SchedulerClient client = schedulerClient.getIfAvailable();
        if (client == null) {
            throw new ResponseStatusException(BAD_REQUEST, "db-scheduler is disabled");
        }
        client.schedule(
                SchedulerConfig.AUTOMATION_TRIGGER.instance(scheduleId)
                        .data(scheduleId)
                        .scheduledTo(when),
                SchedulerClient.ScheduleOptions.WHEN_EXISTS_RESCHEDULE
        );
        outboxService.append("automation_schedule", scheduleId, "automation_schedule.trigger_scheduled", Map.of(
                "scheduleId", scheduleId,
                "scheduledAt", when.toString()
        ));
        return new TriggerScheduleResponse(scheduleId, when, null);
    }

    @Transactional
    public void handleTrigger(String scheduleId) {
        AutomationSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null || !"active".equals(schedule.getStatus())) {
            return;
        }
        Conversation conversation = conversationService.create(schedule.getProjectId(), new CreateConversationRequest(
                schedule.getConversationTitleTemplate(),
                schedule.getProvider(),
                schedule.getModel(),
                null,
                "schedule",
                schedule.getId()
        ));
        runLifecycleService.createRun(conversation, "schedule", null,
                schedule.getProvider(), schedule.getModel(), schedule.getPromptTemplate());
        Instant now = clock.instant();
        scheduleMapper.markTriggered(scheduleId, now, now);
        outboxService.append("automation_schedule", scheduleId, "automation_schedule.triggered", Map.of(
                "scheduleId", scheduleId,
                "conversationId", conversation.getId()
        ));
    }

    public AutomationSchedule get(String scheduleId) {
        AutomationSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new ResponseStatusException(NOT_FOUND, "Automation schedule not found");
        }
        return schedule;
    }
}
