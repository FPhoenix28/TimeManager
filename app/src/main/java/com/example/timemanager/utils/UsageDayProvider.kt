package com.example.timemanager.utils

import java.time.Clock
import java.time.LocalTime
import java.time.ZonedDateTime

/** Defines a TimeManager usage day, which starts at 04:00 in the device time zone. */
class UsageDayProvider(
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    fun currentUsageDay(): UsageDay {
        val now = ZonedDateTime.now(clock)
        val resetToday = now.toLocalDate()
            .atTime(RESET_TIME)
            .atZone(now.zone)
        val start = if (now.isBefore(resetToday)) {
            resetToday.minusDays(1)
        } else {
            resetToday
        }
        return UsageDay(
            key = start.toLocalDate().toString(),
            startTimeMillis = start.toInstant().toEpochMilli(),
        )
    }

    data class UsageDay(
        val key: String,
        val startTimeMillis: Long,
    )

    private companion object {
        val RESET_TIME: LocalTime = LocalTime.of(4, 0)
    }
}
