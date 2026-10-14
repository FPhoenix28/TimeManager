package com.example.timemanager.ui.todo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.data.local.TodoEntity
import com.example.timemanager.domain.todo.TODO_TITLE_MAX_LENGTH
import com.example.timemanager.ui.theme.TimeManagerSpacing

@Composable
fun TodoInput(viewModel: TodoViewModel) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val submit = { viewModel.add(title) { title = "" } }
    if (!expanded) {
        TextButton(onClick = { expanded = true }) { Text("＋ 添加待办") }
    } else {
        LaunchedEffect(Unit) { focus.requestFocus() }
        Column {
            OutlinedTextField(value = title, onValueChange = { title = it },
                placeholder = { Text("写下要做的事情") }, singleLine = true,
                isError = title.length > TODO_TITLE_MAX_LENGTH,
                supportingText = if (title.length > TODO_TITLE_MAX_LENGTH) {
                    { Text("最多 $TODO_TITLE_MAX_LENGTH 字") }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!saving) submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { expanded = false }) { Text("收起") }
                TextButton(onClick = submit, enabled = !saving && title.trim().isNotEmpty() && title.length <= TODO_TITLE_MAX_LENGTH) {
                    Text(if (saving) "保存中…" else "添加")
                }
            }
        }
    }
}

@Composable
fun TodoRow(todo: TodoEntity, viewModel: TodoViewModel, showMenu: Boolean = true) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = TimeManagerSpacing.extraSmall), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = todo.isCompleted, onCheckedChange = { viewModel.setCompleted(todo.id, it) },
            modifier = Modifier.semantics { contentDescription = "完成状态：${todo.title}" })
        Text(todo.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = if (todo.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f))
        if (showMenu) Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "待办选项：${todo.title}" }) {
                Text("⋮", style = MaterialTheme.typography.titleLarge)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; confirmDelete = true })
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("删除这项待办？") }, text = { Text(todo.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; viewModel.delete(todo.id) }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } })
}

/** Own state subscription: todo changes do not subscribe the whole Home to a timer. */
@Composable
fun TodoHomeSection(viewModel: TodoViewModel, onOpenAll: () -> Unit) {
    val state by viewModel.dayState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val visible = remember(state) { state.unfinished.take(2) }
    Column(Modifier.fillMaxWidth().padding(top = TimeManagerSpacing.medium)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("今日待办", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenAll) { Text("查看全部 ›") }
        }
        if (state.unfinished.isEmpty()) Text("写下一件想完成的事", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        visible.forEachIndexed { index, todo ->
            if (todo.createdUsageDay == state.usageDay && index > 0 && visible[index - 1].createdUsageDay < state.usageDay) {
                HorizontalDivider(Modifier.padding(vertical = TimeManagerSpacing.small))
            }
            key(todo.id) { TodoRow(todo, viewModel, showMenu = false) }
        }
        if (state.unfinished.size > visible.size) Text("还有 ${state.unfinished.size - visible.size} 项",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TodoInput(viewModel)
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun TodoSectionLabel(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = TimeManagerSpacing.medium, bottom = TimeManagerSpacing.extraSmall))
}
