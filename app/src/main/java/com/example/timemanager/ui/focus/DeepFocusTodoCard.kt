package com.example.timemanager.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.timemanager.data.local.TodoEntity
import com.example.timemanager.ui.theme.TimeManagerSpacing

/** Complete, read-only list. The parent provides a cap based on the available viewport. */
@Composable
internal fun DeepFocusTodoCard(todos: List<TodoEntity>, maxListHeight: Dp) {
    if (todos.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(TimeManagerSpacing.medium)) {
            Text("今日待办", style = MaterialTheme.typography.titleMedium)
            Text("${todos.size} 项未完成", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = TimeManagerSpacing.small).heightIn(max = maxListHeight),
                verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
            ) {
                items(todos, key = { it.id }) { todo ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
                        Text("•", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(todo.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
