# Agent Tool Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a lightweight Agent tool runtime so Persona, memory, docs, and environment context are routed through structured model-selected tool calls.

**Architecture:** A new domain engine asks the LLM for strict JSON tool calls, a local executor runs only allowlisted tools, and chat/ADB paths inject tool results into the final model request. Storage remains split: Persona writes go to `personas`, durable facts go to `memories`, and doc/time tools are read-only context.

**Tech Stack:** Kotlin, Jetpack Compose app domain layer, Gson JSON parsing, existing Room repositories, existing OpenAI-compatible provider.

---

### Task 1: Decision Model

**Files:**
- Create: `app/src/main/java/com/memorychat/app/domain/agent/AgentDecisionEngine.kt`
- Test: `app/src/test/java/com/memorychat/app/domain/agent/AgentDecisionEngineTest.kt`

- [ ] Write tests for parsing strict JSON, fenced JSON, unknown tools, temporary response format, and malformed JSON fallback.
- [ ] Implement `AgentDecisionEngine` with a prompt that lists allowed tools and asks the model to decide by semantic meaning.
- [ ] Run targeted tests.

### Task 2: Tool Executor

**Files:**
- Create: `app/src/main/java/com/memorychat/app/domain/agent/AgentToolExecutor.kt`
- Modify: `app/src/main/java/com/memorychat/app/domain/engine/MemoryExtractionSaver.kt`
- Test: `app/src/test/java/com/memorychat/app/domain/agent/AgentToolExecutorTest.kt`

- [ ] Write tests that `update_persona` updates assistant Persona, `set_user_addressing_preference` saves a preference memory, duplicate memory candidates are merged or skipped, and tool failures return nonfatal results.
- [ ] Extract `MemoryExtractionSaver.saveResult(...)` so direct tool memory candidates reuse existing dedup and merge behavior.
- [ ] Implement executor with `get_current_time`, `update_persona`, `save_memory`, and `set_user_addressing_preference`.
- [ ] Run targeted tests.

### Task 3: Chat and ADB Integration

**Files:**
- Modify: `app/src/main/java/com/memorychat/app/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/memorychat/app/AdbInputReceiver.kt`
- Test: existing unit tests plus real-device ADB smoke.

- [ ] Replace the separate Persona pre-check with `AgentDecisionEngine`.
- [ ] Execute tool calls after saving the user message.
- [ ] Inject environment and tool results into the final system prompt.
- [ ] Keep normal chat and memory extraction nonblocking when decision or tools fail.

### Task 4: Delivery

**Files:**
- Modify: `app/build.gradle`
- Modify: `DEVLOG.md`
- Modify: `tools/e2e/run_memorychat_emulator_smoke.ps1`

- [ ] Bump version.
- [ ] Run `.\gradlew.bat test`.
- [ ] Run `.\gradlew.bat assembleDebug`.
- [ ] Install and smoke test on the connected physical Android device with `adb`.
- [ ] Commit the scoped changes.
