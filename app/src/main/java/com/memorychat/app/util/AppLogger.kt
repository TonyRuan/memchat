package com.memorychat.app.util

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object AppLogger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: Level,
        val tag: String,
        val message: String,
        val detail: String? = null
    )

    fun d(tag: String, message: String, detail: String? = null) = log(Level.DEBUG, tag, message, detail)
    fun i(tag: String, message: String, detail: String? = null) = log(Level.INFO, tag, message, detail)
    fun w(tag: String, message: String, detail: String? = null) = log(Level.WARN, tag, message, detail)
    fun e(tag: String, message: String, detail: String? = null) = log(Level.ERROR, tag, message, detail)

    private fun log(level: Level, tag: String, message: String, detail: String?) {
        val entry = LogEntry(level = level, tag = tag, message = message, detail = detail)
        synchronized(this) {
            _logs.value = (_logs.value + entry).takeLast(500)
        }
        android.util.Log.println(
            when (level) {
                Level.DEBUG -> android.util.Log.DEBUG
                Level.INFO -> android.util.Log.INFO
                Level.WARN -> android.util.Log.WARN
                Level.ERROR -> android.util.Log.ERROR
            },
            tag,
            message + if (detail != null) "\n$detail" else ""
        )
    }

    fun exportLogs(context: Context): String {
        val snapshot = synchronized(this) { _logs.value.toList() }
        val sb = StringBuilder()
        sb.appendLine("=== MemoryChat Logs ===")
        sb.appendLine("Exported: ${dateFormat.format(Date())}")
        sb.appendLine("Device: ${android.os.Build.MODEL} (${android.os.Build.VERSION.RELEASE})")
        sb.appendLine("========================")
        sb.appendLine()

        snapshot.forEach { entry ->
            val time = dateFormat.format(Date(entry.timestamp))
            sb.appendLine("[$time] [${entry.level}] [${entry.tag}] ${entry.message}")
            if (entry.detail != null) {
                sb.appendLine("  Detail: ${entry.detail}")
            }
        }

        val content = sb.toString()
        try {
            val dir = File(context.getExternalFilesDir(null), "logs")
            dir.mkdirs()
            val file = File(dir, "memorychat_log_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt")
            file.writeText(content)
            i("AppLogger", "Logs exported to: ${file.absolutePath}")
            return file.absolutePath
        } catch (e: Exception) {
            e("AppLogger", "Failed to export logs", e.message)
            return ""
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}

