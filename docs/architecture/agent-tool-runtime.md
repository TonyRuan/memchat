# Agent Tool Runtime Design

## 当前状态

截至 `v1.0.63`，MemoryChat 已有一套轻量的 Agent 工具运行时。它不是运行时动态安装的 skill 系统，而是编译进 APK 的静态工具集合：

- 能力清单写在 `AgentDecisionEngine` 的路由 prompt 里。
- 允许调用的工具名写在 `AgentDecisionEngine.allowedTools` 白名单里。
- 工具执行逻辑写在 `AgentToolExecutor` 和后续聊天链路里。
- 新增能力需要改源码、加测试、重新构建 APK。

因此它可以被理解为“静态 skill / 静态工具能力”：模型能一次性看到自己有哪些能力，并用结构化 JSON 请求调用，但还不能从手机文件、远端市场或用户导入目录动态安装新 skill。

## 设计来源

本设计借鉴 OpenClaw 与 Hermes Agent 的成熟分层：

- OpenClaw 的 Agent 由模型、记忆、工具、渠道组成，模型提出工具调用，运行时执行并回填结果。
- Hermes 把稳定事实、技能流程、历史会话搜索分层，并把人格边界放在类似 SOUL 的独立层，而不是混入 memory。

MemoryChat 第一阶段不做复杂多 Agent，而是做本地 Runtime：LLM 负责语义判断和工具候选，App 侧执行白名单工具。

## 核心目标

- Persona、用户偏好/资料、记忆写入/合并、文档查询、联网搜索、环境信息都通过统一工具语义进入模型上下文。
- LLM 负责语义识别和候选生成，App 负责权限、schema 校验、去重、持久化和错误隔离。
- Persona 继续独立存储在 `personas`，长期事实继续存储在 `memories`。
- 文档查询和联网搜索默认只用于当前回答，不自动写入长期记忆。

## 工具清单

### 已执行工具

- `get_current_time`
  - 返回设备当前时间，作为当前回答的环境信息。
- `update_persona`
  - 更新助手姓名、角色、语气、行为规则、边界。
  - 写入 `personas`。
  - 纯 Persona 更新可由 `AgentFinalAnswerPolicy` 直接生成确定性确认语，跳过最终模型调用。
- `save_memory`
  - 写入用户长期事实、偏好、资料或项目事实。
  - 写入 `memories`，复用 `MemoryExtractionSaver` 的 tombstone、去重、相似合并和用户编辑保护。
- `set_user_addressing_preference`
  - 保存“助手如何称呼用户”的偏好。
  - 不改助手 Persona。

### 已路由工具

- `web_search`
  - 触发模型 provider 的内置联网搜索能力。
  - 工具结果会在聊天页显示为搜索/工具轨迹。
  - 搜索结果只用于当前回答，默认不进入长期记忆。

### 静态预留工具

- `search_docs`
  - 当前只返回预留结果文本。
  - 后续可接本地 PRD、帮助文档、skill 文档索引。
- `recall_memory`
  - 当前由正常记忆召回链路处理。
  - 后续可扩展为显式 memory query 工具。

## 决策 JSON

模型先输出严格 JSON，App 只接受白名单工具名：

```json
{
  "tool_calls": [
    {
      "name": "update_persona",
      "arguments": {
        "name": "噜噜",
        "role": null,
        "tone": null,
        "behavior_rules": [],
        "boundaries": []
      }
    }
  ],
  "temporary_response_format": "markdown",
  "should_continue_chat": true
}
```

约束：

- 一次性格式要求只写入 `temporary_response_format`，不改 Persona。
- 用户称呼偏好走 `set_user_addressing_preference` 或 `save_memory`，不改 Persona。
- 文档查询和联网搜索默认只进入当前回答。
- 写入工具失败不能阻断主聊天流程。
- 工具已直接写入记忆的回合会跳过同回合后台显式记忆 fallback，避免重复落库。

## 回答流程

```text
用户消息
  ↓
加载会话、Persona、设置、时间和可用工具
  ↓
AgentDecisionEngine 输出 tool_calls
  ↓
AgentToolExecutor 执行本地白名单工具
  ↓
AgentFinalAnswerPolicy 判断是否可直接确认
  ↓
召回长期记忆、工具结果、联网搜索配置
  ↓
组装 System + Persona + Memory + Tool Results + Recent Messages
  ↓
请求聊天模型
  ↓
保存回答和工具轨迹
  ↓
按策略后台整理记忆
```

## 静态 Skill 边界

当前 runtime 支持把能力像 skill 一样列给模型，但能力来源仍是源码：

| 能力 | 当前状态 |
|---|---|
| 一次性列出可用能力 | 已支持 |
| 模型主动选择能力 | 已支持 |
| App 白名单执行工具 | 已支持 |
| 工具结果回填回答上下文 | 已支持 |
| 手机端动态安装 skill | 未支持 |
| 扫描 `skills/*.md` | 未支持 |
| 按需展开 skill 正文 | 未支持 |

后续如果要升级为真正的动态 skill 机制，建议新增：

- `SkillRegistry`：注册内置和导入的 skill 元数据。
- `search_skills`：按用户消息检索相关 skill 摘要。
- `read_skill`：按需读取 skill 正文并注入当前回答。
- `SkillInstaller`：导入或更新手机端 skill 文件。

## 关键源码

- `app/src/main/java/com/memorychat/app/domain/agent/AgentDecisionEngine.kt`
- `app/src/main/java/com/memorychat/app/domain/agent/AgentToolExecutor.kt`
- `app/src/main/java/com/memorychat/app/domain/agent/AppliedAgentAction.kt`
- `app/src/main/java/com/memorychat/app/ui/chat/ChatViewModel.kt`
- `app/src/main/java/com/memorychat/app/AdbInputReceiver.kt`

## 已验证场景

- “你叫真机露露” 更新 Persona。
- “你叫我真机大王吧” 不改 Persona，只写用户称呼偏好。
- “记住真机防重复编号是 AGENT-1056” 只落一条长期记忆。
- 联网搜索请求可触发 provider 的 web search，并在聊天页显示工具轨迹。
- 纯 Persona 更新可跳过最终模型调用，直接返回确定性确认语。
