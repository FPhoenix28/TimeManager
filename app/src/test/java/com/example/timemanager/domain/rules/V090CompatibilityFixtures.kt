package com.example.timemanager.domain.rules

import com.example.timemanager.domain.profile.AppPolicy
import com.example.timemanager.domain.profile.DailyRuleConfig
import com.example.timemanager.domain.profile.EffectiveAppPolicy
import com.example.timemanager.domain.profile.ProfileId
import com.example.timemanager.permissions.AccessibilityRuntimeState

/** Explicit test facts, never an override of user settings or a source of runtime defaults. */
internal object V090CompatibilityFixtures {
    const val PACKAGE = "example.managed"
    const val DAILY_EXTENSION_SECONDS = 900L
    val usageDay = UsageDayKey("2026-10-22")
    val ledgerIdentity = UsageDayRuntimeIdentity(PACKAGE, usageDay)
    val identity = EvaluationIdentity(PACKAGE, ForegroundGeneration(7L), PolicyRevision(2L), usageDay, 1L)

    fun daily(usageSeconds: Long = 600L, extensionSeconds: Long = 0L) = DailyRuntimeSnapshot(
        ledgerIdentity, usageSeconds, extensionSeconds,
    )

    fun session() = SessionRuntimeSnapshot(
        packageName = PACKAGE,
        episode = SessionEpisodeSnapshot(
            episodeUsageSeconds = 600L,
            firstReminderSeconds = 600L,
            reminderCount = 0,
            nextReminderAtSeconds = 600L,
            lastEngagementElapsedMillis = 1_000L,
        ),
        physicalSession = PhysicalSessionSnapshot(600L, actuallyInForeground = true),
    )

    fun context(profileId: ProfileId = ProfileId.DEFAULT) = RuleEvaluationContext(
        identity = identity,
        target = TargetFacts(PACKAGE, disposition = TargetDisposition.ORDINARY_APP),
        time = EvaluationTime(wallNowMillis = 1_800_000_000_000L, elapsedNowMillis = 1_000L),
        effectivePolicy = EffectiveAppPolicy(profileId, AppPolicy(PACKAGE, managed = true,
            daily = DailyRuleConfig.Explicit(3_600L))),
        daily = daily(),
        session = session(),
        focus = FocusRuntimeSnapshot.Off,
        capabilities = CapabilitySnapshot(true, true, true, AccessibilityRuntimeState.CONNECTED),
        event = EvaluationEvent.TICK,
    )
}
