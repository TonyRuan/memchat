# 项目洞察索引

> 此文件由 `feedback-insights` 技能脚本生成。手工修改可能在下次重建时被覆盖。

## 教训

| ID | 标题 | 状态 | 可信度 | 更新 | 摘要 |
| --- | --- | --- | --- | --- | --- |
| emulator-dialogue-validation-gap | 模拟器对话验证不能用首启 smoke 代替 | active | 95 | 2026-06-01 | 对 MemoryChat 的 PRD 修复交付，不能把 connectedDebugAndroidTest 首启通过表述为已经做过真实对话流程验证；真实聊天、模型回复、自动抽取、召回和记忆中心检查需要单独跑并记录证据。|

## 偏好

| ID | 标题 | 状态 | 可信度 | 更新 | 摘要 |
| --- | --- | --- | --- | --- | --- |
| prd-aligned-subagent-testing | PRD 对齐的子代理测试偏好 | active | 90 | 2026-06-01 | 做测试审查和测试重构时，尽量使用子代理并行，降低上下文污染；测试要按 PRD 和项目真实行为重构，不沿用不严谨旧测试。|
| development-delivery-process | 开发交付流程偏好 | active | 90 | 2026-06-01 | 影响应用行为或可测试产物的有效变更需同步版本号、写开发日志、Git 保存；关键交付构建 Debug APK 方便测试。|
| memory-extraction-trigger-policy | 记忆提取触发策略偏好 | active | 90 | 2026-06-01 | 明确记忆指令应立即后台提取；普通对话不应每轮触发模型整理，默认按批量阈值和离开会话时后台整理，避免影响聊天体验和 API 成本。|
