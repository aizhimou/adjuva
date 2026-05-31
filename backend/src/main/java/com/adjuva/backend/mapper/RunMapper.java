package com.adjuva.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.adjuva.backend.model.entity.Run;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RunMapper extends BaseMapper<Run> {

    List<Run> selectByConversation(String conversationId);

    Optional<Run> selectRunningByConversation(String conversationId);

    int markProcessStarted(String id, String processId, String commandLine, Instant updatedAt);

    int updateExternalSession(String id, String externalSessionIdAfter, Instant updatedAt);

    int endRun(String id, String terminationReason, Integer exitCode, String signal,
               String finalEventType, String timeoutKind, String errorMessage,
               Instant endedAt, Instant updatedAt);
}
