# Security details

## Credential storage

- Provider API keys, MCP OAuth tokens, and sync credentials are persisted in
  app-private storage and encrypted with keys held in the Android Keystore
  (AES-256-GCM; keys are non-exportable and never require user presence so
  background sync works).
- No secret is ever written to logs. Interceptors redact `Authorization`
  headers, API keys, and bearer tokens before any log call.
- Exported backups are created by the user explicitly and can be
  password-protected; the app never uploads anything automatically.

## Network egress

The app contacts only the endpoints you configure:

| Purpose | Destination | When |
| --- | --- | --- |
| Model providers | Your provider base URLs | Chat / generation |
| MCP servers | Your MCP endpoints | Tool calls |
| Search providers | Your search API endpoints | Search tool |
| WebDAV / S3 sync | Your sync server | Manual or scheduled sync |
| Update check | `api.github.com` (this repo's releases) | Manual / opt-in check |

There is no telemetry endpoint, no crash reporting service, and no remote
feature flags.

## Embedded web server

The optional web server binds on the local network only, requires a pairing
token shown in the app, and announces itself via mDNS as `OrchordsAI`. Disable
it in **Settings → Web Server** when not needed.

## Reporting

See [SECURITY.md](../SECURITY.md).
