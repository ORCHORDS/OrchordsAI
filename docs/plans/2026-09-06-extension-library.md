# Extensions Library and Connector Foundation Implementation Plan

**Goal:** Populate the original built-in content library and establish a tested, fail-closed connector action policy without changing existing account grants.

**Architecture:** Native SettingsStore modes/lorebooks and SkillManager remain authoritative. A dependency-free typed catalog provides stable definitions. Connector action policy is a runtime-owned prerequisite, not a network adapter or permission source.

**Spec:** Issues #85, #86, #87, #94, #95, #96 and #367 under master #26.

## Constraints

Use original ORCHORDS titles/content; preserve notices. No connector credentials in content. No automatic activation or implicit OAuth connection. Keep main-only, current-base checks and all required verification. Do not touch Room, backup, provider serialization or unrelated work.

## Task 1 — Content contracts and tests

- Add contract checks for 12 modes, 8 lorebooks/24 entries, and 30 substantive skills.
- Check stable UUIDs, unique skill names, bounded content and plain nonempty triggers.
- Check append-only identity merge preserves existing edited records and is idempotent.
- Observe failing checks before populating definitions; compile/run actual Kotlin sources.

## Task 2 — Populated library

- BuiltInLibrary.kt: immutable definitions and merge helper, no Android/network dependency.
- BuiltInLibraryContent.kt: original modes, reference entries and skill procedures.
- Future native installation must use SettingsStore and SkillManager, with no-replace behavior and explicit outcomes. A catalog without installation wiring is not completion of #367.

## Task 3 — Connector foundation

- ConnectorPolicy.kt: typed action/connection/allowlist/request/approval snapshots and denial reasons.
- Test missing adapter/account, scope/allowlist denial, wrong account/action/resource/arguments, stale approvals, and exact binding.
- No HTTP calls, token parsing, secret storage or adapter-completion claim in this foundational slice.

## Task 4 — Integration and landing gates

- Prefer a reviewed atomic change over sequential partial main commits.
- Recheck base and authenticated author; do not force refs or weaken protection.
- Verify persisted files and relevant CI before claiming a landed build.
- If a gate prevents landing, retain the tested patch and record the exact blocker in the owners; do not create unrequested branches.
