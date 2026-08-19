package com.example.timemanager.ui.home

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onOpenManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        HomeUiState.Loading -> LoadingContent(modifier)
        is HomeUiState.Success -> UsageList(
            appUsages = state.appUsages,
            hasOverlayPermission = hasOverlayPermission,
            onRequestOverlayPermission = onRequestOverlayPermission,
            onOpenManagement = onOpenManagement,
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
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun UsageList(
    appUsages: List<AppUsageWithLimit>,
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onOpenManagement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val usageSummary = remember(appUsages) {
        UsageSummary(
            totalUsageMinutes = appUsages.sumOf(AppUsageWithLimit::usedMinutes),
            mostUsedApp = appUsages.firstOrNull(),
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "TimeManager",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "今日使用情况",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            UsageSummaryCard(
                totalUsageMinutes = usageSummary.totalUsageMinutes,
                mostUsedApp = usageSummary.mostUsedApp,
            )
        }

        item {
            Button(
                onClick = onOpenManagement,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("管理应用")
            }
        }

        if (!hasOverlayPermission) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "冷静页权限尚未开启",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "开启后，受管理应用超出每日额度时会显示冷静页。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(
                            onClick = onRequestOverlayPermission,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("开启冷静页权限")
                        }
                    }
                }
            }
        }

        if (appUsages.isEmpty()) {
            item {
                Text("今天还没有可显示的使用记录")
            }
        } else {
            items(
                items = appUsages,
                key = { appUsage -> appUsage.packageName },
            ) { appUsage ->
                UsageCard(appUsage)
            }
        }
    }
}

@Composable
private fun UsageSummaryCard(
    totalUsageMinutes: Long,
    mostUsedApp: AppUsageWithLimit?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "今日总使用时间",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = formatUsageDuration(totalUsageMinutes),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = if (mostUsedApp == null) {
                    "使用最多：暂无记录"
                } else {
                    "使用最多：${mostUsedApp.appName} · ${formatUsageDuration(mostUsedApp.usedMinutes)}"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun UsageCard(appUsage: AppUsageWithLimit) {
    val appIcon = remember(appUsage.appIcon) {
        appUsage.appIcon?.asImageBitmap()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(
                appIcon = appIcon,
                appName = appUsage.appName,
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = appUsage.appName,
                    style = MaterialTheme.typography.titleMedium,
                )
                UsageLimitContent(appUsage)
            }
        }
    }
}

@Composable
private fun UsageLimitContent(appUsage: AppUsageWithLimit) {
    val limitMinutes = appUsage.limitMinutes
    if (limitMinutes == null) {
        Text(
            text = "今日使用：${appUsage.usedMinutes} 分钟",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val remainingMinutes = (limitMinutes.toLong() - appUsage.usedMinutes).coerceAtLeast(0L)
    val usageProgress = when {
        limitMinutes <= 0 && appUsage.usedMinutes > 0 -> 1f
        limitMinutes <= 0 -> 0f
        else -> (appUsage.usedMinutes.toFloat() / limitMinutes).coerceIn(0f, 1f)
    }

    Text(
        text = "${appUsage.usedMinutes} / $limitMinutes 分钟",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = "每日额度：$limitMinutes 分钟",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = "剩余时间：$remainingMinutes 分钟",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    LinearProgressIndicator(
        progress = { usageProgress },
        modifier = Modifier.fillMaxWidth(),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
private fun AppIcon(
    appIcon: ImageBitmap?,
    appName: String,
) {
    if (appIcon != null) {
        Image(
            bitmap = appIcon,
            contentDescription = "$appName 图标",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    } else {
        Text(
            text = appName.firstOrNull()?.toString() ?: "?",
            modifier = Modifier.size(48.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("读取使用时间失败")
        Text(message)
    }
}

private data class UsageSummary(
    val totalUsageMinutes: Long,
    val mostUsedApp: AppUsageWithLimit?,
)

private fun formatUsageDuration(totalMinutes: Long): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return if (hours > 0) "$hours 小时 $minutes 分钟" else "$minutes 分钟"
}
