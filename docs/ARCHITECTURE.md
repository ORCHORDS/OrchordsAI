# Architecture

OrchordsAI is a modular Android application (Kotlin, Jetpack Compose, Room,
DataStore, WorkManager, Koin, Ktor).

## Module map

| Module | Purpose |
| --- | --- |
| `:app` | Application, UI (Compose), services, DI wiring, Room database |
| `:ai` | Provider-agnostic AI client: OpenAI (Chat Completions + Responses), Claude, Gemini, Ollama-compatible, image/video generation, streaming |
| `:common` | Shared utilities, HTTP helpers, UI-agnostic primitives |
| `:oauth` | OAuth loopback callback server and authorization primitives |
| `:search` | Search service abstraction (Tavily, Exa, Brave, Zhipu, SearXNG, Jina, …) |
| `:speech` | ASR / TTS provider controllers |
| `:videogen` | Video generation providers |
| `:workspace` | Sandboxed workspaces, PTY/terminal, rootfs management |
| `:highlight` | Syntax highlighter engine (tree-sitter based) |
| `:document` | Document parsing (PDF, Office, …) |
| `:material3` | Material 3 component extensions |
| `:web` | Embedded web server exposing the app over the local network |
| `web-ui/` | React web client bundled into `:web` |
| `build-logic/` | Gradle convention plugins |
| `trace-cli/` | Standalone tracing CLI for provider debugging |
| `locale-tui/` | Terminal helper for translation workflows |

## Data flow

1. **UI** (`app/ui`) renders conversations from `ConversationRepository`.
2. **Generation** (`app/service/ChatService` + `data/ai/GenerationHandler`)
   builds provider-agnostic `Conversation` models, applies transformers
   (prompt injection, compression, OCR, templating), then dispatches through
   `ai/` provider clients.
3. **Streaming** responses flow back through state updates, tool calls are
   executed (built-in tools, MCP tools, workspace tools), and everything is
   persisted to Room by the DAO layer.
4. **Settings** live in DataStore (`data/datastore`), including provider
   credentials, network settings, and feature flags.
5. **MCP** (`data/ai/mcp`) manages external tool servers: Streamable HTTP
   transport with SSE fallback, OAuth 2.1 + Dynamic Client Registration,
   per-tool approval, and per-assistant bindings.

See [references/chat-generation-pipeline.md](references/chat-generation-pipeline.md)
for the detailed generation pipeline walkthrough.
