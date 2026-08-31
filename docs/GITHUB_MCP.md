---
title: "ORCHORDS AI — GitHub MCP"
owner: "Security Engineering"
status: "approved"
classification: "public"
last-reviewed: "2026-08-31"
review-cycle: "90 days"
next-review: "2026-11-29"
---

# ORCHORDS AI — GitHub MCP

Connect a trusted GitHub MCP server through ORCHORDS AI MCP settings.

## Security requirements

- Prefer GitHub's official MCP server when its capabilities fit the workflow.
- Use OAuth or a fine-grained token limited to required repositories and permissions.
- Prefer read-only access until a workflow demonstrably requires mutation.
- Keep approval for repository writes, workflow changes, releases, security changes, merges, and destructive operations.
- Revoke unused credentials and rotate any credential suspected of exposure.

Repository administration should follow least privilege even when the operator owns the repository.

## Brand

**ORCHORDS — BUILD DIFFERENT.**
