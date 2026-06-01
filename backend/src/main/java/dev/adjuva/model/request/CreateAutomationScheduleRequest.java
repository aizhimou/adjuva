package dev.adjuva.model.request;

public record CreateAutomationScheduleRequest(
        String title,
        String status,
        String scheduleKind,
        String scheduleExpression,
        String timezone,
        String provider,
        String model,
        String conversationTitleTemplate,
        String promptTemplate,
        String metadataJson
) {
}
