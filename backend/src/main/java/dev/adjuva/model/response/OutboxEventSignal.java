package dev.adjuva.model.response;

import java.time.Instant;

public record OutboxEventSignal(
        String id,
        String eventType,
        String aggregateType,
        String aggregateId,
        Instant createdAt
) {
}
