#!/usr/bin/env node
const { argValue, conversationId, main, request } = require("./lib.js");

main(() => {
  const params = new URLSearchParams();
  const after = argValue("--after");
  if (after) params.set("after", after);

  const query = params.toString();
  return request(`/api/conversations/${conversationId()}/mailbox/messages${query ? `?${query}` : ""}`);
});
