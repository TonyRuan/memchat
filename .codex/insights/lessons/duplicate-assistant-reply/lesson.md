---
id: duplicate-assistant-reply
type: lesson
title: 聊天回复重复需要按保存链路定位
summary: 用户反馈发送一条消息后会出现两条一样的 assistant 回复；定位时必须同时检查 UI 触发、stream done 事件、数据库 messages 写入和 ADB/真实 UI 路径，不能只看一次模型响应。
status: active
confidence: 80
updated_at: 2026-06-01
---

# 聊天回复重复需要按保存链路定位

## 类型

lesson

## 状态

active

## 可信度

80

## 背景

用户在 v1.0.41 修复记忆链路后反馈：手动发送一条消息时，应用会返回两条一样的 assistant 消息。

## 当前结论

这类问题必须沿“用户触发发送 -> LLM stream chunk/done -> assistant 消息保存 -> UI 列表展示 -> Room messages 表”逐层定位。v1.0.41 的子代理模拟器检查显示：一次 UI 发送只产生 1 次 `Sending`、1 次 stream、1 条 assistant 入库；因此当前更像 UI 在 stream 完成时同时显示已保存消息和未清空的 streaming 临时气泡。同时仍需防住快速重复触发发送，因为 `sendMessage()` 原本没有入口级 generation gate。

## 后续动作

- 用模拟器复现并记录同一轮发送后 `messages` 表中 user/assistant 的数量、id 和 createdAt。
- 对照 logcat 中 `ChatVM Sending`、`Stream done`、`Message saved`、`LlmProvider SSE [DONE]` 的次数。
- 修复后补防重复保存的单元测试或 ViewModel 测试，并做真实发送验收。
