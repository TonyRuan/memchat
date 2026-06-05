# Chat Tool Traces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show lightweight animated tool-call status in chat bubbles and keep a collapsible completed trace after the assistant answer.

**Architecture:** Add a small domain model for tool traces and MiMo search metadata, parse real `annotations` / `usage.web_search_usage` from provider responses where available, expose active/completed traces from `ChatViewModel`, and render them inside assistant bubbles. The UI must never invent citation counts; unknown metadata falls back to conservative labels such as `已联网搜索`.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Gson, existing `OpenAICompatibleProvider`, existing chat ViewModel flow.

**Status:** Implemented through `v1.0.61`, then extended by structured applied Agent actions and direct Persona acknowledgements through `v1.0.62`.

---

### Task 1: Tool Trace Domain Model

**Files:**
- Create: `app/src/main/java/com/memorychat/app/domain/model/ToolTrace.kt`
- Test: `app/src/test/java/com/memorychat/app/domain/model/ToolTraceTest.kt`

- [x] Write failing tests for search summaries with exact metadata, fallback search summary, memory recall summary, and completed/running labels.
- [x] Implement `ToolTrace`, `ToolTraceKind`, `ToolTraceStatus`, `SearchCitation`, and `WebSearchUsage`.
- [x] Run targeted test.

### Task 2: MiMo Metadata Parsing

**Files:**
- Modify: `app/src/main/java/com/memorychat/app/domain/model/ChatModels.kt`
- Modify: `app/src/main/java/com/memorychat/app/domain/provider/LlmProvider.kt`
- Test: `app/src/test/java/com/memorychat/app/domain/provider/OpenAICompatibleProviderTest.kt`

- [x] Write failing tests for non-streaming `message.annotations`, non-streaming `usage.web_search_usage`, and streaming annotation chunks.
- [x] Extend `ChatResponse` and `ChatChunk` with optional search metadata.
- [x] Parse `url_citation` annotations and `web_search_usage` without failing normal responses.
- [x] Run targeted test.

### Task 3: Chat State Integration

**Files:**
- Modify: `app/src/main/java/com/memorychat/app/ui/chat/ChatViewModel.kt`
- Test: focused existing tests where possible.

- [x] Expose `activeToolTrace` and `completedToolTraces`.
- [x] Set a running trace during Agent decision and concrete tool execution.
- [x] Update the active search trace from streaming metadata when available.
- [x] Attach the completed trace to the assistant message id after final content is saved.

### Task 4: Compose Bubble UI

**Files:**
- Create: `app/src/main/java/com/memorychat/app/ui/chat/ToolTraceView.kt`
- Modify: `app/src/main/java/com/memorychat/app/ui/chat/ChatScreen.kt`

- [x] Render an animated running trace in the temporary assistant bubble.
- [x] Render a collapsed completed trace above assistant Markdown content.
- [x] Expand to show up to five source titles or conservative detail text.
- [x] Keep user bubbles unchanged.

### Task 5: Delivery

**Files:**
- Modify: `app/build.gradle`
- Modify: `DEVLOG.md`
- Modify: `tools/e2e/run_memorychat_emulator_smoke.ps1`

- [x] Bump version.
- [x] Run `.\gradlew.bat test`.
- [x] Run `.\gradlew.bat assembleDebug`.
- [x] Install and smoke test on the connected Android device when practical.
- [x] Commit only scoped changes.
