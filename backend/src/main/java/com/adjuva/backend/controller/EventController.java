package com.adjuva.backend.controller;

import com.adjuva.backend.model.response.OutboxEventSignal;
import com.adjuva.backend.service.OutboxService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class EventController {

    private final OutboxService outboxService;

    public EventController(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @GetMapping(path = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) throws IOException {
        SseEmitter emitter = new SseEmitter(5_000L);
        List<OutboxEventSignal> signals = outboxService.pendingSignalsAfter(lastEventId, 100);
        if (signals.isEmpty()) {
            emitter.send(SseEmitter.event().name("heartbeat").data("ok").reconnectTime(2_000L));
        } else {
            for (OutboxEventSignal signal : signals) {
                emitter.send(SseEmitter.event()
                        .id(signal.id())
                        .name(signal.eventType())
                        .data(signal)
                        .reconnectTime(2_000L));
            }
        }
        emitter.complete();
        return emitter;
    }
}
