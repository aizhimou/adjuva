package dev.adjuva.model.request;

public record StartRunRequest(
        String provider,
        String model,
        String prompt,
        String triggerType
) {
}
