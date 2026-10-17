package com.example.timemanager.ui.permissions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.timemanager.R
import com.example.timemanager.ui.theme.TimeManagerSpacing
import com.example.timemanager.permissions.PermissionHealthState
import com.example.timemanager.permissions.AccessibilityRuntimeState

typealias PermissionUiState = PermissionHealthState

@Composable
fun PermissionSettingsScreen(
    state: PermissionUiState,
    onBack: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = TimeManagerSpacing.medium,
            top = TimeManagerSpacing.small,
            end = TimeManagerSpacing.medium,
            bottom = TimeManagerSpacing.extraLarge,
        ),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "返回",
                    )
                }
                Text(
                    text = "系统权限",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
        item {
            Text(
                text = "查看各项功能是否已启用。返回应用后，授权状态会自动更新。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    top = TimeManagerSpacing.medium,
                    bottom = TimeManagerSpacing.large,
                ),
            )
        }
        item {
            PermissionCards(
                state = state,
                onRequestUsageAccess = onRequestUsageAccess,
                onRequestNotification = onRequestNotification,
                onRequestOverlay = onRequestOverlay,
                onRequestAccessibility = onRequestAccessibility,
                showAccessibility = true,
            )
        }
    }
}

@Composable
fun PermissionCards(
    state: PermissionUiState,
    onRequestUsageAccess: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit = {},
    showAccessibility: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.compact),
    ) {
        PermissionCard(
            title = "使用情况访问",
            description = if (state.usageAccessGranted) "用于统计应用使用时间。"
                else "无法统计应用使用时间，连续使用提醒和每日额度判断暂不可用。",
            granted = state.usageAccessGranted,
            actionLabel = "去开启",
            onAction = onRequestUsageAccess,
        )
        PermissionCard(
            title = "通知",
            description = if (state.notificationGranted) "用于状态、额度与待办提醒。"
                else "待办和状态提醒将无法显示。",
            granted = state.notificationGranted,
            actionLabel = "允许通知",
            onAction = onRequestNotification,
        )
        PermissionCard(
            title = "悬浮窗",
            description = if (state.overlayGranted) "用于显示冷静页。"
                else "冷静页无法覆盖显示，使用时间仍会继续统计。",
            granted = state.overlayGranted,
            actionLabel = "去开启",
            onAction = onRequestOverlay,
        )
        if (showAccessibility) {
            val runtimeState = state.accessibilityRuntimeState
            PermissionCard(
                title = "无障碍服务",
                description = "开启后可使用更完整的干预功能：\n" +
                    "• 连续使用后的自动退出\n• 再次进入时的冷静拦截\n• 专注模式\n• 静心模式\n" +
                    "仅识别前台应用，不读取页面内容。" +
                    if (runtimeState == AccessibilityRuntimeState.ENABLED_BUT_DISCONNECTED)
                        "\n服务暂未连接，连续使用和每日额度冷静页仍可使用；自动回桌面、再次进入拦截和专注保护暂不可用。"
                    else "",
                granted = state.canForceHome,
                statusLabel = when (runtimeState) {
                    AccessibilityRuntimeState.DISABLED -> "未开启"
                    AccessibilityRuntimeState.ENABLED_BUT_DISCONNECTED -> "已开启，暂未连接"
                    AccessibilityRuntimeState.CONNECTED -> "已开启 · 已连接"
                },
                actionLabel = if (state.accessibilityGranted) "查看系统设置" else "前往开启",
                onAction = onRequestAccessibility,
            )
            var showBackgroundHint by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { showBackgroundHint = !showBackgroundHint }) {
                Text(if (showBackgroundHint) "收起后台运行建议" else "vivo / OriginOS 后台运行建议")
            }
            if (showBackgroundHint) {
                Text("如果系统频繁关闭服务，可在系统设置中允许后台运行、允许自启动，并将电池策略设为不限制。系统关闭无障碍开关后，需要你手动重新开启。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    statusLabel: String = if (granted) "已开启" else "未开启",
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(TimeManagerSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.small),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = statusLabel,
                    color = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}
