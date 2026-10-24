package com.example.timemanager.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Global, configurable product timings only. No usage/episode/admission state. */
enum class DebugTimingParameter(val label: String) {
    INCREMENT_FIRST("第二次提醒增加时间"), INCREMENT_SECOND("第三次提醒增加时间"),
    INCREMENT_REPEAT("后续每次增加时间"), IDLE_RESET("离开多久后重置连续使用"),
    GATE("提醒页面冷却时间"), EXTENSION("每次延长额度"),
    NOTIFICATION_FIRST("Session 通知1（不弹冷静页）"),
    NOTIFICATION_SECOND("Session 通知2（不弹冷静页）");

    fun seconds(c: TimeConfig): Long = when (this) {
        INCREMENT_FIRST -> c.sessionFirstIncrementSeconds
        INCREMENT_SECOND -> c.sessionSecondIncrementSeconds
        INCREMENT_REPEAT -> c.sessionRepeatingIncrementSeconds
        IDLE_RESET -> c.frictionResetIdleSeconds
        GATE -> c.frictionGateSeconds
        EXTENSION -> c.extensionDurationSeconds
        NOTIFICATION_FIRST -> c.firstSessionReminderSeconds
        NOTIFICATION_SECOND -> c.secondSessionReminderSeconds
    }

    fun updated(c: TimeConfig, seconds: Long): TimeConfig {
        require(seconds in 1L..DurationInput.MAX_SECONDS) { "时间须为1秒至99小时59分59秒" }
        return when (this) {
            INCREMENT_FIRST -> c.copy(sessionFirstIncrementSeconds = seconds)
            INCREMENT_SECOND -> c.copy(sessionSecondIncrementSeconds = seconds)
            INCREMENT_REPEAT -> c.copy(sessionRepeatingIncrementSeconds = seconds)
            IDLE_RESET -> c.copy(frictionResetIdleSeconds = seconds)
            GATE -> c.copy(frictionGateSeconds = seconds)
            EXTENSION -> c.copy(extensionDurationSeconds = seconds)
            NOTIFICATION_FIRST -> c.copy(firstSessionReminderSeconds = seconds)
            NOTIFICATION_SECOND -> c.copy(secondSessionReminderSeconds = seconds)
        }
    }
}

interface DebugTimingStorage {
    fun read(): TimeConfig
    fun write(config: TimeConfig)
    fun clear()
}

class DebugTimingController(private val storage: DebugTimingStorage) : TimeConfigProvider {
    private val state = MutableStateFlow(storage.read())
    val settings = state.asStateFlow()
    override fun current(): TimeConfig = state.value
    override fun observe() = settings

    fun save(parameter: DebugTimingParameter, seconds: Long) {
        val updated = parameter.updated(state.value, seconds).copy(debugTimeOverridesEnabled = false)
        val config = updated.copy(debugTimeOverridesEnabled = updated != TimeConfig.production())
        storage.write(config)
        state.value = config
    }

    fun reset() {
        storage.clear()
        state.value = TimeConfig.production()
    }
}

/** Injection-only fixtures retained for existing host tests; never a UI preset or runtime default. */
object DebugTimingPresets {
    val legacyFastRegression = TimeConfig(
        debugTimeOverridesEnabled = true, firstSessionReminderSeconds = 15L,
        secondSessionReminderSeconds = 30L, extensionDurationSeconds = 30L,
        frictionResetIdleSeconds = 30L, sessionInterventionOverrideSeconds = 20L,
    )
    val fast = legacyFastRegression.copy(
        frictionGateSeconds = 2L, sessionFirstIncrementSeconds = 15L,
        sessionSecondIncrementSeconds = 15L, sessionRepeatingIncrementSeconds = 10L,
    )
}
