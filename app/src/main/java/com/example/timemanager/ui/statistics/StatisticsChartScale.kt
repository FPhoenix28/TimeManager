package com.example.timemanager.ui.statistics

import java.time.LocalDate
import kotlin.math.ceil

internal data class YAxisScale(
    val maxSeconds: Long,
    val ticksSeconds: List<Long>,
)

internal fun calculateYAxisScale(maxUsageSeconds: Long): YAxisScale {
    val neededStepMinutes = ceil(maxUsageSeconds.coerceAtLeast(0L) / 180.0).toLong()
        .coerceAtLeast(1L)
    val readableSteps = listOf(1L, 2L, 5L, 10L, 15L, 20L, 30L, 60L, 120L, 180L, 240L, 360L, 480L, 720L)
    val stepMinutes = readableSteps.firstOrNull { it >= neededStepMinutes }
        ?: ceil(neededStepMinutes / 60.0).toLong() * 60L
    val ticks = (0L..3L).map { it * stepMinutes * 60L }
    return YAxisScale(maxSeconds = ticks.last(), ticksSeconds = ticks)
}

internal fun formatYAxisTick(seconds: Long): String {
    val minutes = seconds / 60L
    if (minutes == 0L) return "0"
    if (minutes < 60L) return "${minutes}m"
    val hours = minutes / 60L
    val rest = minutes % 60L
    return if (rest == 0L) "${hours}h" else "${hours}h${rest}m"
}

internal fun chartXAxisLabels(dates: List<String>): Map<Int, String> {
    if (dates.isEmpty()) return emptyMap()
    val lastIndex = dates.lastIndex
    if (dates.size == 7) {
        val weekdays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        return dates.indices.associateWith { index ->
            if (index == lastIndex) "今天"
            else weekdays[LocalDate.parse(dates[index]).dayOfWeek.value - 1]
        }
    }
    return listOf(0, 7, 14, 21, lastIndex)
        .filter { it in dates.indices }
        .distinct()
        .associateWith { index ->
            if (index == lastIndex) "今天" else LocalDate.parse(dates[index]).let {
                "${it.monthValue}/${it.dayOfMonth}"
            }
        }
}

internal fun statisticsUsageShare(usageSeconds: Long, totalSeconds: Long): Float =
    if (totalSeconds <= 0L) 0f
    else (usageSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
