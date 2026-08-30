# Releasing

Releases are built and signed by GitHub Actions from repository secrets.

## Artifacts

- `OrchordsAI-<version>-<abi>.apk` — per-ABI stable APKs
- `OrchordsAI-<version>-universal.apk` — universal APK
- `OrchordsAI-<version>.aab` — bundle for store upload

## Steps

1. Update `versionCode` / `versionName` in `app/build.gradle.kts`.
2. Tag the commit: `git tag <version> && git push origin <version>`
   (tags have **no** `v` prefix).
3. The release workflow builds, signs, and attaches artifacts to the GitHub
   release.

## Required repository secrets

| Secret | Purpose |
| --- | --- |
| `SIGNING_STORE_FILE_BASE64` | Base64 keystore, decoded at build time |
| `SIGNING_KEY_ALIAS` | Keystore key alias |
| `SIGNING_STORE_PASSWORD` | Keystore password |
| `SIGNING_KEY_PASSWORD` | Key password |

Local `local.properties`, `*.jks`, and `google-services.json` are gitignored;
never commit signing material.
