package dev.adjuva.controller;

import dev.adjuva.model.entity.AutomationSchedule;
import dev.adjuva.model.request.CreateAutomationScheduleRequest;
import dev.adjuva.model.response.TriggerScheduleResponse;
import dev.adjuva.service.AutomationScheduleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AutomationScheduleController {

    private final AutomationScheduleService service;

    public AutomationScheduleController(AutomationScheduleService service) {
        this.service = service;
    }

    @GetMapping("/projects/{projectId}/automation-schedules")
    public List<AutomationSchedule> list(@PathVariable String projectId) {
        return service.list(projectId);
    }

    @PostMapping("/projects/{projectId}/automation-schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public AutomationSchedule create(
            @PathVariable String projectId,
            @RequestBody CreateAutomationScheduleRequest request
    ) {
        return service.create(projectId, request);
    }

    @PostMapping("/automation-schedules/{scheduleId}/trigger")
    public TriggerScheduleResponse trigger(@PathVariable String scheduleId) {
        service.get(scheduleId);
        return service.trigger(scheduleId);
    }
}
