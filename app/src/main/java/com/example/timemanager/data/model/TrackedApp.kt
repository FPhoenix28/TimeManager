package com.example.timemanager.data.model

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

/** User-installed application and its real-time tracking selection state. */
@Immutable
data class TrackedApp(
    val packageName: String,
    val appName: String,
    val appIcon: Bitmap?,
    val isTracked: Boolean,
)
