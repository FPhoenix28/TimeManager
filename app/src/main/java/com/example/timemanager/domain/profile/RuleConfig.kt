package com.example.timemanager.domain.profile

sealed interface DailyRuleConfig {
    /** No saved daily limit. This is not zero, an error, or a synthesized default quota. */
    data object Unset : DailyRuleConfig

    data class Explicit(val baseLimitSeconds: Long) : DailyRuleConfig {
        init { require(baseLimitSeconds >= 0L) }
    }
}

sealed interface FirstSessionReminder {
    data object InheritedDefault : FirstSessionReminder

    data class Explicit(val firstReminderSeconds: Long) : FirstSessionReminder {
        init { require(firstReminderSeconds > 0L) }
    }
}

data class SessionRuleConfig(
    val firstReminder: FirstSessionReminder = FirstSessionReminder.InheritedDefault,
) {
    val effectiveFirstReminderSeconds: Long
        get() = when (val setting = firstReminder) {
            FirstSessionReminder.InheritedDefault -> SessionProgression.DEFAULT_FIRST_REMINDER_SECONDS
            is FirstSessionReminder.Explicit -> setting.firstReminderSeconds
        }
}

/** Immutable product policy. Calculates a threshold, never advances a live episode. */
object SessionProgression {
    const val DEFAULT_FIRST_REMINDER_SECONDS = 600L

    /** reminderNumber is one-based: first, first+8min, first+16min, then +5min repeatedly. */
    fun thresholdSeconds(firstReminderSeconds: Long, reminderNumber: Int): Long {
        require(firstReminderSeconds > 0L)
        require(reminderNumber > 0)
        val intervals = reminderNumber.toLong() - 1L
        return firstReminderSeconds + minOf(intervals, 2L) * 480L +
            (intervals - 2L).coerceAtLeast(0L) * 300L
    }
}
