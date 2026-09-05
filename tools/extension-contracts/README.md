# Extensions foundation checks

Run `bash tools/extension-contracts/run.sh` from a checkout with Kotlin and Java available.
These checks compile the actual dependency-free production catalog and connector preflight policy.
They do not require network access or a connected account.

The library contains 12 modes, 8 lorebooks with 24 entries, and 30 original skill procedures.
Tests check identifiers, bounded substantive content, frontmatter, identity-preserving merges,
and repeat installation planning. Connector tests exercise runtime narrowing, disconnected and
unavailable adapters, account/resource/action identity, capabilities, and exact approval bindings.

`BuiltInLibraryModels.kt` maps these definitions to the existing native mode/lorebook classes.
Its Settings transformation is a candidate, not a persisted installation. The Android adapter
must be compiled and verified through the app toolchain; the standalone checks do not compile it.

## Delivery boundaries

The catalog and policy are implementation foundations, not working OAuth/API adapters. No Gmail,
Calendar, Contacts, Drive, GitHub, Canva or HeyGen action becomes executable from catalog content.
An ALLOW preflight decision is not a reusable authorization token. The executor must recheck live
policy and consume approval/idempotency state in the canonical runtime before dispatch.

Native installation and UI activation remain open under #367/#86/#96: use a narrow atomic
SettingsStore merge, no-replace SkillManager publication, explicit partial results and unchanged
assistant selection. Do not wire a success button around a whole-settings optimistic write and
claim concurrent install/edit safety. Preserve existing malformed skill directories and user edits.

No app build, device test, external-account action or deployment is implied by a passing standalone
check. Existing app/JUnit, Android, Web, security and integration gates still apply.
