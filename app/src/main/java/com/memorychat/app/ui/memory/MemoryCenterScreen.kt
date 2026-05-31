package com.memorychat.app.ui.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memorychat.app.MemoryChatApp
import com.memorychat.app.domain.model.Memory
import com.memorychat.app.domain.model.MemoryStatus
import com.memorychat.app.domain.model.MemoryType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryCenterScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val app = context.applicationContext as MemoryChatApp
    val scope = rememberCoroutineScope()

    var memories by remember { mutableStateOf<List<Memory>>(emptyList()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingMemory by remember { mutableStateOf<Memory?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    LaunchedEffect(refreshTrigger) {
        memories = app.memoryRepo.getAllMemories()
    }

    val tabs = listOf("全部", "画像", "偏好", "项目", "摘要", "待确认", "禁用")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("记忆中心") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "添加记忆")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }

            val filteredMemories = when (selectedTab) {
                1 -> memories.filter { it.type == MemoryType.PROFILE && it.status != MemoryStatus.DELETED }
                2 -> memories.filter { it.type == MemoryType.PREFERENCE && it.status != MemoryStatus.DELETED }
                3 -> memories.filter { it.type == MemoryType.PROJECT && it.status != MemoryStatus.DELETED }
                4 -> memories.filter { it.type == MemoryType.SUMMARY && it.status != MemoryStatus.DELETED }
                5 -> memories.filter { it.status == MemoryStatus.PENDING }
                6 -> memories.filter { it.status == MemoryStatus.DISABLED }
                else -> memories.filter { it.status != MemoryStatus.DELETED }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredMemories, key = { it.id }) { memory ->
                    MemoryCard(
                        memory = memory,
                        onEdit = { editingMemory = memory },
                        onDelete = {
                            scope.launch {
                                app.memoryRepo.delete(memory.id)
                                memories = app.memoryRepo.getAllMemories()
                            }
                        },
                        onToggleStatus = {
                            scope.launch {
                                if (memory.status == MemoryStatus.ACTIVE) {
                                    app.memoryRepo.disable(memory.id)
                                } else if (memory.status == MemoryStatus.DISABLED) {
                                    app.memoryRepo.update(memory.copy(status = MemoryStatus.ACTIVE))
                                } else if (memory.status == MemoryStatus.PENDING) {
                                    app.memoryRepo.update(memory.copy(status = MemoryStatus.ACTIVE))
                                }
                                memories = app.memoryRepo.getAllMemories()
                            }
                        }
                    )
                }
            }
        }
    }

    // Edit dialog
    editingMemory?.let { memory ->
        EditMemoryDialog(
            memory = memory,
            onDismiss = { editingMemory = null },
            onSave = { updated ->
                scope.launch {
                    app.memoryRepo.update(updated)
                    memories = app.memoryRepo.getAllMemories()
                    editingMemory = null
                }
            }
        )
    }

    // Add dialog
    if (showAddDialog) {
        AddMemoryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { type, content ->
                scope.launch {
                    app.memoryRepo.insert(Memory(type = type, content = content, status = MemoryStatus.ACTIVE))
                    memories = app.memoryRepo.getAllMemories()
                    showAddDialog = false
                }
            }
        )
    }
}

@Composable
fun MemoryCard(
    memory: Memory,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleStatus: () -> Unit
) {
    var showSourceDialog by remember { mutableStateOf(false) }

    // Source dialog
    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("查看来源") },
            text = {
                Column {
                    Text("来源会话 ID:")
                    Text(
                        text = memory.sourceConversationId ?: "未知",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (memory.sourceMessageIds.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("来源消息数: ${memory.sourceMessageIds.size}")
                        memory.sourceMessageIds.take(6).forEach { id ->
                            Text(
                                text = id,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourceDialog = false }) { Text("关闭") }
            }
        )
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AssistChip(
                    onClick = {},
                    label = { Text(memory.type.name) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text(memory.status.name) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = when (memory.status) {
                            MemoryStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
                            MemoryStatus.PENDING -> MaterialTheme.colorScheme.tertiaryContainer
                            MemoryStatus.DISABLED -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(memory.content, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, "编辑", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { showSourceDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Source, "查看来源", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onToggleStatus, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (memory.status == MemoryStatus.DISABLED) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                        "切换",
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun EditMemoryDialog(memory: Memory, onDismiss: () -> Unit, onSave: (Memory) -> Unit) {
    var content by remember { mutableStateOf(memory.content) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记忆") },
        text = {
            OutlinedTextField(value = content, onValueChange = { content = it }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            TextButton(onClick = { onSave(memory.copy(content = content, updatedAt = System.currentTimeMillis(), userEdited = true)) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun AddMemoryDialog(onDismiss: () -> Unit, onSave: (MemoryType, String) -> Unit) {
    var content by remember { mutableStateOf("") }
    var selectedType by remember { mutableIntStateOf(0) }
    val types = MemoryType.entries

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加记忆") },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    types.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = selectedType == index,
                            onClick = { selectedType = index },
                            shape = SegmentedButtonDefaults.itemShape(index, types.size)
                        ) { Text(type.name) }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入记忆内容...") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (content.isNotBlank()) onSave(types[selectedType], content) }) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

