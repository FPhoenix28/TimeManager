# Phase4.3A Accessibility Shadow Implementation Plan

> Inline execution using writing-plans; execution subskills are unavailable. No delegation, commit or device work is authorized.

**Goal:** Compare the existing Accessibility Focus/Deep/re-entry business intents with RulesEvaluator without changing execution authority.

**Architecture:** A pure immutable snapshot adapter and compatibility projection feed a Debug-only observer. Coordinator exposes publication identity metadata for the existing Daily snapshot; original fresh CheckEntry still executes exactly once before re-entry comparison. Android target classification reuses the existing essential-interface predicates, not copied package/class heuristics.

**Tech Stack:** Kotlin, existing RulesEvaluator, DataStore read-only config Flows, JUnit.

**Spec:** Owner Phase4.3 request, first iteration restricted to 4.3A.

## Constraints

- Legacy Focus/Deep/re-entry remain authoritative; no new execution owner or priority table.
- Preserve HOME launcher confirmation, Friction transitions, gate/token/stage semantics, repositories, UI, timing controls and storage schema.
- Preserve all 478 Debug / 452 Release tests and their expected values.
- No phone, signing, authoritative 4.3B switch or Git mutation.
- Service edits are narrow observation hooks, not 100+ lines of core-flow replacement.

## Tasks

- [x] Append owner-provided Phase4.2 acceptance with its exact scope; retain automated history.
- [x] Add failing Focus/Deep/re-entry adapter/projection tests using immutable snapshots. Assert ordinary Focus without application overlay remains eligible, Deep exemption retains Daily, valid bypass is not consumed.
- [x] Add `AccessibilityRulesSnapshotAdapter.adapt(snapshot)` and `AccessibilityRulesCompatibilityProjection.project(evaluation, overlayOccupied)`; no actions or state writers.
- [x] Add six-state shadow result/comparison with explicit incomplete/stale/error/known execution differences and deduplicated Debug diagnostics. Test revision/generation/errors and repeated evaluation preserving owners.
- [x] Expose existing essential-call/recovery predicates internally for the Android classifier; expose Coordinator read-only managed/current facts and Daily publication identity. Do not change their business decisions.
- [x] Add Debug observer reading existing configuration Flows once, observing Focus/Deep windows and wrapping the original fresh CheckEntry result. Missing/old Daily publication is insufficient, never guessed fresh.
- [x] Run focused tests then all Debug/Release tests, assembleDebug, compileReleaseKotlin and diff checks; inspect Service diff and prohibited-file boundaries.
- [x] Document parity matrix, expected execution differences, mutation audit, host evidence and pending 4.3B review; update roadmap without checking off full 4.3.

## Core assertions

```kotlin
assertEquals(RuleKind.DAILY, engine.evaluate(deepExemptWithDaily).selected?.kind)
assertEquals(beforeEpisode, friction.snapshot(pkg)) // evaluation never commits
assertEquals(AccessibilityIntent.CHECK_SESSION_REENTRY, projected.intent)
```

Completion means host shadow implementation ready for review, not a claim of runtime shadow parity on a phone or authorization for the switch.
