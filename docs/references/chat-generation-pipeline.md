# Chat Generation Pipeline

This document describes the complete data flow from the user sending a message
to the finished AI reply, including the core classes and processing stages.

## Core classes

| Class | Responsibility |
|-------|----------------|
| `ChatService` | Entry point and orchestrator; owns all sessions and exposes operations |
| `ConversationSession` | Per-conversation state container (ref counting, generation job, processing state) |
| `GenerationHandler` | Core generation logic; drives the step loop and tool calls |
| `InputMessageTransformer` | Transformation pipeline applied to messages before the API call |
| `OutputMessageTransformer` | Transformation pipeline applied to messages after streamed chunks |

---

## Full pipeline

```
User sends a message
    │
    ▼
ChatService.sendMessage()
    ├── Cancel the previous job (cancel + join)
    ├── finishInterruptedPendingTools()  // complete tool outputs interrupted last time
    ├── preprocessUserInputParts()       // apply assistant regex replacements to user text
    ├── Append UIMessage(USER) to Conversation.messageNodes
    └── handleMessageComplete()
            │
            ▼
        GenerationHandler.generateText()   ← Flow<GenerationChunk>
            │  (step loop, at most maxSteps=256 rounds)
            │
            ├─ [no pending tool] generateInternal()
            │       ├── Build internalMessages
            │       │       ├── System message (system prompt + memory + tool.systemPrompt)
            │       │       ├── limitContext() trims history in tiers per contextMessageLimit
            │       │       └── InputTransformers pipeline
            │       ├── Build TextGenerationParams
            │       └── Call the provider
            │               ├── stream=true → providerImpl.streamText() emits chunk by chunk
            │               └── stream=false → providerImpl.generateText() returns once
            │
            ├─ On every chunk → OutputTransformers.transforms() (real-time)
            │                  → OutputTransformers.visualTransforms() → emit GenerationChunk
            │
            ├─ Generation finished → OutputTransformers.visualTransforms()
            │           → OutputTransformers.onGenerationFinish()
            │           → set message.finishedAt
            │
            ├─ Check the newest message for unexecuted tools
            │       ├── No tool → break (generation done)
            │       ├── Tool needs approval → set ToolApprovalState.Pending
            │       │                       → emit → break (wait for user approval)
            │       └── Tool approved → execute it (see below)
            │
            ├─ Tool execution
            │       ├── Denied  → output {"error": "denied by user"}
            │       ├── Answered → use the answer provided by the user directly
            │       └── Auto / Approved → toolDef.execute(args)
            │               ├── Output over 32KB with shell access → truncate and write to a file
            │               └── CancellationException must propagate (never swallow it)
            │
            └─ Write results back into messages → emit → continue with the next step
                    (tool results are inlined in the ASSISTANT message parts; no TOOL-role messages)

    ▼
onCompletion (flow ended or cancelled)
    ├── cancelLiveUpdateNotification()
    ├── finishReasoning() on all messages (safety net)
    └── If the app is in background → sendGenerationDoneNotification()

    ▼
onSuccess
    ├── saveConversation()
    ├── generateTitle()     (async, uses titleModel)
    └── generateSuggestion() (async, uses suggestionModel)
```

---

## Stage 1: user message preprocessing

**Entry**: `ChatService.preprocessUserInputParts()`

Applies `Assistant.replaceRegexes(scope=USER)` — the regex replacement rules
configured with `AffectScope.USER` — to the user's `UIMessagePart.Text` parts,
to normalize or redact input text. Non-text parts (images, documents) are not
affected.

---

## Stage 2: InputMessage transformer pipeline

**When**: after `generateInternal()` builds `internalMessages`, before sending
to the API.

Transformers run in order (`fold`); each receives the previous output:

| # | Transformer | Purpose |
|---|-------------|---------|
| 1 | `TimeReminderTransformer` | Inject the current time/date into the system message |
| 2 | `PromptInjectionTransformer` | Inject prompts triggered by ModeInjection and Lorebook |
| 3 | `PlaceholderTransformer` | Replace `{{placeholder}}` tokens in messages |
| 4 | `DocumentAsPromptTransformer` | Convert document attachments into injected text |
| 5 | `OcrTransformer` | OCR image parts and append the recognized text |
| 6 | `TemplateTransformer` | Render messages with Pebble templates (time/date/role variables) |
| 7 | `WorkspaceReminderTransformer` | If the conversation is tied to a workspace, inject workspace path hints |

`PromptInjectionTransformer` supports four injection positions:

- `BEFORE_SYSTEM_PROMPT` / `AFTER_SYSTEM_PROMPT` — around the system message
- `TOP_OF_CHAT` — before the first user message
- `BOTTOM_OF_CHAT` — before the last message
- `AT_DEPTH` — N messages back from the newest message

---

## Stage 3: OutputMessage transformer pipeline

Called at three moments:

| Moment | Method | Purpose |
|--------|--------|---------|
| Streamed chunk arrives (real) | `transforms()` | Real transformation, stored with internal messages |
| Streamed chunk arrives (UI) | `visualTransforms()` | Visual-only transformation, not persisted (e.g. streaming think-tag conversion) |
| Generation fully finished | `onGenerationFinish()` | Final post-processing, e.g. writing base64 images to disk |

Current output transformers:

| Transformer | transforms | visualTransform | onGenerationFinish |
|-------------|-----------|------------------|--------------------|
| `ThinkTagTransformer` | — | ✓ (`<think>` → Reasoning part) | ✓ (final extraction) |
| `Base64ImageToLocalFileTransformer` | — | — | ✓ (base64 → local file) |
| `RegexOutputTransformer` | ✓ (assistant OUTPUT regex) | — | — |

---

## Stage 4: tool system

### Tool registration order

`handleMessageComplete()` builds the tool list in this order:

1. **Search tools** (`createSearchTools`) — when `settings.enableWebSearch = true`
2. **Local tools** (`localTools.getTools(assistant.localTools)`) — enabled per assistant:
   - `JavascriptEngine`: run JS snippets
   - `TimeInfo`: current time
   - `Clipboard`: read/write the clipboard
   - `Tts`: text to speech
   - `AskUser`: ask the user a question (requires approval)
   - `ScreenTime`: screen-time stats
3. **Conversation tools** (`createConversationTools`) — query history when `enableRecentChatsReference = true`
4. **Workspace tools** (`createWorkspaceToolsIfReady`) — injected when the workspace shell is ready, incl. `workspace_shell`
5. **Skill tools** (`createSkillTools`) — the assistant's enabled skills
6. **MCP tools** — tools from all connected MCP servers, named `mcp__{serverName}__{toolName}`
7. **Memory tools** (`buildMemoryTools`, built into GenerationHandler) — memory CRUD when `enableMemory = true`

### Tool approval state machine

```
Auto (default)
    │ toolDef.needsApproval() == true
    ▼
Pending ──── user action ────► Approved → execute the tool
                       ──► Denied   → return a denial error
                       ──► Answered → use the user-provided text as the result
```

Approval is triggered by `ChatService.handleToolApproval()`, which updates the
state and calls `handleMessageComplete()` again; GenerationHandler detects the
tool with `canResumeExecution`, skips regeneration and goes straight to tool
execution.

### Tool output truncation

When workspace shell tools are available and a tool output exceeds **32KB**,
the output is truncated:

- The first 4KB stays in the message
- The full output is written to `filesDir/tool_outputs/{toolCallId}.txt`
- A shell read instruction is appended to the message

---

## Stage 5: session lifecycle

`ConversationSession` manages memory with an **atomic reference count**:

- `acquire()` / `release()` — called when a UI page opens/closes
- `refCount == 0 && !isGenerating` — after a 5-second idle timeout, `ChatService.removeSession()` cleans the session up
- `setJob()` — sets the generation job, auto-nulls on completion and triggers the idle check

---

## Stage 6: background notifications

| Notification | Trigger | Channel |
|--------------|---------|---------|
| Live update (ongoing) | Generating while the app is backgrounded | `CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID` |
| Generation done | Generation finished while backgrounded | `CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID` |

The live-update notification content reflects the current generation state:

- Tool running → tool name and input preview
- Reasoning → reasoning text snippet
- Writing the reply → text snippet

---

## Key file paths

```
app/src/main/java/com/orchords/orchordsai/
├── service/
│   ├── ChatService.kt              # orchestration entry point
│   └── ConversationSession.kt      # session state container
└── data/ai/
    ├── GenerationHandler.kt        # core generation logic
    ├── transformers/
    │   ├── Transformer.kt          # interface definitions and extensions
    │   ├── PromptInjectionTransformer.kt
    │   ├── TemplateTransformer.kt
    │   ├── TimeReminderTransformer.kt
    │   ├── ThinkTagTransformer.kt
    │   ├── RegexOutputTransformer.kt
    │   ├── DocumentAsPromptTransformer.kt
    │   ├── OcrTransformer.kt
    │   ├── Base64ImageToLocalFileTransformer.kt
    │   ├── PlaceholderTransformer.kt
    │   └── WorkspaceReminderTransformer.kt
    └── tools/
        ├── SearchTools.kt
        ├── ConversationTools.kt
        ├── WorkspaceTools.kt
        ├── SkillsTools.kt
        ├── MemoryTools.kt
        └── local/
            └── LocalTools.kt       # local tool registry
```
