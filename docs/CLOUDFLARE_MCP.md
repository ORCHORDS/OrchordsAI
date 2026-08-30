# Cloudflare MCP

Connect OrchordsAI to Cloudflare's remote MCP server for Cloudflare account
tooling (workers, zones, R2, and more).

## Setup

1. **Settings → MCP → Add server**
2. Transport: **Streamable HTTP**
3. URL: `https://mcp.cloudflare.com/mcp`
4. Authentication: **OAuth** — tap *Authorize*; OrchordsAI performs Dynamic
   Client Registration and RFC 8707 resource-bound authorization
   automatically, then opens the Cloudflare consent page.
5. Approve the requested account scopes in the browser and return to the app.

## API-token alternative

If you prefer not to OAuth, create an API token at
dash.cloudflare.com/profile/api-tokens scoped to the exact resources the
assistant may touch, and configure a static header instead:
`Authorization: Bearer <your token>`.

## Notes

- Grant the narrowest scope set possible; you can re-authorize with more
  scopes later from the server's settings page.
- Tool calls are individually approved before execution.
- Full MCP feature overview: [MCP.md](MCP.md).
