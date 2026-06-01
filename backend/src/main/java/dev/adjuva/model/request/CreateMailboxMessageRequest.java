package dev.adjuva.model.request;

public record CreateMailboxMessageRequest(
        String runId,
        String sender,
        String messageType,
        String body
) {
}
