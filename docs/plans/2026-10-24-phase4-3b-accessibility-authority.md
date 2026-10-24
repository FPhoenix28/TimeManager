# Phase4.3B Accessibility Authority Implementation Plan

**Goal:** Make RulesEvaluator authoritative before Accessibility execution; host implementation only.

**Architecture:** Capture immutable window/entry facts, evaluate, project, validate identity, then dispatch exactly one execution path. Reuse Monitor's Daily accounting refresh without executing it during preparation. Keep mutable runtime owners and launcher confirmation unchanged.

**Spec:** Owner's Phase4.3B Accessibility Rules Engine Authoritative Switch request (2026-10-24).

**Constraints:** Preserve dirty worktree; no device, install, commit, push, merge, Phase5 or profile migration. Plans stay in docs/plans. No hashes. Existing tests remain unchanged.

## Ordered tasks

- [x] Add pure authority/projection and tests: Engine / pre-effect fallback / stale; generic window cannot execute lower rules; deliberate disagreement cannot select legacy.
- [x] Split Coordinator/Monitor entry preparation from execution. Carry exact entry, fresh publication, base quota and policy revision. Execute without a second Daily read; validate before mutation and deduplicate entry execution.
- [x] Wire shared readonly configuration facts and Accessibility authority in both variants. Keep existing Focus/Deep presentation, HOME confirmation and Session execution owners. Deep exemption hands off to fresh lower-entry pipeline.
- [x] Replace post-execution observer hooks with Debug reverse shadow; retain capped background private diagnostic sink temporarily.
- [x] Run focused authority tests, then complete Debug/Release JVM tests, Debug assembly and Release Kotlin compile; check diff. Append separate host report without altering 4.3A evidence.

## Verification checkpoint

Required tests cover Focus/Deep capability/exemption/time boundaries, fresh Daily/extension handoff, generation/day/revision/publication invalidation, admission/bypass/pending gate, same-generation dedup, real launcher confirmation, zero evaluator mutations, pre-effect fallback and mismatch remaining Engine-authoritative. Host success is not device acceptance.

## Final round closure — owner authorization, 2026-10-24

The earlier host-only constraints above describe that implementation slice. The owner now authorizes documentation clarification, final host verification and **one local commit**, then STOP. No device operations, production behavior change, push, merge, tag or next phase.

1. [x] Update the existing authoritative Deep Focus section in `docs/PROJECT_STATE.md`: maximal blocking, finite necessary exceptions, no Settings/recovery accessibility guarantee, capability failure distinct from business Allow. Do not create a duplicate product specification.
2. [x] Correct only interpretation/verdict in the authoritative device report. Preserve all runtime evidence and B/D/K NOT_PROVEN; record the owner's bounded-evidence acceptance independently.
3. [x] Review all accumulated tracked/untracked source, tests, Debug support and phase documents. Exclude generated/private artifacts; preserve unrelated work if found.
4. [x] Run `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:testReleaseUnitTest --console=plain` and `git diff --check`. Require at least Debug 574/62 suites and Release 548/59 suites, no failures/errors/unexpected skips. Both unit-test tasks were additionally rerun, with the baseline unchanged and zero failures/errors/skips.
5. [ ] Final action after this recorded gate: stage only reviewed round files, commit `refactor: unify intervention rule decisions`, check status, then STOP. Commit ID/status belong to the final handoff; no follow-up documentation commit is required. No production edit for the product clarification.

Acceptance: one authoritative product-rule section, unchanged observations, explicit owner acceptance rather than fabricated device PASS, passing host gate, one scoped local commit. Execute inline in this session; no new development workstream is authorized.
