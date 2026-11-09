package com.example.timemanager.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.example.timemanager.data.local.TodoEntity
import com.example.timemanager.ui.components.CalmPanel
import com.example.timemanager.ui.components.CalmPanelTone
import com.example.timemanager.ui.theme.TimeManagerSpacing
import com.example.timemanager.ui.todo.ReadOnlyTodoRow

/** Complete, read-only list. The parent provides a cap based on the available viewport. */
@Composable
internal fun DeepFocusTodoCard(todos: List<TodoEntity>, maxListHeight: Dp) {
    if (todos.isEmpty()) return
    val listState = rememberLazyListState()
    CalmPanel(CalmPanelTone.IMMERSIVE) {
        Text("今日待办", style = MaterialTheme.typography.titleMedium)
        Text("${todos.size} 项未完成", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(top = TimeManagerSpacing.compact).heightIn(max = maxListHeight),
            verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
        ) {
            items(todos, key = { it.id }) { todo ->
                ReadOnlyTodoRow(todo.title)
            }
        }
        if (listState.canScrollForward || listState.canScrollBackward) {
            Text(
                "滑动查看全部待办",
                modifier = Modifier.padding(top = TimeManagerSpacing.small),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
