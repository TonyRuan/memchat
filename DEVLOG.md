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
