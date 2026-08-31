---
title: "ORCHORDS AI — Model Context Protocol"
owner: "AI Platform Engineering"
status: "approved"
classification: "public"
last-reviewed: "2026-08-31"
review-cycle: "90 days"
next-review: "2026-11-29"
---

# ORCHORDS AI — Model Context Protocol

ORCHORDS AI connects assistants to trusted MCP servers over supported HTTP streaming transports.

## Integration rules

1. Configure only MCP servers you trust.
2. Authorize each server with the least privilege required for its tools.
3. Bind only the tools needed by the assistant or workflow.
4. Keep explicit approval for sensitive mutations and destructive actions.
5. Treat tool output as untrusted input until validated at the next security boundary.
6. Remove stale servers and revoke credentials when an integration is retired.

MCP expands what an assistant can do; it must not silently expand what the assistant is authorized to do.

## Brand

**ORCHORDS — BUILD DIFFERENT.**
