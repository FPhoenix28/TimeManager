package com.example.timemanager.ui.intervention

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import com.example.timemanager.domain.todo.TodoSummary
import com.example.timemanager.ui.theme.TimeManagerSpacing

/** Session-only, read-only presentation; Daily retains its existing summary. */
@Composable
internal fun SessionTodoCard(summary: TodoSummary) {
    if (summary.totalCount == 0) return
    val visible = summary.topItems.take(3)
    val remaining = (summary.totalCount - visible.size).coerceAtLeast(0)
    val singleLine = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        LocalDensity.current.fontScale > 1.3f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(TimeManagerSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
        ) {
            Text("今天还有 ${summary.totalCount} 项待办", style = MaterialTheme.typography.titleMedium)
            Text("不如先完成一件小事？", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            visible.forEachIndexed { index, todo ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = TimeManagerSpacing.extraSmall),
                    horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium,
                        color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = todo.title,
                        style = if (index == 0) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                        maxLines = if (singleLine) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (remaining > 0) {
                Text("还有 $remaining 项", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
