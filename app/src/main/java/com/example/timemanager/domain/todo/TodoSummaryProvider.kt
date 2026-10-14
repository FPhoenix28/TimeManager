package com.example.timemanager.domain.todo

import com.example.timemanager.data.repository.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*

/** Memory-only read interface for overlays; no Activity, View or monitoring-service references. */
class TodoSummaryProvider(repository: TodoRepository, scope: CoroutineScope) {
    val summary: StateFlow<TodoSummary> = repository.dayState.map { it.summary }
        .distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, TodoSummary())
}
