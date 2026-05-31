package com.adjuva.backend.model.response;

import com.adjuva.backend.model.entity.MailboxMessage;
import com.adjuva.backend.model.entity.Run;

public record TerminalEventResponse(MailboxMessage message, Run run) {
}
