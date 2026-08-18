package com.example.timemanager.utils

import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageDayProviderTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun timeBeforeFourAmBelongsToPreviousUsageDay() {
        val provider = providerAt("2026-08-20T03:59:59+08:00")

        val usageDay = provider.currentUsageDay()

        assertEquals("2026-08-19", usageDay.key)
        assertEquals(
            Instant.parse("2026-08-18T20:00:00Z").toEpochMilli(),
            usageDay.startTimeMillis,
        )
    }

    @Test
    fun timeAtFourAmStartsNewUsageDay() {
        val provider = providerAt("2026-08-20T04:00:00+08:00")

        val usageDay = provider.currentUsageDay()

        assertEquals("2026-08-20", usageDay.key)
        assertEquals(
            Instant.parse("2026-08-19T20:00:00Z").toEpochMilli(),
            usageDay.startTimeMillis,
        )
    }

    private fun providerAt(instant: String): UsageDayProvider = UsageDayProvider(
        Clock.fixed(OffsetDateTime.parse(instant).toInstant(), zone),
    )
}
