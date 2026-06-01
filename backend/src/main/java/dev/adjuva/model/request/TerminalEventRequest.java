package dev.adjuva.model.request;

public record TerminalEventRequest(
        String runId,
        String eventType,
        String body
) {
}
