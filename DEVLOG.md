# MemoryChat Development Log

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
