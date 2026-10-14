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
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.ui.components.AppIcon
import com.example.timemanager.ui.components.FullScreenError
import com.example.timemanager.ui.components.FullScreenLoading
import com.example.timemanager.ui.theme.TimeManagerSpacing
import com.example.timemanager.ui.components.UsageDurationText
import com.example.timemanager.ui.components.formatUsageDuration
import java.time.LocalDate
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role

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

        item("trendTitle") { StatisticsSectionTitle("每日趋势") }
        if (state.totalSeconds == 0L) {
            item("empty") { HistoryEmptyState() }
        } else {
            item("trend") {
                StatisticsTrendChart(
                    points = state.dailyPoints,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item("rankingTitle") { StatisticsSectionTitle("应用排行") }
            items(state.apps, key = StatisticsAppItem::packageName) { app ->
                StatisticsAppRow(
                    app = app,
                    totalSeconds = state.totalSeconds,
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
    val start = state.dailyPoints.firstOrNull()?.date?.let(LocalDate::parse)
    val end = state.dailyPoints.lastOrNull()?.date?.let(LocalDate::parse)
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = TimeManagerSpacing.large),
        verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
    ) {
        if (start != null && end != null) Text(
            "${start.monthValue}月${start.dayOfMonth}日 — ${end.monthValue}月${end.dayOfMonth}日",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        UsageDurationText(state.totalSeconds)
        Text("过去${state.period.label}受管理应用累计使用时长",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.padding(top = TimeManagerSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.extraSmall)) {
            Text("日均", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatHistoryDuration(state.averageSeconds), style = MaterialTheme.typography.titleLarge)
            Text("含今天，今日尚未结束", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatisticsAppRow(
    app: StatisticsAppItem,
    totalSeconds: Long,
    onClick: () -> Unit,
) {
    val share = statisticsUsageShare(app.usageSeconds, totalSeconds)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = TimeManagerSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(bitmap = app.appIcon, appName = app.appName)
        Spacer(Modifier.size(TimeManagerSpacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth()) {
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
            Row(
                modifier = Modifier.padding(top = TimeManagerSpacing.small),
                horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { share },
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
internal fun PeriodSelector(
    selected: HistoryPeriod,
    onSelect: (HistoryPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth().selectableGroup()
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
        .padding(3.dp)) {
        HistoryPeriod.entries.forEach { period ->
            val active = selected == period
            Surface(
                modifier = Modifier.weight(1f).selectable(selected = active,
                    role = Role.Tab, onClick = { onSelect(period) }),
                shape = RoundedCornerShape(24.dp),
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) {
                Box(Modifier.padding(vertical = TimeManagerSpacing.compact), contentAlignment = Alignment.Center) {
                    Text(period.label, style = MaterialTheme.typography.labelLarge)
                }
            }
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
            top = TimeManagerSpacing.large,
            bottom = TimeManagerSpacing.small,
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
        Text("这个周期还没有使用记录", style = MaterialTheme.typography.titleMedium)
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

internal fun formatHistoryDuration(seconds: Long): String = formatUsageDuration(seconds)
