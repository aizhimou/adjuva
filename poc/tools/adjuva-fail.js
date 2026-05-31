#!/usr/bin/env node
const { conversationId, main, readBodyArg, request, runId } = require("./lib.js");

main(() =>
  request(`/api/conversations/${conversationId()}/mailbox/events/fail`, {
    method: "POST",
    body: {
      run_id: runId(),
      body: readBodyArg()
    }
  })
);
