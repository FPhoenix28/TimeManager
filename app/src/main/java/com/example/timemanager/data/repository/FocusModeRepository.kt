package com.example.timemanager.data.repository

import com.example.timemanager.data.model.FocusModeState
import com.example.timemanager.data.model.FocusModeStatus
import com.example.timemanager.data.model.FocusModeType
import com.example.timemanager.data.preferences.FocusModePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class FocusModeRepository(
    private val preferences: FocusModePreferences,
    private val ownPackageName: String,
) {
    fun observeState(): Flow<FocusModeState> = preferences.observeState()

    suspend fun getCurrentState(): FocusModeState = observeState().first()

    suspend fun startFocus(
        durationMinutes: Int,
        selectedPackages: Set<String>,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        require(durationMinutes in 1..720) { "Focus duration must be 1–720 minutes." }
        val blocked = selectedPackages.filterTo(mutableSetOf()) {
            it.isNotBlank() && it != ownPackageName
        }
        require(blocked.isNotEmpty()) { "Select at least one other app." }
        preferences.updateState { current ->
            require(current.mode != FocusModeType.DEEP_FOCUS) {
                "End the current Deep Focus session first."
            }
            FocusModeState(
                mode = FocusModeType.FOCUS,
                startTimeMillis = nowMillis,
                endTimeMillis = nowMillis + durationMinutes * 60_000L,
                blockedPackages = blocked,
            )
        }
    }

    suspend fun startDeepFocus(
        durationMinutes: Int,
        replaceActiveFocus: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        require(durationMinutes in 1..720) { "Deep Focus duration must be 1–720 minutes." }
        preferences.updateState { current ->
            require(current.mode != FocusModeType.DEEP_FOCUS) {
                "A Deep Focus session is already present."
            }
            require(
                current.mode != FocusModeType.FOCUS ||
                    !current.isActive(nowMillis) ||
                    replaceActiveFocus,
            ) { "Starting Deep Focus requires confirmation to end the active Focus session." }
            FocusModeState(
                mode = FocusModeType.DEEP_FOCUS,
                status = FocusModeStatus.ACTIVE,
                startTimeMillis = nowMillis,
                endTimeMillis = nowMillis + durationMinutes * 60_000L,
            )
        }
    }

    suspend fun stopFocus() = preferences.save(FocusModeState())

    suspend fun stopIfExpired(nowMillis: Long = System.currentTimeMillis()) =
        preferences.updateState { FocusModeTransitions.advanceIfExpired(it, nowMillis) }
}

internal object FocusModeTransitions {
    fun advanceIfExpired(state: FocusModeState, nowMillis: Long): FocusModeState = when {
        !state.hasValidTimeWindow -> FocusModeState()
        state.mode == FocusModeType.FOCUS && nowMillis >= state.endTimeMillis ->
            FocusModeState()
        state.mode == FocusModeType.DEEP_FOCUS &&
            state.status == FocusModeStatus.ACTIVE &&
            nowMillis >= state.endTimeMillis -> state.copy(
                status = FocusModeStatus.COMPLETED,
                blockedPackages = emptySet(),
            )
        else -> state
    }
}
