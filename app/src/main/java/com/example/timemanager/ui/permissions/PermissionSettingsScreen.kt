package com.example.timemanager.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class PermissionUiState(
    val usageAccessGranted: Boolean,
    val notificationGranted: Boolean,
    val overlayGranted: Boolean,
) {
    val allRequiredGranted: Boolean
        get() = usageAccessGranted && notificationGranted && overlayGranted
}

@Composable
fun PermissionSettingsScreen(
    state: PermissionUiState,
    onBack: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedButton(onClick = onBack) {
                Text("返回")
            }
        }
        item {
            Text(
                text = "权限与系统设置",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        item {
            PermissionCards(
                state = state,
                onRequestUsageAccess = onRequestUsageAccess,
                onRequestNotification = onRequestNotification,
                onRequestOverlay = onRequestOverlay,
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PermissionCard(
            title = "使用情况访问",
            description = "用于识别当前正在使用的应用，以及统计应用使用时间。",
            granted = state.usageAccessGranted,
            actionLabel = "去开启",
            onAction = onRequestUsageAccess,
        )
        PermissionCard(
            title = "通知",
            description = "用于显示实时使用时间、连续使用提醒和额度提醒。",
            granted = state.notificationGranted,
            actionLabel = "允许通知",
            onAction = onRequestNotification,
        )
        PermissionCard(
            title = "显示在其他应用上层",
            description = "当应用达到使用额度时，用于显示冷静页面。",
            granted = state.overlayGranted,
            actionLabel = "去开启",
            onAction = onRequestOverlay,
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (granted) "已开启" else "未开启",
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
