package com.example.timemanager.ui.intervention

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import com.example.timemanager.domain.todo.TodoSummary
import com.example.timemanager.ui.theme.TimeManagerSpacing

/** Read-only, memory-backed and subordinate to the existing intervention information/actions. */
@Composable
internal fun TodoOverlaySummary(summary: TodoSummary) {
    if (summary.totalCount == 0) return
    val configuration = LocalConfiguration.current
    val compact = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.fontScale > 1.3f
    val color = LocalContentColor.current.copy(alpha = 0.8f)
    Column(Modifier.fillMaxWidth().padding(top = TimeManagerSpacing.small),
        verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.extraSmall)) {
        HorizontalDivider(color = color.copy(alpha = 0.25f))
        Text("今天还有 ${summary.totalCount} 项待办", style = MaterialTheme.typography.labelLarge, color = color)
        summary.topItems.forEach {
            Text("○ ${it.title}", style = MaterialTheme.typography.bodySmall, color = color,
                maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
        }
        if (summary.remainingCount > 0) Text("还有 ${summary.remainingCount} 项",
            style = MaterialTheme.typography.bodySmall, color = color)
    }
}
