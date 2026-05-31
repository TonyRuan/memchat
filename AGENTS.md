# AGENTS.md

## 协作方式

- 尽量使用中文交流。
- 任务可安全拆分时，适时使用子代理并行收集信息或验证。
- 修改前先快速理解现有实现，优先沿用本项目已有结构和风格。

## 项目概览

- 这是 `MemoryChat`，一个 Android/Kotlin/Jetpack Compose 聊天应用。
- 核心能力包括会话聊天、长期记忆抽取与召回、记忆中心、人格配置、设置、日志和 Debug 页面。
- 包名为 `com.memorychat.app`，主要代码在 `app/src/main/java/com/memorychat/app`。
- 产品目标和验收边界以 `permanent_memory_chat_app_full_prd_v1_1---9fb30508-acaa-42b4-9739-6c4f760b7dea.md` 为准。
- 第一阶段核心是验证本地优先的长期记忆、人格分离、上下文压缩、导入导出闭环，不做云同步、账号系统、复杂知识库或多 Agent。

## 常用命令

- 运行单元测试：`.\gradlew.bat test`
- 构建 Debug APK：`.\gradlew.bat assembleDebug`
- 查看可用 Gradle 任务：`.\gradlew.bat tasks`

## 开发交付约定

- 每次有效项目变更交付前，都要同步更新 `app/build.gradle` 中的 `versionCode` 和 `versionName`。
- 每次有效项目变更都要写 `DEVLOG.md`，记录版本、日期、关键变更、验证结果和 APK 输出情况。
- 每次有效项目变更完成验证后，都要用 Git 做一次范围清晰的保存；只提交本次相关文件，不混入无关改动。
- 关键功能、体验、配置或验收相关变更交付时，默认运行 `.\gradlew.bat assembleDebug` 产出 Debug APK，并在交付说明中给出 APK 路径。
- 本机私密配置可以写入被 Git 忽略的文件，例如 `local.properties`，但不得提交 API Key、Token 或个人密钥。
- 纯只读分析、临时排查、用户明确要求不落盘的草稿，不需要更新版本号、写日志、提交或构建 APK。

## 改动约束

- 不要随意改动根目录已有 APK 产物，除非任务明确要求重新打包或发布。
- API Key 等敏感配置应继续走 `SettingsDataStore` 的加密存储路径。
- 记忆相关逻辑优先检查 `MemoryEngine`、`ChatViewModel`、`MemoryRepository` 和 Room DAO 的交互。
- UI 改动应优先保持 Compose + Material3 的现有实现风格。

## 验证建议

- 纯逻辑或数据层改动至少运行 `.\gradlew.bat test`。
- 涉及聊天、记忆召回、设置或 UI 流程时，优先构建 `assembleDebug`，必要时用模拟器或 ADB 广播做端到端验证。
- 涉及产品范围、P0/P1 优先级或验收标准时，先对照 PRD 第 3、17、18、20 节。
