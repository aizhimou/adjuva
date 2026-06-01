package dev.adjuva.model.response;

import dev.adjuva.model.entity.Conversation;

import java.time.Instant;

public record TriggerScheduleResponse(
        String scheduleId,
        Instant scheduledAt,
        Conversation conversation
) {
}
