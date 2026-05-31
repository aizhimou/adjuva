package com.adjuva.backend.model.response;

import com.adjuva.backend.model.entity.Conversation;

import java.time.Instant;

public record TriggerScheduleResponse(
        String scheduleId,
        Instant scheduledAt,
        Conversation conversation
) {
}
