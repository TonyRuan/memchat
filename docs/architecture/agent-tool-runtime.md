# Agent Tool Runtime Design

## 背景

MemoryChat 目前已经有独立的 Persona、长期记忆、记忆中心和调试链路，但语义判断分散在多条 prompt 中：Persona 更新先截断聊天，长期记忆在回答后异步抽取，文档与环境信息还没有统一入口。这会导致同一句自然语言在不同模块里被重复判断，容易出现“知道用户意图但改错对象”的问题。

本设计借鉴 OpenClaw 与 Hermes Agent 的成熟分层：

- OpenClaw 的 Agent 由模型、记忆、工具、渠道组成，模型提出工具调用，运行时执行并回填结果。
- Hermes 把稳定事实、技能流程、历史会话搜索分层，并把人格边界放在类似 SOUL 的独立层，而不是混入 memory。

MemoryChat 第一阶段不做复杂多 Agent，只做轻量的本地 Agent Runtime：让模型用严格 JSON 提出工具调用候选，App 侧执行白名单工具。

## 目标

- Persona、用户偏好/资料、记忆写入/合并、文档查询、环境信息都通过统一工具语义进入模型上下文。
- LLM 负责语义识别和候选生成，App 负责权限、schema 校验、去重、持久化和错误隔离。
- Persona 继续独立存储在 `personas`，长期事实继续存储在 `memories`，文档查询默认只用于当前回答。
- 不引入云同步、账号、多 Agent 或复杂知识库。

## 工具边界

### 只读工具

- `get_current_time`
  - 返回设备当前时间、时区和日期。
  - 每轮可自动注入，不需要用户确认。
- `search_docs`
  - 查询 App 内置或项目内可用文档摘要。
  - 结果只进入当前回答，不自动写入长期记忆。
- `recall_memory`
  - 查询相关长期记忆。
  - 受当前会话 `use_memory` 控制。

### 写入工具

- `update_persona`
  - 更新助手姓名、角色、语气、行为规则、边界。
  - 写入 `personas`。
  - 用于“你叫噜噜”“以后你更直接一点”等助手人格设置。
- `save_memory`
  - 写入用户长期事实、偏好、资料或项目事实。
  - 写入 `memories`，复用现有 tombstone、去重、相似合并和用户编辑保护。
  - 用于“记住我喜欢蓝色”“这个项目的通信机型号是 COM-1047”等长期事实。
- `set_user_addressing_preference`
  - 本质上是 `save_memory(type=preference)`，但语义上明确这是“助手如何称呼用户”，不能改 Persona。

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
- 文档查询走 `search_docs`，除非用户明确要求保存结论，否则不写入长期记忆。
- 写入工具失败不能阻断主聊天流程。

## 回答流程

```text
用户消息
  ↓
加载会话、Persona、设置、时间和可用工具
  ↓
AgentDecisionEngine 输出 tool_calls
  ↓
AgentToolExecutor 执行白名单工具
  ↓
召回长期记忆、文档结果、工具结果
  ↓
组装 System + Persona + Memory + Tool Results + Recent Messages
  ↓
请求聊天模型
  ↓
保存回答
  ↓
按现有策略后台整理记忆
```

## 第一阶段落地

第一阶段实现最小闭环：

- 新增 `AgentDecisionEngine`，用 LLM 生成工具调用 JSON。
- 新增 `AgentToolExecutor`，执行 `update_persona` 和 `save_memory`，并生成工具结果文本。
- 聊天 UI 与 ADB 测试路径都先走 Agent 决策，再进入正常回答。
- `get_current_time` 作为每轮环境信息注入。
- `search_docs` 先预留工具 schema 和结果注入结构，文档索引后续扩展。

## 验收

- “你叫噜噜” 调用 `update_persona`，助手 Persona 名称变为“噜噜”。
- “你叫我大王吧” 不改 Persona，写入用户称呼偏好或进入后续记忆整理。
- “记住我喜欢蓝色” 可产生 `save_memory` 候选并通过现有去重保护落库。
- “请用 Markdown 回复” 只影响当前回答格式，不写 Persona 或长期记忆。
- 工具决策失败、JSON 解析失败或工具执行失败时，聊天仍正常继续。
