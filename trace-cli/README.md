# trace-cli

Records replayable `events.jsonl` fixtures for the `ai` module using real
provider SSE responses. The CLI only records the post-SSE-framing `id`,
`event`, `data` and `retryMillis` fields — never request headers or API keys.

Supported providers:

- `openai-responses`
- `openai-chat`
- `claude`
- `google-generateContent`
- `google-interactions`

## Install

```bash
cd trace-cli
bun install
cp .env.example .env
```

Fill in the keys you need in `.env`:

```dotenv
OPENAI_API_KEY=...
ANTHROPIC_API_KEY=...
GEMINI_API_KEY=...
DEEPSEEK_API_KEY=...
```

`.env` is excluded by the subproject's `.gitignore`. Individual traces can
also point at another environment variable name via `apiKeyEnv`.

## Generating traces

Edit `traces.yml` to select the providers, models and request bodies to
record.

List the parsed cases:

```bash
bun run trace -- traces.yml --list
```

Inspect the redacted request without touching the network:

```bash
bun run trace -- traces.yml --case openai-responses-tool --dry-run
```

Generate one or all traces:

```bash
bun run trace -- traces.yml --case openai-responses-tool
bun run trace -- traces.yml
```

Existing files are not overwritten by default; force re-recording with:

```bash
bun run trace -- traces.yml --case openai-responses-tool --force
```

The CLI writes to a temporary file first and atomically replaces the target
`events.jsonl` only after the request and SSE parsing fully succeed.

## YAML format

```yaml
version: 1
defaults:
  outputRoot: ../ai/src/test/resources/stream-traces/generated
  timeoutMs: 120000
  headers: {}

traces:
  - name: responses-tool
    provider: openai-responses
    model: gpt-5.6
    apiKeyEnv: OPENAI_API_KEY
    # Optional: override the provider's default auth, e.g. Bearer for OpenRouter Anthropic Messages.
    auth:
      header: Authorization
      scheme: Bearer
    baseUrl: https://api.openai.com/v1
    # endpoint can be omitted; it may be a path relative to baseUrl or a full URL.
    endpoint: /responses
    # output can be omitted; defaults to outputRoot/provider/name/events.jsonl.
    output: ../ai/src/test/resources/stream-traces/openai-responses/responses-tool/events.jsonl
    headers: {}
    timeoutMs: 120000
    body:
      store: false
      input:
        - role: user
          content: Call the weather tool for Paris.
      tools:
        - type: function
          name: weather
          description: Get weather for a city.
          parameters:
            type: object
            properties:
              city:
                type: string
            required: [city]
            additionalProperties: false
```

The CLI adds each provider's required auth headers automatically and forces
`stream: true` for OpenAI, Claude and Google Interactions requests.
`google-generateContent` uses `:streamGenerateContent?alt=sse` (the model name
only appears in the URL); `google-interactions` uses `/interactions` and puts
the model name in the request body. Interactions can also omit `model` and
specify the agent directly in `body.agent`.

After generation, create the matching `expected.json` by hand and register the
directory in `StreamTraceReplayTest`.
