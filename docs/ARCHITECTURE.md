---
title: "ORCHORDS AI Architecture"
owner: "Engineering"
status: "approved"
classification: "public"
last-reviewed: "2026-08-31"
review-cycle: "90 days"
next-review: "2026-11-29"
---

# ORCHORDS AI Architecture

ORCHORDS AI is a modular Kotlin and Jetpack Compose application.

## Component boundaries

- `app` integrates the Android UI, application state, persistence, and product flows.
- `ai` implements model protocols, provider normalization, streaming, tool contracts, and native AI integration.
- Capability modules provide search, speech, documents, video, OAuth, web, and workspace features.
- View models expose UI state; repositories own persistence; provider adapters normalize requests and streams.

Keep provider-specific formats at adapter boundaries and preserve explicit approval boundaries for tool execution.

## Brand

**ORCHORDS — BUILD DIFFERENT.**
