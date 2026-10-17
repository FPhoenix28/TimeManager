# Session reminder layout and countdown implementation plan

> Execute inline in the current worktree. Preserve existing edits; no commit or push. Device work is installation only, not runtime testing.

**Goal:** Center the first Session reminder and transition directly from its countdown to a persistent choice in the same overlay.

**Architecture:** Keep the existing gate/deadline, Compose host and overlay instance. Derive action availability from the deadline; do not dismiss on countdown completion. Reuse admission for an explicit Continue after either HOME-first gate phase. Leave re-entry consumption and reminder progression unchanged.

**Tech Stack:** Kotlin, Compose Material 3, existing JVM tests and Android SDK installation tools.

**Spec:** Owner-approved 2026-10-17 chat proposal: first-reminder alignment and direct final-state transition; second Re-entry Gate unchanged; install Dev only and leave runtime acceptance to the owner.

## Constraints

- No timing overrides, quota/usage/history/day-boundary changes, navigation changes or Compose-host rewrite.
- No device launch, UI automation or functional verification; no stable-package writes.
- Install the verified TimeManager Dev APK with `install -r -t`; notify the owner, wait at least one minute, require installation success, never uninstall/clear as fallback.

## Steps

- [x] Add failing JVM regressions: HOME-first actions enabled at deadline, Continue dispatches admission, and first-reminder admission preserves progression plus the later re-entry gate.
  - Files: `InterventionPresentationPolicyTest.kt`, `SessionFrictionManagerTest.kt` in their existing test directories.
  - Run `:app:testDebugUnitTest --tests '*InterventionPresentationPolicyTest' --tests '*SessionFrictionManagerTest'`; expect failure before the fix.
- [x] Implement the narrow fix.
  - `ui/intervention/ResponsiveInterventionScaffold.kt`: full-width portrait and information columns.
  - `ui/intervention/SessionFrictionContent.kt`: centered full-width text, stable countdown region, choice buttons in the same composition, no automatic completion effect or intermediate placeholder.
  - `domain/intervention/InterventionPresentation.kt`: both phases offer choices after the deadline; HOME-first Continue uses existing admission.
  - `monitor/SessionFrictionManager.kt`: allow deadline-validated Continue for the shown first reminder, without altering progression or re-entry arming.
  - `service/UsageMonitorService.kt`, `ui/intervention/InterventionOverlayController.kt`, `ui/intervention/LimitReachedScreen.kt`: remove unused countdown-dismiss callback plumbing only.
- [x] Run `:app:testDebugUnitTest :app:assembleDebug` and `git diff --check`; inspect APK package/name and normal timing. Record results in `docs/testing/2026-10-17-session-reminder-choice.md`.
- [x] Verify the connected vivo device and installed Dev package; notify the owner and cover-install Dev only. Wait at least 60 seconds from installation start and confirm success. Do not launch or test. Report owner acceptance as pending.
