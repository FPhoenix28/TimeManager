# Accessibility fallback implementation plan

Execute inline; no commit or push. Scope follows the owner's Session/Daily fallback request, not a new enforcement system.

## Constraints

Keep +15-minute Daily extensions, +8/+8/+5 Session progression, five-minute genuine-rest reset, 04:00 boundaries, Focus/Deep enforcement and existing overlay host. No secure-settings writes or unsafe connected tests. Device checks, if available, follow DEVICE_SAFETY.md.

## Steps and verification

- [x] Reproduce lost episode on Accessibility disconnect using `InterventionCoordinatorTest`, before implementation (one test / one failure). Inspect service branches for Daily suppression/countdown auto-dismiss.
- [x] Add request presentation and pure deadline/capability policy. Test fallback countdown stays open, permissions, daily/session priority and HOME mode regression.
- [x] Preserve episode on disconnect, remove stale admission/gates, support `onReminderShown(..., armReentry=false)`. Test fallback continuation/exit, restoration without retroactive gate, next-stage one-shot gate and usage paused during overlay.
- [x] Use ordinary Daily evaluation with HOME as presentation capability only; wire in-place Session/Daily show, timing flush/pause, explicit Continue/Exit and permission-loss handling. Reuse daily entry trigger state; do not create a second state machine. Fix same-package delta-baseline resume linkage in UsageTimeRepository; core query/timer algorithms unchanged.
- [x] Expose enabled/disconnected/connected runtime health using process StateFlow; show real permission-page status and a folded OriginOS hint. Add stopWithTask=false. Keep Focus/Deep startup gated by actual runtime availability.
- [x] Run all JVM tests, Debug/Release builds, Todo migration and diff checks (28 suites / 141 tests, zero failures/errors/skips). Record no connected device and unexecuted device matrix in docs/testing/ACCESSIBILITY_FALLBACK_REPORT.md; update PROJECT_STATE and OPEN_ISSUES.

## Files

Existing: UsageMonitorService, TimeManagerAccessibilityService, InterventionCoordinator, SessionFrictionManager, LimitInterventionRequest, SessionFrictionContent, PermissionHealthState/Manager, PermissionSettingsScreen, MainActivity permission capability linkage, AndroidManifest and targeted tests.

New: small presentation/capability policy and AccessibilityRuntimeState, with JVM regression tests. LimitInterventionManager/evaluator, timer algorithm, UsageStats/Room/Todo code stay unchanged unless a demonstrated linkage defect requires a narrowly documented fix.

## Acceptance

Usage + Overlay alone supports Session choice and Daily re-entry without HOME. A displayed in-place overlay pauses actual realtime timing; Continue does not relaunch or mint admission. Accessibility loss does not erase progression or close fallback pages; only enhanced re-entry fails open. Enabled and connected remain separate facts. OriginOS service persistence is investigated rather than claimed repaired without device evidence.
