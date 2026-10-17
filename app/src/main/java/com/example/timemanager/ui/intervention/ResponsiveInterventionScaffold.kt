package com.example.timemanager.ui.intervention

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.timemanager.ui.theme.TimeManagerSpacing

/** Layout only: callers retain the intervention state, countdown and all action callbacks. */
@Composable
internal fun ResponsiveInterventionScaffold(
    containerColor: Color,
    contentColor: Color,
    header: @Composable ColumnScope.(Boolean) -> Unit,
    mainContent: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = true,
    scrimAlpha: Float = 0.82f,
    portraitMaxWidth: Dp = OverlayLayoutPolicy.PORTRAIT_CARD_MAX_WIDTH_DP.dp,
    shape: Shape = MaterialTheme.shapes.extraLarge,
) {
    val latestHeader by rememberUpdatedState(header)
    val latestContent by rememberUpdatedState(mainContent)
    // Keep countdown composition/state when moving information between Column and Row.
    val information = remember {
        movableContentOf<Boolean> { wide ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.compact),
            ) {
                latestHeader(wide)
                latestContent()
            }
        }
    }
    Surface(
        modifier = modifier.then(if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth()),
        color = if (fullScreen) {
            MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha)
        } else {
            Color.Transparent
        },
    ) {
        BoxWithConstraints(
            modifier = if (fullScreen) {
                Modifier.fillMaxSize().safeDrawingPadding().padding(TimeManagerSpacing.large)
            } else {
                Modifier.fillMaxWidth()
            },
            contentAlignment = Alignment.Center,
        ) {
            val isWide = OverlayLayoutPolicy.isWide(maxWidth.value, maxHeight.value)
            val infoScroll = rememberScrollState()
            val actionsScroll = rememberScrollState()
            val portraitScroll = rememberScrollState()
            Card(
                modifier = Modifier
                    .widthIn(max = if (isWide) OverlayLayoutPolicy.WIDE_CARD_MAX_WIDTH_DP.dp else portraitMaxWidth)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
            ) {
                if (isWide) {
                    Row(
                        modifier = Modifier.padding(TimeManagerSpacing.large),
                        horizontalArrangement = Arrangement.spacedBy(TimeManagerSpacing.large),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).verticalScroll(infoScroll),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.compact),
                        ) {
                            information(true)
                        }
                        Column(
                            modifier = Modifier
                                .width(OverlayLayoutPolicy.ACTIONS_WIDTH_DP.dp)
                                .verticalScroll(actionsScroll),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.compact),
                            content = actions,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(portraitScroll).padding(TimeManagerSpacing.large),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(TimeManagerSpacing.compact),
                    ) {
                        information(false)
                        actions()
                    }
                }
            }
        }
    }
}
