package com.adjuva.backend.service;

import com.adjuva.backend.mapper.OutboxEventMapper;
import com.adjuva.backend.model.entity.OutboxEvent;
import com.adjuva.backend.model.response.OutboxEventSignal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class OutboxService {

    private final OutboxEventMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxService(OutboxEventMapper mapper, ObjectMapper objectMapper, Clock clock) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void append(String aggregateType, String aggregateId, String eventType, Map<String, ?> payload) {
        Instant now = clock.instant();
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayloadJson(toJson(payload));
        event.setStatus("pending");
        event.setAttempts(0);
        event.setAvailableAt(now);
        mapper.insert(event);
    }

    public List<OutboxEventSignal> pendingSignalsAfter(String afterEventId, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return mapper.selectPendingAfter(afterEventId, boundedLimit).stream()
                .map(event -> new OutboxEventSignal(
                        event.getId(),
                        event.getEventType(),
                        event.getAggregateType(),
                        event.getAggregateId(),
                        event.getCreatedAt()
                ))
                .toList();
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize outbox payload", e);
        }
    }
}
