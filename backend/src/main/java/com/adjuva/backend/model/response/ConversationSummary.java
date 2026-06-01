package com.adjuva.backend.model.response;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class ConversationSummary {
    private String id;
    private String projectId;
    private String projectName;
    private String projectSlug;
    private String title;
    private String status;
    private Instant lastActivityAt;
    private String activeRunId;
    private String pendingQuestionMessageId;
    private Instant updatedAt;
}
