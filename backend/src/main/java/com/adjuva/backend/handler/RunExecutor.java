package com.adjuva.backend.handler;

import com.adjuva.backend.model.entity.Conversation;
import com.adjuva.backend.model.entity.Run;

public interface RunExecutor {

    String provider();

    void execute(Conversation conversation, Run run);
}
