# ACCESSIBILITY_FALLBACK_IMPLEMENTATION_REPORT

Date: 2026-10-14. Branch: `fix/accessibility-fallback-interventions`. No automatic commit/push. Scope: Session/Daily soft fallback and runtime permission health.

## Root causes and feedback loop

Current master tied Daily evaluation and display to enhanced Accessibility capability; its display path required HOME. Permission observation dismissed Daily on Accessibility loss, and Accessibility onUnbind requested dismissal of lower-priority overlays. Session countdown completion always auto-closed the reminder, regardless of presentation. Coordinator disconnect also cleared the whole episode.

Before implementation, the focused JVM test `InterventionCoordinatorTest.accessibilityDisconnectPreservesEpisodeAndDoesNotRestartFirstThreshold` failed with an assertion that disconnect must not erase reminder progression (one test, one failure). It uses the actual coordinator/friction classes. The final suite reruns that test together with all existing regressions. OriginOS disabling the system setting itself is a separate, unproven platform behavior, not explained by this host failure.

## Changed files

Production files, under `app/src/main/`:

- AndroidManifest.xml: monitoring stopWithTask=false.
- MainActivity.kt: existing Focus/Deep availability inputs use connected capability rather than the enabled switch alone.
- data/model/LimitInterventionRequest.kt: explicit presentation and Daily action deadline.
- data/repository/UsageTimeRepository.kt: physical-session end baseline and paused same-package resume linkage, plus current runtime snapshot access. No replacement timer or UsageStats query algorithm.
- domain/intervention/InterventionCoordinator.kt: observable connection state, overlay timing pause and episode-preserving disconnect.
- domain/intervention/InterventionPresentation.kt (new): pure capability, deadline, dismiss and completion-action decisions shared by UI/service.
- monitor/SessionFrictionManager.kt: optional gate arming, disconnect admission cleanup and pending-choice preservation.
- permissions/AccessibilityRuntimeState.kt (new), PermissionHealthState.kt, PermissionHealthManager.kt: enabled/disconnected/connected health.
- service/UsageMonitorService.kt: Daily availability independent of HOME; in-place display, pause, user actions and health refresh.
- service/TimeManagerAccessibilityService.kt: disconnect no longer dismisses ordinary monitoring overlays; existing connect/unbind/destroy health callbacks retained.
- ui/intervention/SessionFrictionContent.kt: same-view choice after the deadline, reserved buttons/number area.
- ui/permissions/PermissionSettingsScreen.kt: actual runtime status and folded background advice.

Tests: InterventionCoordinatorTest, SessionFrictionManagerTest, PermissionHealthStateTest and new InterventionPresentationPolicyTest. Documentation: this report, PROJECT_STATE, OPEN_ISSUES and the implementation plan.

Overlay controller/Compose Host, timer algorithm, foreground detector, quota evaluator/extension persistence, UsageStats reader, Room/Todo/Statistics, notification IDs and 04:00 boundaries were not changed.

## Session fallback

Usage + Overlay, with Accessibility disconnected: show one full-screen WindowManager/ComposeView and count down to the fixed deadline. Buttons exist throughout, initially disabled. At zero the same content enables Continue / Exit; the countdown does not invoke the auto-close callback. UI does not query Accessibility directly.

Continue closes only this overlay, without launching the target, creating a bypass/admission token, changing quota or resetting the episode. Exit explicitly requests HOME through the existing intent, then closes. Physical timing resumes on a later detected foreground tick, not inside the click handler. The shown stage advances +8/+8/+5, with no re-entry gate. Reconnecting cannot retroactively arm that stage; a future shown HOME-mode stage can arm exactly one gate.

Full mode keeps HOME, automatically closing the reminder cooldown, one re-entry gate and deadline-validated launch/admission. Existing re-entry tests remain in the suite.

## Daily fallback and priority

Availability requires Usage + Overlay and ordinary coordinator arbitration; Accessibility only selects HOME-first vs in-place presentation. Daily crossing and an over-limit new physical entry reuse LimitInterventionManager/onManagedAppEntered. Fallback exits via the user-requested HOME intent; +15 invokes the existing extension repository, preserves the base and episode, refreshes the effective snapshot and dismisses without relaunch. The service also validates the extension deadline.

Daily precedes Session when both are due; a Session stage is not committed when Daily wins. Focus/Deep enforcement is not simulated with polling. Their setup remains gated by actual runtime availability. Ordinary Daily pages are not dismissed merely because Accessibility disconnects or the session episode reaches its idle expiry.

## Timing pause and health

After a successful in-place show, the coordinator pauses timing, the service records/flushes AppTimer's final state and ends the physical Session. No tick updates the covered target while that overlay remains owned. Release allows a new physical session. UsageTimeRepository clears the previous physical delta baseline and resumes its flushed estimate for the covered package. Daily snapshot refresh prefers known realtime usage, avoiding a system foreground reseed of the paused wait during the decision.

This verifies realtime admission/pause and episode state, not a new reconstruction of platform history. Existing OS-derived history/backfill remains unchanged; whether vendor UsageEvents count a covered app as foreground, and how later historical reconciliation represents that interval, remain device-validation concerns.

Accessibility loss clears only enhanced gates/admission and preserves ordinary overlays/progression. Re-entry fails open. Usage/Overlay loss dismisses ordinary overlays; Usage loss ends tracking, while Overlay loss does not stop the monitoring service or notifications. Health refresh uses connection StateFlow, existing lifecycle callbacks and a five-second service cadence. No Service instance is exposed to UI.

## Tests and builds

Final post-review host run: **BUILD SUCCESSFUL in 45s**, 96 actionable tasks (18 executed / 78 up-to-date). Parsed this run's XML: **28 suites / 141 tests / 0 failures / 0 errors / 0 skipped**. This adds 16 tests to the 125-test baseline; existing regressions remain, with the former Daily-without-Accessibility expectation corrected to the requested Daily-first behavior and health fixtures now explicitly connected.

Debug build: **PASS**. Release build: **PASS**, unsigned APK. Todo migration check: **PASS**. `git diff --check`: **PASS**. Both APKs remain ignored local outputs; neither was installed. Commands:

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
tools/Verify-TodoMigration.ps1
git diff --check
```

New coverage includes deadline choice/no auto-dismiss, actual completion-action policy, fallback stage retention, no retroactive gate, future one-shot gate, Daily crossing/re-entry without Accessibility, +15 effective/base model regression, Daily priority, permission-loss decisions, enabled-vs-connected facts and timing pause ownership. Service/WindowManager and DataStore action integration still require device validation; pure JVM decisions are not claimed as screenshot or persistence-integration evidence.

## Device tests actually run / OriginOS investigation

Only `adb devices -l` was run; it listed **no connected device**. No APK was installed, no phone was manipulated and no connected instrumentation was run.

The requested HOME / recents swipe / system cleanup / vendor-manager cleanup / 5–10-minute lock / TimeManager-only force-stop / manual Accessibility-off matrix is **NOT RUN**. Enabled switch, runtime connection, foreground service/notification survival and automatic reconnect therefore have no new device evidence. Code contains no disableSelf, secure-settings mutation, vendor private intent or automatic reopening of Accessibility settings.

## Still unverified / known risks

- Same-window countdown appearance, real HOME intent return, in-place +15, covered-app realtime pause/resume and over-limit polling re-entry on vivo X200s / Android 16.
- Runtime revocation/reconnect during visible overlays and full-mode HOME/Re-entry/Focus/Deep regression on device.
- OriginOS may disable or kill services despite stopWithTask=false; the app cannot silently restore the system switch.
- Polling entry detection is naturally delayed. Process death still loses process-only reminder episodes; this task does not add persistence or promise complete historical correction.

Await owner device acceptance before deciding to commit or push. No data clearing/uninstall/cleanup is authorized by this report.

Git status: branch `fix/accessibility-fallback-interventions`; 17 tracked files modified and five new files (two production helpers, one test suite, plan and report), all unstaged/uncommitted. No unrelated starting changes were present; HEAD remains the existing master baseline. No commit or push was performed.
