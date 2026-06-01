package dev.adjuva.model.request;

public record CreateConversationRequest(
        String title,
        String provider,
        String model,
        String workspacePath,
        String sourceType,
        String sourceRef
) {
}
