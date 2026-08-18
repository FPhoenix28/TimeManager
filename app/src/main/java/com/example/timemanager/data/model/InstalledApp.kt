package com.example.timemanager.data.model

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable

/** Display information for an installed user application. */
@Immutable
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val appIcon: Bitmap?,
)
