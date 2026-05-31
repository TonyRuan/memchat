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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memorychat.app.MemoryChatApp
import com.memorychat.app.ui.chat.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(conversationId: String, onBack: () -> Unit, viewModel: ChatViewModel = viewModel()) {
    val context = LocalContext.current
    val app = context.applicationContext as MemoryChatApp

    var conversation by remember { mutableStateOf<com.memorychat.app.domain.model.Conversation?>(null) }
    var activeMemories by remember { mutableStateOf<List<com.memorychat.app.domain.model.Memory>>(emptyList()) }
    val lastRecallResult by viewModel.lastRecallResult.collectAsState()

    LaunchedEffect(conversationId) {
        conversation = app.conversationRepo.getConversation(conversationId)
        activeMemories = app.memoryRepo.getActiveMemories()
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

            DebugCard("活跃记忆统计") {
                Text("总数: ${activeMemories.size}")
                Text("画像: ${activeMemories.count { it.type == com.memorychat.app.domain.model.MemoryType.PROFILE }}")
                Text("偏好: ${activeMemories.count { it.type == com.memorychat.app.domain.model.MemoryType.PREFERENCE }}")
                Text("项目: ${activeMemories.count { it.type == com.memorychat.app.domain.model.MemoryType.PROJECT }}")
                Text("摘要: ${activeMemories.count { it.type == com.memorychat.app.domain.model.MemoryType.SUMMARY }}")
            }

            DebugCard("召回记忆详情") {
                if (lastRecallResult == null) {
                    Text("暂无召回记录（发送消息后显示）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    val result = lastRecallResult!!
                    Text("场景: ${result.scene}", style = MaterialTheme.typography.bodyMedium)
                    Text("召回数量: ${result.memories.size}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (result.memories.isEmpty()) {
                        Text("无召回记忆", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        result.memories.forEach { mem ->
                            val reason = result.reasons[mem.id] ?: ""
                            Text(
                                "[${mem.type}] ${mem.content}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (reason.isNotBlank()) {
                                Text(
                                    "  原因: $reason",
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
