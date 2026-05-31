package com.memorychat.app.ui.debug

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.memorychat.app.util.AppLogger
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val logs by AppLogger.logs.collectAsState()
    var filterLevel by remember { mutableStateOf<AppLogger.Level?>(null) }

    val filteredLogs = if (filterLevel == null) logs else logs.filter { it.level == filterLevel }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs (${logs.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val path = AppLogger.exportLogs(context)
                        Toast.makeText(context, "Saved: $path", Toast.LENGTH_LONG).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export Logs")
                }
                OutlinedButton(
                    onClick = { AppLogger.clearLogs() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(selected = filterLevel == null, onClick = { filterLevel = null }, label = { Text("All") })
                FilterChip(selected = filterLevel == AppLogger.Level.ERROR, onClick = { filterLevel = AppLogger.Level.ERROR }, label = { Text("Error") })
                FilterChip(selected = filterLevel == AppLogger.Level.WARN, onClick = { filterLevel = AppLogger.Level.WARN }, label = { Text("Warn") })
                FilterChip(selected = filterLevel == AppLogger.Level.INFO, onClick = { filterLevel = AppLogger.Level.INFO }, label = { Text("Info") })
                FilterChip(selected = filterLevel == AppLogger.Level.DEBUG, onClick = { filterLevel = AppLogger.Level.DEBUG }, label = { Text("Debug") })
            }

            val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(filteredLogs.size) { index ->
                    val entry = filteredLogs[filteredLogs.size - 1 - index]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = when (entry.level) {
                                AppLogger.Level.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                AppLogger.Level.WARN -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(timeFmt.format(Date(entry.timestamp)), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                                Text("[${entry.level}]", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = when(entry.level) {
                                    AppLogger.Level.ERROR -> MaterialTheme.colorScheme.error
                                    AppLogger.Level.WARN -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                })
                                Text("[${entry.tag}]", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(entry.message, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            if (entry.detail != null) {
                                Text(entry.detail, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

