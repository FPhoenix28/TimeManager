package com.example.timemanager.ui.statistics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onBack: () -> Unit,
    onOpenAppHistory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    BackHandler(onBack = onBack)
    when (val current = state) {
        StatisticsUiState.Loading -> Column(
            modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        is StatisticsUiState.Error -> Column(
            modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(current.message)
            TextButton(onClick = viewModel::refresh) { Text("重试") }
            TextButton(onClick = onBack) { Text("返回") }
        }
        is StatisticsUiState.Success -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("← 返回") }
                    Text("使用统计", style = MaterialTheme.typography.headlineSmall)
                }
            }
            item("period") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryPeriod.entries.forEach { period ->
                        TextButton(onClick = { viewModel.selectPeriod(period) }) {
                            Text(if (period == current.period) "[${period.label}]" else period.label)
                        }
                    }
                }
            }
            item("total") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("最近${current.period.label}")
                        Text("总使用")
                        Text(
                            formatHistoryDuration(current.totalSeconds),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text("日均 ${formatHistoryDuration(current.averageSeconds)}")
                        TrendComparisonText(
                            changePercent = current.changePercent,
                            previousLabel = if (current.period == HistoryPeriod.SEVEN_DAYS) {
                                "前7天"
                            } else {
                                "前30天"
                            },
                        )
                        Text(
                            text = current.topApp?.let {
                                "使用最多：${it.appName} ${formatHistoryDuration(it.usageSeconds)}"
                            } ?: "使用最多：暂无记录",
                        )
                    }
                }
            }
            item("trend") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("每日趋势", style = MaterialTheme.typography.titleMedium)
                        UsageTrendChart(
                            points = current.dailyPoints,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            items(current.apps, key = StatisticsAppItem::packageName) { app ->
                Card(
                    onClick = { onOpenAppHistory(app.packageName) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = remember(app.appIcon) { app.appIcon?.asImageBitmap() }
                        if (icon != null) {
                            Image(
                                icon,
                                contentDescription = "${app.appName}图标",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                            )
                        } else {
                            Text(app.appName.firstOrNull()?.toString() ?: "?")
                        }
                        Spacer(Modifier.size(14.dp))
                        Text(app.appName, modifier = Modifier.weight(1f))
                        Text(formatHistoryDuration(app.usageSeconds))
                    }
                }
            }
        }
    }
}

internal fun formatHistoryDuration(seconds: Long): String {
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    return if (hours > 0L) "${hours}小时${minutes}分钟" else "${minutes}分钟"
}
