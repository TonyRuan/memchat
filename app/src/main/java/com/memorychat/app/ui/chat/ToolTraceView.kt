package com.memorychat.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memorychat.app.domain.model.ToolTrace
import com.memorychat.app.domain.model.ToolTraceKind
import com.memorychat.app.domain.model.ToolTraceStatus

@Composable
fun ToolTraceView(
    trace: ToolTrace,
    modifier: Modifier = Modifier
) {
    var expanded by remember(trace.summary) { mutableStateOf(false) }
    val details = trace.detailLines()
    val canExpand = details.isNotEmpty()
    val isRunning = trace.status == ToolTraceStatus.RUNNING

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = canExpand) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = iconFor(trace.kind),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trace.summary,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (canExpand) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse tool details" else "Expand tool details",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (expanded && details.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            details.forEach { line ->
                Text(
                    text = line,
                    modifier = Modifier.padding(start = 22.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun iconFor(kind: ToolTraceKind): ImageVector {
    return when (kind) {
        ToolTraceKind.WEB_SEARCH -> Icons.Default.Search
        ToolTraceKind.MEMORY_RECALL,
        ToolTraceKind.MEMORY_WRITE -> Icons.Default.Psychology
        ToolTraceKind.PERSONA_UPDATE -> Icons.Default.AutoAwesome
        ToolTraceKind.DOC_SEARCH -> Icons.AutoMirrored.Filled.ManageSearch
        ToolTraceKind.THINKING -> Icons.Default.CheckCircle
    }
}
