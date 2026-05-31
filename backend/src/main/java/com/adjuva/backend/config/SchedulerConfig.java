package com.adjuva.backend.config;

import com.adjuva.backend.service.AutomationScheduleService;
import com.github.kagkarlsson.scheduler.task.TaskDescriptor;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.Schedules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class SchedulerConfig {

    public static final TaskDescriptor<String> AUTOMATION_TRIGGER =
            TaskDescriptor.of("adjuva-automation-trigger", String.class);

    @Bean
    RecurringTask<Void> controlPlaneMaintenanceTask() {
        return Tasks.recurring("adjuva-control-plane-maintenance", Schedules.fixedDelay(Duration.ofMinutes(15)))
                .execute((taskInstance, executionContext) -> {
                    // Placeholder for orphan-reference scans and outbox maintenance.
                });
    }

    @Bean
    OneTimeTask<String> automationTriggerTask(AutomationScheduleService automationScheduleService) {
        return Tasks.oneTime(AUTOMATION_TRIGGER)
                .execute((taskInstance, executionContext) ->
                        automationScheduleService.handleTrigger(taskInstance.getData()));
    }
}
