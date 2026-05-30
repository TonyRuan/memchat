package com.memorychat.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.memorychat.app.MemoryChatApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as MemoryChatApp
    val scope = rememberCoroutineScope()

    var providerType by remember { mutableStateOf("openai") }
    var baseUrl by remember { mutableStateOf("https://api.openai.com/v1") }
    var apiKey by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("gpt-4o-mini") }
    var defaultUseMemory by remember { mutableStateOf(true) }
    var defaultGenerateMemory by remember { mutableStateOf(true) }
    var maxTokens by remember { mutableStateOf("8192") }

    LaunchedEffect(Unit) {
        app.settingsDataStore.providerType.collect { providerType = it }
    }
    LaunchedEffect(Unit) {
        app.settingsDataStore.baseUrl.collect { baseUrl = it }
    }
    LaunchedEffect(Unit) {
        app.settingsDataStore.apiKey.collect { apiKey = it }
    }
    LaunchedEffect(Unit) {
        app.settingsDataStore.modelName.collect { modelName = it }
    }
    LaunchedEffect(Unit) {
        app.settingsDataStore.defaultUseMemory.collect { defaultUseMemory = it }
    }
    LaunchedEffect(Unit) {
        app.settingsDataStore.defaultGenerateMemory.collect { defaultGenerateMemory = it }
    }
    LaunchedEffect(Unit) {
        app.settingsDataStore.maxTokens.collect { maxTokens = it.toString() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("模型配置", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = providerType,
                onValueChange = { providerType = it },
                label = { Text("Provider Type") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model Name") },
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Text("默认会话设置", style = MaterialTheme.typography.titleMedium)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("默认使用记忆")
                Switch(checked = defaultUseMemory, onCheckedChange = { defaultUseMemory = it })
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("默认生成记忆")
                Switch(checked = defaultGenerateMemory, onCheckedChange = { defaultGenerateMemory = it })
            }

            HorizontalDivider()

            Text("导入导出", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val json = app.exportImportService.exportMemoriesJson()
                        // TODO: share file
                        Toast.makeText(context, "记忆导出完成", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("导出记忆 JSON") }

                OutlinedButton(onClick = {
                    scope.launch {
                        val md = app.exportImportService.exportMemoriesMarkdown()
                        Toast.makeText(context, "记忆导出完成", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("导出记忆 MD") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val json = app.exportImportService.exportPersonasJson()
                        Toast.makeText(context, "人格导出完成", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("导出人格 JSON") }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        app.settingsDataStore.saveProviderType(providerType)
                        app.settingsDataStore.saveBaseUrl(baseUrl)
                        app.settingsDataStore.saveApiKey(apiKey)
                        app.settingsDataStore.saveModelName(modelName)
                        app.settingsDataStore.saveDefaultUseMemory(defaultUseMemory)
                        app.settingsDataStore.saveDefaultGenerateMemory(defaultGenerateMemory)
                        app.settingsDataStore.saveMaxTokens(maxTokens.toIntOrNull() ?: 8192)
                        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存设置")
            }
        }
    }
}

