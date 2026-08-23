package com.example.timemanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.timemanager.ui.theme.TimeManagerThemeValues

enum class UsageProgressState {
    NORMAL,
    APPROACHING_LIMIT,
    LIMIT_REACHED,
}

@Composable
fun UsageProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = safeProgress,
        animationSpec = tween(durationMillis = 220),
        label = "usageProgress",
    )
    val state = when {
        safeProgress >= 1f -> UsageProgressState.LIMIT_REACHED
        safeProgress >= 0.8f -> UsageProgressState.APPROACHING_LIMIT
        else -> UsageProgressState.NORMAL
    }
    val indicatorColor = when (state) {
        UsageProgressState.NORMAL -> MaterialTheme.colorScheme.primary
        UsageProgressState.APPROACHING_LIMIT -> TimeManagerThemeValues.semanticColors.warning
        UsageProgressState.LIMIT_REACHED -> MaterialTheme.colorScheme.error
    }

    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier,
        color = indicatorColor,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
