package com.example.timemanager.domain.rules

import com.example.timemanager.permissions.AccessibilityRuntimeState

internal object CandidateCapabilityEvaluator {
    fun missing(candidate: RuleCandidate, capabilities: CapabilitySnapshot): List<CandidateCapabilityGap> = buildList {
        if (candidate !is RuleCandidate.Focus && !capabilities.overlayGranted) {
            add(CandidateCapabilityGap(candidate, RequiredCapability.OVERLAY))
        }
        if ((candidate is RuleCandidate.Daily || candidate is RuleCandidate.Session) && !capabilities.usageAccessGranted) {
            add(CandidateCapabilityGap(candidate, RequiredCapability.USAGE_ACCESS))
        }
        val needsConnection = candidate is RuleCandidate.Focus || candidate is RuleCandidate.DeepFocus ||
            (candidate is RuleCandidate.Session && candidate.reason == SessionCandidateReason.REENTRY_GATE_ELIGIBLE)
        if (needsConnection && capabilities.accessibility != AccessibilityRuntimeState.CONNECTED) {
            add(CandidateCapabilityGap(candidate, RequiredCapability.ACCESSIBILITY_CONNECTION))
        }
        // Notification permission is not a prerequisite for these overlay business candidates.
    }
}
