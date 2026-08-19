package com.example.timemanager.data.model

/** Today's allowance: the saved base limit plus temporary extensions for this usage day. */
data class EffectiveAppLimit(
    val packageName: String,
    val baseDailyLimitMinutes: Int,
    val todayExtensionMinutes: Int,
) {
    val effectiveLimitMinutes: Int = baseDailyLimitMinutes + todayExtensionMinutes
}
