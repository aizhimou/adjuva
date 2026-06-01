package dev.adjuva.model.response;

import dev.adjuva.model.entity.MailboxMessage;
import dev.adjuva.model.entity.Run;

public record ConversationMessageResult(MailboxMessage message, Run run) {
}
