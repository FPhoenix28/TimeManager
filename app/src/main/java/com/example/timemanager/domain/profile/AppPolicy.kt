package com.example.timemanager.domain.profile

/** Managed eligibility is independent of a Focus session's selected blocked packages. */
data class AppPolicy(
    val packageName: String,
    val managed: Boolean,
    val daily: DailyRuleConfig = DailyRuleConfig.Unset,
    val session: SessionRuleConfig = SessionRuleConfig(),
) {
    init { require(packageName.isNotBlank()) }
}

/** Already-resolved configuration source; resolving active/default profiles is a later phase. */
data class EffectiveAppPolicy(
    val sourceProfileId: ProfileId,
    val appPolicy: AppPolicy,
)
