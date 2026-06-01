package dev.adjuva.handler;

import dev.adjuva.model.entity.Conversation;
import dev.adjuva.model.entity.Run;

public interface RunExecutor {

    String provider();

    void execute(Conversation conversation, Run run);
}
