package dev.adjuva.model.response;

import dev.adjuva.model.entity.Conversation;
import dev.adjuva.model.entity.MailboxMessage;
import dev.adjuva.model.entity.Run;

import java.util.List;

public record ConversationDetail(
        Conversation conversation,
        List<MailboxMessage> messages,
        List<Run> runs
) {
}
