package com.example.timemanager.domain.todo

import com.example.timemanager.utils.UsageDayProvider
import java.time.Clock
import java.time.LocalDate

enum class TodoReminderStage(val hour: Int) { AFTERNOON(14), EVENING(20), LATE(23) }

data class TodoReminderWindow(
    val usageDay: String, val stage: TodoReminderStage, val startMillis: Long,
    val endMillis: Long, val delayMillis: Long,
) {
    val workName get() = "todo_reminder_${usageDay}_${stage.name.lowercase()}"
}

/** Approximate windows, not exact alarms. Past windows are never caught up. */
class TodoReminderPolicy(private val clock: Clock = Clock.systemDefaultZone()) {
    private val days = UsageDayProvider(clock)

    fun plan(): List<TodoReminderWindow> {
        val day = days.currentUsageDay()
        val date = LocalDate.parse(day.key)
        val now = clock.millis()
        return TodoReminderStage.entries.mapIndexedNotNull { index, stage ->
            val start = date.atTime(stage.hour, 0).atZone(clock.zone).toInstant().toEpochMilli()
            val end = TodoReminderStage.entries.getOrNull(index + 1)?.let {
                date.atTime(it.hour, 0).atZone(clock.zone).toInstant().toEpochMilli()
            } ?: day.endTimeMillis
            if (now >= end) null else TodoReminderWindow(day.key, stage, start, end, (start - now).coerceAtLeast(0))
        }
    }

    fun isApplicable(day: String, stage: TodoReminderStage): Boolean =
        plan().any { it.usageDay == day && it.stage == stage && clock.millis() >= it.startMillis }

    fun nextBoundaryMillis(): Long = days.currentUsageDay().endTimeMillis
}
