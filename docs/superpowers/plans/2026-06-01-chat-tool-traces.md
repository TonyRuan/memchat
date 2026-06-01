# Chat Tool Traces Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show lightweight animated tool-call status in chat bubbles and keep a collapsible completed trace after the assistant answer.

**Architecture:** Add a small domain model for tool traces and MiMo search metadata, parse real `annotations` / `usage.web_search_usage` from provider responses where available, expose active/completed traces from `ChatViewModel`, and render them inside assistant bubbles. The UI must never invent citation counts; unknown metadata falls back to conservative labels such as `已联网搜索`.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Gson, existing `OpenAICompatibleProvider`, existing chat ViewModel flow.

---

### Task 1: Tool Trace Domain Model

**Files:**
- Create: `app/src/main/java/com/memorychat/app/domain/model/ToolTrace.kt`
- Test: `app/src/test/java/com/memorychat/app/domain/model/ToolTraceTest.kt`

- [ ] Write failing tests for search summaries with exact metadata, fallback search summary, memory recall summary, and completed/running labels.
- [ ] Implement `ToolTrace`, `ToolTraceKind`, `ToolTraceStatus`, `SearchCitation`, and `WebSearchUsage`.
- [ ] Run targeted test.

### Task 2: MiMo Metadata Parsing

**Files:**
- Modify: `app/src/main/java/com/memorychat/app/domain/model/ChatModels.kt`
- Modify: `app/src/main/java/com/memorychat/app/domain/provider/LlmProvider.kt`
- Test: `app/src/test/java/com/memorychat/app/domain/provider/OpenAICompatibleProviderTest.kt`

- [ ] Write failing tests for non-streaming `message.annotations`, non-streaming `usage.web_search_usage`, and streaming annotation chunks.
- [ ] Extend `ChatResponse` and `ChatChunk` with optional search metadata.
- [ ] Parse `url_citation` annotations and `web_search_usage` without failing normal responses.
- [ ] Run targeted test.

### Task 3: Chat State Integration

**Files:**
- Modify: `app/src/main/java/com/memorychat/app/ui/chat/ChatViewModel.kt`
- Test: focused existing tests where possible.

- [ ] Expose `activeToolTrace` and `completedToolTraces`.
- [ ] Set a running trace during Agent decision and concrete tool execution.
- [ ] Update the active search trace from streaming metadata when available.
- [ ] Attach the completed trace to the assistant message id after final content is saved.

### Task 4: Compose Bubble UI

**Files:**
- Create: `app/src/main/java/com/memorychat/app/ui/chat/ToolTraceView.kt`
- Modify: `app/src/main/java/com/memorychat/app/ui/chat/ChatScreen.kt`

- [ ] Render an animated running trace in the temporary assistant bubble.
- [ ] Render a collapsed completed trace above assistant Markdown content.
- [ ] Expand to show up to five source titles or conservative detail text.
- [ ] Keep user bubbles unchanged.

### Task 5: Delivery

**Files:**
- Modify: `app/build.gradle`
- Modify: `DEVLOG.md`
- Modify: `tools/e2e/run_memorychat_emulator_smoke.ps1`

- [ ] Bump version.
- [ ] Run `.\gradlew.bat test`.
- [ ] Run `.\gradlew.bat assembleDebug`.
- [ ] Install and smoke test on the connected Android device when practical.
- [ ] Commit only scoped changes.
