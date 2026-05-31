package com.adjuva.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.adjuva.backend.model.entity.Conversation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationMapper extends BaseMapper<Conversation> {

    List<Conversation> selectByProject(String projectId);

    Optional<Conversation> selectForDetail(String id);

    int markRunning(String id, String activeRunId, Instant lastActivityAt, Instant updatedAt);

    int markWaitingForUser(String id, String pendingQuestionMessageId, Instant lastActivityAt, Instant updatedAt);

    int clearQuestionAndMarkRunning(String id, String activeRunId, Instant lastActivityAt, Instant updatedAt);

    int markTerminal(String id, String status, Instant lastActivityAt, Instant updatedAt);

    int clearActiveRunKeepWaiting(String id, Instant lastActivityAt, Instant updatedAt);
}
