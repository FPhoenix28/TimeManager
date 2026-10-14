package com.example.timemanager.ui.todo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.timemanager.R
import com.example.timemanager.ui.theme.TimeManagerSpacing

@Composable
fun TodoScreen(viewModel: TodoViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onBack)
    val state by viewModel.dayState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var showCompleted by rememberSaveable { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(TimeManagerSpacing.medium)) {
        item("header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "返回") }
                Text("今日待办", style = MaterialTheme.typography.headlineSmall)
            }
        }
        if (state.carried.isNotEmpty()) {
            item("carriedHeader") { TodoSectionLabel("昨日未完成") }
            items(state.carried, key = { "carried-${it.id}" }) { TodoRow(it, viewModel) }
            item("divider") { HorizontalDivider(Modifier.padding(vertical = TimeManagerSpacing.small)) }
        }
        item("todayHeader") { TodoSectionLabel("今天") }
        items(state.today, key = { "today-${it.id}" }) { TodoRow(it, viewModel) }
        if (state.unfinished.isEmpty()) item("empty") {
            Text(if (state.usageDay.isBlank()) "正在读取待办…" else "写下要做的事情，完成一项就勾选一项。",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = TimeManagerSpacing.medium))
        }
        item("input") { TodoInput(viewModel) }
        message?.let { item("message") { Text(it, color = MaterialTheme.colorScheme.error) } }
        if (state.completed.isNotEmpty()) {
            item("completedHeader") {
                TextButton(onClick = { showCompleted = !showCompleted }) {
                    Text("已完成 ${state.completed.size} ${if (showCompleted) "⌃" else "⌄"}")
                }
            }
            if (showCompleted) items(state.completed, key = { "completed-${it.id}" }) { TodoRow(it, viewModel) }
        }
    }
}
