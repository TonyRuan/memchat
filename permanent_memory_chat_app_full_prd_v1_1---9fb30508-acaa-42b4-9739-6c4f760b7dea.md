# 永久记忆 AI 聊天 APP：完整产品定义与技术实现文档

版本：v1.1  
更新时间：2026-05-30  
目标平台：Android  
开发阶段：第一阶段功能技术实验  
模型接入方式：BYOK，用户自填 API Key  
测试模型：小米 MiMo / OpenAI-compatible API  
核心目标：验证长期记忆系统、人格系统、上下文压缩机制是否可用  

---

# 0. 一句话定义

这是一个 **本地优先、用户自填 API Key、拥有长期记忆和可塑造人格的 Android AI 聊天 APP**。

第一阶段不是做完整商业化产品，而是做一个功能技术实验：

> 用最小聊天壳，验证 AI 能否长期记住用户、项目和偏好，并在上下文窗口用尽后仍能通过摘要和记忆保持连续性。

---

# 1. 项目背景

很多用户会把豆包、DeepSeek、ChatGPT 等聊天 APP 当成朋友或长期助手使用。  
但普通聊天 APP 的问题是：

1. 上下文窗口有限；
2. 会话变长后模型会忘记早期内容；
3. 开新会话后历史上下文断裂；
4. 换平台、换模型后长期关系消失；
5. 用户不能真正拥有自己的记忆资产；
6. AI 的人格和用户记忆经常混在一起，难以迁移和管理。

这个项目的目标是解决：

> AI 聊天的“失忆感”和“关系断裂感”。

第一阶段重点不是知识库，而是 **记忆系统**。

---

# 2. 第一阶段核心目标

第一阶段只验证一个核心闭环：

```text
用户聊天
  ↓
保存完整原始消息
  ↓
抽取长期记忆
  ↓
用户可查看、编辑、删除、禁用
  ↓
后续对话召回相关记忆
  ↓
AI 自然使用记忆回答
  ↓
长会话通过 rolling_summary 压缩上下文
  ↓
导出/导入记忆资产
```

第一阶段成功标准：

1. 记得准；
2. 不乱记；
3. 能更新；
4. 能召回；
5. 用户可控；
6. 可导出导入；
7. 记忆和人格分离；
8. 上下文窗口用尽后仍能继续对话；
9. MiMo 等较弱模型输出不稳定时 APP 不崩溃。

---

# 3. 产品边界

## 3.1 P0 必做

### 基础聊天

1. Android 基础聊天界面；
2. 用户自填 API Key；
3. 用户自填 Base URL；
4. 用户自填 Model Name；
5. 支持 OpenAI-compatible Chat Completions API；
6. 支持流式输出；
7. 支持停止生成；
8. 本地保存会话和消息；
9. API 错误不崩溃。

### 记忆系统

1. 本地长期记忆表；
2. 记忆自动抽取；
3. 记忆分级写入；
4. 记忆中心；
5. 查看记忆；
6. 编辑记忆；
7. 删除记忆；
8. 禁用记忆；
9. 查看来源；
10. 跨会话召回记忆；
11. Debug 页显示召回过程。

### 人格系统

1. 记忆和人格分离；
2. 默认人格；
3. 人格单独存储；
4. 每个会话绑定 persona_id；
5. Prompt 注入当前人格；
6. 数据结构预留多人格。

### 上下文压缩

1. Context Window Manager；
2. Token Budget / 字符预算估算；
3. 当前会话 rolling_summary；
4. 超限前自动压缩旧消息；
5. 保留最近原始消息；
6. context_length_exceeded 错误后自动压缩并重试一次；
7. Debug 页显示压缩信息。

### 导入导出

1. 导出 memories.json；
2. 导出 memories.md；
3. 导入 memories.json；
4. 导出 personas.json，若开发时间不足可 P1；
5. 导出聊天记录 JSON，若开发时间不足可 P1。

---

## 3.2 P1 可做

1. 人格列表；
2. 新建人格；
3. 切换人格；
4. 复制人格；
5. 删除人格；
6. 导入 personas.json；
7. 导出 personas.md；
8. 手动联网搜索按钮；
9. SearchProvider 接口；
10. 导入前差异预览；
11. Memory Playground 调试页；
12. 当前会话手动压缩按钮。

---

## 3.3 第一阶段不要做

明确不要做：

```text
云端同步
账号系统
商业化订阅
复杂知识库
PDF / Word / Excel 导入
OCR
Obsidian 同步
网页爬虫
多端同步
多 Agent
多人格独立记忆
多人格群聊
人格市场
语音陪伴
自动任务系统
```

第一阶段是功能技术实验，不要被这些能力拖偏。

---

# 4. 总体架构

第一阶段采用 **Local-first 本地优先架构**。

```text
Android App
  ├── Chat UI
  ├── Memory Center
  ├── Persona Center
  ├── Debug Center
  ├── Settings
  │
  ├── LlmProvider
  │     ├── OpenAICompatibleProvider
  │     └── MiMoProvider
  │
  ├── ConversationEngine
  │     ├── ConversationRepository
  │     ├── MessageRepository
  │     └── ContextWindowManager
  │
  ├── MemoryEngine
  │     ├── MemoryExtractor
  │     ├── MemoryRepository
  │     ├── MemoryRecallEngine
  │     └── MemoryInjector
  │
  ├── PersonaEngine
  │     └── PersonaRepository
  │
  ├── SummaryEngine
  │     ├── RollingSummaryGenerator
  │     └── ConversationSummaryRepository
  │
  ├── ExportImportService
  └── Local Database
        └── Room / SQLite
```

---

# 5. 技术选型

## 5.1 Android

```text
Kotlin
Jetpack Compose
MVVM 或 MVI
Room
DataStore
OkHttp
Retrofit
Coroutine
Flow
WorkManager
Android Keystore
EncryptedSharedPreferences 或 Encrypted DataStore
```

## 5.2 本地存储

```text
聊天记录：Room / SQLite
记忆：Room / SQLite
人格：Room / SQLite
上下文摘要：Room / SQLite
设置：DataStore
API Key：Android Keystore + 加密存储
导出文件：JSON / Markdown
```

## 5.3 模型调用

第一阶段使用 OpenAI-compatible API。

用户可配置：

```text
Provider Type
Base URL
API Key
Model Name
Context Window Tokens
Max Output Tokens
Safety Margin
```

默认配置建议：

```text
Context Window Tokens = 32000
Max Output Tokens = 4096
Safety Margin = 2000
```

---

# 6. 核心产品页面

## 6.1 聊天页

必须包含：

```text
会话标题
当前人格
使用记忆开关 use_memory
生成记忆开关 generate_memory
消息列表
输入框
发送按钮
停止生成按钮
```

P1 可加：

```text
手动联网搜索按钮
手动整理记忆按钮
手动压缩上下文按钮
```

---

## 6.2 记忆中心

分组展示：

```text
用户画像 profile
用户偏好 preference
项目记忆 project
会话摘要 summary
待确认记忆 pending
禁用记忆 disabled
```

每条记忆支持：

```text
编辑
删除
禁用
启用
查看来源
```

---

## 6.3 人格中心

第一阶段至少支持默认人格编辑。

字段：

```text
人格名称
关系定位
语气风格
行为规则
边界
主动程度
是否默认
```

P1 支持多个人格列表和切换。

---

## 6.4 Debug 页

Debug 页是第一阶段必做，因为这个 APP 需要调记忆系统。

必须显示：

```text
当前会话 ID
当前 persona_id
use_memory 状态
generate_memory 状态
本轮识别场景
召回记忆列表
召回原因
最终注入 Prompt
模型上下文窗口大小
估算 Prompt Token / 字符数
是否触发上下文压缩
当前 rolling_summary
本轮保留了多少条最近原始消息
哪些消息已被压缩进摘要
是否发生 context_length_exceeded 重试
记忆抽取原始输出
JSON 解析结果
被保存的记忆
被丢弃的候选记忆
```

敏感信息必须打码。

---

## 6.5 设置页

字段：

```text
Provider Type
Base URL
API Key
Model Name
Context Window Tokens
Max Output Tokens
Safety Margin
是否默认使用记忆
是否默认生成记忆
是否启用搜索
默认 Persona
导出 memories.json
导出 memories.md
导入 memories.json
导出 personas.json
导入 personas.json
```

---

# 7. 数据库设计

## 7.1 conversations

```sql
CREATE TABLE conversations (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    persona_id TEXT,
    use_memory INTEGER NOT NULL DEFAULT 1,
    generate_memory INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

---

## 7.2 messages

```sql
CREATE TABLE messages (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    compressed_in_summary_id TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(conversation_id) REFERENCES conversations(id)
);
```

字段说明：

```text
compressed_in_summary_id:
表示该消息已经被纳入某个 rolling_summary。
消息原文仍然保留，只是 Prompt 组装时不再直接塞入。
```

---

## 7.3 memories

```sql
CREATE TABLE memories (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    content TEXT NOT NULL,
    status TEXT NOT NULL,
    importance INTEGER NOT NULL DEFAULT 3,
    confidence REAL NOT NULL DEFAULT 0.8,
    source_message_ids TEXT,
    source_conversation_id TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    last_used_at INTEGER,
    user_edited INTEGER NOT NULL DEFAULT 0
);
```

type 第一版只保留：

```text
profile
preference
project
summary
```

status 第一版只保留：

```text
candidate
pending
active
disabled
deleted
```

---

## 7.4 memory_tombstones

防止用户删除过的记忆被反复生成。

```sql
CREATE TABLE memory_tombstones (
    id TEXT PRIMARY KEY,
    memory_type TEXT NOT NULL,
    content_fingerprint TEXT NOT NULL,
    deleted_reason TEXT,
    created_at INTEGER NOT NULL
);
```

---

## 7.5 personas

```sql
CREATE TABLE personas (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    avatar TEXT,
    description TEXT,
    role TEXT,
    tone TEXT,
    behavior_rules_json TEXT,
    boundaries_json TEXT,
    proactivity INTEGER NOT NULL DEFAULT 3,
    is_default INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
```

---

## 7.6 conversation_summaries

用于上下文压缩，不等于长期记忆。

```sql
CREATE TABLE conversation_summaries (
    id TEXT PRIMARY KEY,
    conversation_id TEXT NOT NULL,
    summary_type TEXT NOT NULL,
    content TEXT NOT NULL,
    covered_message_start_id TEXT,
    covered_message_end_id TEXT,
    covered_message_count INTEGER NOT NULL DEFAULT 0,
    token_estimate INTEGER,
    version INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    FOREIGN KEY(conversation_id) REFERENCES conversations(id)
);
```

summary_type：

```text
rolling   当前会话滚动摘要
final     会话结束后的最终摘要
```

---

# 8. 记忆系统设计

## 8.1 记忆和聊天记录的关系

完整聊天记录必须保存，但不能全部塞给模型。

```text
messages：完整原始记录
memories：长期高价值信息
conversation_summaries：当前会话压缩摘要
```

三者职责不同：

| 类型 | 作用 |
|---|---|
| messages | 完整溯源、导出、重新整理 |
| memories | 跨会话长期记忆 |
| conversation_summaries | 当前长会话压缩续聊 |

---

## 8.2 记忆类型

第一阶段只用 4 类：

| 类型 | 说明 | 示例 |
|---|---|---|
| profile | 用户画像 | 用户正在开发永久记忆聊天 APP |
| preference | 用户偏好 | 用户喜欢中文、直接、有判断的回答 |
| project | 项目记忆 | 第一阶段优先调好记忆系统 |
| summary | 会话摘要 | 某轮讨论确认本地优先架构 |

不要把人格写进 memories 表。

---

## 8.3 记忆写入策略

采用分级记忆策略。

```text
对话结束 / 用户手动整理
  ↓
模型抽取候选记忆
  ↓
系统判断类型、风险、置信度
  ↓
低风险事实自动保存 active
  ↓
偏好/关系/人设类进入 pending
  ↓
用户确认/编辑/删除
```

### 直接保存

用户明确说：

```text
记住这个
以后都这样
把这个加入记忆
以后回答我直接一点
```

可以直接 active。

### 自动保存

明确、低风险、稳定的信息：

```text
用户正在做永久记忆 Android APP。
第一阶段优先做记忆系统。
聊天壳先简单。
```

### 待确认

容易影响长期互动方式的信息：

```text
用户可能希望 AI 更像朋友。
用户可能偏好更温柔陪伴。
用户可能喜欢某种角色语气。
```

### 不保存

```text
临时情绪
玩笑
随口吐槽
一次性事件
API Key
密码
Token
未经确认的推断
```

---

## 8.4 记忆更新与冲突

优先级：

```text
用户明确指令 > 用户手动编辑 > 最近明确表达 > 多次重复行为 > 模型推断
```

规则：

1. 用户明确要求修改：直接更新旧记忆；
2. 项目状态变化：可以自动合并更新；
3. 偏好、人设、关系类变化：进入 pending；
4. 用户手动编辑过的记忆：禁止自动覆盖；
5. 用户删除/禁用过的记忆：不允许反复自动生成；
6. 无法判断是否冲突：进入 pending。

---

## 8.5 记忆召回

不要只做向量 topK。

第一阶段采用：

```text
场景识别 + 多路召回 + 排序压缩 + Prompt 注入 + Debug 展示
```

场景类型：

```text
普通闲聊
情绪陪伴
项目讨论
知识问答
偏好设置
人格设置
搜索型问题
```

召回规则建议：

| 场景 | 优先召回 |
|---|---|
| 项目讨论 | project、summary、preference |
| 普通闲聊 | profile、preference、少量 summary |
| 技术方案 | project、preference、summary |
| 人格设置 | persona、preference |
| 搜索型问题 | 临时搜索上下文，不自动写入 memory |

每次最多注入：

```text
8～10 条长期记忆
1 条当前会话 rolling_summary
最近 8～12 轮原始消息
```

---

# 9. 人格系统设计

## 9.1 记忆和人格必须分离

记忆系统回答：

> AI 记得用户什么？

人格系统回答：

> AI 应该以什么身份、语气、关系方式和用户互动？

不能混在一张表里。

---

## 9.2 默认人格建议

第一阶段默认人格：

```json
{
  "name": "产品技术合伙人",
  "description": "适合产品方案、技术方案、Agent 任务拆解的默认人格。",
  "role": "长期协作伙伴",
  "tone": "直接、明确、有判断",
  "behavior_rules": [
    "优先指出方案漏洞",
    "给出可执行建议",
    "避免空泛安慰",
    "不确定时直接说明"
  ],
  "boundaries": [
    "不要假装知道不存在的信息",
    "不要把临时偏好当长期偏好"
  ],
  "proactivity": 4,
  "is_default": true
}
```

---

## 9.3 多人格预留

第一阶段不完整做复杂多人格，但必须预留：

1. personas 表；
2. conversations.persona_id；
3. Prompt 注入当前人格；
4. 后续可在人格中心切换。

第一阶段规则：

```text
长期记忆：全局共享
人格设定：会话级选择
会话摘要：绑定会话
项目记忆：全局共享
```

暂时不做：

```text
每个人格独立记忆
人格之间互相知道彼此
多人格群聊
人格市场
人格成长系统
```

---

# 10. 上下文窗口与压缩策略

这是 v1.1 新增的 P0 模块。

## 10.1 为什么必须做

长期记忆解决跨会话问题，但不解决当前会话无限变长的问题。

如果当前会话一直聊下去，会发生：

1. Prompt 太长；
2. 请求变慢；
3. Token 成本上升；
4. 模型报 context length exceeded；
5. 早期重要信息被截断；
6. AI 在当前会话里也开始“失忆”。

所以必须引入：

```text
Context Window Manager
Rolling Summary
Token Budget
超限自动压缩重试
```

---

## 10.2 三类上下文

| 类型 | 作用 | 是否长期记忆 |
|---|---|---|
| Long-term Memory | 跨会话记住用户和项目 | 是 |
| Rolling Summary | 当前长会话续聊 | 否 |
| Recent Raw Messages | 保留最近细节 | 否 |

核心原则：

```text
长期记忆负责跨会话连续性
滚动摘要负责当前长会话连续性
最近原文负责细节准确性
```

---

## 10.3 Context Window Manager 流程

```text
用户发送消息
  ↓
加载当前 Persona
  ↓
根据 use_memory 召回长期 Memory
  ↓
加载当前会话 rolling_summary
  ↓
加载最近原始消息
  ↓
估算 Prompt 长度
  ↓
如果未超过预算：直接请求模型
  ↓
如果超过预算：压缩旧消息，更新 rolling_summary
  ↓
重新组装 Prompt
  ↓
请求模型
```

---

## 10.4 上下文预算

用户可以在设置页配置：

```text
Context Window Tokens
Max Output Tokens
Safety Margin
```

默认：

```text
Context Window Tokens = 32000
Max Output Tokens = 4096
Safety Margin = 2000
```

可用输入预算：

```text
可用输入预算 = Context Window Tokens - Max Output Tokens - Safety Margin
```

第一版可以用粗略字符数估算，不强制精准 tokenizer。

中文粗估：

```text
1 个中文字符 ≈ 1～2 tokens
```

Agent 实现时可先用：

```text
estimated_tokens = text.length / 2 到 text.length
```

保守一点更安全。

---

## 10.5 预算分配建议

```text
System + Persona：10%
长期记忆：15%
Rolling Summary：20%
最近原始消息：50%
当前用户输入：必须保留
```

注意：

1. 当前用户输入永远不能被压缩；
2. 最近几轮原始消息优先保留；
3. 旧消息优先压缩进 rolling_summary；
4. 长期记忆过多时也要截断，只保留最相关的 8～10 条。

---

## 10.6 压缩触发条件

第一阶段支持：

```text
1. 当前会话消息数超过 30 条
2. 估算 Prompt 超过可用预算的 70%
3. 模型返回 context_length_exceeded 错误
4. 用户手动点击“压缩当前会话上下文”（P1）
```

最重要的是：

```text
预算超限前主动压缩
context_length_exceeded 后自动压缩并重试一次
```

---

## 10.7 Rolling Summary 策略

不要无限堆叠摘要。

错误方式：

```text
summary_1
summary_2
summary_3
summary_4
...
```

正确方式：

```text
旧 rolling_summary + 新一批待压缩旧消息
  ↓
生成新的 rolling_summary
  ↓
替换旧 rolling_summary
```

每个会话始终维护一份当前 rolling_summary。

---

## 10.8 原始消息保留策略

压缩不等于删除。

```text
完整消息仍保存在 messages 表
Prompt 只使用 rolling_summary + 最近原始消息
```

推荐保留：

```text
最近 8～12 轮原始对话
```

更早的消息被标记为：

```text
compressed_in_summary_id = 当前 summary_id
```

---

## 10.9 use_memory / generate_memory 与 rolling_summary 的关系

### use_memory = false

不使用长期记忆。

但仍可以使用当前会话 rolling_summary，因为它属于当前会话上下文，不是跨会话记忆。

### generate_memory = false

不生成长期记忆。

但仍可以生成当前会话 rolling_summary，因为它只是当前会话压缩，不进入记忆中心，也不跨会话召回。

### 未来可加隐私会话

隐私会话可以定义为：

```text
不保存聊天记录
不生成 rolling_summary
不生成长期记忆
不导出
```

第一阶段暂缓。

---

## 10.10 上下文压缩 Prompt

建议使用 Markdown 输出，不强制 JSON，因为摘要是给模型读的，不是结构化导入。

```text
你是一个会话上下文压缩器。

你的任务是把较长的历史对话压缩成一份紧凑、准确、可继续对话的状态摘要。

要求：
1. 保留用户目标、当前任务、已确认决策、关键约束、待办问题。
2. 保留对后续回答有影响的信息。
3. 删除寒暄、重复内容、无关闲聊。
4. 不要把临时情绪写成长期事实。
5. 不要保存 API Key、密码、Token 等敏感信息。
6. 不要编造原文没有的信息。
7. 摘要用于当前会话继续，不等同于长期记忆。

输出 Markdown，结构如下：

# 当前会话压缩摘要

## 用户当前目标
- ...

## 已确认决策
- ...

## 关键约束
- ...

## 当前进展
- ...

## 待解决问题
- ...

## 后续回答注意事项
- ...
```

---

## 10.11 Prompt 组装顺序

最终请求模型时，Prompt 分层如下：

```text
[System Base Rules]

[Active Persona]
当前人格设定

[User Memories]
长期记忆，最多 8～10 条

[Conversation Rolling Summary]
当前会话压缩摘要，如果存在

[Recent Raw Messages]
最近 8～12 轮原始消息

[Current User Message]
当前用户输入
```

---

# 11. Prompt 模板

## 11.1 记忆抽取 Prompt

```text
你是一个长期记忆整理器。

你的任务是从本轮对话中提取值得未来继续使用的记忆。

只保存明确、稳定、对未来对话有帮助的信息。
不要保存琐碎事实、临时情绪、玩笑、猜测、敏感隐私、API Key、密码、Token。
如果信息只是当前任务中的过程，不要保存为长期记忆，可以写入会话摘要。
如果信息会影响用户长期偏好、项目背景、长期目标、回答方式，才生成记忆。

请结合已有记忆判断：
1. 是否有新增记忆
2. 是否需要更新旧记忆
3. 是否存在冲突
4. 是否应该忽略

输出严格 JSON，不要输出 Markdown。

JSON 格式如下：

{
  "new_memories": [
    {
      "type": "profile | preference | project | summary",
      "content": "记忆内容，使用简洁中文",
      "importance": 1,
      "confidence": 0.0,
      "status_suggestion": "active | pending",
      "reason": "为什么值得保存",
      "source_message_ids": []
    }
  ],
  "updates": [
    {
      "target_memory_id": "旧记忆 ID",
      "new_content": "更新后的记忆内容",
      "reason": "为什么需要更新",
      "status_suggestion": "active | pending"
    }
  ],
  "discarded": [
    {
      "content": "被丢弃的信息",
      "reason": "为什么不保存"
    }
  ]
}
```

---

## 11.2 记忆注入 Prompt

```text
以下是用户的长期记忆、项目上下文和最近会话摘要。
请自然参考这些信息回答，不要机械复述，不要频繁说“根据我的记忆”。
如果记忆与用户当前消息冲突，以用户当前明确表达为准。
如果不确定，不要编造。

[当前人格]
{{persona}}

[用户偏好]
{{preference_memories}}

[用户画像]
{{profile_memories}}

[项目记忆]
{{project_memories}}

[当前会话压缩摘要]
{{rolling_summary}}

[最近会话摘要]
{{summary_memories}}
```

---

## 11.3 人格 Prompt

```text
你当前使用的人格如下：

名称：{{name}}
关系定位：{{role}}
语气风格：{{tone}}
行为规则：
{{behavior_rules}}

边界：
{{boundaries}}

主动程度：{{proactivity}}

请按照该人格与用户互动，但不要违背系统基础规则。
```

---

# 12. Kotlin 接口建议

## 12.1 LlmProvider

```kotlin
interface LlmProvider {
    fun streamChat(request: ChatRequest): Flow<ChatChunk>

    suspend fun complete(request: ChatRequest): ChatResponse

    suspend fun extractMemories(
        conversation: Conversation,
        messages: List<Message>,
        existingMemories: List<Memory>
    ): MemoryExtractionResult

    suspend fun summarizeConversation(
        oldSummary: String?,
        messagesToCompress: List<Message>
    ): String
}
```

---

## 12.2 MemoryRepository

```kotlin
interface MemoryRepository {
    suspend fun insert(memory: Memory)

    suspend fun update(memory: Memory)

    suspend fun disable(memoryId: String)

    suspend fun delete(memoryId: String)

    suspend fun getActiveMemories(): List<Memory>

    suspend fun getPendingMemories(): List<Memory>

    suspend fun searchMemories(query: MemoryQuery): List<Memory>

    suspend fun getMemoriesByType(type: MemoryType): List<Memory>
}
```

---

## 12.3 MemoryRecallEngine

```kotlin
interface MemoryRecallEngine {
    suspend fun recall(
        userMessage: String,
        conversation: Conversation,
        persona: Persona?
    ): MemoryRecallResult
}
```

---

## 12.4 ContextWindowManager

```kotlin
interface ContextWindowManager {
    suspend fun buildPromptContext(
        conversationId: String,
        currentUserMessage: String,
        persona: Persona?,
        recalledMemories: List<Memory>,
        settings: ModelContextSettings
    ): PromptContext

    suspend fun shouldCompress(
        conversationId: String,
        estimatedPromptTokens: Int,
        settings: ModelContextSettings
    ): Boolean

    suspend fun compressIfNeeded(
        conversationId: String,
        settings: ModelContextSettings
    ): ConversationSummary?
}
```

---

## 12.5 PersonaRepository

```kotlin
interface PersonaRepository {
    suspend fun getDefaultPersona(): Persona?

    suspend fun getPersona(id: String): Persona?

    suspend fun listPersonas(): List<Persona>

    suspend fun savePersona(persona: Persona)

    suspend fun deletePersona(id: String)
}
```

---

## 12.6 ExportImportService

```kotlin
interface ExportImportService {
    suspend fun exportMemoriesJson(): String

    suspend fun exportMemoriesMarkdown(): String

    suspend fun importMemoriesJson(json: String): ImportResult

    suspend fun exportPersonasJson(): String

    suspend fun importPersonasJson(json: String): ImportResult

    suspend fun exportChatHistoryJson(): String
}
```

---

## 12.7 SearchProvider

第一阶段只预留。

```kotlin
interface SearchProvider {
    suspend fun search(query: String): List<SearchResult>
}
```

---

# 13. 导入导出格式

## 13.1 memories.json

```json
{
  "version": "1.0",
  "exported_at": "2026-05-30T00:00:00Z",
  "app": "Permanent Memory Chat",
  "memories": [
    {
      "id": "mem_001",
      "type": "preference",
      "content": "用户喜欢直接、明确、有判断的中文回答。",
      "status": "active",
      "importance": 5,
      "confidence": 0.95,
      "source_message_ids": ["msg_001", "msg_002"],
      "source_conversation_id": "conv_001",
      "created_at": "2026-05-30T00:00:00Z",
      "updated_at": "2026-05-30T00:00:00Z",
      "last_used_at": null
    }
  ]
}
```

---

## 13.2 memories.md

```markdown
# Long-term Memories

Exported at: 2026-05-30

## User Profile

- 用户正在做一个拥有永久记忆的聊天 APP。

## Preferences

- 用户喜欢中文回答。
- 用户喜欢直接、明确、有判断的回答。

## Projects

- 永久记忆聊天 APP：第一阶段优先验证记忆系统。
- 聊天壳保持简单，知识库暂缓。

## Session Summaries

- 本轮讨论确认：第一阶段采用本地优先架构，不做云端同步。
```

---

## 13.3 personas.json

```json
{
  "version": "1.0",
  "exported_at": "2026-05-30T00:00:00Z",
  "personas": [
    {
      "id": "persona_default",
      "name": "产品技术合伙人",
      "description": "适合产品方案、技术方案、Agent 任务拆解的默认人格。",
      "role": "长期协作伙伴",
      "tone": "直接、明确、有判断",
      "behavior_rules": [
        "优先指出方案漏洞",
        "给出可执行建议",
        "避免空泛安慰",
        "不确定时直接说明"
      ],
      "boundaries": [
        "不要假装知道不存在的信息",
        "不要把临时偏好当长期偏好"
      ],
      "proactivity": 4,
      "is_default": true
    }
  ]
}
```

---

# 14. MiMo / 非 GPT 模型适配要求

因为第一阶段计划用 MiMo 写代码和测试，必须假设模型不一定稳定输出严格 JSON。

## 14.1 记忆抽取 JSON 容错

系统必须能处理：

1. JSON 被 Markdown 代码块包住；
2. JSON 前后有解释废话；
3. 字段名轻微错误；
4. 输出空文本；
5. 输出非 JSON；
6. 输出重复记忆；
7. 输出内容缺字段。

推荐策略：

```text
先尝试直接 JSON parse
失败后提取 ```json ... ``` 代码块
再失败后尝试截取第一个 { 到最后一个 }
再失败则本轮记忆抽取失败，但 APP 不崩溃
```

失败时：

```text
不新增记忆
Debug 页记录错误
用户可点击重试
聊天功能不受影响
```

---

## 14.2 记忆抽取失败不能影响聊天

记忆是增强能力，不能成为聊天主流程单点故障。

```text
聊天成功 + 记忆整理失败 = 允许
聊天失败 + 记忆成功 = 不应出现
```

聊天主流程优先级高于记忆整理。

---

# 15. 搜索能力策略

第一阶段搜索不是主线。

只做：

```text
SearchProvider 接口
设置页搜索开关
聊天页手动搜索按钮可选
搜索结果注入 Prompt 的能力预留
```

原则：

```text
搜索结果默认只用于当前回答
搜索结果不自动进入长期记忆
除非用户明确保存或确认搜索结论
```

避免把价格、新闻、版本、API 资费等会变化的信息污染长期记忆。

---

# 16. 开发顺序

## 阶段 1：项目骨架

1. 创建 Android 项目；
2. 接入 Jetpack Compose；
3. 建立 Room；
4. 建立 DataStore；
5. 建立基础导航；
6. 建立 Debug 页面空壳。

## 阶段 2：设置与模型调用

1. 设置 API Key / Base URL / Model；
2. API Key 加密存储；
3. 实现 OpenAI-compatible Provider；
4. 实现流式输出；
5. 错误处理。

## 阶段 3：聊天壳

1. 新建会话；
2. 会话列表；
3. 消息发送；
4. 消息保存；
5. 停止生成；
6. 会话恢复。

## 阶段 4：记忆中心

1. memories 表；
2. MemoryRepository；
3. 手动新增记忆；
4. 查看记忆；
5. 编辑记忆；
6. 删除记忆；
7. 禁用记忆；
8. 查看来源。

## 阶段 5：记忆抽取

1. 记忆抽取 Prompt；
2. 模型输出 JSON 容错；
3. pending / active 分级；
4. 旧记忆更新；
5. tombstone 防重复生成；
6. Debug 展示。

## 阶段 6：记忆召回

1. 场景识别；
2. 规则召回；
3. Prompt 注入；
4. use_memory 开关；
5. Debug 展示召回过程。

## 阶段 7：人格系统

1. personas 表；
2. 默认人格；
3. 编辑默认人格；
4. conversations.persona_id；
5. Prompt 注入人格；
6. 预留多人格。

## 阶段 8：上下文压缩

1. conversation_summaries 表；
2. Token / 字符预算估算；
3. rolling_summary 生成；
4. 压缩旧消息；
5. 保留最近原始消息；
6. 超限错误自动压缩并重试一次；
7. Debug 显示压缩状态。

## 阶段 9：导入导出

1. 导出 memories.json；
2. 导出 memories.md；
3. 导入 memories.json；
4. 导出 personas.json；
5. 导入 personas.json；
6. 导入格式校验。

---

# 17. P0 测试用例清单

## 17.1 基础聊天

| 编号 | 测试点 | 预期 |
|---|---|---|
| TC-P0-001 | 首次启动 APP | 不崩溃，可进入设置、聊天、记忆中心 |
| TC-P0-002 | 配置模型信息 | Base URL、Model 保存，API Key 加密 |
| TC-P0-003 | 发送第一条消息 | 能回复，消息本地保存 |
| TC-P0-004 | API 错误 | 不崩溃，显示明确错误 |

---

## 17.2 记忆抽取

| 编号 | 输入 | 预期 |
|---|---|---|
| TC-MEM-001 | “以后用中文，直接给结论” | 生成 preference |
| TC-MEM-002 | “我在做永久记忆 Android APP” | 生成 project |
| TC-MEM-003 | “今天有点烦” | 不生成长期记忆 |
| TC-MEM-004 | “我是宇宙第一产品经理，开玩笑” | 不保存玩笑 |
| TC-MEM-005 | “我的 API Key 是 xxx” | 不保存敏感信息 |
| TC-MEM-006 | “今天中午吃了拉面” | 不生成长期记忆 |
| TC-MEM-007 | “记住，第一阶段不做云端” | 生成 active project |
| TC-MEM-008 | “哈哈” | 不新增记忆，不崩溃 |

---

## 17.3 记忆冲突

| 编号 | 场景 | 预期 |
|---|---|---|
| TC-CONFLICT-001 | 项目方向从知识库改为记忆系统 | 更新旧 project，不保留冲突 active |
| TC-CONFLICT-002 | 日常温柔，工作直接 | 合并为场景化偏好 |
| TC-CONFLICT-003 | 用户手动编辑过的记忆 | 不允许自动覆盖 |
| TC-CONFLICT-004 | 用户删除“猫娘语气”记忆 | 不反复生成 |

---

## 17.4 记忆中心

| 编号 | 测试点 | 预期 |
|---|---|---|
| TC-CENTER-001 | 查看记忆列表 | 按类型分组 |
| TC-CENTER-002 | 编辑记忆 | 内容更新，user_edited=true |
| TC-CENTER-003 | 禁用记忆 | 不参与召回 |
| TC-CENTER-004 | 删除记忆 | 不显示，不导出 |
| TC-CENTER-005 | 查看来源 | 能定位原始消息 |

---

## 17.5 记忆召回

| 编号 | 场景 | 预期 |
|---|---|---|
| TC-RECALL-001 | 跨会话问第一阶段重点 | 能答“优先记忆系统” |
| TC-RECALL-002 | 普通闲聊 | 不注入大量项目记忆 |
| TC-RECALL-003 | 技术方案讨论 | 召回 project 和 preference |
| TC-RECALL-004 | use_memory=false | 不使用长期记忆 |
| TC-RECALL-005 | disabled 记忆 | 不参与召回 |

---

## 17.6 人格系统

| 编号 | 测试点 | 预期 |
|---|---|---|
| TC-PERSONA-001 | 默认人格存在 | 字段完整，可编辑 |
| TC-PERSONA-002 | 人格和记忆分离 | persona 不进入 memories |
| TC-PERSONA-003 | 会话绑定 persona_id | conversations.persona_id 有值 |
| TC-PERSONA-004 | 修改人格影响回答 | 风格变化，记忆不变 |

---

## 17.7 上下文压缩

| 编号 | 场景 | 预期 |
|---|---|---|
| TC-CTX-001 | 长会话超过 40 轮 | 生成 rolling_summary |
| TC-CTX-002 | 压缩后问早期决策 | 能回答早期决策 |
| TC-CTX-003 | use_memory=false | 仍可用当前会话 rolling_summary |
| TC-CTX-004 | rolling_summary | 不进入长期记忆中心 |
| TC-CTX-005 | context_length_exceeded | 自动压缩并重试一次 |
| TC-CTX-006 | 最近消息 | 最近 8～12 轮保留原文 |
| TC-CTX-007 | 原文保存 | 压缩不删除 messages 原文 |

---

## 17.8 导入导出

| 编号 | 测试点 | 预期 |
|---|---|---|
| TC-EXPORT-001 | 导出 memories.json | JSON 合法，不含敏感信息 |
| TC-EXPORT-002 | 导出 memories.md | 人类可读，按类型分组 |
| TC-IMPORT-001 | 导入合法 JSON | 成功导入 |
| TC-IMPORT-002 | 导入非法 JSON | 不崩溃 |
| TC-IMPORT-003 | 导入缺字段 JSON | 提示错误或补默认值 |

---

## 17.9 MiMo JSON 容错

| 编号 | 场景 | 预期 |
|---|---|---|
| TC-JSON-001 | Markdown 包裹 JSON | 能解析 |
| TC-JSON-002 | JSON 前后有废话 | 尽量提取 |
| TC-JSON-003 | 字段名错误 | 不盲目写入脏数据 |
| TC-JSON-004 | 非 JSON 输出 | 不崩溃，不新增记忆 |
| TC-JSON-005 | 重复记忆 | 不重复插入 active |

---

# 18. 端到端验收

## E2E-001：完整记忆闭环

步骤：

1. 清空 APP；
2. 配置 API；
3. 新建会话 A；
4. 输入：
   > 我正在做一个拥有永久记忆的 Android 聊天 APP，第一阶段优先调好记忆系统，聊天壳简单一点。
5. AI 回复；
6. 点击整理记忆；
7. 进入记忆中心确认生成 project 记忆；
8. 新建会话 B；
9. 输入：
   > 我们这个 APP 第一阶段重点是什么？
10. 查看 AI 回复和 Debug 页。

预期：

1. 会话 A 消息保存；
2. 生成 project 记忆；
3. 会话 B 能召回 project 记忆；
4. AI 回答第一阶段重点是记忆系统；
5. Debug 页显示召回来源；
6. 重启 APP 后仍存在。

---

## E2E-002：长会话上下文压缩闭环

步骤：

1. 新建会话；
2. 第 1 轮输入：
   > 这个 APP 第一阶段不做云端同步。
3. 继续聊 40 轮；
4. 触发 rolling_summary；
5. 输入：
   > 我们第一阶段做不做云端？
6. 查看 Debug 页。

预期：

1. APP 生成 rolling_summary；
2. 最近消息保留原文；
3. 早期消息被压缩进摘要；
4. AI 回答第一阶段不做云端；
5. rolling_summary 不进入长期记忆中心。

---

## E2E-003：导出、清空、导入恢复

步骤：

1. 生成至少 3 条记忆；
2. 导出 memories.json；
3. 清空 APP 数据；
4. 重新打开 APP；
5. 导入 memories.json；
6. 新建会话问：
   > 你还记得我这个 APP 的第一阶段重点吗？

预期：

1. 导入成功；
2. 记忆中心恢复原记忆；
3. AI 可以召回导入后的记忆；
4. 导出/导入闭环成立。

---

# 19. 给代码生成 Agent 的硬性约束

实现时必须遵守：

1. 不要把 API Key 写入日志；
2. 不要把 API Key 写入聊天记录；
3. 不要把 API Key 写入记忆；
4. 模型返回 JSON 不稳定时 APP 不能崩溃；
5. 记忆抽取失败时，不影响正常聊天；
6. use_memory 和 generate_memory 是两个独立开关；
7. rolling_summary 不等于长期记忆；
8. rolling_summary 不显示在长期记忆中心；
9. persona 不要混入 memories 表；
10. memories.json 和 personas.json 必须分开；
11. 用户删除/禁用的记忆不参与召回；
12. 用户手动编辑过的记忆不能被自动覆盖；
13. 导入 JSON 前必须做格式校验；
14. 导出文件不得包含敏感凭据；
15. Debug 页可以显示 Prompt，但必须打码敏感信息；
16. 压缩上下文不能删除原始消息；
17. context_length_exceeded 必须捕获并自动压缩重试一次；
18. 第一阶段不要实现云同步、账号系统、复杂知识库；
19. 聊天主流程优先级高于记忆整理；
20. 记忆系统默认保守：宁可少记，不要乱记。

---

# 20. 最小通过标准

如果时间有限，至少必须通过：

```text
TC-P0-001 首次启动
TC-P0-002 配置模型
TC-P0-003 发送第一条消息
TC-P0-004 API 错误不崩溃
TC-MEM-001 明确偏好保存
TC-MEM-002 项目背景保存
TC-MEM-003 临时情绪不保存
TC-MEM-005 敏感信息不保存
TC-CONFLICT-001 项目方向更新
TC-CENTER-002 编辑记忆
TC-CENTER-003 禁用记忆
TC-CENTER-004 删除记忆
TC-RECALL-001 跨会话召回
TC-RECALL-004 关闭 use_memory 不召回
TC-PERSONA-002 人格和记忆分离
TC-CTX-001 长会话触发压缩
TC-CTX-002 压缩后仍记得早期决策
TC-CTX-005 上下文超限自动重试
TC-EXPORT-001 导出 memories.json
TC-IMPORT-001 导入 memories.json
TC-JSON-004 非 JSON 输出不崩溃
E2E-001 完整记忆闭环
E2E-002 长会话压缩闭环
E2E-003 导出导入恢复
```

---

# 21. 最终结论

第一阶段的目标不是做一个功能很全的聊天 APP，而是验证这几个核心能力：

```text
简单聊天
本地持久化
长期记忆
人格分离
上下文压缩
用户可控
导入导出
MiMo 容错
```

最重要的产品判断：

> 这个 APP 的核心资产不是模型，也不是聊天 UI，而是用户可控、可迁移、可持续更新的长期记忆资产。

第一阶段只要把下面这个闭环跑通，就算成功：

```text
聊天
  ↓
原始消息持久化
  ↓
长期记忆抽取
  ↓
记忆中心可控
  ↓
跨会话召回
  ↓
长会话 rolling_summary 压缩
  ↓
人格分离注入
  ↓
导出导入迁移
```

后续再扩展知识库、搜索、云同步、多端、多人格、Agent 自动任务，都不会偏离主线。
