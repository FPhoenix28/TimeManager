package com.example.timemanager.accessibility.rules

import android.content.Context
import android.util.Log
import com.example.timemanager.domain.rules.RuleKind
import java.io.File
import java.util.concurrent.Executors

/** Temporary reverse-shadow evidence through 4.3B acceptance. No business owner or event content. */
class AccessibilityRulesObserver(context: Context) {
    private val file = File(context.applicationContext.filesDir, "phase43b-runtime-shadow.log")
    private val counts = AccessibilityShadowStatus.entries.associateWith { 0L }.toMutableMap()
    private var closed = false
    init { diagnostic("observer opened authority=ENGINE") }

    fun observeAuthority(input: AccessibilityRulesSnapshot, decision: AccessibilityAuthorityDecision, legacy: AccessibilityIntent) {
        if (closed) return
        val result = when (decision) {
            AccessibilityAuthorityDecision.Stale -> AccessibilityShadowResult(AccessibilityShadowStatus.STALE)
            is AccessibilityAuthorityDecision.LegacyFallback -> AccessibilityShadowResult(
                AccessibilityShadowStatus.INSUFFICIENT_FACTS, detail = decision.reason)
            is AccessibilityAuthorityDecision.Engine -> AccessibilityReverseShadow.compare(decision, legacy)
        }
        counts[result.status] = counts.getValue(result.status) + 1L
        val plan = decision as? AccessibilityAuthorityDecision.Engine
        diagnostic("scope=${input.scope} package=${input.target.packageName} generation=${input.identity.foregroundGeneration.value} " +
            "revision=${input.identity.policyRevision.value} engine=${plan?.projection?.intent} legacy=$legacy " +
            "selected=${plan?.evaluation?.selected?.kind} deepExempt=${plan?.evaluation?.explicitExemptions?.any { it.rule == RuleKind.DEEP_FOCUS }} " +
            "freshDaily=${input.dailyFreshForEntry} status=${result.status} detail=${result.detail} counts=$counts")
    }
    fun fallback(reason: String) { diagnostic("ENGINE_FALLBACK_LEGACY reason=$reason") }
    fun close() { if (!closed) { closed = true; diagnostic("observer closed counts=$counts") } }
    private fun diagnostic(message: String) {
        Log.d("AccessibilityRulesRuntime", message)
        val line = "${System.currentTimeMillis()} $message\n"
        writer.execute { runCatching { if (file.length() + line.length < 1_048_576L) file.appendText(line) } }
    }
    private companion object {
        val writer = Executors.newSingleThreadExecutor { task -> Thread(task, "AccessibilityShadowDiagnostic").apply { isDaemon = true } }
    }
}
