# MemoryChat Development Log

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
