package com.memorychat.app.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.memorychat.app.domain.model.ChatMessage
import com.memorychat.app.domain.model.ToolTrace
import com.memorychat.app.ui.components.MemoryExtractionIndicator
import com.memorychat.app.ui.markdown.MarkdownText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    onNavigateToDebug: () -> Unit,
    viewModel: ChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val streamingContent by viewModel.streamingContent.collectAsState()
    val activeToolTrace by viewModel.activeToolTrace.collectAsState()
    val completedToolTraces by viewModel.completedToolTraces.collectAsState()
    val conversation by viewModel.conversation.collectAsState()
    val memoryExtractionStatus by viewModel.memoryExtractionStatus.collectAsState()
    val chatStatusMessage by viewModel.chatStatusMessage.collectAsState()
    val activeExtractionConversations by viewModel.activeMemoryExtractionConversationIds.collectAsState()
    val isCurrentConversationExtracting = conversationId in activeExtractionConversations
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var inputText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(conversationId) {
        viewModel.loadConversation(conversationId)
    }

    DisposableEffect(conversationId) {
        onDispose {
            viewModel.flushPendingMemoryExtraction()
        }
    }

    LaunchedEffect(messages.size, streamingContent) {
        val total = messages.size + if (streamingContent.isNotEmpty()) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    LaunchedEffect(memoryExtractionStatus) {
        memoryExtractionStatus?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMemoryExtractionStatus()
        }
    }

    LaunchedEffect(chatStatusMessage) {
        chatStatusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearChatStatusMessage()
        }
    }

    // Delete confirmation dialog
    deleteTarget?.let { msg ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Message") },
            text = { Text("Delete this message?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMessage(msg.id)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    // Session settings dialog
    if (showSettingsDialog) {
        var useMemory by remember { mutableStateOf(conversation?.useMemory ?: true) }
        var generateMemory by remember { mutableStateOf(conversation?.generateMemory ?: true) }

        // Sync state when conversation changes
        LaunchedEffect(conversation) {
            conversation?.let {
                useMemory = it.useMemory
                generateMemory = it.generateMemory
            }
        }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("会话设置") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("使用记忆", style = MaterialTheme.typography.bodyLarge)
                            Text("发送消息时召回长期记忆", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = useMemory, onCheckedChange = { useMemory = it })
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("生成记忆", style = MaterialTheme.typography.bodyLarge)
                            Text("从对话中自动提取记忆", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = generateMemory, onCheckedChange = { generateMemory = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateConversationSettings(useMemory, generateMemory)
                    showSettingsDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(conversation?.title ?: "Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (isCurrentConversationExtracting) {
                        MemoryExtractionIndicator(modifier = Modifier.padding(end = 8.dp))
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, "Session Settings")
                    }
                    IconButton(onClick = onNavigateToDebug) {
                        Icon(Icons.Default.BugReport, "Debug")
                    }
                    IconButton(onClick = { viewModel.extractMemories() }) {
                        Icon(Icons.Default.Psychology, "Extract Memories")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        message = msg,
                        toolTrace = completedToolTraces[msg.id],
                        onLongClick = { deleteTarget = msg }
                    )
                }

                if (isGenerating && streamingContent.isNotEmpty()) {
                    item {
                        MessageBubble(
                            message = ChatMessage(conversationId = conversationId, role = "assistant", content = streamingContent),
                            toolTrace = activeToolTrace,
                            onLongClick = {}
                        )
                    }
                }

                if (isGenerating && streamingContent.isEmpty()) {
                    item {
                        MessageBubble(
                            message = ChatMessage(conversationId = conversationId, role = "assistant", content = ""),
                            toolTrace = activeToolTrace,
                            onLongClick = {}
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    maxLines = 4
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isGenerating) {
                    IconButton(onClick = { viewModel.stopGeneration() }) {
                        Icon(Icons.Default.Stop, "Stop", tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                val accepted = viewModel.sendMessage(inputText.trim())
                                if (accepted) inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(message: ChatMessage, toolTrace: ToolTrace? = null, onLongClick: () -> Unit) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
                .padding(12.dp)
        ) {
            val textColor = if (isUser) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            if (!isUser && toolTrace != null) {
                ToolTraceView(trace = toolTrace)
            }
            if (isUser) {
                Text(text = message.content, color = textColor)
            } else if (message.content.isNotBlank()) {
                MarkdownText(markdown = message.content, color = textColor)
            }
        }
    }
}
