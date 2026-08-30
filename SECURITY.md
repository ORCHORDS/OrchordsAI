# Security Policy

## Reporting a vulnerability

Do not open public issues for security problems. Email the maintainers via the
address listed on the ORCHORDS organization page, or use the repository's
private vulnerability reporting. Include reproduction steps and affected
versions. We aim to respond within 7 days.

## Product security posture

- **No telemetry.** The app ships with zero analytics, zero crash reporting,
  and zero third-party trackers. Nothing is sent anywhere unless you configure
  a provider, sync backend, or MCP server yourself.
- **Local-first storage.** Conversations, settings, and credentials live in
  on-device storage. Credentials are stored using Android Keystore-backed
  encryption; they never leave the device except as request authentication to
  the endpoints you configured.
- **Update checks are opt-in** and only contact `api.github.com` for this
  repository's releases. There is no remote configuration channel.
- **No bundled secrets.** The repository contains no API keys, signing keys,
  or service credentials. CI signs releases from repository secrets only.

## Scope

The Android app, the embedded web server UI, and this repository's CI are in
scope. Vulnerabilities in third-party model providers you connect to are out
of scope. See [docs/SECURITY.md](docs/SECURITY.md) for the detailed threat
model and credential architecture.
