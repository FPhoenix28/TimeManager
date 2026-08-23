package com.example.timemanager.ui.home

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.timemanager.R
import com.example.timemanager.ui.components.AppIcon
import com.example.timemanager.ui.components.EmptyState
import com.example.timemanager.ui.components.FullScreenError
import com.example.timemanager.ui.components.FullScreenLoading
import com.example.timemanager.ui.components.UsageProgressIndicator
import com.example.timemanager.ui.statistics.TrendComparisonText
import com.example.timemanager.ui.theme.TimeManagerSpacing

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    hasRequiredPermissions: Boolean,
    onOpenPermissionSettings: () -> Unit,
    onOpenManagement: () -> Unit,
    onOpenAppDetail: (String) -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        HomeUiState.Loading -> LoadingContent(modifier)
        is HomeUiState.Success -> HomeDashboard(
            appUsages = state.appUsages,
            weeklyTrend = state.weeklyTrend,
            hasRequiredPermissions = hasRequiredPermissions,
            onOpenPermissionSettings = onOpenPermissionSettings,
            onOpenManagement = onOpenManagement,
            onOpenAppDetail = onOpenAppDetail,
            onOpenStatistics = onOpenStatistics,
            modifier = modifier,
        )
        is HomeUiState.Error -> ErrorContent(
            message = state.message,
            modifier = modifier,
        )
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    FullScreenLoading(modifier)
}

@Composable
private fun HomeDashboard(
    appUsages: List<AppUsageWithLimit>,
    weeklyTrend: WeeklyTrendSummary,
    hasRequiredPermissions: Boolean,
    onOpenPermissionSettings: () -> Unit,
    onOpenManagement: () -> Unit,
    onOpenAppDetail: (String) -> Unit,
    onOpenStatistics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val managedApps = remember(appUsages) {
        appUsages
            .filter { it.baseLimitSeconds != null }
            .sortedByDescending(AppUsageWithLimit::usedSeconds)
    }
    val totalUsageSeconds = remember(managedApps) {
        managedApps.sumOf(AppUsageWithLimit::usedSeconds)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TimeManagerSpacing.medium,
            top = TimeManagerSpacing.small,
            end = TimeManagerSpacing.medium,
            bottom = TimeManagerSpacing.extraLarge,
        ),
    ) {
        item("topBar") {
            HomeTopBar(onOpenSettings = onOpenPermissionSettings)
        }

        item("todaySummary") {
            TodaySummary(
                totalUsageSeconds = totalUsageSeconds,
                weeklyTrend = weeklyTrend,
                onOpenStatistics = onOpenStatistics,
            )
        }

        if (!hasRequiredPermissions) {
            item("permissionWarning") {
                PermissionWarning(onOpenPermissionSettings)
            }
        }

        item("appsHeader") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = TimeManagerSpacing.large,
                        bottom = TimeManagerSpacing.small,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "今日应用",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (managedApps.isNotEmpty()) {
                    TextButton(onClick = onOpenManagement) { Text("管理") }
                }
            }
        }

        if (managedApps.isEmpty()) {
            item("empty") {
                EmptyState(
                    title = "还没有管理任何应用",
                    message = "选择容易占用你时间的应用，\nTimeManager 会帮助你关注使用时间。",
                    actionLabel = "选择应用",
                    onAction = onOpenManagement,
                    modifier = Modifier.padding(vertical = TimeManagerSpacing.extraLarge),
                )
            }
        } else {
            items(
                items = managedApps,
                key = AppUsageWithLimit::packageName,
            ) { appUsage ->
                ManagedAppUsageItem(
                    appUsage = appUsage,
                    onClick = { onOpenAppDetail(appUsage.packageName) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = TimeManagerSpacing.medium + TimeManagerSpacing.extraLarge),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeTopBar(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = TimeManagerSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "TimeManager",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSettings) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = "权限与系统设置",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TodaySummary(
    totalUsageSeconds: Long,
    weeklyTrend: WeeklyTrendSummary,
    onOpenStatistics: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = TimeManagerSpacing.large),
        verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
    ) {
        Text(
            text = "今天",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = formatDashboardDuration(totalUsageSeconds),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = "受管理应用总使用时间",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenStatistics)
                .padding(vertical = TimeManagerSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "过去7天 ${formatCompactDuration(weeklyTrend.totalSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TrendComparisonText(weeklyTrend.changePercent, "前7天")
            }
            Text(
                text = "查看统计  ›",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PermissionWarning(onOpenPermissionSettings: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TimeManagerSpacing.medium),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(TimeManagerSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("缺少必要权限", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "部分统计、提醒或冷静页功能可能无法运行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            OutlinedButton(onClick = onOpenPermissionSettings) { Text("重新授权") }
        }
    }
}

@Composable
private fun ManagedAppUsageItem(
    appUsage: AppUsageWithLimit,
    onClick: () -> Unit,
) {
    val effectiveLimitSeconds = checkNotNull(appUsage.effectiveLimitSeconds)
    val progress = when {
        effectiveLimitSeconds <= 0L && appUsage.usedSeconds > 0L -> 1f
        effectiveLimitSeconds <= 0L -> 0f
        else -> appUsage.usedSeconds.toFloat() / effectiveLimitSeconds.toFloat()
    }
    val differenceSeconds = effectiveLimitSeconds - appUsage.usedSeconds

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = TimeManagerSpacing.medium),
        verticalAlignment = Alignment.Top,
    ) {
        AppIcon(bitmap = appUsage.appIcon, appName = appUsage.appName)
        Spacer(Modifier.size(TimeManagerSpacing.medium))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = appUsage.appName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatCompactDuration(appUsage.usedSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            UsageProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
            )
            Row {
                Text(
                    text = if (differenceSeconds >= 0L) {
                        "剩余 ${formatCompactDuration(differenceSeconds)}"
                    } else {
                        "已超出 ${formatCompactDuration(-differenceSeconds)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (differenceSeconds >= 0L) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "/ ${formatCompactDuration(effectiveLimitSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    FullScreenError(
        title = "读取使用时间失败",
        message = message,
        modifier = modifier,
    )
}

private fun formatDashboardDuration(seconds: Long): String {
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    return if (hours > 0L) "${hours}h ${minutes}min" else "${minutes} min"
}

private fun formatCompactDuration(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3_600L
    val minutes = (safeSeconds % 3_600L) / 60L
    return when {
        hours > 0L -> "${hours}h ${minutes}min"
        minutes > 0L -> "${minutes} min"
        else -> "${safeSeconds} sec"
    }
}
