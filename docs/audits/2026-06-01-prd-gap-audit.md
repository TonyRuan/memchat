# PRD Gap Audit - 2026-06-01

## 结论

当前实现更接近“基础聊天 + 手动记忆抽取 + 记忆管理雏形”，还没有达到 PRD v1.1 的 P0 闭环。`test` 和 `assembleDebug` 能通过，但现有测试主要证明少量 JVM 逻辑，不足以证明 PRD 要求的长期记忆、人格分离、上下文压缩、Debug 可解释性、导入导出和真实端到端链路。

本次审计采用主线程整合 + 4 个只读子代理并行扫描：数据/存储、聊天/记忆/上下文、UI/交互、测试/构建。基线验证命令已通过：

- `.\gradlew.bat test`
- `.\gradlew.bat assembleDebug`

## P0 / 阻塞偏差

1. 上下文压缩基本缺失。
   - PRD 要求消息过长时生成摘要、压缩旧消息、标记已压缩内容，并在 `context_length_exceeded` 后压缩重试。
   - 当前 Room 只有 `conversations`、`messages`、`memories`、`memory_tombstones`、`personas`，没有 `conversation_summaries`；`MessageEntity` 也没有 `compressedInSummaryId`、`deleted` 等字段。
   - `ChatViewModel.sendMessage()` 直接把当前 `_messages` 全量拼进请求，没有 token 预算、摘要插入、旧消息截断或压缩重试。

2. 自动记忆抽取没有接入发送链路。
   - PRD 的核心闭环是“聊天后自动抽取/更新长期记忆”。
   - 当前 `ChatViewModel.extractMemories()` 只由聊天页按钮触发；`sendMessage()` 在 assistant 消息保存后没有调用抽取流程。
   - 因此真实聊天不会稳定地产生长期记忆，除非用户手动点按钮。

3. Debug 页面不能可靠展示最近一次召回。
   - `DebugScreen()` 默认创建自己的 `ChatViewModel = viewModel()`，读取的是该实例的 `lastRecallResult`。
   - 聊天页发送消息使用的是聊天页实例，Debug 页实例通常没有刚才的召回状态。
   - 召回详情也没有持久化到调试表或日志结构，页面切换后不可解释。

4. Persona Center / 默认人格编辑能力缺失。
   - PRD 要求人格中心、默认人格配置、人格与记忆/对话隔离和导入导出闭环。
   - 当前只有默认人格创建、会话保存 personaId、人格 JSON 导出；没有人格列表/编辑/删除 UI，也没有人格导入。

5. 新建会话存在保存/跳转竞态。
   - `ConversationListViewModel.createConversation()` 先创建 `Conversation`，异步保存带默认 persona/useMemory/generateMemory 的副本，然后立即返回原始 id。
   - UI 可能在 DB 写入完成前跳转到聊天页，导致聊天页查不到会话或默认配置尚未落库。

6. ADB 输入链路绕过会话的 `useMemory=false`。
   - PRD 要求会话级“使用记忆/生成记忆”开关可控。
   - `AdbInputReceiver` 只要数据库有 active memories 就召回并注入系统提示，没有检查当前 conversation 的 `useMemory`。

7. 用户编辑和删除保护不完整。
   - PRD 要求用户手动编辑/删除的记忆不能被模型轻易覆盖或复活。
   - `extractMemories()` 的 update 分支直接覆盖 existing memory，没有跳过 `userEdited=true`。
   - disable 只改状态，不写 tombstone；`isTombstoned(content, type)` 实际只按内容 fingerprint 查询，忽略 type。

## 高风险问题

- 敏感信息过滤主要依赖提示词，没有硬过滤或可测试规则；API key、隐私字段、凭证类内容仍可能被抽成记忆。
- ADB 日志会输出用户消息片段、召回记忆内容和 API 响应片段；调试阶段可用，但不符合隐私最小暴露。
- `OpenAICompatibleProvider.complete()` 遇到 HTTP/解析/网络错误返回空字符串，调用方可能保存空 assistant 消息，错误不可追踪。
- 流式请求失败时错误 assistant 消息只写到 UI 状态，不保存 DB；用户返回会话后错误上下文丢失。
- Memory source view 只能显示来源消息数量；抽取时未写 `sourceMessageIds`，无法定位原文。
- 设置页加载并保存 `maxTokens`，但没有输入控件；用户无法在 UI 修改。
- 导入记忆只给“导入完成”Toast，即使有错误也不展开错误明细；也没有导入人格入口。
- 召回策略目前偏静态分组和提示词拼接，缺少 PRD 要求的多路径召回、排序、压缩和场景化策略验证。
- 数据库缺少外键/索引/迁移策略，`exportSchema=false` 会增加后续迁移和回归风险。

## 测试偏差

当前 JVM 测试是必要起点，但覆盖不够：

- 没有 `androidTest`，无法验证 Room、EncryptedSharedPreferences/DataStore、Compose 导航和真实 Android 生命周期。
- 没有 fake/mock LLM 端到端测试来证明发送消息后会自动抽取、更新、召回、注入、压缩。
- 没有 `context_length_exceeded`、摘要重试、旧消息压缩、source message 定位的测试。
- 没有 ADB broadcast E2E 测试，无法覆盖自动化输入链路是否遵守会话开关。
- 没有导入导出兼容性、去重、tombstone、防复活的完整夹具。
- 没有 APK 启动/主流程 smoke test；`assembleDebug` 只证明能打包。

## 建议修复顺序

1. 先补验证底座：androidTest 依赖、Room/设置存储测试、fake LLM、聊天主链路 E2E、ADB broadcast smoke。
2. 修 P0 用户控制和数据安全：自动抽取接入发送链路、尊重 `useMemory/generateMemory`、保护 `userEdited`、删除/禁用 tombstone、错误持久化。
3. 实现上下文压缩数据模型和策略：`conversation_summaries`、消息压缩标记、token 预算、`context_length_exceeded` 压缩重试、Debug 展示原始压缩过程。
4. 补 Persona Center：人格列表/编辑/默认选择/导入导出，并验证人格隔离。
5. 完善 Debug 和来源追踪：持久化召回记录、保存 `sourceMessageIds`、支持从记忆跳转来源消息。
6. 再优化召回策略、导入导出 UX 和隐私过滤规则。

## 下一步交付标准

后续每个 P0 修复都应至少包含：

- PRD 对应条款说明。
- 自动化测试或明确的手工/ADB 验证记录。
- 版本号、`DEVLOG.md`、Debug APK。
- 独立 Git commit，避免混入旧的无关源码改动。
