# MemoryChat 文档索引

本目录用于保存产品审计、架构设计和实施计划。当前产品边界仍以根目录 PRD 为准：

- `../permanent_memory_chat_app_full_prd_v1_1---9fb30508-acaa-42b4-9739-6c4f760b7dea.md`

## 架构文档

- `architecture/agent-tool-runtime.md`
  - 当前 Agent 工具运行时的设计与实现状态。
  - 说明静态 skill / 静态工具清单、Persona 与 Memory 分层、只读/写入工具边界，以及后续动态 skill 路线。

## 审计文档

- `audits/2026-06-01-prd-gap-audit.md`
  - 对照 PRD 的差距审计。
  - 后续做 P0/P1 修复前应先看这里，避免重复扫描。

## 实施计划

- `superpowers/plans/2026-06-01-prd-repair-validation.md`
  - 以 PRD 为目标的修复和验证计划。
- `superpowers/plans/2026-06-01-agent-tool-runtime.md`
  - Agent 工具运行时第一阶段计划，当前已落地。
- `superpowers/plans/2026-06-01-chat-tool-traces.md`
  - 聊天页工具调用轨迹计划，当前已落地。

## 维护约定

- 已完成的实施计划要标注完成状态，避免后续 agent 误判为待办。
- 架构文档要区分“已实现”“静态预留”“后续路线”。
- 文档整理不影响 APK 行为时，不单独更新 `app/build.gradle` 版本号。
