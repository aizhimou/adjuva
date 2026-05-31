#!/usr/bin/env node
const { conversationId, main, readBodyArg, request, runId } = require("./lib.js");

main(() =>
  request(`/api/conversations/${conversationId()}/mailbox/messages`, {
    method: "POST",
    body: {
      run_id: runId(),
      sender: "agent",
      type: "question",
      body: readBodyArg()
    }
  })
);
