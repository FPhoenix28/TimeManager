package com.example.timemanager.ui.intervention

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.timemanager.data.model.LimitInterventionRequest
import com.example.timemanager.data.model.InterventionType
import kotlinx.coroutines.delay

@Composable
fun LimitReachedScreen(
    request: LimitInterventionRequest,
    onExit: () -> Unit,
    onContinue: () -> Unit,
) {
    if (request.type == InterventionType.SESSION_REMINDER) {
        SessionReminderContent(request, onExit, onContinue)
        return
    }

    DailyLimitReachedContent(request, onExit, onContinue)
}

@Composable
private fun SessionReminderContent(
    request: LimitInterventionRequest,
    onExit: () -> Unit,
    onContinue: () -> Unit,
) {
    InterventionSurface {
        Text("休息一下？", style = MaterialTheme.typography.headlineMedium)
        Text(request.appName, style = MaterialTheme.typography.titleLarge)
        Text(
            text = "你已经连续使用${request.appName} 30分钟",
            textAlign = TextAlign.Center,
        )
        Text(
            text = "要不要暂时休息一下？",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        UsageLine("当前连续使用", formatDuration(request.currentSessionSeconds))
        UsageLine("今日累计使用", formatDuration(request.todayUsageSeconds))
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("继续使用")
        }
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text("退出")
        }
    }
}

@Composable
private fun DailyLimitReachedContent(
    request: LimitInterventionRequest,
    onExit: () -> Unit,
    onContinue: () -> Unit,
) {
    var waitSeconds by remember(request.packageName) { mutableIntStateOf(WAIT_SECONDS) }
    LaunchedEffect(request.packageName) {
        while (waitSeconds > 0) {
            delay(1_000L)
            waitSeconds--
        }
    }

    InterventionSurface {
        Text("先停一下", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = request.appName,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(4.dp))
        UsageLine("今日已使用", formatDuration(request.todayUsageSeconds))
        UsageLine("基础额度", formatDuration(request.baseLimitSeconds))
        UsageLine("今日已延长", formatDuration(request.extensionSeconds))
        UsageLine("当前有效额度", formatDuration(request.effectiveLimitSeconds))
        UsageLine("已经超出", formatDuration(request.exceededSeconds))
        UsageLine("当前连续使用", formatDuration(request.currentSessionSeconds))
        Spacer(modifier = Modifier.height(8.dp))

        if (waitSeconds > 0) {
            Text(
                text = "$waitSeconds 秒后可以选择继续使用",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (request.extensionsRemaining > 0) {
            Text(
                text = "今天还可以临时延长 ${request.extensionsRemaining} 次",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "今天的临时延长次数已经用完",
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onContinue,
            enabled = waitSeconds == 0 && request.extensionsRemaining > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("继续使用${formatShortDuration(request.extensionDurationSeconds)}")
        }
        OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
            Text("退出")
        }
    }
}

@Composable
private fun InterventionSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.82f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) { content() }
            }
        }
    }
}

@Composable
private fun UsageLine(label: String, value: String) {
    Text(
        text = "$label：$value",
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth(),
    )
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
    totalSeconds % 60L == 0L -> "${totalSeconds / 60L}分钟"
    else -> "${totalSeconds / 60L}分${totalSeconds % 60L}秒"
}

private const val WAIT_SECONDS = 5
