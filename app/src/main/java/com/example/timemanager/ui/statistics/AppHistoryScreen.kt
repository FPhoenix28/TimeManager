package com.example.timemanager.ui.statistics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.timemanager.ui.components.AppIcon
import com.example.timemanager.ui.components.FullScreenError
import com.example.timemanager.ui.components.FullScreenLoading
import com.example.timemanager.ui.theme.TimeManagerSpacing

@Composable
fun AppHistoryScreen(
    viewModel: AppHistoryViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    when (val current = state) {
        AppHistoryUiState.Loading -> FullScreenLoading(modifier)
        is AppHistoryUiState.Error -> HistoryError(
            message = current.message,
            onRetry = viewModel::refresh,
            onBack = onBack,
            modifier = modifier,
        )
        is AppHistoryUiState.Success -> HistoryContent(
            state = current,
            onBack = onBack,
            onSelectPeriod = viewModel::selectPeriod,
            modifier = modifier,
        )
    }
}

@Composable
private fun HistoryContent(
    state: AppHistoryUiState.Success,
    onBack: () -> Unit,
    onSelectPeriod: (HistoryPeriod) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TimeManagerSpacing.medium,
            top = TimeManagerSpacing.small,
            end = TimeManagerSpacing.medium,
            bottom = TimeManagerSpacing.extraLarge,
        ),
    ) {
        item("header") { StatisticsTopBar(onBack = onBack, title = "使用历史") }
        item("identity") {
            Row(
                modifier = Modifier.padding(top = TimeManagerSpacing.large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(
                    bitmap = state.appIcon,
                    appName = state.appName,
                    size = TimeManagerSpacing.extraLarge + TimeManagerSpacing.extraLarge,
                )
                Spacer(Modifier.size(TimeManagerSpacing.medium))
                Column {
                    Text(state.appName, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        state.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item("period") {
            PeriodSelector(
                selected = state.period,
                onSelect = onSelectPeriod,
                modifier = Modifier.padding(top = TimeManagerSpacing.large),
            )
        }
        item("summary") {
            Column(
                modifier = Modifier.padding(top = TimeManagerSpacing.large),
                verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
            ) {
                Text(
                    text = "最近${state.period.label}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatHistoryDuration(state.totalSeconds),
                    style = MaterialTheme.typography.displaySmall,
                )
                TrendComparisonText(
                    changePercent = state.changePercent,
                    previousLabel = previousPeriodLabel(state.period),
                )
            }
        }

        if (state.totalSeconds == 0L) {
            item("empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = TimeManagerSpacing.extraLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
                ) {
                    Text("还没有足够的使用记录", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "使用一段时间后，这里会显示每日使用情况。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            item("trendTitle") { StatisticsSectionTitle("每日趋势") }
            item("trend") {
                UsageTrendChart(
                    points = state.dailyPoints,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item("daysTitle") { StatisticsSectionTitle("每日记录") }
            items(state.days, key = HistoryDayItem::usageDate) { day ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = TimeManagerSpacing.compact),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(day.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            day.usageDate,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        formatHistoryDuration(day.usageSeconds),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun HistoryError(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    FullScreenError(
        title = "无法读取应用历史",
        message = message,
        onRetry = onRetry,
        onBack = onBack,
        modifier = modifier,
    )
}
