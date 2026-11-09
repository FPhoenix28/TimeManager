package com.example.timemanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.timemanager.ui.theme.TimeManagerSpacing

internal enum class CalmPanelTone {
    INFORMATION,
    REMINDER,
    IMMERSIVE,
}

internal val CalmPanelTone.containerColor: Color
    @Composable get() = when (this) {
        CalmPanelTone.INFORMATION -> MaterialTheme.colorScheme.surfaceContainer
        CalmPanelTone.REMINDER -> MaterialTheme.colorScheme.surface
        CalmPanelTone.IMMERSIVE -> MaterialTheme.colorScheme.surfaceContainerLow
    }

internal val calmPanelBorder: BorderStroke
    @Composable get() = BorderStroke(
        TimeManagerSpacing.hairline,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
    )

@Composable
internal fun CalmPanel(
    tone: CalmPanelTone,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(TimeManagerSpacing.medium),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = tone.containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = calmPanelBorder,
    ) {
        Column(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
}
