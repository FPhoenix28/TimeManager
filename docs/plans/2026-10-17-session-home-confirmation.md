# Session HOME confirmation implementation plan

> Execute inline; preserve the dirty worktree. No commit/push and no device functional tests.

**Goal:** Restore HOME before the first Session reminder and make its desktop button request navigation instead of merely dismissing the overlay.

**Architecture:** Session HOME commands wait for the existing stable Accessibility foreground stream to observe the resolved launcher. A HOME API acknowledgement alone is not confirmation. Daily/Focus HOME callers keep their existing behavior; missing Session capability degrades to an explicitly labelled in-place reminder. Existing layout, countdown and re-entry progression remain intact.

**Tech Stack:** Kotlin coroutines/StateFlow, existing services and Compose, JVM tests, Debug APK installation.

**Spec:** Owner-approved 2026-10-17 HOME-chain correction in this chat; installation only, owner performs runtime acceptance.

## Constraints

- No quota, usage/timer, Room, 04:00, notification ID or gate-consumption algorithm changes.
- No temporary timing overrides or permission/configuration writes.
- Dev-only `install -r -t` after APK/device identity checks; notify owner and wait at least 60 seconds. No launch/UI testing, stable writes, uninstall/clear or automatic failed-install retry.

## Steps

- [x] Regress the current button mapping: update `InterventionPresentationPolicyTest.kt` to expect `EXIT_TO_HOME` for HOME-first Exit, then run the focused test and observe failure.
- [x] Add Session-specific confirmed navigation in `InterventionCoordinator.kt`: mark a Session command, track stable foreground through one StateFlow, resolve launcher on Accessibility connection and await that launcher under the existing command timeout. Add coordinator tests for accepted-but-unconfirmed HOME, transient/other windows, already-home, failure/disconnect and legacy command isolation.
- [x] Update only the Session branch of `TimeManagerAccessibilityService.kt` HOME handling to acknowledge dispatch without synthesizing a completed exit. Existing foreground callbacks provide confirmation; do not block the event callback or use a fixed delay.
- [x] In `UsageMonitorService.kt`, refresh permission health at Session boundaries, require confirmed Session HOME before HOME-first display, and explicitly request HOME for the Session desktop button. Keep Daily exit and Focus paths unchanged. Fall back to a user-initiated launcher intent if Session HOME is unavailable; do not label dispatch as confirmed navigation.
- [x] Keep centered/no-flash layout in `SessionFrictionContent.kt`; label first Exit as return-to-desktop and explain when automatic HOME is unavailable. Re-entry choice semantics stay unchanged.
- [x] Run `:app:testDebugUnitTest :app:assembleDebug`, `git diff --check`, APK metadata and normal-time checks. Record actual results in `docs/testing/2026-10-17-session-home-confirmation.md`.
- [x] Cover-install verified Dev only; wait at least one minute and confirm installer success. Do not launch/test. Leave user acceptance pending.
