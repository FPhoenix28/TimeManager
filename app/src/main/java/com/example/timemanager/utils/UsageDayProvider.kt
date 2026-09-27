package com.example.timemanager.utils

import java.time.Clock
import java.time.LocalDate
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
        return usageDay(start.toLocalDate())
    }

    /** Resolves one persisted usage-date key to its local 04:00-to-04:00 interval. */
    fun usageDay(usageDate: LocalDate): UsageDay {
        val start = usageDate
            .atTime(RESET_TIME)
            .atZone(clock.zone)
        return UsageDay(
            key = usageDate.toString(),
            startTimeMillis = start.toInstant().toEpochMilli(),
            endTimeMillis = start.plusDays(1L).toInstant().toEpochMilli(),
        )
    }

    data class UsageDay(
        val key: String,
        val startTimeMillis: Long,
        val endTimeMillis: Long,
    )

    private companion object {
        val RESET_TIME: LocalTime = LocalTime.of(4, 0)
    }
}
