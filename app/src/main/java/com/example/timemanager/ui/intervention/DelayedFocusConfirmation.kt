package com.example.timemanager.ui.intervention

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.timemanager.R
import com.example.timemanager.ui.components.FeatureArtwork
import com.example.timemanager.ui.theme.TimeManagerSpacing

@Composable
internal fun DelayedFocusConfirmation(compactHeight: Boolean, error: String?) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactHeight) TimeManagerSpacing.extraSmall else TimeManagerSpacing.small),
    ) {
        FeatureArtwork(R.drawable.ic_tm_deep_focus, Modifier.size(if (compactHeight) 36.dp else 64.dp))
        Text(
            "准备开始静心",
            style = if (compactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            "给自己一点时间，结束手头的事情。",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = if (compactHeight) 0.dp else TimeManagerSpacing.small),
            shape = MaterialTheme.shapes.large,
            color = colors.primary.copy(alpha = 0.08f),
            contentColor = colors.onSurface,
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (compactHeight) TimeManagerSpacing.small else TimeManagerSpacing.compact,
                    vertical = if (compactHeight) TimeManagerSpacing.extraSmall else TimeManagerSpacing.small,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.extraSmall),
            ) {
                Text("5 分钟后自动开始", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Text("静心持续 30 分钟", style = MaterialTheme.typography.titleMedium,
                    color = colors.primary, textAlign = TextAlign.Center)
            }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.error, textAlign = TextAlign.Center)
        }
    }
}
