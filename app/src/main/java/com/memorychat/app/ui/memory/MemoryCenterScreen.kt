package com.memorychat.app.ui.memory

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.memorychat.app.MemoryChatApp
import com.memorychat.app.domain.model.Memory
import com.memorychat.app.domain.model.MemoryStatus
import com.memorychat.app.domain.model.MemoryType
import com.memorychat.app.domain.model.Persona
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryCenterScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = context.applicationContext as MemoryChatApp
    val scope = rememberCoroutineScope()

    var memories by remember { mutableStateOf<List<Memory>>(emptyList()) }
    var personas by remember { mutableStateOf<List<Persona>>(emptyList()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingMemory by remember { mutableStateOf<Memory?>(null) }
    var editingPersona by remember { mutableStateOf<Persona?>(null) }
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
        app.getOrCreateDefaultPersona()
        personas = app.personaRepo.listPersonas()
    }

    val tabs = listOf("全部", "人格", "画像", "偏好", "项目", "摘要", "待确认", "禁用")

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
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 0.dp) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                }
            }

            val filteredMemories = when (selectedTab) {
                2 -> memories.filter { it.type == MemoryType.PROFILE && it.status != MemoryStatus.DELETED }
                3 -> memories.filter { it.type == MemoryType.PREFERENCE && it.status != MemoryStatus.DELETED }
                4 -> memories.filter { it.type == MemoryType.PROJECT && it.status != MemoryStatus.DELETED }
                5 -> memories.filter { it.type == MemoryType.SUMMARY && it.status != MemoryStatus.DELETED }
                6 -> memories.filter { it.status == MemoryStatus.PENDING }
                7 -> memories.filter { it.status == MemoryStatus.DISABLED }
                else -> memories.filter { it.status != MemoryStatus.DELETED }
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedTab == 1) {
                    item {
                        Text(
                            text = "人格是助手的稳定契约，包括身份、使命、专长、风格、规则和边界，不属于用户长期记忆。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (personas.isEmpty()) {
                        item {
                            Text(
                                text = "暂无人格",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(personas, key = { it.id }) { persona ->
                        PersonaCard(persona = persona, onEdit = { editingPersona = persona })
                    }
                } else {
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

    editingPersona?.let { persona ->
        EditPersonaDialog(
            persona = persona,
            onDismiss = { editingPersona = null },
            onSave = { updated ->
                scope.launch {
                    app.personaRepo.savePersona(updated)
                    personas = app.personaRepo.listPersonas()
                    editingPersona = null
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
fun PersonaCard(persona: Persona, onEdit: () -> Unit) {
    val fields = PersonaDisplayFormatter.fields(persona)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(persona.name, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    AssistChip(
                        onClick = {},
                        label = { Text(if (persona.isDefault) "默认人格" else "人格") }
                    )
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, "编辑人格", modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (fields.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "暂无更多人格设置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    fields.forEach { (label, value) ->
                        Column {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(value, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditPersonaDialog(persona: Persona, onDismiss: () -> Unit, onSave: (Persona) -> Unit) {
    var name by remember { mutableStateOf(persona.name) }
    var description by remember { mutableStateOf(persona.description.orEmpty()) }
    var role by remember { mutableStateOf(persona.role.orEmpty()) }
    var mission by remember { mutableStateOf(persona.mission.orEmpty()) }
    var expertise by remember { mutableStateOf(persona.expertise.joinToString("；")) }
    var tone by remember { mutableStateOf(persona.tone.orEmpty()) }
    var communicationStyle by remember { mutableStateOf(persona.communicationStyle.orEmpty()) }
    var behaviorRules by remember { mutableStateOf(persona.behaviorRules.joinToString("；")) }
    var boundaries by remember { mutableStateOf(persona.boundaries.joinToString("；")) }
    var toolPolicy by remember { mutableStateOf(persona.toolPolicy.joinToString("；")) }
    var memoryPolicy by remember { mutableStateOf(persona.memoryPolicy.joinToString("；")) }
    var exampleDialogues by remember { mutableStateOf(persona.exampleDialogues.joinToString("\n\n")) }
    var isDefault by remember { mutableStateOf(persona.isDefault) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑人格") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("描述") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = role, onValueChange = { role = it }, label = { Text("角色") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mission, onValueChange = { mission = it }, label = { Text("使命") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = expertise, onValueChange = { expertise = it }, label = { Text("专长（用分号分隔）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = tone, onValueChange = { tone = it }, label = { Text("语气") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = communicationStyle, onValueChange = { communicationStyle = it }, label = { Text("沟通风格") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = behaviorRules, onValueChange = { behaviorRules = it }, label = { Text("行为规则（用分号分隔）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = boundaries, onValueChange = { boundaries = it }, label = { Text("边界（用分号分隔）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = toolPolicy, onValueChange = { toolPolicy = it }, label = { Text("工具策略（用分号分隔）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = memoryPolicy, onValueChange = { memoryPolicy = it }, label = { Text("记忆策略（用分号分隔）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = exampleDialogues, onValueChange = { exampleDialogues = it }, label = { Text("示例对话（空行分隔）") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("默认人格")
                    Switch(checked = isDefault, onCheckedChange = { isDefault = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            persona.copy(
                                name = name.trim(),
                                description = description.trim().ifBlank { null },
                                role = role.trim().ifBlank { null },
                                mission = mission.trim().ifBlank { null },
                                expertise = PersonaDisplayFormatter.parseListField(expertise),
                                tone = tone.trim().ifBlank { null },
                                communicationStyle = communicationStyle.trim().ifBlank { null },
                                behaviorRules = PersonaDisplayFormatter.parseListField(behaviorRules),
                                boundaries = PersonaDisplayFormatter.parseListField(boundaries),
                                toolPolicy = PersonaDisplayFormatter.parseListField(toolPolicy),
                                memoryPolicy = PersonaDisplayFormatter.parseListField(memoryPolicy),
                                exampleDialogues = PersonaDisplayFormatter.parseExampleDialogues(exampleDialogues),
                                isDefault = isDefault,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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

