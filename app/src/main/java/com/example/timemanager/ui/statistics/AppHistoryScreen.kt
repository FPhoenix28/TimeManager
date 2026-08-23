package com.example.timemanager.ui.statistics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppHistoryScreen(
    viewModel: AppHistoryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    BackHandler(onBack = onBack)
    when (val current = state) {
        AppHistoryUiState.Loading -> Column(
            modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        is AppHistoryUiState.Error -> Column(
            modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(current.message)
            TextButton(onClick = viewModel::refresh) { Text("重试") }
            TextButton(onClick = onBack) { Text("返回") }
        }
        is AppHistoryUiState.Success -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item("header") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("← 返回") }
                    Column {
                        Text(current.appName, style = MaterialTheme.typography.headlineSmall)
                        Text("使用历史", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "最近${current.period.label}：${formatHistoryDuration(current.totalSeconds)}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        TrendComparisonText(
                            changePercent = current.changePercent,
                            previousLabel = if (current.period == HistoryPeriod.SEVEN_DAYS) {
                                "前7天"
                            } else {
                                "前30天"
                            },
                        )
                    }
                }
            }
            item("trend") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("每日趋势", style = MaterialTheme.typography.titleMedium)
                        UsageTrendChart(current.dailyPoints, Modifier.fillMaxWidth())
                    }
                }
            }
            items(current.days, key = HistoryDayItem::usageDate) { day ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(day.label)
                            Text(
                                day.usageDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(formatHistoryDuration(day.usageSeconds))
                    }
                }
            }
        }
    }
}
