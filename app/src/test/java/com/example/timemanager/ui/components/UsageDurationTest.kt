package com.example.timemanager.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageDurationTest {
    @Test fun formatsHoursAndMinutes() = assertEquals("1小时47分", formatUsageDuration(6_420))
    @Test fun formatsMinutes() = assertEquals("42分钟", formatUsageDuration(2_520))
    @Test fun formatsSubMinuteUsage() = assertEquals("45秒", formatUsageDuration(45))
    @Test fun formatsZeroAndClampsNegativeDisplay() {
        assertEquals("0分钟", formatUsageDuration(0))
        assertEquals("0分钟", formatUsageDuration(-1))
    }
}
