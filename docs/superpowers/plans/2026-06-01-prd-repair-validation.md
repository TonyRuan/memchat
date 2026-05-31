# PRD Repair And Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring MemoryChat from the current prototype state to the PRD v1.1 P0 target: reliable long-term memory, persona separation, context compression, import/export, and real APK-level validation.

**Architecture:** Treat chat as the critical path and keep memory/context work failure-isolated: a chat reply must persist even if memory extraction fails. Add deterministic repository/use-case tests first, then wire fake LLM tests, then real model and emulator validation as release gates. Store debug traces persistently so PRD behavior can be inspected after navigation or ADB runs.

**Tech Stack:** Android, Kotlin, Jetpack Compose, Room, DataStore, OkHttp, JUnit, AndroidX test, Compose UI test, ADB, OpenAI-compatible chat completions API.

---

## Scope And Delivery Rules

- App-version bump is required only for code/config changes that affect app behavior, debug flow, or APK output.
- Pure plans, audits, or read-only analysis should be committed without bumping `versionCode` / `versionName`.
- Every code task below should end with `.\gradlew.bat test`, `.\gradlew.bat assembleDebug`, `DEVLOG.md`, and a scoped Git commit.
- Broad verification work should use subagents for independent domains: JVM tests, Android instrumentation, real-model E2E, and UI/manual smoke.
- Real API validation must use the local debug default API key from `local.properties`; do not print or commit the key.

## Target PRD Gates

- `TC-P0-001` to `TC-P0-004`: startup, settings, first chat, API error.
- `TC-MEM-001` to `TC-MEM-008`: automatic memory extraction and conservative filtering.
- `TC-CONFLICT-001` to `TC-CONFLICT-004`: memory update, edit protection, tombstone prevention.
- `TC-RECALL-001` to `TC-RECALL-005`: cross-session recall and memory switches.
- `TC-PERSONA-001` to `TC-PERSONA-004`: persona separation and editable persona behavior.
- `TC-CTX-001` to `TC-CTX-007`: rolling summary and context-length retry.
- `TC-EXPORT-001` to `TC-IMPORT-003`: import/export validity and recovery.
- `E2E-001` to `E2E-003`: full memory loop, long conversation compression, export/import recovery.

---

### Task 1: Build The Test And E2E Harness

**Files:**
- Modify: `app/build.gradle`
- Create: `app/src/androidTest/java/com/memorychat/app/MemoryChatSmokeTest.kt`
- Create: `app/src/test/java/com/memorychat/app/testutil/FakeLlmProvider.kt`
- Create: `tools/e2e/run_memorychat_emulator_smoke.ps1`
- Modify: `DEVLOG.md`

- [ ] **Step 1: Add Android and coroutine test dependencies**

Add these dependencies to `app/build.gradle`:

```gradle
androidTestImplementation 'androidx.test.ext:junit:1.2.1'
androidTestImplementation 'androidx.test:runner:1.6.2'
androidTestImplementation 'androidx.test:rules:1.6.1'
androidTestImplementation 'androidx.compose.ui:ui-test-junit4'
androidTestImplementation 'androidx.test.uiautomator:uiautomator:2.3.0'
debugImplementation 'androidx.compose.ui:ui-test-manifest'
testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0'
testImplementation 'com.squareup.okhttp3:mockwebserver:4.12.0'
```

- [ ] **Step 2: Add a minimal launch instrumentation test**

Create `MemoryChatSmokeTest.kt`:

```kotlin
package com.memorychat.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertExists
import org.junit.Rule
import org.junit.Test

class MemoryChatSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun firstLaunchShowsConversationList() {
        compose.onNodeWithText("MemoryChat", substring = true).assertExists()
    }
}
```

- [ ] **Step 3: Add fake LLM provider for deterministic JVM tests**

Create `FakeLlmProvider.kt`:

```kotlin
package com.memorychat.app.testutil

import com.memorychat.app.domain.model.ChatChunk
import com.memorychat.app.domain.model.ChatRequest
import com.memorychat.app.domain.model.ChatResponse
import com.memorychat.app.domain.provider.LlmProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeLlmProvider(
    private val completeResponses: ArrayDeque<String> = ArrayDeque(),
    private val streamResponses: ArrayDeque<String> = ArrayDeque()
) : LlmProvider {
    override fun streamChat(request: ChatRequest): Flow<ChatChunk> = flow {
        val text = streamResponses.removeFirstOrNull() ?: ""
        emit(ChatChunk(text, done = false))
        emit(ChatChunk("", done = true))
    }

    override suspend fun complete(request: ChatRequest): ChatResponse {
        return ChatResponse(completeResponses.removeFirstOrNull() ?: "")
    }
}
```

- [ ] **Step 4: Add emulator smoke script skeleton**

Create `tools/e2e/run_memorychat_emulator_smoke.ps1`:

```powershell
param(
  [string]$Apk = "app/build/outputs/apk/debug/MemoryChat-v1.0.34-debug.apk",
  [string]$Package = "com.memorychat.app"
)

$ErrorActionPreference = "Stop"
adb devices
adb install -r $Apk
adb shell am start -n "$Package/.MainActivity"

Write-Host "Create a conversation in the app UI, then press Enter."
Read-Host

adb exec-out run-as $Package cat databases/memorychat.db > .\memorychat-device.db
$ConvId = python -c "import sqlite3; con=sqlite3.connect('memorychat-device.db'); rows=con.execute('select id from conversations order by updatedAt desc limit 1').fetchall(); print(rows[0][0] if rows else '')"
if (-not $ConvId) { throw "No conversation id found. Create a conversation before running the smoke." }

adb logcat -c
adb shell am broadcast -n "$Package/.AdbInputReceiver" -a com.memorychat.app.SEND_MESSAGE --es conv_id "$ConvId" --es msg "我正在做永久记忆 Android APP，第一阶段优先调好记忆系统。"
Start-Sleep -Seconds 25
adb logcat -d -s AdbInput ChatVM LlmProvider MemoryEngine
```

- [ ] **Step 5: Verify harness**

Run:

```powershell
.\gradlew.bat test
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat assembleDebug
```

Expected: JVM tests pass; instrumentation test either passes on a running emulator or fails with a clear “no connected devices” environment blocker.

- [ ] **Step 6: Commit**

```powershell
git add app/build.gradle app/src/androidTest app/src/test/java/com/memorychat/app/testutil tools/e2e DEVLOG.md
git commit -m "Add PRD validation harness"
```

---

### Task 2: Make Chat Persist Errors And Auto-Extract Memories

**Files:**
- Modify: `app/src/main/java/com/memorychat/app/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/memorychat/app/domain/provider/LlmProvider.kt`
- Test: `app/src/test/java/com/memorychat/app/ui/chat/ChatViewModelTest.kt`

- [ ] **Step 1: Write tests for chat failure isolation**

Test expectations:

```kotlin
@Test
fun streamErrorPersistsAssistantErrorMessage() = runTest {
    // Given provider throws before any assistant content.
    // When sendMessage("hello") runs.
    // Then DB contains user message and assistant "Error: ..." message.
}
```

- [ ] **Step 2: Write tests for automatic memory extraction trigger**

Test expectations:

```kotlin
@Test
fun assistantDoneTriggersMemoryExtractionWhenGenerateMemoryEnabled() = runTest {
    // Given conversation.generateMemory = true and fake extraction response returns one project memory.
    // When assistant stream completes.
    // Then memory repository contains that project memory without manual button click.
}
```

- [ ] **Step 3: Implement minimal production change**

Implementation direction:

- In `sendMessage()`, after assistant message is saved, call a private suspend function that runs extraction for the latest turn.
- Wrap extraction in `try/catch`; log failure but do not fail chat.
- Save error assistant messages to DB, not only UI state.
- Make `complete()` surface structured failure or explicit empty response reason instead of silently returning empty content for all failures.

- [ ] **Step 4: Verify**

Run:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

- [ ] **Step 5: Real model smoke**

Run the debug APK with the local default API key and send:

```text
我正在做一个拥有永久记忆的 Android 聊天 APP，第一阶段优先调好记忆系统。
```

Expected: assistant replies, memory extraction runs automatically, memory center shows a `project` memory.

---

### Task 3: Enforce Memory Ownership, Tombstones, And Source Traceability

**Files:**
- Modify: `app/src/main/java/com/memorychat/app/data/repository/Repositories.kt`
- Modify: `app/src/main/java/com/memorychat/app/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/memorychat/app/ui/memory/MemoryCenterScreen.kt`
- Test: `app/src/test/java/com/memorychat/app/data/repository/RepositoriesTest.kt`
- Test: `app/src/test/java/com/memorychat/app/domain/engine/MemoryEngineTest.kt`

- [ ] **Step 1: Add tests for user-edited protection**

Expected: an extraction update targeting `userEdited=true` memory is skipped and logged as discarded.

- [ ] **Step 2: Add tests for delete/disable tombstones**

Expected:

- Deleted memory creates tombstone.
- Disabled memory does not participate in recall.
- Tombstoned content with same type cannot be reinserted automatically.
- Different type with same text is handled deliberately and covered by a test.

- [ ] **Step 3: Save `sourceMessageIds`**

When inserting new extracted memories, persist the IDs from the source turn. If model output omits IDs, attach the current user and assistant message IDs.

- [ ] **Step 4: Verify source UI**

Memory center should show source count and support a clear next action for opening or previewing source messages.

---

### Task 4: Add Persistent Debug Trace

**Files:**
- Modify: `app/src/main/java/com/memorychat/app/data/local/db/entity/Entities.kt`
- Modify: `app/src/main/java/com/memorychat/app/data/local/db/AppDatabase.kt`
- Create: `app/src/main/java/com/memorychat/app/data/local/db/dao/DebugTraceDao.kt`
- Create: `app/src/main/java/com/memorychat/app/data/repository/DebugTraceRepository.kt`
- Modify: `app/src/main/java/com/memorychat/app/ui/debug/DebugScreen.kt`

- [ ] **Step 1: Add `debug_traces` table**

Fields:

```text
id, conversation_id, user_message_id, assistant_message_id, persona_id,
recall_json, injected_prompt, extraction_raw_output, extracted_memory_ids,
discarded_candidates_json, compression_summary_id, error, created_at
```

- [ ] **Step 2: Write traces from ChatViewModel and ADB path**

Persist recall reasons, injected memories, extraction raw output, saved memories, discarded candidates, and errors.

- [ ] **Step 3: Update DebugScreen**

Read latest trace from repository instead of a separate `ChatViewModel` instance.

- [ ] **Step 4: Verify**

Manual flow: send a message, leave chat, open Debug. Expected: latest recall and extraction details are still visible.

---

### Task 5: Implement Rolling Context Compression

**Files:**
- Modify: `app/src/main/java/com/memorychat/app/data/local/db/entity/Entities.kt`
- Modify: `app/src/main/java/com/memorychat/app/data/local/db/AppDatabase.kt`
- Create: `app/src/main/java/com/memorychat/app/data/local/db/dao/ConversationSummaryDao.kt`
- Create: `app/src/main/java/com/memorychat/app/data/repository/ConversationSummaryRepository.kt`
- Create: `app/src/main/java/com/memorychat/app/domain/engine/ContextCompressor.kt`
- Modify: `app/src/main/java/com/memorychat/app/ui/chat/ChatViewModel.kt`
- Modify: `app/src/main/java/com/memorychat/app/ui/debug/DebugScreen.kt`

- [ ] **Step 1: Add schema**

Add `conversation_summaries` and message compression markers:

```kotlin
val compressedInSummaryId: String? = null
val deleted: Int = 0
```

- [ ] **Step 2: Add token-budget approximation**

Implement conservative character-based estimation first, with one function that can be swapped for tokenizer logic later:

```kotlin
fun estimateTokens(text: String): Int = (text.length / 3).coerceAtLeast(1)
```

- [ ] **Step 3: Build request context**

The request should contain:

- system prompt with persona and recalled long-term memories;
- current rolling summary if present;
- recent raw messages under budget;
- current user input uncompressed.

- [ ] **Step 4: Compress old messages before over-budget calls**

Use PRD compression prompt, update the current rolling summary, and mark included messages with `compressedInSummaryId`.

- [ ] **Step 5: Retry once after `context_length_exceeded`**

If provider returns/throws a context-length error, compress immediately and retry once. Persist this in debug trace.

- [ ] **Step 6: Verify**

Run a fake LLM test that forces over-budget and one that forces `context_length_exceeded`.

---

### Task 6: Complete Persona Center And Settings

**Files:**
- Create: `app/src/main/java/com/memorychat/app/ui/persona/PersonaCenterScreen.kt`
- Modify: `app/src/main/java/com/memorychat/app/ui/Navigation.kt`
- Modify: `app/src/main/java/com/memorychat/app/ui/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/memorychat/app/data/repository/Repositories.kt`

- [ ] **Step 1: Add persona list/edit/default UI**

Persona Center must support create, edit, delete, set default, and visible current default.

- [ ] **Step 2: Add persona import UI**

Settings should support import `personas.json` as well as export.

- [ ] **Step 3: Add missing settings controls**

Expose `maxTokens`, context-window budget, default persona selection, default use memory, and default generate memory.

- [ ] **Step 4: Verify persona separation**

Real model smoke: change persona tone, ask the same memory-backed question, verify style changes while `memories` content remains unchanged.

---

### Task 7: Real API And Emulator PRD Acceptance

**Files:**
- Create: `docs/validation/2026-06-01-real-model-e2e.md`
- Modify: `tools/e2e/run_memorychat_emulator_smoke.ps1`

- [ ] **Step 1: Prepare emulator**

Commands:

```powershell
.\gradlew.bat assembleDebug
adb devices
adb install -r app/build/outputs/apk/debug/MemoryChat-v<version>-debug.apk
adb shell pm clear com.memorychat.app
adb shell monkey -p com.memorychat.app -c android.intent.category.LAUNCHER 1
```

- [ ] **Step 2: Validate first real chat**

Create a conversation in the UI first. Until a debug-only create-conversation command exists, fetch the latest conversation id from the app database:

```powershell
adb exec-out run-as com.memorychat.app cat databases/memorychat.db > .\memorychat-device.db
python -c "import sqlite3; con=sqlite3.connect('memorychat-device.db'); print(con.execute('select id,title,personaId,updatedAt from conversations order by updatedAt desc limit 5').fetchall())"
```

Then use either UI input or ADB broadcast. The existing receiver expects `conv_id` and `msg` extras:

```powershell
adb shell am broadcast -n com.memorychat.app/.AdbInputReceiver -a com.memorychat.app.SEND_MESSAGE --es conv_id "<conversation-id>" --es msg "我正在做一个拥有永久记忆的 Android 聊天 APP，第一阶段优先调好记忆系统。"
adb logcat -d -s AdbInput ChatVM LlmProvider MemoryEngine
```

Expected:

- API request succeeds using local debug default key.
- Assistant reply is saved.
- Memory extraction runs automatically.
- Debug trace records recall/extraction.
- The current ADB path is only a smoke path until it validates conversation existence and honors `useMemory` / `generateMemory`.

- [ ] **Step 3: Validate cross-session recall**

Create a new session and ask:

```text
我这个 APP 第一阶段重点是什么？
```

Expected: answer says first phase prioritizes memory system, without user restating it.

- [ ] **Step 4: Validate context compression**

Send enough turns to exceed the configured budget or use a small debug budget. Expected: rolling summary created, old raw messages preserved, Debug page shows compressed message IDs.

- [ ] **Step 5: Validate export/import recovery**

Export `memories.json`, clear app data, import it, ask the recall question again. Expected: imported memory is available and answer remains correct.

- [ ] **Step 6: Record evidence**

Write `docs/validation/2026-06-01-real-model-e2e.md` with:

- APK path and version.
- Device/emulator id.
- Commands run.
- Redacted log snippets.
- Pass/fail table mapped to PRD test cases.
- Known residual risks.

---

## Recommended Execution Order

1. Task 1: validation harness.
2. Task 2: chat persistence and auto extraction.
3. Task 3: memory ownership/tombstone/source traceability.
4. Task 4: persistent debug trace.
5. Task 5: rolling context compression.
6. Task 6: Persona Center and settings completeness.
7. Task 7: real API and emulator acceptance.

Do not start broad UI polish until Tasks 1-5 are passing. The product risk is currently in memory correctness, traceability, and context continuity, not visual layout.
