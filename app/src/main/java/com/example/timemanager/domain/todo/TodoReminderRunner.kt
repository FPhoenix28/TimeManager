package com.example.timemanager.domain.todo

import com.example.timemanager.data.local.TodoEntity
import com.example.timemanager.data.repository.TodoRepository

/** Testable worker business path: reload state at execution, then atomically reserve one stage. */
class TodoReminderRunner(
    private val repository: TodoRepository,
    private val policy: TodoReminderPolicy,
    private val canNotify: () -> Boolean,
    private val notify: (TodoReminderStage, List<TodoEntity>) -> Unit,
) {
    suspend fun run(day: String, stage: TodoReminderStage): Boolean {
        if (!policy.isApplicable(day, stage) || !canNotify()) return false
        val unfinished = repository.getUnfinishedSnapshot()
        if (unfinished.isEmpty() || !repository.claimReminder(day, stage)) return false
        notify(stage, unfinished)
        return true
    }
}
