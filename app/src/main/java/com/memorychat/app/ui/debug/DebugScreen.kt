package com.memorychat.app.ui.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.memorychat.app.MemoryChatApp
import com.memorychat.app.domain.model.ConversationDebugSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(conversationId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MemoryChatApp

    var conversation by remember { mutableStateOf<com.memorychat.app.domain.model.Conversation?>(null) }
    var activeMemories by remember { mutableStateOf<List<com.memorychat.app.domain.model.Memory>>(emptyList()) }
    var debugSnapshot by remember { mutableStateOf<ConversationDebugSnapshot?>(null) }

    LaunchedEffect(conversationId) {
        conversation = app.conversationRepo.getConversation(conversationId)
        activeMemories = app.memoryRepo.getActiveMemories()
        debugSnapshot = app.settingsDataStore.getConversationDebugSnapshot(conversationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("调试信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DebugCard("会话信息") {
                Text("会话 ID: ${conversation?.id ?: "-"}")
                Text("Persona ID: ${conversation?.personaId ?: "默认"}")
                Text("使用记忆: ${conversation?.useMemory ?: true}")
                Text("生成记忆: ${conversation?.generateMemory ?: true}")
            }

            DebugCard("上下文压缩") {
                val snapshot = debugSnapshot
                if (snapshot == null) {
                    Text("暂无上下文记录（发送消息后显示）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("请求消息数: ${snapshot.contextMessageCount}")
                    Text("压缩水位: ${snapshot.summaryWatermark}")
                    Text("本轮更新摘要: ${snapshot.summaryUpdated}")
                    Text("超限后重试: ${snapshot.retryAfterContextLimit}")
                    if (snapshot.rollingSummary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Rolling Summary:", color = MaterialTheme.colorScheme.primary)
                        Text(snapshot.rollingSummary, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("暂无 rolling summary", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            DebugCard("活跃记忆统计") {
                Text("总数: ${activeMemories.size}")
                Text("画像: ${activeMemories.count { it.type == com.memorychat.app.domain.model.MemoryType.PROFILE }}")
                Text("偏好: ${activeMemories.count { it.type == com.memorychat.app.domain.model.MemoryType.PREFERENCE }}")
                Text("项目: ${activeMemories.count { it.type == com.memorychat.app.domain.model.MemoryType.PROJECT }}")
                Text("摘要: ${activeMemories.count { it.type == com.memorychat.app.domain.model.MemoryType.SUMMARY }}")
            }

            DebugCard("召回记忆详情") {
                val snapshot = debugSnapshot
                if (snapshot == null) {
                    Text("暂无召回记录（发送消息后显示）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("场景: ${snapshot.recallScene}", style = MaterialTheme.typography.bodyMedium)
                    Text("召回数量: ${snapshot.recalledMemories.size}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (snapshot.recalledMemories.isEmpty()) {
                        Text("无召回记忆", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        snapshot.recalledMemories.forEach { mem ->
                            Text(
                                "[${mem.type}] ${mem.content}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (mem.reason.isNotBlank()) {
                                Text(
                                    "  原因: ${mem.reason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            DebugCard("注入记忆预览") {
                if (activeMemories.isEmpty()) {
                    Text("暂无活跃记忆")
                } else {
                    activeMemories.take(10).forEach { mem ->
                        Text("[${mem.type}] ${mem.content}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun DebugCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
