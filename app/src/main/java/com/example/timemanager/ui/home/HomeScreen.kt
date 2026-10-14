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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.timemanager.R
import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeType
import com.example.timemanager.ui.focus.FocusCountdown
import com.example.timemanager.ui.components.AppIcon
import com.example.timemanager.ui.components.EmptyState
import com.example.timemanager.ui.components.FullScreenError
import com.example.timemanager.ui.components.FullScreenLoading
import com.example.timemanager.ui.components.UsageProgressIndicator
import com.example.timemanager.ui.components.UsageDurationText
import com.example.timemanager.ui.components.formatUsageDuration
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import com.example.timemanager.ui.theme.TimeManagerSpacing
import com.example.timemanager.ui.todo.TodoHomeSection
import com.example.timemanager.ui.todo.TodoViewModel
import com.example.timemanager.permissions.PermissionHealthState

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    todoViewModel: TodoViewModel,
    onOpenTodos: () -> Unit,
    permissionHealth: PermissionHealthState,
    onOpenPermissionSettings: () -> Unit,
    onOpenManagement: () -> Unit,
    onOpenAppDetail: (String) -> Unit,
    onOpenStatistics: () -> Unit,
    focusState: FocusModeState,
    onOpenFocus: () -> Unit,
    onOpenDeepFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        HomeUiState.Loading -> FocusHomeFallback(
            permissionHealth = permissionHealth,
            focusState = focusState,
            onOpenFocus = onOpenFocus,
            onOpenDeepFocus = onOpenDeepFocus,
            onOpenSettings = onOpenPermissionSettings,
            todoViewModel = todoViewModel,
            onOpenTodos = onOpenTodos,
            message = if (permissionHealth.usageAccessGranted) "正在读取今日使用情况…"
                else "使用情况访问未开启，使用统计、连续提醒和每日额度判断暂不可用。",
            modifier = modifier,
        )
        is HomeUiState.Success -> HomeDashboard(
            appUsages = state.appUsages,
            todoViewModel = todoViewModel,
            onOpenTodos = onOpenTodos,
            permissionHealth = permissionHealth,
            onOpenPermissionSettings = onOpenPermissionSettings,
            onOpenManagement = onOpenManagement,
            onOpenAppDetail = onOpenAppDetail,
            onOpenStatistics = onOpenStatistics,
            focusState = focusState,
            onOpenFocus = onOpenFocus,
            onOpenDeepFocus = onOpenDeepFocus,
            modifier = modifier,
        )
        is HomeUiState.Error -> FocusHomeFallback(
            permissionHealth = permissionHealth,
            focusState = focusState,
            onOpenFocus = onOpenFocus,
            onOpenDeepFocus = onOpenDeepFocus,
            onOpenSettings = onOpenPermissionSettings,
            message = "读取使用时间失败：${state.message}",
            todoViewModel = todoViewModel,
            onOpenTodos = onOpenTodos,
            modifier = modifier,
        )
    }
}

@Composable
private fun FocusHomeFallback(
    permissionHealth: PermissionHealthState,
    todoViewModel: TodoViewModel,
    onOpenTodos: () -> Unit,
    focusState: FocusModeState,
    onOpenFocus: () -> Unit,
    onOpenDeepFocus: () -> Unit,
    onOpenSettings: () -> Unit,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(TimeManagerSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.medium),
    ) {
        HomeTopBar(onOpenSettings, !permissionHealth.isHealthy)
        FocusModeEntries(focusState, onOpenFocus, onOpenDeepFocus)
        TodoHomeSection(todoViewModel, onOpenTodos)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeDashboard(
    todoViewModel: TodoViewModel,
    onOpenTodos: () -> Unit,
    appUsages: List<AppUsageWithLimit>,
    permissionHealth: PermissionHealthState,
    onOpenPermissionSettings: () -> Unit,
    onOpenManagement: () -> Unit,
    onOpenAppDetail: (String) -> Unit,
    onOpenStatistics: () -> Unit,
    focusState: FocusModeState,
    onOpenFocus: () -> Unit,
    onOpenDeepFocus: () -> Unit,
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
            HomeTopBar(onOpenSettings = onOpenPermissionSettings, showPermissionBadge = !permissionHealth.isHealthy)
        }

        item("todaySummary") {
            TodaySummary(
                usageAccessGranted = permissionHealth.usageAccessGranted,
                totalUsageSeconds = totalUsageSeconds,
            )
        }

        item("focusEntry") {
            FocusModeEntries(focusState, onOpenFocus, onOpenDeepFocus)
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
                    usageAccessGranted = permissionHealth.usageAccessGranted,
                    appUsage = appUsage,
                    onClick = { onOpenAppDetail(appUsage.packageName) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = TimeManagerSpacing.medium + TimeManagerSpacing.extraLarge),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        item("todos") { TodoHomeSection(todoViewModel, onOpenTodos) }
    }
}

@Composable
private fun FocusModeEntries(
    focusState: FocusModeState,
    onOpenFocus: () -> Unit,
    onOpenDeepFocus: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
        when (focusState.mode) {
            FocusModeType.FOCUS -> {
                ModeEntry(
                    title = "专注模式",
                    description = if (focusState.isActive()) "专注进行中" else "专注已结束",
                    onClick = onOpenFocus,
                    endTimeMillis = focusState.endTimeMillis.takeIf { focusState.isActive() },
                )
                if (focusState.isActive()) {
                    TextButton(onClick = onOpenDeepFocus) {
                        Text("改为静心模式")
                    }
                }
            }
            FocusModeType.DEEP_FOCUS -> ModeEntry(
                title = "静心模式",
                description = if (focusState.isDeepFocusCompleted()) "静心结束" else "静心进行中",
                onClick = onOpenDeepFocus,
                endTimeMillis = focusState.endTimeMillis.takeIf { focusState.isDeepFocusActive() },
            )
            FocusModeType.OFF -> {
                BoxWithConstraints(Modifier.fillMaxWidth().padding(top = TimeManagerSpacing.large)) {
                    val largeFont = LocalDensity.current.fontScale > 1.25f
                    if (maxWidth < 300.dp || largeFont) {
                        Column(verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
                            CompactModeEntry("专注模式", "屏蔽分心应用", R.drawable.ic_focus, onOpenFocus)
                            CompactModeEntry("静心模式", "暂时离开手机", R.drawable.ic_leaf, onOpenDeepFocus)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
                            CompactModeEntry("专注模式", "屏蔽分心应用", R.drawable.ic_focus, onOpenFocus, Modifier.weight(1f))
                            CompactModeEntry("静心模式", "暂时离开手机", R.drawable.ic_leaf, onOpenDeepFocus, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactModeEntry(title: String, description: String, icon: Int,
                             onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(TimeManagerSpacing.compact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small)) {
            Icon(painterResource(icon), null, modifier = Modifier.size(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun ModeEntry(
    title: String,
    description: String,
    onClick: () -> Unit,
    endTimeMillis: Long? = null,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = TimeManagerSpacing.large),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(TimeManagerSpacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
            if (endTimeMillis != null) {
                FocusCountdown(endTimeMillis, MaterialTheme.typography.titleMedium)
            } else {
                Text("进入 ›", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun HomeTopBar(onOpenSettings: () -> Unit, showPermissionBadge: Boolean) {
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
            BadgedBox(badge = {
                if (showPermissionBadge) {
                    Badge(containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError) { Text("!") }
                }
            }) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = if (showPermissionBadge) "系统权限：有功能未启用" else "权限与系统设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TodaySummary(
    usageAccessGranted: Boolean,
    totalUsageSeconds: Long,
) {
    Column(
        modifier = Modifier.padding(top = TimeManagerSpacing.large),
        verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.extraSmall),
    ) {
        Text("今日已使用", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (usageAccessGranted) UsageDurationText(totalUsageSeconds)
        else Text("—", style = MaterialTheme.typography.displaySmall)
        Text(if (usageAccessGranted) "受管理应用总使用时间"
            else "无法统计应用使用时间，连续使用提醒与每日额度判断暂不可用。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ManagedAppUsageItem(
    usageAccessGranted: Boolean,
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
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (usageAccessGranted) formatCompactDuration(appUsage.usedSeconds) else "—",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (!usageAccessGranted) {
                Text("使用统计暂不可用", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                UsageProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    Text(
                        text = if (differenceSeconds == 0L) {
                            "额度已用完"
                        } else if (differenceSeconds > 0L) {
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

private fun formatCompactDuration(seconds: Long): String = formatUsageDuration(seconds)
