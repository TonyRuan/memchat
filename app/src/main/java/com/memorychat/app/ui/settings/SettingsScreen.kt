package com.memorychat.app.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.memorychat.app.MemoryChatApp
import com.memorychat.app.domain.model.ModelRuntimeDefaults
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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
    var maxTokens by remember { mutableStateOf(ModelRuntimeDefaults.MIMO_V25_MAX_COMPLETION_TOKENS.toString()) }
    var contextWindowTokens by remember { mutableStateOf(ModelRuntimeDefaults.MIMO_V25_CONTEXT_WINDOW_TOKENS.toString()) }
    var safetyMarginTokens by remember { mutableStateOf(ModelRuntimeDefaults.DEFAULT_SAFETY_MARGIN_TOKENS.toString()) }
    var compressionMessageTurnThreshold by remember { mutableStateOf(ModelRuntimeDefaults.DEFAULT_COMPRESSION_MESSAGE_TURN_THRESHOLD.toString()) }
    var temperature by remember { mutableStateOf(ModelRuntimeDefaults.MIMO_V25_TEMPERATURE.toString()) }
    var topP by remember { mutableStateOf(ModelRuntimeDefaults.MIMO_V25_TOP_P.toString()) }

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
    LaunchedEffect(Unit) {
        app.settingsDataStore.contextWindowTokens.collect { contextWindowTokens = it.toString() }
    }
    LaunchedEffect(Unit) {
        app.settingsDataStore.safetyMarginTokens.collect { safetyMarginTokens = it.toString() }
    }
    LaunchedEffect(Unit) {
        app.settingsDataStore.compressionMessageTurnThreshold.collect { compressionMessageTurnThreshold = it.toString() }
    }
    LaunchedEffect(Unit) {
        app.settingsDataStore.temperature.collect { temperature = it.toString() }
    }
    LaunchedEffect(Unit) {
        app.settingsDataStore.topP.collect { topP = it.toString() }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    if (json != null) {
                        val result = app.exportImportService.importMemoriesJson(json)
                        Toast.makeText(
                            context,
                            "导入完成: ${result.imported}条导入, ${result.skipped}条跳过${if (result.errors.isNotEmpty()) ", ${result.errors.size}个错误" else ""}",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "读取文件失败", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun shareFile(content: String, fileName: String, mimeType: String) {
        try {
            val dir = File(context.getExternalFilesDir(null), "exports")
            dir.mkdirs()
            val file = File(dir, fileName)
            file.writeText(content)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享"))
        } catch (e: Exception) {
            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
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
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = modelName,
                onValueChange = { modelName = it },
                label = { Text("Model Name") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("上下文与生成参数", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = contextWindowTokens,
                onValueChange = { contextWindowTokens = it },
                label = { Text("Context Window Tokens") },
                supportingText = { Text("MiMo v2.5 官方默认：1,000,000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = maxTokens,
                onValueChange = { maxTokens = it },
                label = { Text("Max Output Tokens") },
                supportingText = { Text("作为 max_completion_tokens 发送；MiMo v2.5 最大输出：128,000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = safetyMarginTokens,
                onValueChange = { safetyMarginTokens = it },
                label = { Text("Safety Margin Tokens") },
                supportingText = { Text("后续上下文压缩会用于预留安全余量") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = temperature,
                    onValueChange = { temperature = it },
                    label = { Text("Temperature") },
                    supportingText = { Text("MiMo v2.5 默认：1.0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = topP,
                    onValueChange = { topP = it },
                    label = { Text("Top P") },
                    supportingText = { Text("MiMo 默认：0.95") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = compressionMessageTurnThreshold,
                onValueChange = { compressionMessageTurnThreshold = it },
                label = { Text("压缩触发轮数") },
                supportingText = { Text("后续 rolling summary 使用；默认 200 轮") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            HorizontalDivider()

            Text("默认会话配置", style = MaterialTheme.typography.titleMedium)

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

            // 记忆导入按钮
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("导入记忆 JSON")
            }

            // 记忆导出按钮
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val json = app.exportImportService.exportMemoriesJson()
                        val fileName = "memories_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                        shareFile(json, fileName, "application/json")
                    }
                }) { Text("导出记忆 JSON") }

                OutlinedButton(onClick = {
                    scope.launch {
                        val md = app.exportImportService.exportMemoriesMarkdown()
                        val fileName = "memories_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.md"
                        shareFile(md, fileName, "text/markdown")
                    }
                }) { Text("导出记忆 MD") }
            }

            // 人格导出按钮
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        val json = app.exportImportService.exportPersonasJson()
                        val fileName = "personas_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                        shareFile(json, fileName, "application/json")
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
                        app.settingsDataStore.saveMaxTokens(maxTokens.toIntOrNull() ?: ModelRuntimeDefaults.MIMO_V25_MAX_COMPLETION_TOKENS)
                        app.settingsDataStore.saveContextWindowTokens(contextWindowTokens.toIntOrNull() ?: ModelRuntimeDefaults.MIMO_V25_CONTEXT_WINDOW_TOKENS)
                        app.settingsDataStore.saveSafetyMarginTokens(safetyMarginTokens.toIntOrNull() ?: ModelRuntimeDefaults.DEFAULT_SAFETY_MARGIN_TOKENS)
                        app.settingsDataStore.saveCompressionMessageTurnThreshold(compressionMessageTurnThreshold.toIntOrNull() ?: ModelRuntimeDefaults.DEFAULT_COMPRESSION_MESSAGE_TURN_THRESHOLD)
                        app.settingsDataStore.saveTemperature(temperature.toFloatOrNull() ?: ModelRuntimeDefaults.MIMO_V25_TEMPERATURE.toFloat())
                        app.settingsDataStore.saveTopP(topP.toFloatOrNull() ?: ModelRuntimeDefaults.MIMO_V25_TOP_P.toFloat())
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
