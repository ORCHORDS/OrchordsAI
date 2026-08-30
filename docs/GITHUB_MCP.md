# GitHub MCP

Connect OrchordsAI to GitHub's official MCP server to let assistants work
with issues, pull requests, and repositories.

## Setup

1. **Settings → MCP → Add server**
2. Transport: **Streamable HTTP**
3. URL: `https://api.githubcopilot.com/mcp/`
4. Authentication: **Bearer token** — create a fine-grained PAT
   (github.com/settings/personal-access-tokens) with the minimum repository
   permissions you want the assistant to have, then set the header:
   `Authorization: Bearer <your PAT>`
5. Save, then approve the tool prompts when the assistant first calls GitHub.

## Recommended token scopes

- Read-only usage: `Issues: read`, `Pull requests: read`, `Metadata: read`
- Triaging: add `Issues: write`, `Pull requests: write`
- Never grant `Administration: write` unless you specifically need it.

## Notes

- The token is stored encrypted on-device (see [SECURITY.md](SECURITY.md)).
- Tool calls are approved individually; remember approvals only for tools you
  trust (e.g. `search_issues`, `get_pull_request`).
- Full MCP feature overview: [MCP.md](MCP.md).
