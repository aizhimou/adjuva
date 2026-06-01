package dev.adjuva.model.request;

public record CreateProjectRequest(
        String name,
        String slug,
        String description,
        String workspacePath,
        String defaultProvider,
        String defaultModel
) {
}
