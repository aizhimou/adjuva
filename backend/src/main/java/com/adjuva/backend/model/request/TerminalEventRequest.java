package com.adjuva.backend.model.request;

public record TerminalEventRequest(
        String runId,
        String eventType,
        String body
) {
}
