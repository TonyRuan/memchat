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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(conversationId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MemoryChatApp

    var conversation by remember { mutableStateOf<com.memorychat.app.domain.model.Conversation?>(null) }
    var activeMemories by remember { mutableStateOf<List<com.memorychat.app.domain.model.Memory>>(emptyList()) }

    LaunchedEffect(conversationId) {
        conversation = app.conversationRepo.getConversation(conversationId)
        activeMemories = app.memoryRepo.getActiveMemories()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("调试中心") },
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

            DebugCard("记忆注入预览") {
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
