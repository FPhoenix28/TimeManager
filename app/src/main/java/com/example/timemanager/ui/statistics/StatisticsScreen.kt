package com.example.timemanager.ui.statistics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.timemanager.R
import com.example.timemanager.ui.components.AppIcon
import com.example.timemanager.ui.components.FullScreenError
import com.example.timemanager.ui.components.FullScreenLoading
import com.example.timemanager.ui.theme.TimeManagerSpacing

@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onBack: () -> Unit,
    onOpenAppHistory: (String) -> Unit,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    when (val current = state) {
        StatisticsUiState.Loading -> FullScreenLoading(modifier)
        is StatisticsUiState.Error -> StatisticsError(
            message = current.message,
            onRetry = viewModel::refresh,
            onBack = onBack,
            modifier = modifier,
        )
        is StatisticsUiState.Success -> StatisticsContent(
            state = current,
            onBack = onBack,
            showBack = showBack,
            onSelectPeriod = viewModel::selectPeriod,
            onOpenAppHistory = onOpenAppHistory,
            modifier = modifier,
        )
    }
}

@Composable
private fun StatisticsContent(
    state: StatisticsUiState.Success,
    onBack: () -> Unit,
    showBack: Boolean,
    onSelectPeriod: (HistoryPeriod) -> Unit,
    onOpenAppHistory: (String) -> Unit,
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
        item("header") {
            StatisticsTopBar(onBack = onBack, title = "使用统计", showBack = showBack)
        }
        item("period") {
            PeriodSelector(
                selected = state.period,
                onSelect = onSelectPeriod,
                modifier = Modifier.padding(top = TimeManagerSpacing.medium),
            )
        }
        item("summary") { StatisticsSummary(state) }

        if (state.totalSeconds == 0L) {
            item("empty") { HistoryEmptyState() }
        } else {
            item("trendTitle") { StatisticsSectionTitle("使用趋势") }
            item("trend") {
                UsageTrendChart(
                    points = state.dailyPoints,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item("rankingTitle") { StatisticsSectionTitle("应用排行") }
            items(state.apps, key = StatisticsAppItem::packageName) { app ->
                StatisticsAppRow(
                    app = app,
                    onClick = { onOpenAppHistory(app.packageName) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = TimeManagerSpacing.extraLarge * 3),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun StatisticsSummary(state: StatisticsUiState.Success) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TimeManagerSpacing.large),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(TimeManagerSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
        ) {
            Text(
                text = "总使用时间",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = formatHistoryDuration(state.totalSeconds),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = "过去${state.period.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(
                modifier = Modifier.padding(top = TimeManagerSpacing.small),
                horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.large),
            ) {
                SummaryMetric("日均", formatHistoryDuration(state.averageSeconds))
                SummaryMetric(
                    "使用最多",
                    state.topApp?.let { "${it.appName} ${formatHistoryDuration(it.usageSeconds)}" }
                        ?: "暂无记录",
                )
            }
            TrendComparisonText(
                changePercent = state.changePercent,
                previousLabel = previousPeriodLabel(state.period),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun StatisticsAppRow(app: StatisticsAppItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = TimeManagerSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(bitmap = app.appIcon, appName = app.appName)
        Spacer(Modifier.size(TimeManagerSpacing.medium))
        Text(
            text = app.appName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatHistoryDuration(app.usageSeconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun PeriodSelector(
    selected: HistoryPeriod,
    onSelect: (HistoryPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        HistoryPeriod.entries.forEachIndexed { index, period ->
            SegmentedButton(
                selected = selected == period,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index, HistoryPeriod.entries.size),
                label = { Text(period.label) },
            )
        }
    }
}

@Composable
internal fun StatisticsTopBar(
    onBack: () -> Unit,
    title: String,
    showBack: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TimeManagerSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "返回",
                )
            }
        }
        Text(text = title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
internal fun StatisticsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(
            top = TimeManagerSpacing.extraLarge,
            bottom = TimeManagerSpacing.medium,
        ),
    )
}

@Composable
private fun HistoryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TimeManagerSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
    ) {
        Text("还没有足够的使用记录", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "使用一段时间后，这里会显示你的使用趋势。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatisticsError(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    FullScreenError(
        title = "无法读取使用统计",
        message = message,
        onRetry = onRetry,
        onBack = onBack,
        modifier = modifier,
    )
}

internal fun previousPeriodLabel(period: HistoryPeriod): String =
    if (period == HistoryPeriod.SEVEN_DAYS) "前7天" else "前30天"

internal fun formatHistoryDuration(seconds: Long): String {
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    return if (hours > 0L) "${hours}h ${minutes}min" else "${minutes} min"
}
