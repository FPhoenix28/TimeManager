package com.example.timemanager.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/** Decorative feature artwork paired with an adjacent text label. */
@Composable
internal fun FeatureArtwork(@DrawableRes resource: Int, modifier: Modifier) {
    Image(
        painter = painterResource(resource),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
