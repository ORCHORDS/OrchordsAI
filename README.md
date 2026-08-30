<h1 align="center">OrchestrdsAI</h1>

<p align="center">
  <b>A local-first, open-source Android AI assistant.</b><br/>
  Bring your own keys, your own models, your own servers.<br/>
</p>

<p align="center">
  <a href="https://github.com/ORCHORDS/OrchordsAI/releases"><img alt="GitHub Release" src="https://img.shields.io/github/v/release/ORCHORDS/OrchordsAI?display_name=tag&include_prereleases"></a>
  <a href="./LICENSE"><img alt="License AGPL-3.0" src="https://img.shields.io/badge/License-AGPL%20v3-blue.svg"></a>
  <a href="https://github.com/ORCHORDS/OrchordsAI/releases"><img alt="Platform" src="https://img.shields.io/badge/platform-Android-3DDC84.svg"></a>
</p>

<p align="center">
  <a href="https://github.com/ORCHORDS/OrchordsAI/releases">Download</a> ·
  <a href="#features">Features</a> ·
  <a href="#quick-start">Quick Start</a> ·
  <a href="docs/BUILDING.md">Build</a> ·
  <a href="SECURITY.md">Security</a> ·
  <a href="CONTRIBUTING.md">Contribute</a>
</p>

---

## Features

- **Multi-provider AI chat** — OpenAI (Chat Completions + Responses), Anthropic Claude, Google Gemini (API key and Vertex AI), plus any OpenAI-compatible or Ollama-compatible endpoint with custom base URLs and model IDs.
- **Model Context Protocol (MCP)** — connect tool servers over Streamable HTTP (with SSE compatibility), OAuth 2.1 with Dynamic Client Registration and RFC 8707 resource indicators, multiple servers, per-assistant bindings, and per-tool approval.
- **Local-first** — all conversations, settings, and credentials stay on your device; optional WebDAV/S3 sync and local network web access you fully control.
- **Voice** — speech-to-text and text-to-speech across OpenAI, DashScope, Volcengine, MiMo, Step, ElevenLabs, Fish Audio and more.
- **Search & research tools** — built-in search tool with Tavily, Exa, Brave, Zhipu, SearXNG, Jina and other providers; web scraping; knowledge base with local embeddings.
- **Rich rendering** — Markdown, code highlighting, LaTeX, Mermaid diagrams, HTML, and voice/image/video generation support.
- **Workspaces & skills** — sandboxed workspaces, terminal access, and extensible skill packages.
- **No telemetry** — zero analytics, zero crash reporting to third parties, no tracking. Check for updates on your terms.

## Quick Start

1. Download the latest APK from [Releases](https://github.com/ORCHORDS/OrchordsAI/releases) (pick your ABI; `universal` works everywhere).
2. Enable installation from unknown sources and install.
3. Open the app, add a provider under **Settings → AI Provider** with your own API key.
4. Optional: connect MCP tool servers under **Settings → MCP**.

## Building

See [docs/BUILDING.md](docs/BUILDING.md) for full local setup (JDK 17, Android SDK, `./gradlew :app:assembleDebug`).

## Documentation

- [Architecture](docs/ARCHITECTURE.md) — module map and data flow
- [MCP support](docs/MCP.md) · [GitHub MCP](docs/GITHUB_MCP.md) · [Cloudflare MCP](docs/CLOUDFLARE_MCP.md)
- [Security policy](SECURITY.md) · [Security details](docs/SECURITY.md)
- [Releasing](docs/RELEASING.md)

## Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md). Bug reports and pull requests happen on this repository.

## License

OrchestrdsAI is licensed under the [GNU AGPL-3.0](LICENSE).
