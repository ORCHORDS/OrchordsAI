# CLAUDE.md

This file provides guidance to AI agents when working with code in this repository.

## Project Overview

web-ui is the embedded web frontend of the OrchordsAI project, a React
Router 7 single-page application (SPA). Build output is copied to
`../web/src/main/resources/static` by the `copy.ts` script and served as
static files by the Kotlin backend's Ktor server.

## Technology Stack

- **React Router 7** (7.12.0): file-based routing + Vite build (SPA mode: `ssr: false`)
- **React 19** (19.2.4): UI framework
- **TypeScript** (5.9.2): strict typing, fully aligned with the Kotlin backend types
- **Tailwind CSS v4**: atomic CSS framework + CSS-variable theming
- **shadcn/ui** (New York style): component library built on Radix UI
- **Zustand** (5.0.11): lightweight state management (composed slices pattern)
- **ky** (1.14.3): fetch-based HTTP client
- **i18next + react-i18next**: internationalization (en-US)
- **pnpm**: package manager

## Development Commands

```bash
pnpm run dev        # Dev server (HMR + API proxy to localhost:8080)
pnpm run build      # Production build (build + copy to backend)
pnpm run typecheck  # Type checking and generation
pnpm run format     # Code formatting
pnpm run format:check  # Check formatting
pnpm run start      # Production server (runs build/server/index.js)
```

## Architecture

### Directory Structure

```
web-ui/app/
├── routes/                   # React Router 7 file routes
│   ├── home.tsx             # / route (re-exports conversations.tsx)
│   ├── c.$id.tsx            # /c/:id route (re-exports conversations.tsx)
│   └── conversations.tsx     # main conversation page (650+ lines)
├── components/               # component library
│   ├── ui/                   # shadcn/ui base components (36+)
│   ├── message/              # message components (chat-message, parts/)
│   ├── markdown/             # markdown rendering (markdown.tsx + code-block.tsx)
│   ├── input/                # input components (chat-input, model-list, pickers)
│   ├── workbench/            # code execution workbench
│   ├── extended/             # extended components (conversation, infinite-scroll-area)
│   └── conversation-sidebar.tsx      # conversation list sidebar
├── stores/                   # Zustand state (composed slices)
│   ├── app-store.ts          # main store (composes all slices)
│   ├── settings.ts           # exports useSettingsStore
│   ├── chat-input.ts         # exports useChatInputStore
│   ├── slices/
│   │   ├── types.ts          # store type definitions
│   │   ├── settings-slice.ts # settings slice (updated from SSE)
│   │   └── chat-input-slice.ts # chat input slice (per-conversation drafts)
│   └── hooks/
│       └── use-settings-subscription.ts # subscribe to the settings SSE stream
├── hooks/                    # custom React hooks
│   ├── use-conversation-list.ts  # conversation list (pagination / live updates)
│   ├── use-current-assistant.ts  # current assistant info
│   ├── use-current-model.ts      # current model info
│   ├── use-mobile.ts             # mobile detection
│   └── use-picker-popover.ts     # picker popover management
├── services/                 # API service layer
│   └── api.ts                # ky HTTP client + SSE implementation
├── types/                    # TypeScript types (aligned with Kotlin)
│   ├── helpers.ts            # type guards and utilities
│   └── index.ts              # unified exports
├── lib/                      # utilities
│   ├── display.ts            # display-name formatting
│   ├── files.ts              # file URL conversion
│   └── error.ts              # error handling
├── locales/                  # i18n language files (namespace organized)
│   └── en-US/                # English
├── assets/                   # static assets
├── i18n.ts                   # i18next configuration
├── root.tsx                  # root layout (calls useSettingsSubscription)
├── routes.ts                 # route configuration (type-safe)
└── app.css                   # global styles (Tailwind + CSS variables)
```

### Key Concepts

#### Type System (fully aligned with the Kotlin backend)

All types under `app/types/` correspond strictly to the Kotlin backend.
When updating a type, update both sides!

#### Message Parts

Messages are composed of multiple `UIMessagePart`s (a union type) supporting
mixed content. Each part is rendered by its own component under
`app/components/message/parts/`.

#### Message Branching

`MessageNode` supports conversation branching; each node holds multiple
alternative messages with a `selectIndex`. Users can regenerate to create a
new branch, switch between branches, and edit messages to fork.

#### State Management (Zustand slices)

All slices share one store, composed in `app-store.ts`:

- **Settings slice**: live-updated from the backend SSE stream
  (`/api/settings/stream`); contains assistants, models, providers and
  display settings; subscribed in `root.tsx` via `useSettingsSubscription`.
- **Chat input slice**: per-conversation drafts, text + multimedia
  attachments, and source parts kept for diffing when editing.

#### API Client (app/services/api.ts)

A `ky`-based HTTP client supporting REST and SSE:

- Default prefix `/api` (proxied to `http://localhost:8080` in development)
- 30 s timeout, automatic error conversion to `ApiError`
- Manual SSE implementation supporting event types and multi-line data
- `api.postMultipart<T>(url, formData)` for file uploads

#### Routing (React Router 7)

- SPA mode (`react-router.config.ts: ssr: false`)
- Automatic type generation for file routes (`.react-router/types/`)
- The real UI lives in `routes/conversations.tsx`; other route files re-export
- Error boundaries and loading placeholders live in `root.tsx`

#### Internationalization (i18next)

- Supported language: en-US
- Namespace organization (`app/i18n.ts`): `common` (general UI), `input`
  (chat input, model/file pickers), `markdown` (code blocks, copy buttons),
  `message` (message parts, tool calls, reasoning steps)
- Detection order: localStorage > browser language > en-US
- Add new keys to the matching namespace file, then use
  `t("namespace:key")` or `t("key")` for the default namespace

### Build Pipeline

Two stages:

1. `react-router build` → `build/client/` (static assets) and
   `build/server/` (SSR code, unused in SPA mode)
2. `copy.ts` copies `build/client/` to `../web/src/main/resources/static/`
   where the Kotlin Ktor server serves it

Full build: `pnpm run build` (runs both steps).

## Development Guidelines

- Import shadcn/ui base components from `~/components/ui/`; icons from
  `lucide-react`; path alias `~` → `app/`
- Read Zustand state via selector hooks to avoid unnecessary re-renders
- Use `useState` for UI-local state

### Markdown rendering

`app/components/markdown/markdown.tsx` provides enhanced rendering:

- LaTeX math: inline `\(...\)` and block `\[...\]` (converted to `$...$` / `$$...$$`)
- GFM: tables, strikethrough, task lists
- Syntax highlighting via Shiki with copy button
- `<think>` tags rendered as a quote block
- `[citation,domain](id)` link handling
- Light/dark theme adaptation

Preprocessing: locate code blocks first (to avoid replacing inside them),
then replace LaTeX syntax and `<think>` tags, then hand off to
`react-markdown` + rehype plugins.

### Message rendering

Dispatcher pattern: `ChatMessage` → `MessageParts` (iterates parts) →
`MessagePart` (dispatches on `part.type`) → part components
(`ReasoningPart` with expandable reasoning, `ToolPart` with tool call display
and approval, …).

Adding a new part type:

1. Add the type in `app/types/parts.ts`
2. Add the matching Kotlin type (`ai/.../ui/Message.kt`)
3. Create the component in `app/components/message/parts/`
4. Add dispatch logic in `app/components/message/message-part.tsx`
5. Add a type guard in `app/types/helpers.ts`

### File URLs

`app/lib/files.ts` converts URLs: `data:` URLs and `http(s)` pass through;
relative paths become `/api/files/path/...`. Used by image, video, audio and
document parts.

### Type safety workflow

- `pnpm run typecheck` runs React Router type generation + tsc
- Any change under `app/types/` must be mirrored in the Kotlin backend
  (see the type mapping in `app/types/`)
- Checklist for type changes: update TS types, update Kotlin types, run
  typecheck on both sides, test serialization, update type guards in
  `app/types/helpers.ts`

## Key Flows

### App startup

`root.tsx` renders → `useSettingsSubscription()` subscribes to
`/api/settings/stream` (SSE) → backend pushes the Settings object →
`useSettingsStore.setSettings()` updates global state → all components
react (assistants, models, providers, …).

### Conversation loading

User selects/creates a conversation → `GET /api/conversations/:id` (initial
snapshot, `ConversationDto` with the full message tree) → SSE connection
`GET /api/conversations/:id/stream` → backend pushes
`ConversationSnapshotEventDto` (full snapshot) and
`ConversationNodeUpdateEventDto` (incremental) → UI renders live.

### Message sending

User taps send → `useChatInputStore.getSubmitParts(conversationId)` builds
the parts → `POST /api/conversations/:id/send` → backend processes and starts
generation → SSE `node_update` events stream per token/part → UI renders
streaming → on completion, `useChatInputStore.clearDraft(conversationId)`.

### `useConversationList`

Conversation list hook with pagination and live updates: `conversations`,
`activeId`/`setActiveId`, `loading`, `error`, `hasMore`/`loadMore`,
`refreshList`, `updateConversationSummary`, route-bound `routeId`.
Auto-refreshes on assistant switches, supports infinite scroll, SSE-driven
incremental updates, pinned-first ordering by update time.

## Deployment

Build output flow:

```
pnpm run build
    ↓
web-ui/build/client/
    ↓ (copy.ts)
../web/src/main/resources/static/
    ↓ (Ktor static file routes)
User opens http://localhost:8080/
```

## Backend API Endpoints

All endpoints are defined in the `web` module's Kotlin code:

- `GET /api/settings/stream` — settings SSE stream
- `GET /api/conversations` — conversation list
- `GET /api/conversations/:id` — fetch a conversation
- `GET /api/conversations/:id/stream` — conversation SSE stream
- `POST /api/conversations/:id/send` — send a message
- `POST /api/files/upload` — file uploads
- `GET /api/files/path/*` — file access

## Performance Notes

- Code splitting: React Router 7 splits automatically per route
- Tree shaking: Tailwind v4 + Vite remove unused styles and code
- Selective subscriptions: Zustand selectors avoid re-renders
- Virtualized scrolling: conversation list uses `react-infinite-scroll-component`
- SSE streaming: no API polling, data is pushed in real time

## Troubleshooting

### Dev server fails to start

Check whether port 5173 is occupied:

```bash
lsof -ti:5173 | xargs kill -9  # kill the occupying process
```

### API requests fail (development)

Ensure the Kotlin backend is running on port 8080 (start it from the Kotlin
project directory).

### Type errors

Run type generation and checking; inspect generated types under
`.react-router/types/`.

### Build failures

Clear caches and reinstall dependencies.
