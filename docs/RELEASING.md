---
title: "Releasing ORCHORDS AI"
owner: "Release Engineering"
status: "approved"
classification: "public"
last-reviewed: "2026-08-31"
review-cycle: "90 days"
next-review: "2026-11-29"
---

# Releasing ORCHORDS AI

Update version metadata, run the required checks, build with private signing configuration, verify signatures and installability, and publish release notes.

## Artifact rules

Release APKs use the established `OrchordsAI-<version>-<abi>.apk` artifact pattern. Preserve artifact naming required by automation and consumers even when public prose uses the `ORCHORDS AI` brand.

Never commit signing keys, signing credentials, local signing configuration, or generated APKs to source control. Release provenance and checksums must be verified before publication.

## Brand

**ORCHORDS — BUILD DIFFERENT.**
