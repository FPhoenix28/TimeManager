package com.example.timemanager.ui.statistics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StatisticsChartScaleTest {
    @Test
    fun eightyMinutesUsesHalfHourTicksFromZero() {
        val scale = calculateYAxisScale(80L * 60L)
        assertEquals(listOf(0L, 1800L, 3600L, 5400L), scale.ticksSeconds)
        assertEquals(5400L, scale.maxSeconds)
    }

    @Test
    fun fiveHoursTwentyMinutesUsesTwoHourTicks() {
        val scale = calculateYAxisScale(320L * 60L)
        assertEquals(listOf(0L, 7200L, 14400L, 21600L), scale.ticksSeconds)
    }

    @Test
    fun largerValuesStillHaveReadableTicks() {
        val scale = calculateYAxisScale(11L * 3600L)
        assertEquals(0L, scale.ticksSeconds.first())
        assertTrue(scale.maxSeconds >= 11L * 3600L)
        assertEquals("4h", formatYAxisTick(scale.ticksSeconds[1]))
    }

    @Test
    fun labelsUseMinutesAndHours() {
        assertEquals("0", formatYAxisTick(0))
        assertEquals("30m", formatYAxisTick(1800))
        assertEquals("1h", formatYAxisTick(3600))
        assertEquals("1h30m", formatYAxisTick(5400))
    }

    @Test
    fun sevenDaysHaveOneLabelPerBarAndCurrentDayIsToday() {
        val dates = (0L..6L).map { LocalDate.of(2026, 9, 21).plusDays(it).toString() }
        val labels = chartXAxisLabels(dates)
        assertEquals(7, labels.size)
        assertEquals("周一", labels[0])
        assertEquals("今天", labels[6])
    }

    @Test
    fun thirtyDaysKeepAllPointsButOnlyFiveLabels() {
        val dates = (0L..29L).map { LocalDate.of(2026, 8, 29).plusDays(it).toString() }
        val labels = chartXAxisLabels(dates)
        assertEquals(setOf(0, 7, 14, 21, 29), labels.keys)
        assertEquals("8/29", labels[0])
        assertEquals("今天", labels[29])
    }

    @Test
    fun rankingShareHandlesZeroTotalAndClampsToOne() {
        assertEquals(0f, statisticsUsageShare(60, 0), 0f)
        assertEquals(0.46f, statisticsUsageShare(46, 100), 0.001f)
        assertEquals(1f, statisticsUsageShare(120, 100), 0f)
    }
}
