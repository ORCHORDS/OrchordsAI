---
name: publish-release
description: Use this skill when users request to publish a release update.
---

# Publish Release

Generate a changelog from the git log, then create a new release.

## Changelog

- Review the changes between the previous release tag and the current commit
- Summarize the updates into a changelog; merge bug fixes and UI tweaks where
  possible and keep the total number of entries at or below 10
- Avoid technical jargon in the changelog

Use this format:

```markdown
Updates:

- xxx
- xxx
```

After generating the changelog, ask the user to confirm it is reasonable.
Create the release only after the user confirms.

## Publishing

Create the release with the GitHub CLI and upload
`app/release/OrchestrdsAI-<version>-<abi>.apk`:

- Upload the arm64 build only; rename the APK to
  `OrchestrdsAI-<version>-arm64-v8a.apk` before uploading
- Use the version number as the release title and the changelog as the
  description
- Tag name is the version number, without a `v` prefix
