# MCP support

OrchordsAI is a first-class Model Context Protocol client.

## Transports

- **Streamable HTTP** (preferred) with automatic **SSE fallback** for older
  servers.
- Per-server configuration: URL, headers, auth, request timeout (default
  30 s), and enable/disable per assistant.
- Explicit cancellation: in-flight tool calls can be cancelled from the
  approval dialog or the running tool indicator.

## Authentication

- **OAuth 2.1** with **Dynamic Client Registration (RFC 7591)** — the app
  registers itself automatically with servers that support it.
- **RFC 8707 resource indicators** — authorization requests are bound to the
  server's resource URL.
- Bearer + refresh token handling, `401` + `WWW-Authenticate` re-auth, and
  loopback redirect capture via the `:oauth` module.
- API-key style servers work via static `Authorization` headers.

## Tool approval

Every MCP tool call surfaces an approval prompt showing the server, the tool,
and the arguments before execution. Approvals can be remembered per tool.

## Assistant bindings

Each assistant can enable a subset of configured MCP servers, so different
workflows get different tool sets.

## Guides

- [GitHub MCP](GITHUB_MCP.md)
- [Cloudflare MCP](CLOUDFLARE_MCP.md)

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `401` loop | Re-run OAuth from the server's settings page; check the server's required scopes |
| DCR rejected | Some servers require pre-registered clients — paste the client id/secret into advanced auth fields |
| Tools never appear | Check the server is enabled for the active assistant |
| Timeout on long tools | Raise the per-server request timeout |
