package com.adjuva.backend.model.response;

import com.adjuva.backend.model.entity.Conversation;
import com.adjuva.backend.model.entity.MailboxMessage;
import com.adjuva.backend.model.entity.Run;

import java.util.List;

public record ConversationDetail(
        Conversation conversation,
        List<MailboxMessage> messages,
        List<Run> runs
) {
}
