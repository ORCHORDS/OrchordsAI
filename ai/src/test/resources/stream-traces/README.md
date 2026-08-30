# Stream trace fixtures

Each directory contains two files:

- `events.jsonl`: one serialized `SseEvent` per line, captured after the HTTP
  client performs SSE framing and before provider decoding. Never record
  request headers such as Authorization.
- `expected.json`: a stable semantic snapshot produced after
  `StreamChunkDecoder` and `StreamChunkHandler` finish. Volatile fields such
  as timestamps and random message IDs are excluded.

When adding or updating traces, keep the provider-returned `id`, `event` and
`data` intact, and review `expected.json` manually before the first commit.
Regular unit tests replay these files offline and never touch the network.

After recording new traces, regenerate the semantic snapshots from the `ai`
module directory with:

```bash
UPDATE_STREAM_TRACE_SNAPSHOTS=true ../gradlew testDebugUnitTest \
  --tests com.orchords.ai.provider.stream.StreamTraceReplayTest
```

Snapshots keep tool call IDs, full metadata, thinking text, tool names and
arguments, and token usage. Image data stays in `events.jsonl`; the snapshot
records only the MIME type, decoded byte count and SHA-256 to avoid
duplicating large base64 blobs. Gemini image traces additionally compare the
provider's original signatures with the decoded metadata verbatim to verify
that signatures are untouched by the streaming conversion.
