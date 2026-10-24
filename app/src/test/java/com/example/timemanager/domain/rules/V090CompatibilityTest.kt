package com.example.timemanager.domain.rules

import com.example.timemanager.config.TimeConfig
import com.example.timemanager.domain.profile.SessionProgression
import org.junit.Assert.*
import org.junit.Test

class V090CompatibilityTest {
    @Test fun inheritedFirstThresholdKeepsTenEighteenTwentySixThirtyOneThirtySixMinutes() {
        val thresholds = (1..5).map { SessionProgression.thresholdSeconds(600L, it) }
        assertEquals(listOf(600L, 1_080L, 1_560L, 1_860L, 2_160L), thresholds)
        assertEquals(2_460L, SessionProgression.thresholdSeconds(600L, 6))
    }

    @Test fun explicitTwentyMinuteFirstThresholdUsesTheSameIntervals() {
        assertEquals(listOf(1_200L, 1_680L, 2_160L, 2_460L),
            (1..4).map { SessionProgression.thresholdSeconds(1_200L, it) })
    }

    @Test fun progressionCalculationDoesNotAdvanceTheProvidedEpisodeSnapshot() {
        val episode = V090CompatibilityFixtures.session().episode
        val before = episode.copy()
        SessionProgression.thresholdSeconds(episode.firstReminderSeconds, episode.reminderCount + 2)
        assertEquals(before, episode)
    }

    @Test fun twoFifteenMinuteExtensionsAreRuntimeFactsWithoutChangingTheBaseConfiguration() {
        val context = V090CompatibilityFixtures.context()
        val increment = V090CompatibilityFixtures.DAILY_EXTENSION_SECONDS
        assertEquals(TimeConfig.LIMIT_EXTENSION_SECONDS, increment)
        assertEquals(900L, increment)
        val after = context.daily!!.copy(todayExtensionSeconds = increment + increment)
        assertEquals(1_800L, after.todayExtensionSeconds)
        assertEquals(context.daily.identity, after.identity)
        assertEquals(0L, context.daily.todayExtensionSeconds)
    }

    @Test fun allFourPriorityCandidatesCanBeRepresentedWithoutResolvingTheirOrder() {
        val candidates = listOf(
            RuleCandidate.DeepFocus(61_000L), RuleCandidate.Focus(61_000L),
            RuleCandidate.Daily(600L, V090CompatibilityFixtures.daily(), DailyCandidateReason.LIMIT_CROSSED),
            RuleCandidate.Session(V090CompatibilityFixtures.session(), SessionCandidateReason.EPISODE_THRESHOLD_REACHED),
        )
        assertEquals(setOf(RuleKind.DEEP_FOCUS, RuleKind.FOCUS, RuleKind.DAILY, RuleKind.SESSION),
            candidates.map(RuleCandidate::kind).toSet())
    }
}
