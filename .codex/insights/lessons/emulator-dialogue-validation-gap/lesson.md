---
id: emulator-dialogue-validation-gap
type: lesson
title: 模拟器对话验证不能用首启 smoke 代替
summary: 对 MemoryChat 的 PRD 修复交付，不能把 connectedDebugAndroidTest 首启通过表述为已经做过真实对话流程验证；真实聊天、模型回复、自动抽取、召回和记忆中心检查需要单独跑并记录证据。
status: active
confidence: 95
updated_at: 2026-06-01
---

# 模拟器对话验证不能用首启 smoke 代替

## 类型

lesson

## 状态

active

## 可信度

95

## 背景

在推进 PRD 修复时，交付说明列出了 `connectedDebugAndroidTest` 和 APK 构建，并提到模拟器相关验证。用户随后在实际简单使用时发现很多 bug，追问是否在模拟器里做过对话测试。

## 偏差

当时实际完成的是首启 Compose smoke test、JVM 测试和 APK 构建，并未完成真实模拟器对话链路：

- 新建会话
- 发送消息
- 真实模型回复
- 自动记忆抽取
- 进入记忆中心核对
- 新会话召回验证

## 规则

- `connectedDebugAndroidTest` 首启通过只能说明 APP 能启动到基础页面。
- 只有真实跑过对话链路、保存日志/数据库证据，并检查 PRD 关键状态，才能说“做过模拟器对话验证”。
- 交付说明必须区分“首启 smoke”“ADB/真实模型 smoke”“完整 PRD E2E”。

## 后续动作

当前应先在模拟器复现真实对话链路，收集 logcat、数据库状态和 UI 现象，再按根因修复。
