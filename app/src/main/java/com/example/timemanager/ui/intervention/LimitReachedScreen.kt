package com.example.timemanager.ui.intervention

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.data.model.InterventionType
import com.example.timemanager.data.model.LimitInterventionRequest
import com.example.timemanager.ui.theme.TimeManagerSpacing
import kotlinx.coroutines.delay

@Composable
fun LimitReachedScreen(
    request: LimitInterventionRequest,
    onExit: () -> Unit,
    onContinue: () -> Unit,
) {
    when (request.type) {
        InterventionType.DAILY_LIMIT_REACHED -> DailyLimitReachedContent(
            request = request,
            onExit = onExit,
            onContinue = onContinue,
        )

        InterventionType.SESSION_REMINDER -> SessionReminderContent(
            request = request,
            onExit = onExit,
            onContinue = onContinue,
        )
    }
}

@Composable
private fun SessionReminderContent(
    request: LimitInterventionRequest,
    onExit: () -> Unit,
    onContinue: () -> Unit,
) {
    InterventionSurface(
        iconRes = R.drawable.ic_session_pause,
        iconDescription = "连续使用提醒",
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = "已经连续使用一段时间",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "你已经连续使用${request.appName}",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = formatShortDuration(request.currentSessionSeconds),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = "可以稍微离开一下屏幕，再决定是否继续。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                alpha = SECONDARY_TEXT_ALPHA,
            ),
        )
        Spacer(modifier = Modifier.height(TimeManagerSpacing.small))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("继续使用")
        }
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("退出应用")
        }
    }
}

@Composable
private fun DailyLimitReachedContent(
    request: LimitInterventionRequest,
    onExit: () -> Unit,
    onContinue: () -> Unit,
) {
    var waitSeconds by remember(request.packageName, request.effectiveLimitSeconds) {
        mutableIntStateOf(WAIT_SECONDS)
    }
    LaunchedEffect(request.packageName, request.effectiveLimitSeconds) {
        while (waitSeconds > 0) {
            delay(1_000L)
            waitSeconds--
        }
    }

    InterventionSurface(
        iconRes = R.drawable.ic_daily_limit,
        iconDescription = "每日额度提醒",
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = "今日额度已用完",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${request.appName}今天的可用时间已经用完",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(TimeManagerSpacing.extraSmall))
        Text(
            text = "今日已使用",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onErrorContainer.copy(
                alpha = SECONDARY_TEXT_ALPHA,
            ),
        )
        Text(
            text = formatDuration(request.todayUsageSeconds),
            style = MaterialTheme.typography.displaySmall,
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.compact),
        ) {
            UsageLine("基础额度", formatDuration(request.baseLimitSeconds))
            UsageLine("今日已延长", formatDuration(request.extensionSeconds))
            UsageLine("当前有效额度", formatDuration(request.effectiveLimitSeconds))
            if (request.exceededSeconds > 0L) {
                UsageLine("已经超出", formatDuration(request.exceededSeconds))
            }
        }
        Spacer(modifier = Modifier.height(TimeManagerSpacing.small))
        if (waitSeconds > 0) {
            Text(
                text = "$waitSeconds 秒后可以选择延长使用",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(
                    alpha = SECONDARY_TEXT_ALPHA,
                ),
            )
        }
        Button(
            onClick = onContinue,
            enabled = waitSeconds == 0,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text("延长 ${formatShortDuration(request.extensionDurationSeconds)}")
        }
        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("退出应用")
        }
    }
}

@Composable
private fun InterventionSurface(
    @DrawableRes iconRes: Int,
    iconDescription: String,
    containerColor: Color,
    contentColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(TimeManagerSpacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(TimeManagerSpacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.compact),
                ) {
                    Surface(
                        modifier = Modifier.size(ICON_CONTAINER_SIZE),
                        shape = MaterialTheme.shapes.large,
                        color = contentColor.copy(alpha = ICON_CONTAINER_ALPHA),
                        contentColor = contentColor,
                    ) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = iconDescription,
                            modifier = Modifier.padding(TimeManagerSpacing.medium),
                        )
                    }
                    Spacer(modifier = Modifier.height(TimeManagerSpacing.extraSmall))
                    content()
                }
            }
        }
    }
}

@Composable
private fun UsageLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun formatShortDuration(totalSeconds: Long): String = when {
    totalSeconds < 60L -> "$totalSeconds 秒"
    totalSeconds % 60L == 0L -> "${totalSeconds / 60L} 分钟"
    else -> "${totalSeconds / 60L} 分 ${totalSeconds % 60L} 秒"
}

private const val WAIT_SECONDS = 5
private const val SECONDARY_TEXT_ALPHA = 0.76f
private const val ICON_CONTAINER_ALPHA = 0.12f
private const val SCRIM_ALPHA = 0.82f
private val ICON_CONTAINER_SIZE = 64.dp
