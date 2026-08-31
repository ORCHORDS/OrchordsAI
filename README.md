# ORCHORDS AI

ORCHORDS AI is a local-first Android AI assistant for user-selected models and services.

## Capabilities

- Multi-provider chat and configurable endpoints
- MCP tools with approval controls
- On-device conversations, settings, and credentials
- Voice, search, rich rendering, image, and video workflows

## Build

See [Building](docs/BUILDING.md).

## Verify nightly APKs

Nightly releases publish SHA-256 checksums and a GitHub/Sigstore SLSA provenance attestation. After downloading the APK and `SHA256SUMS`, verify both before installing:

```bash
sha256sum --check SHA256SUMS
gh attestation verify ./ORCHORDS-AI.apk \
  --repo ORCHORDS/OrchordsAI \
  --signer-workflow ORCHORDS/OrchordsAI/.github/workflows/daily-build.yml \
  --source-ref refs/heads/main
```

Replace `ORCHORDS-AI.apk` with the downloaded APK filename.

## License

Licensed under the [GNU AGPL v3](LICENSE). See [Third-Party Notices](THIRD_PARTY_NOTICES.md).
