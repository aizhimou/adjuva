#!/usr/bin/env node
const { argValue, conversationId, main, request } = require("./lib.js");

main(() => {
  const params = new URLSearchParams({
    timeout: argValue("--timeout", "300"),
    sender: "user"
  });
  const after = argValue("--after");
  if (after) params.set("after", after);

  return request(`/api/conversations/${conversationId()}/mailbox/wait?${params.toString()}`);
});
