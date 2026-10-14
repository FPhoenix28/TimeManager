package com.example.timemanager.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.timemanager.domain.statistics.DailyUsagePoint
import com.example.timemanager.ui.theme.TimeManagerSpacing
import java.time.LocalDate

@Composable
internal fun StatisticsTrendChart(
    points: List<DailyUsagePoint>,
    modifier: Modifier = Modifier,
) {
    if (points.isEmpty()) return
    val scale = remember(points) { calculateYAxisScale(points.maxOf(DailyUsagePoint::usageSeconds)) }
    val xLabels = remember(points) { chartXAxisLabels(points.map(DailyUsagePoint::date)) }
    var selectedIndex by rememberSaveable(points.first().date, points.last().date) {
        mutableIntStateOf(-1)
    }
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val barColor = MaterialTheme.colorScheme.primary
    val chartHeight = 120.dp

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("今天进行中", style = MaterialTheme.typography.bodySmall, color = axisColor)
        }
        if (selectedIndex in points.indices) {
            val point = points[selectedIndex]
            val date = LocalDate.parse(point.date)
            val dateLabel = "${date.monthValue}月${date.dayOfMonth}日"
            val dayLabel = if (selectedIndex == points.lastIndex) "今天（$dateLabel）" else dateLabel
            Text(
                "$dayLabel · ${formatHistoryDuration(point.usageSeconds)}" +
                    if (selectedIndex == points.lastIndex) " · 进行中" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(48.dp).height(chartHeight)) {
                val plotHeight = chartHeight - 16.dp
                scale.ticksSeconds.forEach { tick ->
                    val ratio = tick.toFloat() / scale.maxSeconds.toFloat()
                    Text(
                        text = formatYAxisTick(tick).replace("h", "小时").replace("m", "分"),
                        style = MaterialTheme.typography.labelSmall,
                        color = axisColor,
                        modifier = Modifier.offset(y = plotHeight * (1f - ratio)).height(16.dp),
                    )
                }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(chartHeight)
                    .pointerInput(points.size) {
                        detectTapGestures { tap ->
                            selectedIndex = (tap.x / size.width * points.size).toInt()
                                .coerceIn(points.indices)
                        }
                    },
            ) {
                val inset = 8.dp.toPx()
                val plotHeight = size.height - 2f * inset
                val baseline = size.height - inset
                scale.ticksSeconds.forEach { tick ->
                    val y = baseline - plotHeight * tick.toFloat() / scale.maxSeconds.toFloat()
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val slotWidth = size.width / points.size
                val barWidth = slotWidth * 0.62f
                points.forEachIndexed { index, point ->
                    if (point.usageSeconds > 0L) {
                        val barHeight = plotHeight *
                            (point.usageSeconds.toFloat() / scale.maxSeconds.toFloat()).coerceIn(0f, 1f)
                        val alpha = when {
                            selectedIndex == index -> 1f
                            selectedIndex >= 0 -> 0.5f
                            index == points.lastIndex -> 0.65f
                            else -> 0.85f
                        }
                        drawRect(
                            color = barColor.copy(alpha = alpha),
                            topLeft = Offset(index * slotWidth + (slotWidth - barWidth) / 2f, baseline - barHeight),
                            size = Size(barWidth, barHeight),
                        )
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(48.dp))
            if (points.size == 7) {
                Row(modifier = Modifier.weight(1f)) {
                    points.indices.forEach { index ->
                        Text(
                            text = LocalDate.parse(points[index].date).dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = axisColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.weight(1f).height(20.dp)) {
                    val labelWidth = 48.dp
                    val slotWidth = maxWidth / points.size
                    xLabels.forEach { (index, label) ->
                        val x = (slotWidth * (index + 0.5f) - labelWidth / 2f)
                            .coerceIn(0.dp, (maxWidth - labelWidth).coerceAtLeast(0.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = axisColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.offset(x = x).width(labelWidth),
                        )
                    }
                }
            }
        }
    }
}
