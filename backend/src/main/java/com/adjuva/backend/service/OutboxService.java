package com.adjuva.backend.service;

import com.adjuva.backend.mapper.OutboxEventMapper;
import com.adjuva.backend.model.entity.OutboxEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
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

    private String toJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize outbox payload", e);
        }
    }
}
