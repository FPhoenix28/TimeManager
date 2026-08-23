package com.example.timemanager.domain.statistics

import com.example.timemanager.data.local.UsageHistoryEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatisticsCalculatorTest {
    private val calculator = StatisticsCalculator()

    @Test
    fun fillsMissingDaysAndComparesWithPreviousPeriod() {
        val analysis = calculator.calculateManagedApps(
            records = listOf(
                record("app.one", "2026-08-17", 100L),
                record("app.one", "2026-08-18", 200L),
                record("app.two", "2026-08-18", 100L),
                record("app.one", "2026-08-11", 200L),
            ),
            managedPackages = setOf("app.one", "app.two"),
            currentEndDate = LocalDate.parse("2026-08-23"),
            days = 7L,
        )

        assertEquals(7, analysis.dailyPoints.size)
        assertEquals(400L, analysis.totalUsageSeconds)
        assertEquals(200L, analysis.previousPeriodUsageSeconds)
        assertEquals(100.0, analysis.changePercent!!, 0.001)
        assertEquals(5, analysis.dailyPoints.count { it.usageSeconds == 0L })
        assertEquals("app.one", analysis.topPackageName)
    }

    @Test
    fun previousZeroProducesNoComparablePercentage() {
        val analysis = calculator.calculateApp(
            records = listOf(record("app.one", "2026-08-23", 60L)),
            packageName = "app.one",
            currentEndDate = LocalDate.parse("2026-08-23"),
            days = 7L,
        )

        assertNull(analysis.changePercent)
    }

    private fun record(packageName: String, date: String, seconds: Long) =
        UsageHistoryEntity(packageName, date, seconds)
}
