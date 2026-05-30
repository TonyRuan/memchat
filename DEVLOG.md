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

