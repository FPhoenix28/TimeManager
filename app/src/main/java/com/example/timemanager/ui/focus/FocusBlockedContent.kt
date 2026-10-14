package com.example.timemanager.ui.focus

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.timemanager.ui.intervention.OverlayLayoutPolicy
import com.example.timemanager.ui.intervention.ResponsiveInterventionScaffold
import kotlinx.coroutines.delay

@Composable
fun FocusBlockedContent(
    appName: String,
    endTimeMillis: Long,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ResponsiveInterventionScaffold(
        modifier = modifier,
        fullScreen = false,
        portraitMaxWidth = OverlayLayoutPolicy.FOCUS_PORTRAIT_MAX_WIDTH_DP.dp,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        header = {
            Text("专注进行中", style = MaterialTheme.typography.headlineMedium)
        },
        mainContent = {
            FocusCountdown(endTimeMillis, MaterialTheme.typography.displaySmall)
            Text(
                "$appName 暂时不可使用",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                "你已将该应用加入本次专注。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
            )
        },
        actions = {
            Button(onClick = onReturn, modifier = Modifier.fillMaxWidth()) {
                Text("返回")
            }
        },
    )
}

@Composable
fun FocusCountdown(
    endTimeMillis: Long,
    textStyle: androidx.compose.ui.text.TextStyle,
) {
    val remainingSeconds by produceState(
        initialValue = ((endTimeMillis - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L),
        key1 = endTimeMillis,
    ) {
        while (true) {
            value = ((endTimeMillis - System.currentTimeMillis() + 999L) / 1000L).coerceAtLeast(0L)
            if (value == 0L) break
            delay(1_000L)
        }
    }
    val hours = remainingSeconds / 3600L
    val minutes = (remainingSeconds % 3600L) / 60L
    val seconds = remainingSeconds % 60L
    val text = if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
    Text(text, style = textStyle)
}
