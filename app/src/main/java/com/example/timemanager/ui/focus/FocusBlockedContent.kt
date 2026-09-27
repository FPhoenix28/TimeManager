package com.example.timemanager.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.timemanager.ui.theme.TimeManagerSpacing
import kotlinx.coroutines.delay

@Composable
fun FocusBlockedContent(
    appName: String,
    endTimeMillis: Long,
    onReturn: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(TimeManagerSpacing.large),
            verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("专注进行中", style = MaterialTheme.typography.headlineMedium)
            FocusCountdown(endTimeMillis, MaterialTheme.typography.displaySmall)
            Text(
                "$appName 暂时不可使用",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "你已将该应用加入本次专注。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(onClick = onReturn, modifier = Modifier.fillMaxWidth()) {
                Text("返回")
            }
        }
    }
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
