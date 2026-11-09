package com.example.timemanager.ui.intervention

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import com.example.timemanager.domain.todo.TodoSummary
import com.example.timemanager.ui.components.CalmPanel
import com.example.timemanager.ui.components.CalmPanelTone
import com.example.timemanager.ui.theme.TimeManagerSpacing
import com.example.timemanager.ui.todo.ReadOnlyTodoRow

/** Session-only, read-only presentation; Daily retains its existing summary. */
@Composable
internal fun SessionTodoCard(summary: TodoSummary) {
    if (summary.totalCount == 0) return
    val visible = summary.topItems.take(3)
    val remaining = (summary.totalCount - visible.size).coerceAtLeast(0)
    val singleLine = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        LocalDensity.current.fontScale > 1.3f
    CalmPanel(CalmPanelTone.REMINDER) {
        Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
            Text("今天还有 ${summary.totalCount} 项待办", style = MaterialTheme.typography.titleMedium)
            Text("不如先完成一件小事？", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            visible.forEachIndexed { index, todo ->
                ReadOnlyTodoRow(todo.title, emphasized = index == 0, maxLines = if (singleLine) 1 else 2)
            }
            if (remaining > 0) {
                Text("还有 $remaining 项", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
