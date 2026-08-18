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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.example.timemanager.data.model.AppUsage

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        HomeUiState.Loading -> LoadingContent(modifier)
        is HomeUiState.Success -> UsageList(
            appUsages = state.appUsages,
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
    appUsages: List<AppUsage>,
    modifier: Modifier = Modifier,
) {
    val usageSummary = remember(appUsages) {
        UsageSummary(
            totalUsageMillis = appUsages.sumOf(AppUsage::usageTimeMillis),
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
                totalUsageMillis = usageSummary.totalUsageMillis,
                mostUsedApp = usageSummary.mostUsedApp,
            )
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
    totalUsageMillis: Long,
    mostUsedApp: AppUsage?,
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
                text = formatUsageDuration(totalUsageMillis),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = if (mostUsedApp == null) {
                    "使用最多：暂无记录"
                } else {
                    "使用最多：${mostUsedApp.appName} · ${formatUsageDuration(mostUsedApp.usageTimeMillis)}"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun UsageCard(appUsage: AppUsage) {
    val usageMinutes = appUsage.usageTimeMillis / MILLIS_PER_MINUTE
    val usageProgress = (usageMinutes.toFloat() / DEFAULT_DAILY_LIMIT_MINUTES)
        .coerceIn(0f, 1f)
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
                Text(
                    text = "今日使用 $usageMinutes 分钟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$usageMinutes/$DEFAULT_DAILY_LIMIT_MINUTES 分钟",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { usageProgress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
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

private const val MILLIS_PER_MINUTE = 60_000L
private const val DEFAULT_DAILY_LIMIT_MINUTES = 60

private data class UsageSummary(
    val totalUsageMillis: Long,
    val mostUsedApp: AppUsage?,
)

private fun formatUsageDuration(usageTimeMillis: Long): String {
    val totalMinutes = usageTimeMillis / MILLIS_PER_MINUTE
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60

    return if (hours > 0) "$hours 小时 $minutes 分钟" else "$minutes 分钟"
}
