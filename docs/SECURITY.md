---
title: "ORCHORDS AI Security Design"
owner: "Security Engineering"
status: "approved"
classification: "public"
last-reviewed: "2026-08-31"
review-cycle: "90 days"
next-review: "2026-11-29"
---

# ORCHORDS AI Security Design

Credentials and conversations must not appear in logs, diagnostics, test fixtures, or repository files.

## Design expectations

- Verify custom provider endpoints and MCP servers before granting access.
- Keep provider, OAuth, and MCP credentials within the intended storage boundary.
- Optional local servers should bind narrowly, validate requests, and stop when unused.
- Tool execution must preserve least privilege and explicit approval for sensitive actions.
- Generated or remote content must not be trusted merely because it came through an AI provider or tool.

For vulnerability reporting, follow the repository [Security Policy](../SECURITY.md).

## Brand

**ORCHORDS — BUILD DIFFERENT.**
