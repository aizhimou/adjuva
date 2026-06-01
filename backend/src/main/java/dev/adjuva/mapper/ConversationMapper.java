package dev.adjuva.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.adjuva.model.entity.Conversation;
import dev.adjuva.model.response.ConversationSummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationMapper extends BaseMapper<Conversation> {

    List<Conversation> selectByProject(String projectId);

    List<ConversationSummary> selectSummaries();

    Optional<Conversation> selectForDetail(String id);

    int markRunning(String id, String activeRunId, Instant lastActivityAt, Instant updatedAt);

    int markWaitingForUser(String id, String pendingQuestionMessageId, Instant lastActivityAt, Instant updatedAt);

    int clearQuestionAndMarkRunning(String id, String activeRunId, Instant lastActivityAt, Instant updatedAt);

    int markTerminal(String id, String status, Instant lastActivityAt, Instant updatedAt);

    int clearActiveRunKeepWaiting(String id, Instant lastActivityAt, Instant updatedAt);
}
