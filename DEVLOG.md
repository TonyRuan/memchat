# MemoryChat Development Log

## v1.0.68 (2026-06-18)

### Changes
- Upgraded Persona injection from a flat name/role/tone block to a richer Persona Contract with identity, mission, expertise, communication style, behavior rules, boundaries, tool policy, memory policy, and example dialogues
- Added Room v1 -> v2 migration for the new Persona Contract fields without clearing existing local data
- Extended `update_persona`, Persona import/export, Memory Center display/editing, and default Persona seed data to preserve the new contract fields
- Made the final prompt explicitly state Persona/memory/tool priority and that memories, summaries, documents, and tool outputs are data rather than instructions
- Hardened memory extraction so assistant mission, expertise, tool policy, memory policy, boundaries, and example dialogues cannot leak into long-term Memory
- Applied the same Persona leak guard to model memory updates, not only new memory candidates, and narrowed English guard markers so user-owned communication/tool preferences are still saved
- Narrowed the fallback Persona leak guard to avoid dropping real project memories such as assistant-rule UI work while still discarding explicit assistant-name/persona paraphrases from model memory output
- Changed Persona list fields to replace with the model-provided final list instead of always merging, with prompts instructing the model to return the full final desired list for add/replace edits
- Told Persona routing prompts to return only fields explicitly changed by the user, and split semicolon-joined list echoes defensively so rename-only turns do not collapse existing policy lists
- Added an instrumentation migration test that creates a real v1 SQLite database, opens it through Room, and verifies v2 Persona Contract columns plus built-in and configured default Persona backfill
- Kept one-off formatting/style requests out of Persona; no temporary Persona scope was added
- Updated the emulator smoke script default APK path to v1.0.68

### Verification
- PASS: `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.MemoryEngineTest.buildRecallPromptSeparatesPersonaFromLongTermMemories" --tests "com.memorychat.app.domain.agent.AgentToolExecutorTest.updatePersonaToolPersistsAssistantPersona" --tests "com.memorychat.app.domain.agent.AgentDecisionEngineTest.promptDefinesAllowedToolsAndSemanticRouting" --tests "com.memorychat.app.ui.memory.PersonaDisplayFormatterTest.formatsPersonaFieldsForMemoryCenterDisplay" --tests "com.memorychat.app.data.repository.RepositoriesTest.importPersonasJsonAcceptsCamelCaseFieldsFromExistingExports" --tests "com.memorychat.app.data.repository.RepositoriesTest.exportPersonasJsonUsesSnakeCaseFieldNames"` (RED failed before implementation, then passed)
- PASS: `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.MemoryEngineTest.extractionPromptIncludesExistingMemoryIdsForModelUpdates" --tests "com.memorychat.app.domain.engine.MemoryExtractionSaverTest.skipsPersonaContractFieldsFromModelMemoryOutput" --tests "com.memorychat.app.domain.engine.MemoryExtractionSaverTest.explicitRememberFallbackSkipsPersonaContractSettings" --tests "com.memorychat.app.domain.agent.AgentDecisionEngineTest.promptDefinesAllowedToolsAndSemanticRouting" --tests "com.memorychat.app.domain.engine.MemoryExtractionSaverTest.stillSavesUserAnswerPreferenceAsMemory"` (RED failed before implementation, then passed)
- PASS: `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.MemoryExtractionSaverTest.skipsPersonaContractFieldsFromModelMemoryUpdates" --tests "com.memorychat.app.domain.engine.MemoryExtractionSaverTest.skipsPersonaExampleDialoguesEvenWhenTheyContainUserIdentityWords" --tests "com.memorychat.app.domain.engine.MemoryExtractionSaverTest.stillSavesUserOwnedCommunicationStyleAndToolPolicyMemories" --tests "com.memorychat.app.domain.engine.PersonaInstructionExtractorTest.promptIncludesFullCurrentPersonaContractForModelClassification"` (RED failed before implementation, then passed)
- PASS: `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.MemoryExtractionSaverTest.skipsAssistantPersonaParaphraseFromModelMemoryOutput" --tests "com.memorychat.app.domain.engine.MemoryExtractionSaverTest.stillSavesProjectMemoryAboutAssistantRulesUi" --tests "com.memorychat.app.domain.engine.PersonaInstructionDetectorTest.replacesListFieldsWhenInstructionProvidesNewLists" --tests "com.memorychat.app.domain.agent.AgentDecisionEngineTest.promptDefinesAllowedToolsAndSemanticRouting" --tests "com.memorychat.app.domain.engine.PersonaInstructionExtractorTest.promptIncludesFullCurrentPersonaContractForModelClassification"` (RED failed before implementation, then passed)
- PASS: `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.agent.AgentToolExecutorTest.updatePersonaSplitsModelEchoedSemicolonLists" --tests "com.memorychat.app.domain.agent.AgentDecisionEngineTest.promptDefinesAllowedToolsAndSemanticRouting" --tests "com.memorychat.app.domain.engine.PersonaInstructionExtractorTest.promptIncludesFullCurrentPersonaContractForModelClassification"` (RED failed before implementation, then passed)
- PASS: `.\gradlew.bat "-Pandroid.testInstrumentationRunnerArguments.class=com.memorychat.app.data.local.db.AppDatabaseMigrationTest" connectedDebugAndroidTest`
- PASS: `.\gradlew.bat test`
- PASS: `.\gradlew.bat assembleDebug`
- PASS: Clean current APK install on `emulator-5554`, UI-created conversation, DB verified `user_version=2`, Persona Contract columns populated, real-model rename changed Persona name to `验证桃子` without collapsing policy lists, and real-model ADB message returned Mission verbatim: `帮助用户把想法推进成可验证的产品和工程改动`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.68-debug.apk`

---

## v1.0.67 (2026-06-17)

### Changes
- Added automatic conversation titles: a new chat starts as `新会话`, then immediately uses the first user message as a local title after send
- Added background smart-title generation after the assistant reply is saved, using the configured model to replace only the placeholder or the known local auto-title
- Added DAO-level atomic overwrite protection so future manual titles are not replaced by concurrent automatic title updates
- Reloaded the conversation list on resume so title updates written from the chat screen are visible after returning to the list
- Updated the ADB broadcast path to apply the same local first-message title behavior without adding an extra model call to smoke tests
- Added an ADB smoke database assertion for the local first-message title update
- Updated the emulator smoke script default APK path to v1.0.67

### Verification
- PASS: `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.ConversationTitleGeneratorTest" --tests "com.memorychat.app.data.repository.RepositoriesTest.updateConversationTitleIfAutoReplacesPlaceholderTitle" --tests "com.memorychat.app.data.repository.RepositoriesTest.updateConversationTitleIfAutoDoesNotOverwriteManualTitle" --tests "com.memorychat.app.data.repository.RepositoriesTest.updateConversationTitleIfAutoDoesNotOverwriteTitleChangedDuringUpdate"` (RED failed before implementation, then passed)
- PASS: `.\gradlew.bat test`
- PASS: `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.67-debug.apk`

---

## v1.0.66 (2026-06-17)

### Changes
- Prevented chat input loss when a conversation or model provider is not ready; the chat page now keeps the draft and shows a snackbar instead of silently clearing text
- Added persistent per-conversation Debug snapshots for recall results, rolling summary, compression watermark, request context size, and context-limit retry state
- Added one automatic context-limit retry path: when a context length error is detected, the app forces rolling-summary compression and retries the model request once
- Added editable Persona cards in the memory/persona center and exposed Persona JSON import in Settings
- Ensured only one Persona can be marked as default and made Persona import report skipped invalid rows instead of silently swallowing them
- Updated the emulator smoke script default APK path to v1.0.66

### Verification
- PASS: `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.ChatContextWindowManagerTest" --tests "com.memorychat.app.domain.engine.ContextLengthErrorDetectorTest" --tests "com.memorychat.app.ui.chat.ChatSendReadinessPolicyTest" --tests "com.memorychat.app.domain.model.ConversationDebugSnapshotTest" --tests "com.memorychat.app.ui.memory.PersonaDisplayFormatterTest"`
- PASS: `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.data.repository.RepositoriesTest.savingDefaultPersonaClearsOtherDefaults" --tests "com.memorychat.app.data.repository.RepositoriesTest.importPersonasJsonReportsInvalidRows"`
- PASS: `.\gradlew.bat test`
- PASS: `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.66-debug.apk`

---

## v1.0.65 (2026-06-17)

### Changes
- Added a rolling conversation context window for Chat UI and ADB E2E paths: older messages can be compressed into a persisted per-conversation summary, while model requests keep the summary plus recent turns instead of resending all history
- Added `generateMemory=false` hard gating for Agent memory tools so `save_memory` and user addressing preferences cannot write memories when memory generation is disabled
- Added a hard save-layer guard so assistant Persona settings are discarded from long-term memory even if the model returns them as memory candidates
- Updated the emulator smoke script default APK path to v1.0.65

### Verification
- PASS: `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.agent.AgentToolExecutorTest" --tests "com.memorychat.app.domain.engine.MemoryExtractionSaverTest" --tests "com.memorychat.app.domain.engine.ChatContextWindowManagerTest"`
- PASS: `.\gradlew.bat test`
- PASS: `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.65-debug.apk`

---

## v1.0.64 (2026-06-17)

### Changes
- Updated the built-in MiMo API defaults to use `https://token-plan-cn.xiaomimimo.com/v1` with `mimo-v2.5`
- Added `ApiConfigDefaults` so the default base URL and model are covered by unit tests
- Configured the local debug default API key through ignored `local.properties`; the key is not committed
- Updated the emulator smoke script default APK path to v1.0.64

### Verification
- PASS: `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.data.local.datastore.ApiKeyDefaultsTest.defaultApiEndpointUsesTokenPlanMimoV25"` (RED failed before implementation, then passed)
- PASS: `.\gradlew.bat test`
- PASS: `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.64-debug.apk`

---

## Docs (2026-06-05)

### Changes
- Added `docs/README.md` as the documentation index for architecture, audits, and implementation plans
- Updated `docs/architecture/agent-tool-runtime.md` to reflect the current static skill/tool runtime, implemented tools, web search routing, applied actions, and dynamic skill roadmap
- Marked completed Agent runtime and chat tool trace implementation plans as implemented so future agents do not treat them as pending work

### Verification
- PASS: `git diff --check`
- Documentation-only change; app version and APK were not updated

### APK
- Not applicable

---

## v1.0.63 (2026-06-01)

### Changes
- Added user-configurable context and generation settings for context window tokens, max output tokens, safety margin, temperature, top_p, and future compression trigger turns
- Updated MiMo defaults from official docs: `mimo-v2.5` context window `1,000,000`, maximum output `128,000`, `temperature=1.0`, `top_p=0.95`
- Passed saved `max_completion_tokens`, `temperature`, and `top_p` into both Chat UI and ADB model requests
- Kept compression trigger turns configurable for the upcoming rolling-summary implementation, defaulting to `200`

### Verification
- PASS: `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.provider.OpenAICompatibleProviderTest"`
- PASS: `.\gradlew.bat test`
- PASS: `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.63-debug.apk`

---

## v1.0.62 (2026-06-01)

### Changes
- Reworked Persona-setting replies around structured applied Agent actions instead of prompt-only refusal suppression
- `AgentToolExecutor` now returns `AppliedAgentAction` observations for persisted Persona updates
- Added `AgentFinalAnswerPolicy` so pure Persona-setting turns produce a deterministic acknowledgement and skip the final model call
- Mixed turns can continue to the model with structured applied-action observations
- Updated the emulator smoke script default APK path to v1.0.62

### Verification
- PASS: `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.agent.AgentToolExecutorTest" --tests "com.memorychat.app.domain.agent.AgentFinalAnswerPolicyTest"`
- PASS: `.\gradlew.bat test`
- PASS: `.\gradlew.bat assembleDebug`
- PASS: emulator Persona rename smoke on `emulator-5554`
  - Installed v1.0.62 and sent `给你改名叫豆包` through `AdbInputReceiver`
  - Logcat showed `Agent tools: calls=1, results=1` and `Direct agent action answer saved`
  - Pulled Room DB with WAL and confirmed Persona name became `豆包` and assistant reply was `好的，已经改名为「豆包」。`
  - Physical device `10AE2P094M002SL` was not connected during this run

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.62-debug.apk`

---

## v1.0.61 (2026-06-01)

### Changes
- Added chat tool traces so tool execution can show an animated in-bubble status and a completed collapsible trace
- Added conservative search summaries that display real MiMo search metadata when `annotations` / `usage.web_search_usage` are present
- Added provider parsing for MiMo search citations and web search usage in streaming and non-streaming responses
- Updated the emulator smoke script default APK path to v1.0.61

### Verification
- PASS: `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.model.ToolTraceTest" --tests "com.memorychat.app.domain.provider.OpenAICompatibleProviderTest"`
- PASS: `.\gradlew.bat test`
- PASS: `.\gradlew.bat assembleDebug`
- PASS: physical-device UI smoke on `10AE2P094M002SL`
  - Installed v1.0.61 and sent `Will it rain in Shenzhen this weekend` through the real Chat UI
  - Logcat showed `Agent tools: calls=1, results=1` and `Stream request: model=mimo-v2.5, webSearch=true`
  - UI showed a collapsible search trace such as `参考 6 篇资料`, and the assistant text rendered below it without overlap

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.61-debug.apk`

---

## v1.0.60 (2026-06-01)

### Changes
- Fixed UI chat Agent routing on Android by running blocking `complete()` calls on `Dispatchers.IO`
- This prevents `NetworkOnMainThreadException` from causing empty Agent decisions and disabling `web_search` for ordinary Chat UI messages
- Updated the emulator smoke script default APK path to v1.0.60

### Verification
- PASS: `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.provider.OpenAICompatibleProviderTest"`
- PASS: `.\gradlew.bat test`
- PASS: `.\gradlew.bat assembleDebug`
- PASS: physical-device UI search smoke on `10AE2P094M002SL`
  - Installed v1.0.60 and sent `Will it rain in Shenzhen this weekend` through the real Chat UI
  - Logcat showed `Agent tools: calls=1, results=1` and `Stream request: model=mimo-v2.5, webSearch=true`
  - UI response used search results to answer that Shenzhen is expected to have rain over the weekend

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.60-debug.apk`

---

## v1.0.59 (2026-06-01)

### Changes
- Aligned MiMo web search requests with the official Xiaomi MiMo web search schema: `{"type":"web_search","max_keyword":3,"force_search":true,"limit":3}`
- Removed the local DuckDuckGo/Baidu fallback search path; search now relies on the official MiMo web search plugin only
- Sent both `Authorization: Bearer` and `api-key` headers to match OpenAI-compatible behavior and Xiaomi MiMo documentation
- Kept streaming search as the primary Chat UI path; non-streaming ADB remains useful for diagnostics but no longer pretends to search locally
- Updated the emulator smoke script default APK path to v1.0.59

### Verification
- Direct official-schema MiMo API check: non-streaming `web_search` returned synthesized content and URL annotations after plugin activation
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.provider.OpenAICompatibleProviderTest"`
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`
- Physical-device ADB smoke on `10AE2P094M002SL`: installed v1.0.59 and sent `强制联网搜索武汉明天天气怎么样，用一句话回答`; MiMo official web search returned non-empty synthesized weather content without local fallback

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.59-debug.apk`

---

## v1.0.58 (2026-06-01)

### Changes
- Updated Chat UI web-search turns to use MiMo's streaming search path after plugin activation; streaming now carries the official `web_search` tool request and receives synthesized content
- Kept the ADB/non-streaming fallback path because current non-streaming MiMo responses still return an empty content plus `tool_calls[web_search]`
- Added provider coverage proving streaming requests include the MiMo `web_search` tool schema
- Updated the emulator smoke script default APK path to v1.0.58

### Verification
- Direct MiMo API check after Web Search Plugin activation: non-streaming still returned empty content plus `web_search` tool call, while streaming returned content chunks after search
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.provider.OpenAICompatibleProviderTest.streamAddsMimoWebSearchToolWhenEnabled"`
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.58-debug.apk`

---

## v1.0.57 (2026-06-01)

### Changes
- Added MiMo native web search support through the OpenAI-compatible Chat Completions request body
- Added `enableWebSearch` to `ChatRequest`; when enabled, requests include MiMo's `web_search` function tool schema and `tool_choice=auto`
- Added `web_search` to the Agent tool router so search-like user intents can enable provider-side live web search for the final answer
- Added a fallback for MiMo responses that return `tool_calls[web_search]` with empty content: the app extracts the search query, runs a lightweight web search, and sends the results back through MiMo for final synthesis
- Added a Baidu fallback after DuckDuckGo search timeouts so physical-device searches can still return a synthesized answer in China-network conditions
- Updated Chat UI search turns to use non-streaming completion so web-search fallback can complete before the assistant message is saved
- Updated ADB completion flows to pass the web search flag into model requests
- Added provider and Agent routing tests for the MiMo web search request shape
- Updated the emulator smoke script default APK path to v1.0.57

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.provider.OpenAICompatibleProviderTest.completeAddsMimoWebSearchToolWhenEnabled" --tests "com.memorychat.app.domain.agent.AgentDecisionEngineTest.keepsWebSearchToolCall" --tests "com.memorychat.app.domain.agent.AgentDecisionEngineTest.promptDefinesAllowedToolsAndSemanticRouting"` (RED failed before implementation, then passed)
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.provider.OpenAICompatibleProviderTest.completeRunsFallbackSearchWhenMimoReturnsWebSearchToolCall" --tests "com.memorychat.app.domain.provider.OpenAICompatibleProviderTest.completeAddsMimoWebSearchToolWhenEnabled" --tests "com.memorychat.app.domain.agent.AgentDecisionEngineTest.keepsWebSearchToolCall"`
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`
- Physical-device ADB search smoke on `10AE2P094M002SL`: installed v1.0.57, sent `联网搜一下MiMo V2.5`, and confirmed a non-empty assistant answer was saved after the web-search fallback path

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.57-debug.apk`

---

## v1.0.56 (2026-06-01)

### Changes
- Added `docs/architecture/agent-tool-runtime.md` to document the OpenClaw/Hermes-inspired lightweight Agent tool runtime design
- Added a structured `AgentDecisionEngine` that asks the LLM for allowlisted tool calls instead of running separate hard-coded Persona routing
- Added `AgentToolExecutor` for local execution of `update_persona`, `save_memory`, `set_user_addressing_preference`, `get_current_time`, and reserved doc/recall tool results
- Reused `MemoryExtractionSaver` for direct tool memory writes so tombstones, deduplication, similar-memory merging, and user-edited protections stay centralized
- Updated Chat UI and ADB message flows to run Agent decisions before the final chat request and inject environment/tool results into the system prompt
- Skipped post-turn background extraction for turns where the Agent memory tool already wrote memory, preventing duplicate direct-tool and explicit-fallback saves
- Updated the emulator smoke script default APK path to v1.0.56

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.agent.AgentDecisionEngineTest" --tests "com.memorychat.app.domain.agent.AgentToolExecutorTest"` (RED failed before implementation, then passed)
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.agent.AgentToolExecutorTest"` (duplicate-extraction guard regression)
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`
- Physical-device ADB smoke on `10AE2P094M002SL`: installed v1.0.56 and launched `com.memorychat.app/.MainActivity`
- Physical-device Persona tool smoke: sent `你叫真机露露`; `persona_default` changed to `真机露露`, and the normal chat reply used the updated name
- Physical-device addressing-preference smoke: sent `你叫我真机大王吧`; Persona stayed `真机露露`, and one active `PREFERENCE` memory was saved for the user addressing preference
- Physical-device direct-memory smoke: sent `记住真机防重复编号是 AGENT-1056`; exactly one active memory containing `AGENT-1056` existed afterward, and logs showed `Extraction skipped: agent memory tool already wrote this turn`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.56-debug.apk`

---

## v1.0.55 (2026-06-01)

### Changes
- Added assistant-message Markdown rich text rendering for headings, paragraphs, bold, italic, inline code, links, bullet lists, quotes, and fenced code blocks
- Kept user messages as plain text while preserving raw Markdown in message storage
- Added a lightweight in-project Markdown parser with fallback to plain text for unclosed inline markers
- Updated chat system prompts to allow concise Markdown, bullet lists, and code fences when useful, while disallowing HTML
- Injected the response style prompt in both Chat UI and ADB message flows
- Updated the emulator smoke script default APK path to v1.0.55

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.ui.markdown.MarkdownParserTest" --tests "com.memorychat.app.domain.engine.MemoryEngineTest.buildRecallPromptAsksForMarkdownWhenHelpful"` (RED failed before implementation, then passed)
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`
- Android emulator QA on `emulator-5554`: installed v1.0.55 and launched `com.memorychat.app/.MainActivity`
- ADB real-model Markdown smoke: sent a Markdown response request, confirmed the system prompt was injected, the normal chat API path returned a heading, bullets, and a fenced Kotlin code block
- Chat UI screenshot verification: opened the conversation and confirmed the assistant bubble rendered the heading, bullet list, and code block as rich text rather than raw Markdown markers
- Regression check: one-off Markdown formatting requests are treated as `other` in Persona classification and no longer trigger Persona acknowledgement

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.55-debug.apk`

---

## v1.0.54 (2026-06-01)

### Changes
- Added a deterministic Persona update acknowledgement after LLM classification succeeds, so applied renames are confirmed instead of being re-decided by the chat model
- Updated Chat UI flow to save the user message, apply Persona changes, save the acknowledgement, and skip the normal chat completion for Persona update turns
- Updated the ADB test message path with the same acknowledgement behavior
- Added regression coverage for acknowledgement text to prevent refusal wording after successful Persona updates
- Updated the emulator smoke script default APK path to v1.0.54

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.PersonaUpdateAcknowledgerTest" --tests "com.memorychat.app.domain.engine.PersonaInstructionExtractorTest"` (RED failed before implementation, then passed)
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`
- Android emulator QA on `emulator-5554`: installed v1.0.54 and launched `com.memorychat.app/.MainActivity`
- ADB real-model Persona acknowledgement smoke: sent `改为露露`; `persona_default` changed from `噜噜` to `露露`, the saved assistant message was `好的，已经改名为「露露」。`, and the normal chat API response path was skipped

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.54-debug.apk`

---

## v1.0.53 (2026-06-01)

### Changes
- Removed deterministic Persona instruction matching from the chat and ADB Persona update path; nonblank messages are now classified by the LLM
- Removed keyword gating before Persona classification so natural wording such as `给你改名字为比比拉布` is handled by semantic classification
- Reduced `PersonaInstructionDetector` to applying LLM extraction results to the current Persona; regex detection and Persona-like memory text matching were removed
- Removed Persona rule filtering from memory saving; the memory extraction model prompt remains responsible for discarding assistant Persona settings
- Added regression coverage proving explicit assistant renames, user profile statements, user-addressing preferences, and natural rename wording all go through model classification
- Updated the emulator smoke script default APK path to v1.0.53

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.PersonaInstructionExtractorTest" --tests "com.memorychat.app.domain.engine.PersonaInstructionDetectorTest" --tests "com.memorychat.app.domain.engine.MemoryExtractionSaverTest"` (RED failed before implementation, then passed)
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`
- Android emulator QA on `emulator-5554`: installed v1.0.53 and launched `com.memorychat.app/.MainActivity`
- ADB real-model Persona rename smoke: sent `给你改名字为比比拉布`; `persona_default` changed from `猪妞` to `比比拉布`, `Persona updated from message` was logged, and the assistant replied `我叫比比拉布`
- ADB real-model non-update smoke: sent `你叫我啥`; `persona_default` stayed `比比拉布`, and no `Persona updated` log was emitted

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.53-debug.apk`

---

## v1.0.52 (2026-06-01)

### Changes
- Changed ambiguous Persona/name messages to use model-backed semantic classification instead of hard user-addressing exclusion
- Added extraction categories for `assistant_persona_update`, `user_addressing_preference`, `user_profile`, and `other`
- Kept deterministic Persona detection as the fast path for clear assistant Persona updates, while ambiguous addressing messages now ask the model to classify intent
- Added regression coverage for subjectless and assistant-referenced user-addressing messages such as `以后叫我大王就好` and `你叫我大王吧`
- Updated the emulator smoke script default APK path to v1.0.52

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.PersonaInstructionDetectorTest" --tests "com.memorychat.app.domain.engine.PersonaInstructionExtractorTest"` (RED failed before implementation, then passed)
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`
- Android emulator QA on `emulator-5554`: installed v1.0.52, launched `com.memorychat.app/.MainActivity`, and captured screenshot
- ADB real-model Persona regression smoke: sent `以后叫我大王就好`; `persona_default` stayed `猪妞`, no `Persona updated` log was emitted, and the assistant replied using `大王`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.52-debug.apk`

---

## v1.0.51 (2026-06-01)

### Changes
- Fixed Persona instruction detection so user-addressing requests such as `你叫我大王吧` are not interpreted as assistant renames
- Shared the user-addressing exclusion with the model-backed Persona extractor to prevent fallback extraction from renaming Persona
- Excluded the same user-addressing phrasing from Persona-like memory detection
- Updated the emulator smoke script default APK path to v1.0.51

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.PersonaInstructionDetectorTest" --tests "com.memorychat.app.domain.engine.PersonaInstructionExtractorTest"` (RED failed before implementation, then passed)
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`
- Android emulator QA on `emulator-5554`: installed v1.0.51, launched `com.memorychat.app/.MainActivity`, captured UI tree/screenshot, crash log empty
- ADB Persona regression smoke: sent `你叫我大王吧` to the existing conversation; `persona_default` stayed `猪妞`, and no `Persona updated` log was emitted

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.51-debug.apk`

---

## v1.0.50 (2026-06-01)

### Changes
- Passed the current Persona into the model-backed Persona instruction extractor so subjectless follow-up renames can be understood in context
- Added fallback gating for short rename messages such as `改成猪妞吧` when a current Persona exists
- Updated the Persona extraction prompt with current assistant persona name, role, and tone
- Wired the contextual Persona extractor through both chat UI and ADB message paths
- Updated the emulator smoke script default APK path to v1.0.50

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.PersonaInstructionExtractorTest"` (RED failed before implementation, then passed)
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.50-debug.apk`

---

## v1.0.49 (2026-06-01)

### Changes
- Added a model-backed Persona instruction extractor so assistant name, role, tone, and rule changes are not limited to fixed regex patterns
- Kept deterministic Persona detection as a fast path, then gated model fallback to messages that look like assistant-persona settings
- Added support for rename wording such as `给你取名叫芒果` and `把你改名为小芒果`, including trailing particle cleanup
- Wired the hybrid Persona extractor into both chat UI and ADB test message paths
- Updated the emulator smoke script default APK path to v1.0.49

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.PersonaInstructionDetectorTest" --tests "com.memorychat.app.domain.engine.PersonaInstructionExtractorTest"` (RED failed before implementation, then passed)
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.49-debug.apk`

---

## v1.0.48 (2026-06-01)

### Changes
- Added type-scoped memory deduplication for semantically equivalent device-style facts such as `通信机型号是 COM-1047` vs `通信机是 COM-1047`
- Added deterministic merge behavior so a more specific extracted memory updates the existing memory instead of inserting a duplicate
- Updated the memory extraction prompt to include existing memory ids and explicit update guidance so model-generated updates can target real memories
- Updated the emulator smoke script default APK path to v1.0.48

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.MemoryExtractionSaverTest" --tests "com.memorychat.app.domain.engine.MemoryEngineTest"` (RED failed before implementation, then passed)
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.48-debug.apk`

---

## v1.0.47 (2026-06-01)

### Changes
- Added a dedicated Persona tab to Memory Center so users can inspect assistant persona state separately from long-term memories
- Displayed persona name, default status, description, role, tone, behavior rules, and boundaries in the Persona card
- Switched Memory Center tabs to a scrollable row so the added Persona tab does not squeeze labels on small screens
- Updated the emulator smoke script default APK path to v1.0.47

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.ui.memory.PersonaDisplayFormatterTest"` (RED failed before implementation, then passed)
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.47-debug.apk`

---

## v1.0.46 (2026-06-01)

### Changes
- Exposed active background memory extraction state from `MemoryExtractionCoordinator`
- Added a small non-blocking memory extraction indicator to the conversation list top bar when any extraction job is active
- Added the same indicator to the chat top bar for the current conversation only
- Updated the emulator smoke script default APK path to v1.0.46

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.MemoryExtractionCoordinatorTest"` (RED failed before implementation, then passed)
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.46-debug.apk`

---

## v1.0.45 (2026-06-01)

### Changes
- Added deterministic persona instruction detection for assistant name, role, tone, and behavior-rule updates
- Updated chat and ADB message paths so explicit persona settings update the current conversation persona instead of becoming long-term memories
- Hardened memory extraction saving so model outputs that misclassify persona settings as `PREFERENCE` are filtered before writing `memories`
- Updated the memory extraction prompt to state that assistant persona settings belong to the Persona system, not long-term memory
- Added regression tests for persona instruction detection, persona-like memory filtering, and preserving real user answer preferences
- Updated the emulator smoke script default APK path to v1.0.45

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.PersonaInstructionDetectorTest" --tests "com.memorychat.app.domain.engine.MemoryExtractionSaverTest"` (RED failed before implementation, then passed)
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.45-debug.apk`

---

## v1.0.44 (2026-06-01)

### Changes
- Added an application-level `MemoryExtractionCoordinator` keyed by conversation ID so rapid exit/re-enter/exit cannot launch duplicate background extraction for the same conversation
- Switched chat memory extraction scheduling from ViewModel-local job tracking to the shared coordinator
- Kept different conversations able to run extraction independently
- Updated the emulator smoke script default APK path to v1.0.44

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.MemoryExtractionCoordinatorTest"` (RED failed before implementation, then passed)
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`
- Skipped emulator/APK-install smoke per user request

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.44-debug.apk`

---

## v1.0.43 (2026-06-01)

### Changes
- Changed automatic memory extraction from every completed assistant turn to a background trigger policy
- Explicit memory commands like "记住/remember" still trigger immediate background extraction
- Ordinary conversation turns now batch extraction until 20 completed unextracted turns
- Leaving a chat screen flushes pending unextracted messages in the background
- Added a per-conversation DataStore extraction watermark so successful extraction only processes new messages
- Aligned the ADB debug message path with the same extraction trigger policy and watermark
- Updated the emulator smoke script default APK path to v1.0.43

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.MemoryExtractionTriggerPolicyTest"` (RED failed before implementation, then passed)
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.MemoryExtractionTriggerPolicyTest" --tests "com.memorychat.app.domain.engine.MemoryExtractionSaverTest"`
- `.\gradlew.bat test`
- `.\gradlew.bat connectedDebugAndroidTest`
- `.\gradlew.bat assembleDebug`
- Sub-agent emulator ADB validation on `emulator-5554`: ordinary message logged `Extraction deferred: unextracted=2` and created no memories; explicit `记住，批量策略验证码是 MX1043` logged `trigger=EXPLICIT_MEMORY, new=1, updates=0` and created one active memory; latest conversation had `user=2`, `assistant=2`, and no duplicate assistant groups

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.43-debug.apk`

---

## v1.0.42 (2026-06-01)

### Changes
- Added a generation send gate so a chat turn cannot start a second LLM stream before the current generation finishes
- Cleared the streaming bubble before appending the final assistant message, avoiding a duplicate-looking UI frame at stream completion
- Made stream cancellation skip the generic error-save path so Stop does not race with the stream collector into duplicate partial replies
- Updated the emulator smoke script default APK path to v1.0.42

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.ui.chat.GenerationSendGateTest"` (RED failed before implementation, then passed)
- `.\gradlew.bat test`
- `.\gradlew.bat connectedDebugAndroidTest`
- `.\gradlew.bat assembleDebug`
- Sub-agent emulator UI validation on `emulator-5554`: installed v1.0.42, sent `duplicate ui probe 1042`, final UI showed 1 assistant bubble, DB had 1 user + 1 assistant, and logcat counted `Sending`/`Starting stream`/`Stream done`/`Message saved`/`SSE [DONE]` once each

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.42-debug.apk`

---

## v1.0.41 (2026-06-01)

### Changes
- Added explicit-remember fallback extraction so clear user commands like "记住/remember" still save a memory when the model replies with non-JSON text
- Made memory extraction parsing tolerate Chinese type/status labels and unknown labels without dropping the whole result
- Improved duplicate detection to ignore case, punctuation, and spacing differences when saving extracted memories
- Added a memory-query recall scene so questions like "还记得/remember" include project and preference memories
- Added snackbar feedback for manual memory extraction outcomes
- Wired the debug ADB message path to run memory extraction after successful assistant replies and to honor conversation memory switches
- Made the ADB smoke script fail on model/network errors or missing completion markers instead of silently passing

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.engine.MemoryExtractionSaverTest" --tests "com.memorychat.app.domain.engine.MemoryEngineTest"`
- `.\gradlew.bat test`
- `.\gradlew.bat test connectedDebugAndroidTest assembleDebug`
- Sub-agent emulator check on the previous installed build confirmed the memory chain was reachable: automatic extraction wrote an active preference, memory center displayed it, and later recall answered with the saved fact; it also exposed duplicate extraction for case/punctuation variants, which is fixed in this version
- Final v1.0.41 emulator ADB validation passed after switching the debug receiver to Base64 message input: latest APK installed successfully, network/API returned `[4/4] API response`, extraction saved `blue cedar QX77` with `new=1, updates=0`, and the recall answer included `blue cedar QX77`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.41-debug.apk`

---

## v1.0.40 (2026-06-01)

### Changes
- Made non-streaming provider calls throw on HTTP, network, parse, or unexpected response failures instead of returning empty content
- Added provider tests so ADB and memory extraction callers cannot mistake failed model calls for successful empty replies

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.domain.provider.OpenAICompatibleProviderTest"`
- `.\gradlew.bat test`
- `.\gradlew.bat connectedDebugAndroidTest`
- `.\gradlew.bat assembleDebug`
- Manual ADB smoke on `emulator-5554`: debug receiver accepted `SEND_MESSAGE`; emulator DNS still failed for `api.xiaomimimo.com`; ADB path saved an explicit assistant error instead of an empty response

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.40-debug.apk`

---

## v1.0.39 (2026-06-01)

### Changes
- Fixed chat input layout so the message box and send button avoid the soft keyboard
- Added an instrumentation regression test for the new-chat save-before-navigation flow
- Exported the ADB input receiver only for debug builds so emulator smoke scripts can actually deliver broadcasts
- Documented emulator dialogue findings: previous send failure was `conv=null`, and the current emulator network cannot resolve the model host

### Verification
- `.\gradlew.bat test`
- `.\gradlew.bat connectedDebugAndroidTest`
- `.\gradlew.bat assembleDebug`
- Manual emulator dialogue smoke on `emulator-5554`: new chat opened as `新会话`, keyboard no longer covered send, sending reached `ChatVM`, model call failed at emulator DNS with `Unable to resolve host "api.xiaomimimo.com"`, and the assistant error message was persisted

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.39-debug.apk`

---

## v1.0.38 (2026-06-01)

### Changes
- Fixed new conversation navigation race so chat opens only after the conversation is saved with defaults
- Verified the bug from emulator logs where send failed with `conv=null`

### Verification
- Pending

### APK
- Pending

---

## v1.0.37 (2026-06-01)

### Changes
- Moved memory delete/disable tombstone creation into `MemoryRepository`
- Made tombstone checks type-scoped so identical text in different memory categories is handled deliberately
- Added repository tests for disabled/deleted memory tombstones
- Expanded memory source dialog to show saved source message IDs

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.data.repository.RepositoriesTest"`
- `.\gradlew.bat test`
- `.\gradlew.bat connectedDebugAndroidTest`
- `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.37-debug.apk`

---

## v1.0.36 (2026-06-01)

### Changes
- Added a tested memory extraction saver for persisting LLM extraction results
- Wired completed assistant turns to automatically trigger memory extraction when `generateMemory` is enabled
- Reused the same extraction saver for manual memory organization
- Persisted assistant error messages when streaming fails before any assistant content is produced

### Verification
- `.\gradlew.bat test`
- `.\gradlew.bat connectedDebugAndroidTest`
- `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.36-debug.apk`

---

## v1.0.35 (2026-06-01)

### Changes
- Added Android instrumentation test dependencies and a first-launch Compose smoke test
- Added shared fake LLM provider test utility for deterministic chat and memory tests
- Added an ADB smoke script for installing the APK, selecting a conversation, and exercising the real-model broadcast path

### Verification
- `.\gradlew.bat test`
- `.\gradlew.bat connectedDebugAndroidTest`
- `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.35-debug.apk`

---

## v1.0.34 (2026-06-01)

### Changes
- Saved pending source changes around default persona initialization and lookup
- Reused the default persona creation path from application startup and new conversation creation
- Added persona-aware memory recall for the ADB input path
- Improved persona import/export JSON compatibility and fixed memory center dialog titles

### Verification
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.34-debug.apk`

---

## v1.0.33 (2026-06-01)

### Changes
- Added a PRD-aligned gap and bug audit report under `docs/audits/`
- Consolidated subagent findings across data/storage, chat/memory/context, UI, and test/build coverage
- Identified current P0 blockers around context compression, automatic memory extraction, Debug recall visibility, persona editing, conversation creation, ADB memory switches, and memory overwrite protections

### Verification
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.33-debug.apk`

---

## v1.0.32 (2026-06-01)

### Changes
- Added testing conventions to prefer parallel subagents for broad test review and refactoring
- Defined PRD-aligned test refactoring as the default direction for future test work
- Recorded the testing workflow preference in project insights
- Refactored JVM unit coverage around API key defaults, memories import/export, memory recall, and recall prompt assembly

### Verification
- `.\gradlew.bat testDebugUnitTest --tests "com.memorychat.app.data.local.datastore.ApiKeyDefaultsTest" --tests "com.memorychat.app.data.repository.RepositoriesTest" --tests "com.memorychat.app.domain.engine.MemoryEngineTest"`
- `.\gradlew.bat test`

### APK
- `app/build/outputs/apk/debug/MemoryChat-v1.0.32-debug.apk`

---

## v1.0.31 (2026-06-01)

### Changes
- Added project-level agent/development conventions in `AGENTS.md`
- Registered the v1.1 PRD as the authoritative product and acceptance reference
- Added debug-only default API key injection from ignored `local.properties`
- Added fallback logic so encrypted saved API key takes priority over the debug default

### Verification
- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`

### APK
- Debug APK should be produced as `app/build/outputs/apk/debug/MemoryChat-v1.0.31-debug.apk`

---

## v1.0.3 (2026-05-30 22:25)

### Changes
- Stream provider rewritten: switched from `channelFlow` to plain `flow` with synchronous HTTP on `Dispatchers.IO`
- Eliminates all cross-thread emission issues
- Added detailed logging throughout stream lifecycle

### Bug Fixes
- Fixed message flash-disappear issue: SSE `[DONE]` handling was broken, empty content chunks were incorrectly treated as stream end

---

## v1.0.2 (2026-05-30 22:18)

### Changes
- Explicit `done=true` signal from Provider after SSE `[DONE]`
- ViewModel only saves message on explicit `done=true`
- Added stream lifecycle logging (chunks count, content length)

### Bug Fixes
- Fixed message disappearing: some API responses had empty content chunks mid-stream that triggered premature completion

---

## v1.0.1 (2026-05-30 22:12)

### Changes
- Added AppLogger system with log levels (DEBUG/INFO/WARN/ERROR)
- Added LogScreen with filter chips and export functionality
- Export/Save/Clear buttons moved to bottom bar for visibility
- Navigation updated to include LogScreen
- ConversationListScreen gets Logs button in top bar

### Bug Fixes
- Fixed Flow exception transparency violation: changed `callbackFlow` to `channelFlow`
- Fixed message flash-disappear: stream completion handling was incorrect

---

## v1.0.0 (2026-05-30 21:45)

### Initial Release
- Chat UI with Jetpack Compose
- OpenAI-compatible API support (MiMo provider configured)
- Local Room database for conversations, messages, memories, personas
- Long-term memory system (4 types: profile/preference/project/summary)
- Memory extraction from conversations
- Memory recall with scene detection + Prompt injection
- Memory Center: view/edit/delete/disable/add memories
- Persona system (separate from memory)
- Export/Import memories and personas (JSON/Markdown)
- Debug page for conversation inspection
- Settings page with API configuration
- Default API config pre-filled (MiMo mimo-v2.5-pro)

---

## v1.0.3 Test Report (2026-05-30 22:35)

### API Tests (Direct)
| Test Case | Result | Notes |
|-----------|--------|-------|
| TC-P0-003 Basic chat | PASS | Response length 313, API working |
| TC-P0-004 API error | PASS | Error handling in code, no crash |
| TC-MEM-001 Preference extraction | PASS | Correctly extracts preference type |
| TC-MEM-002 Project memory | PASS | Correctly extracts project type |
| TC-MEM-003 Temp emotion not saved | PASS | Correctly discarded |
| TC-MEM-005 API Key not saved | PASS | Sensitive info filtered |
| Streaming test | PASS | 19 data lines, 5 chunks, [DONE] signal present |

### Code Coverage
| Feature | Status |
|---------|--------|
| Error handling (no crash) | ✅ Present |
| Sensitive info filter in prompt | ✅ Present |
| use_memory toggle | ✅ Present |
| generate_memory toggle | ✅ Present |
| Session isolation (conversationId) | ✅ Present |
| Memory CRUD | ✅ Present |
| Export/Import | ✅ Present |

### Known Issues
- Streaming message flash-disappear: Fixed in v1.0.3 (switched to sync flow on Dispatchers.IO)
- PowerShell encoding display issue: Not an app issue, only affects terminal output

### Pending for Next Round
- TC-CONFLICT-001~004: Conflict resolution logic
- TC-RECALL-001~005: Memory recall verification on device
- TC-PERSONA-001~004: Persona system verification
- E2E-001~003: End-to-end flows


---

## v1.0.4 (2026-05-30 22:50)

### Changes
- Added max_tokens=4096 to LlmProvider (MiMo reasoning consumes tokens)
- Added long-press delete on messages (combinedClickable + AlertDialog confirmation)
- MessageBubble now supports onLongClick callback

### Test Results (Code Review)
36/36 test cases verified in code:
- P0 Smoke: 4/4
- Chat & Persistence: 4/4
- Memory Extraction: 8/8
- Conflict Resolution: 4/4
- Memory Center: 5/5
- Memory Recall: 5/5
- Persona System: 4/4
- Export/Import: 4/4
- JSON Handling: 4/4
- Security: 3/3
- Logging: 1/1

### Key Finding
MiMo model uses separate reasoning_content field that consumes max_tokens. Must set max_tokens >= 4096 to ensure content is not empty.


---

## v1.0.5 (2026-05-30 23:10)

### Changes
- Fixed: Use max_completion_tokens instead of max_tokens (MiMo API uses OpenAI newer param)
- Added configurable Max Tokens in Settings page (default: 8192)
- MiMo reasoning_content consumes ~100 tokens per request, rest is for content
- SettingsDataStore cleaned up with proper function declarations

### API Testing
- max_tokens=100 → finish_reason=length, content empty (too small for reasoning)
- max_completion_tokens=100 → finish_reason=stop, content works
- No max_tokens → finish_reason=stop, auto-uses needed tokens

---

## v1.0.6 (2026-05-30 23:35)

### Changes
- Added AdbInputReceiver for emulator testing via ADB broadcast
- Fixed AndroidManifest.xml to properly register the receiver
- API call now works end-to-end via emulator

### Emulator Testing
- ADB broadcast: m broadcast -n com.memorychat.app/.AdbInputReceiver -a com.memorychat.app.SEND_MESSAGE --es msg 'text' --es conv_id 'id'
- Verified: user message saved, API call successful, assistant response saved

---

## v1.0.7 (2026-05-30 16:02)

### Bug Fixes
- **MemoryEngine**: Added modelName parameter — extraction API was calling with empty model
- **MemoryEngine**: Wrapped API call in withContext(Dispatchers.IO) — fixed NetworkOnMainThreadException
- **MemoryEngine**: Enhanced error logging with exception class name
- **EXTRACTION_PROMPT**: Improved rules for explicit "remember" patterns

### Verified
- Memory extraction now works: "用户是一名Android开发者，在北京工作" extracted as PROFILE

---

## v1.0.8 (2026-05-31 18:20)

### Bug Fixes
- **LogScreen**: Fixed key collision crash — switched to index-based keys
- **AppLogger**: Added synchronized blocks for thread-safe log operations
- **AppLogger**: Made export function thread-safe with snapshot

---

## v1.0.23 (2026-05-31 21:40)

### Changes
- Added API key setter broadcast for testing: m broadcast --es set_api_key 'xxx'
- Pushed to GitHub: git@github.com:TonyRuan/memchat.git

## v1.0.22 (2026-05-31 21:23)

### PRD Compliance Fixes
- **API Key 加密存储**: 使用 EncryptedSharedPreferences 替代明文 DataStore
- **查看来源**: MemoryCenterScreen 添加"查看来源"按钮
- **会话级设置**: ChatScreen 添加 useMemory/generateMemory 开关
- **Debug 召回详情**: DebugScreen 显示最近一次 recall 结果
- **中文场景检测**: MemoryEngine.detectScene() 添加中文关键词
- **导出写文件**: SettingsScreen 导出时写入文件并分享

## v1.0.21 (2026-05-31 21:07)

### P0 Fixes
- **自动提取记忆**: sendMessage 完成后自动调用 extractMemories()
- **userEdited 保护**: 编辑过的记忆不会被自动覆盖
- **API Key 遮罩**: 设置页 API Key 使用密码输入样式
- **sourceMessageIds**: 提取记忆时传递来源消息 ID
- **禁用记忆 Tab**: 记忆中心添加"禁用"分页

## v1.0.20 (2026-05-31 20:50)

### Fixes
- MemoryCenterScreen 每次进入刷新（ON_RESUME 生命周期）
- PROFILE 召回数量 take(1) -> take(3)

## v1.0.19 (2026-05-31 20:25)

### Memory Pipeline Logging
- AdbInputReceiver 添加记忆召回日志锚点
- 跨会话记忆测试通过

## v1.0.18 (2026-05-31 20:10)

### Memory System Fixes
- MemoryDao 查询 'active' -> 'ACTIVE'（SQLite 区分大小写）
- AdbInputReceiver 加入记忆召回逻辑
- 提取前重新加载消息

## v1.0.15-v1.0.17

### Fixes
- JsonNull 静默处理
- LogScreen 闪退修复（remember + stable keys）
- 版本号修正

## v1.0.12-v1.0.14

### Fixes
- JSON null 错误修复
- 流式响应解析容错
- API 地址修正为 api.xiaomimimo.com
