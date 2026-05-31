#!/usr/bin/env node
const { conversationId, main, request } = require("./lib.js");

main(() => {
  const messageId = process.argv[2];
  if (!messageId) throw new Error("message id is required");
  return request(`/api/conversations/${conversationId()}/mailbox/messages/${messageId}/ack`, {
    method: "POST",
    body: {}
  });
});
