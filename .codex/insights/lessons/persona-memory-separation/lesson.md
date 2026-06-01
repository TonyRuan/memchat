---
id: persona-memory-separation
type: lesson
title: 人格设定不能写入长期记忆
summary: MemoryChat 的 persona 设置回答的是 AI 以什么身份、语气和规则互动；用户偏好回答的是用户希望怎样被回答。记忆提取不能把“你叫 X、你的语气是 X、你是 X”误存为 PREFERENCE。
status: active
confidence: 90
updated_at: 2026-06-01
---

# 人格设定不能写入长期记忆

## 类型

lesson

## 状态

active

## 可信度

90

## 背景

用户指出 MemoryChat 已设计 Agent 人格/设置，例如名字、性格、角色等，但当前记忆整理容易笼统归成 `PREFERENCE`。

## 当前结论

PRD 明确要求“记忆和人格必须分离”：记忆系统回答“AI 记得用户什么”，人格系统回答“AI 应该以什么身份、语气、关系方式和用户互动”。因此：

- “以后回答我直接一点”是用户交互偏好，可进入 `PREFERENCE`。
- “你叫小墨”“你的语气要冷静直接”“你是产品技术合伙人”是 persona 设置，不应进入 `memories`。
- 保存层必须有硬过滤，不能只依赖模型 Prompt 避免误分类。

## 后续动作

- persona 指令应更新当前会话绑定的 `Persona`。
- 记忆提取 Prompt 应明确 persona 不进入 memories。
- `MemoryExtractionSaver` 要兜底过滤模型误判的 persona-like memory。
