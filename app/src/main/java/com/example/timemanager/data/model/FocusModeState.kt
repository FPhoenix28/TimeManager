package com.example.timemanager.data.model

data class FocusModeState(
    val mode: FocusModeType = FocusModeType.OFF,
    val status: FocusModeStatus = FocusModeStatus.ACTIVE,
    val startTimeMillis: Long = 0L,
    val endTimeMillis: Long = 0L,
    val blockedPackages: Set<String> = emptySet(),
) {
    val hasValidTimeWindow: Boolean
        get() = startTimeMillis > 0L && endTimeMillis > startTimeMillis

    /** Kept as the ordinary Focus predicate for existing callers. */
    fun isActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
        mode == FocusModeType.FOCUS &&
            status == FocusModeStatus.ACTIVE &&
            hasValidTimeWindow &&
            nowMillis >= startTimeMillis &&
            nowMillis < endTimeMillis &&
            blockedPackages.isNotEmpty()

    fun isDeepFocusActive(nowMillis: Long = System.currentTimeMillis()): Boolean =
        mode == FocusModeType.DEEP_FOCUS &&
            status == FocusModeStatus.ACTIVE &&
            hasValidTimeWindow &&
            nowMillis >= startTimeMillis &&
            nowMillis < endTimeMillis

    fun isDeepFocusCompleted(): Boolean =
        mode == FocusModeType.DEEP_FOCUS &&
            status == FocusModeStatus.COMPLETED &&
            hasValidTimeWindow

    fun remainingMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        (endTimeMillis - nowMillis).coerceAtLeast(0L)
}
