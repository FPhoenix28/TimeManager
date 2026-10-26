package com.example.timemanager.config

import org.junit.Assert.*
import org.junit.Test

class DurationInputTest {
    @Test fun convertsAllThreeUnitsWithoutTruncation() {
        assertEquals(3_723L, DurationInput("1", "2", "3").totalSeconds())
        assertEquals(20L, DurationInput("0", "0", "20").totalSeconds())
        assertEquals(60L, DurationInput("0", "1", "0").totalSeconds())
        assertEquals(90L, DurationInput("0", "1", "30").totalSeconds())
    }
    @Test fun rejectsZeroNegativeMalformedAndOutOfRange() {
        for (input in listOf(DurationInput("0", "0", "0"), DurationInput("-1", "0", "0"),
            DurationInput("0", "60", "0"), DurationInput("0", "0", "60"),
            DurationInput("100", "0", "0"), DurationInput("a", "0", "0"))) {
            assertThrows(IllegalArgumentException::class.java) { input.totalSeconds() }
        }
    }
    @Test fun maxValueAndFormattingRoundTrip() {
        val input = DurationInput("99", "59", "59")
        assertEquals(359_999L, input.totalSeconds())
        assertEquals(input, DurationInput.fromSeconds(input.totalSeconds()))
        assertEquals("2分30秒", formatPreciseDuration(150L))
        assertEquals("20秒", formatPreciseDuration(20L))
    }
}
