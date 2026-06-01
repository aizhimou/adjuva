package dev.adjuva.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import dev.adjuva.model.entity.MailboxMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MailboxMessageMapper extends BaseMapper<MailboxMessage> {

    List<MailboxMessage> selectByConversation(String conversationId);

    List<MailboxMessage> selectAfter(String conversationId, Instant afterCreatedAt, String afterId);

    Optional<MailboxMessage> selectOwned(String conversationId, String messageId);

    int markRead(String id, Instant readAt, Instant updatedAt);

    int markAck(String id, Instant ackAt, Instant updatedAt);
}
