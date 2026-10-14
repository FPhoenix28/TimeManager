package com.example.timemanager.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.timemanager.domain.statistics.DailyUsagePoint
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun UsageTrendChart(
    points: List<DailyUsagePoint>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (points.isEmpty()) return
    val maxSeconds = points.maxOf(DailyUsagePoint::usageSeconds).coerceAtLeast(1L)
    Column(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            val slotWidth = size.width / points.size
            val barWidth = (slotWidth * 0.62f).coerceAtLeast(2f)
            points.forEachIndexed { index, point ->
                val ratio = point.usageSeconds.toFloat() / maxSeconds.toFloat()
                val barHeight = if (point.usageSeconds == 0L) 2f else size.height * ratio
                drawRect(
                    color = barColor.copy(alpha = if (point.usageSeconds == 0L) 0.18f else 0.85f),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        x = index * slotWidth + (slotWidth - barWidth) / 2f,
                        y = size.height - barHeight,
                    ),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(shortDate(points.first().date), modifier = Modifier.weight(1f))
            Text(shortDate(points.last().date))
        }
    }
}

@Composable
fun TrendComparisonText(
    changePercent: Double?,
    previousLabel: String,
    explicitPeriod: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val comparisonTarget = if (explicitPeriod) previousLabel else "上一周期"
    Text(
        text = when {
            changePercent == null -> "${previousLabel}无使用记录，暂无可比数据"
            changePercent > 0.0 -> "↑ ${abs(changePercent).roundToInt()}% · 比${comparisonTarget}增加"
            changePercent < 0.0 -> "↓ ${abs(changePercent).roundToInt()}% · 比${comparisonTarget}减少"
            else -> "0% · 与${comparisonTarget}持平"
        },
        color = color,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun shortDate(value: String): String {
    val date = LocalDate.parse(value)
    return "${date.monthValue}/${date.dayOfMonth}"
}
