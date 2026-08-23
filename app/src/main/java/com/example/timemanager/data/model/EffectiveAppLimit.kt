package com.example.timemanager.data.model

/** Today's allowance: the saved base limit plus temporary extensions for this usage day. */
data class EffectiveAppLimit(
    val packageName: String,
    val baseLimitSeconds: Long,
    val todayExtensionSeconds: Long,
) {
    val effectiveLimitSeconds: Long = baseLimitSeconds + todayExtensionSeconds
}
