# DEVELOPMENT_WORKFLOW_STANDARDIZATION_REPORT

Date: 2026-10-16. Repository: FPhoenix28/TimeManager. Scope: documentation standardization and read-only host verification of the existing build roles. **Not a release process or a new device test.**

## Files changed

| File | This task's change |
| --- | --- |
| `AGENTS.md` | Added Default local development and device-testing workflow; explicit acceptance, commit, push and master gates; current-task stable-package authorization |
| `DEVICE_SAFETY.md` | Added Debug/stable boundary and dual-instance isolation; APK metadata prerequisite; standard testOnly failure handling; end-of-test/report rules |
| `CONTRIBUTING.md` | Integrated a concise lifecycle and build-role summary, replacing the mandatory fork/PR sequence and publication-centric Release description |
| `docs/DEVELOPMENT_WORKFLOW.md` (new) | Full daily workflow, three state layers and independent stable-phone update decision |
| `docs/testing/DEVELOPMENT_TEST_REPORT_TEMPLATE.md` (new) | Reusable factual report covering commands, counts, identity, real cases, recovery and user/Git gates |
| `docs/testing/README.md` | Current Dev target/report links; legacy production-ID reports clearly identified as historical evidence |
| `docs/PROJECT_STATE.md` | Continuous-development model, separate source/phone cadence and current missing monitoring switch |
| This report (new) | Changes, conflict resolution, validation, gaps and Git snapshots |

No application source, Gradle, manifest, resources, business rules or user README changes were added by this task. Those files already had earlier uncommitted changes: their `git diff -- app README.md` output was compared with the start-of-task snapshot and remained identical. No reset or branch switch was performed.

## Rules added

- Start with status / branch / last commit, preserve unrelated work and prefer focused feat/fix branches for substantial changes.
- Code → JVM/static checks → Debug build → verify actual APK identity → install Dev → device validation → **user experience acceptance** → explicitly requested commit → optional, explicitly requested push/master integration.
- Debug installation uses `-r -t`, with the existing phone confirmation/minute wait; failure or signature conflict stops without uninstall or data-clear fallback.
- Dev must own the measured foreground/overlay/notification result. A service or notification existing does not by itself prove timing works.
- Prefer a monitoring switch for intervention isolation. Without it, the user decides how to pause stable; do not default to force-stop. Current-task authorization cannot be carried into a later task as permanent permission.
- Test loops can repeat without per-iteration commits or stable-app updates. Restore temporary timing/logging/settings; Debug build alone never authorizes short user quotas.
- Test success, user acceptance, commit, push/master integration and stable APK update are distinct gates. Acceptance permits a recommendation, not automatic Git mutations.
- Master is the intended accepted source baseline. The user's installed stable APK can lag behind; only a separately initiated update request permits synchronizing it.
- Keep APKs and private device material out of source history; retain historical evidence and the incident record.

## Old conflicting rules removed / updated

| Previous instruction or ambiguity | Current result |
| --- | --- |
| CONTRIBUTING required fork → PR for every change | Focused branches recommended; small verified owner-authorized changes need not use a PR |
| Release row described mainly published builds | Stable identity is the user's daily-use role; assembling Release is only a compatibility build check here |
| Device rules allowed stable writes with generic explicit intent but did not delimit its lifetime | Current-task explicit approval, no inherited permission; stable update is separately initiated |
| No concrete dual-instance isolation rule | Switch-first; user decision when absent; verify actual result owner; no automatic stable force-stop |
| Preflight relied on build configuration without explicitly requiring actual APK label/ID inspection | SDK inspection of Dev package and name required; inability to verify means stop |
| Only destructive actions explicitly appeared in the pause/approval checklist | Stable operations also require current-task separate intent; destructive actions retain exact COMMAND/TARGET/DATA IMPACT approval |
| Rollback language could be read as part of every iteration | Dev rollback only when required by the task; not a routine stable update or mandatory backup per loop |
| Testing index named the legacy stable package without a current-target distinction | Historical records retain their facts; new runs use Dev and the standard report template |
| User acceptance and Git authorizations were not fully stated | Explicit independent gates in AGENTS, workflow and contributor summary |

Historical reports and `docs/DEVICE_INCIDENT_REPORT-2026-09-30.md` were not rewritten. Their old actions are evidence, not current operating authorization. The incident conclusion remains ROOT CAUSE NOT PROVEN; no safety hard boundary was removed.

## Debug / stable configuration status

Inspected actual Gradle/source-set resources and existing local APKs using installed SDK Build Tools 36.0.0 `aapt2 dump badging` and `dump xmltree ... --file AndroidManifest.xml`:

| Check | Debug | Stable/Release identity |
| --- | --- | --- |
| Final package | `com.example.timemanager.debug` | `com.example.timemanager` |
| Display name | TimeManager Dev | TimeManager |
| Version | `1.0-dev` | `1.0` |
| namespace / Kotlin packages | `com.example.timemanager`, unchanged | same, unchanged |
| testOnly in inspected CLI artifact | absent (false by default) | absent (false by default) |
| debuggable in inspected artifact | true | absent (false by default) |
| Default timing | normal; overrides disabled | normal; overrides disabled |
| Fast fixture | Debug source set, explicit opt-in | no fast fixture in Release source set |

Configuration meets the requested separation; **no build configuration change was needed**. Debug testOnly remains allowed for Studio Run and uses normal `-t` installation. The existing Release artifact is unsigned; no keystore/password/signing work was performed. The installed phone APK's current quality or signature is not re-certified by this host-only task.

## Device safety changes

Safe Dev testing remains the default, not read-only development. Stable remains a real user-data environment. Independent authorizations, exact device/package targeting, no automatic uninstall/pm clear/user-data deletion/bulk or other-app mutation/destructive fallback are retained. Default connected instrumentation remains prohibited on the long-term vivo; a safe-runner check plus explicit approval is required before considering it.

**No ADB/device commands, stable-phone operations or data changes were performed in this task.** No commit, push, staging or master integration occurred.

## Verification

- Documentation links: PASS across the eight changed/new documents (relative-link targets exist).
- `git diff --check`: PASS.
- Required workflow/gate/boundary sections and report fields: checked against the supplied request.
- App source/configuration and user README scope guard: PASS; no new changes compared with this task's entry snapshot.
- Existing local Debug/Release metadata: PASS as above; not a newly built or installed APK claim.
- Unit tests / Debug build / Release build / device tests: **NOT RUN in this task**, because changes are documentation-only. Earlier counts/build results remain attributed to their own reports, not claimed as this run's results.

The writing-for-agents skill guided a compact AGENTS summary, one detailed workflow, a separate device-boundary authority and one reusable report template. It did not expand the task into application development.

## Remaining gaps

1. A unified monitoring master switch is not implemented. Until a separate feature task provides it, the user decides stable-monitor isolation; documentation does not fabricate an OFF switch.
2. Dev's independent foreground timing/intervention acceptance is still failed/blocked as recorded in `docs/testing/DEBUG_RELEASE_DEVICE_RUNTIME_REPORT.md`. This task neither fixes it nor marks it accepted/master-ready.
3. User acceptance of this documentation remains pending. There is no commit/push/integration authorization from completing this task.
4. Release signing or a stable-phone APK update is separate work only if the user later requests it; neither is necessary to establish this daily workflow.

## Git diff --stat / git status

Start/end branch: `fix/accessibility-fallback-interventions`; last committed base observed: `b905d33`. All pre-existing fallback, package-split and runtime-report work was preserved. The following snapshot includes those earlier changes; it is **not** the change size of this documentation task alone. Untracked new documents do not appear in `git diff --stat` until staged; nothing was staged.

```text
 AGENTS.md                                          |  19 +++-
 CONTRIBUTING.md                                    |  35 ++++--
 DEVICE_SAFETY.md                                   |  51 +++++++--
 app/build.gradle.kts                               |   4 +
 .../example/timemanager/ExampleInstrumentedTest.kt |   4 +-
 app/src/main/AndroidManifest.xml                   |   1 +
 .../java/com/example/timemanager/MainActivity.kt   |  10 +-
 .../data/model/LimitInterventionRequest.kt         |   6 ++
 .../data/repository/UsageTimeRepository.kt         |  26 ++++-
 .../domain/intervention/InterventionCoordinator.kt |  29 +++--
 .../timemanager/monitor/SessionFrictionManager.kt  |  18 +++-
 .../notifications/DeepFocusNotificationHelper.kt   |   5 +-
 .../notifications/MonitorNotificationHelper.kt     |   7 +-
 .../notifications/NotificationHelper.kt            |   2 +-
 .../notifications/TodoNotificationHelper.kt        |   5 +-
 .../permissions/PermissionHealthManager.kt         |  13 +++
 .../permissions/PermissionHealthState.kt           |   6 +-
 .../service/TimeManagerAccessibilityService.kt     |   2 +-
 .../timemanager/service/UsageMonitorService.kt     | 118 ++++++++++++++++-----
 .../ui/intervention/SessionFrictionContent.kt      |  21 ++--
 .../ui/permissions/PermissionSettingsScreen.kt     |  33 +++++-
 .../domain/focus/FocusEnforcementPolicyTest.kt     |  16 +++
 .../intervention/InterventionCoordinatorTest.kt    |  19 +++-
 .../monitor/SessionFrictionManagerTest.kt          |  68 ++++++++++++
 .../permissions/PermissionHealthStateTest.kt       |   9 +-
 .../timemanager/config/DebugTimeConfigTest.kt      |   6 ++
 docs/OPEN_ISSUES.md                                |   3 +
 docs/PROJECT_STATE.md                              |  24 ++++-
 docs/testing/README.md                             |  11 +-
 29 files changed, 474 insertions(+), 97 deletions(-)
```

```text
 M AGENTS.md
 M CONTRIBUTING.md
 M DEVICE_SAFETY.md
 M app/build.gradle.kts
 M app/src/androidTest/java/com/example/timemanager/ExampleInstrumentedTest.kt
 M app/src/main/AndroidManifest.xml
 M app/src/main/java/com/example/timemanager/MainActivity.kt
 M app/src/main/java/com/example/timemanager/data/model/LimitInterventionRequest.kt
 M app/src/main/java/com/example/timemanager/data/repository/UsageTimeRepository.kt
 M app/src/main/java/com/example/timemanager/domain/intervention/InterventionCoordinator.kt
 M app/src/main/java/com/example/timemanager/monitor/SessionFrictionManager.kt
 M app/src/main/java/com/example/timemanager/notifications/DeepFocusNotificationHelper.kt
 M app/src/main/java/com/example/timemanager/notifications/MonitorNotificationHelper.kt
 M app/src/main/java/com/example/timemanager/notifications/NotificationHelper.kt
 M app/src/main/java/com/example/timemanager/notifications/TodoNotificationHelper.kt
 M app/src/main/java/com/example/timemanager/permissions/PermissionHealthManager.kt
 M app/src/main/java/com/example/timemanager/permissions/PermissionHealthState.kt
 M app/src/main/java/com/example/timemanager/service/TimeManagerAccessibilityService.kt
 M app/src/main/java/com/example/timemanager/service/UsageMonitorService.kt
 M app/src/main/java/com/example/timemanager/ui/intervention/SessionFrictionContent.kt
 M app/src/main/java/com/example/timemanager/ui/permissions/PermissionSettingsScreen.kt
 M app/src/test/java/com/example/timemanager/domain/focus/FocusEnforcementPolicyTest.kt
 M app/src/test/java/com/example/timemanager/domain/intervention/InterventionCoordinatorTest.kt
 M app/src/test/java/com/example/timemanager/monitor/SessionFrictionManagerTest.kt
 M app/src/test/java/com/example/timemanager/permissions/PermissionHealthStateTest.kt
 M app/src/testDebug/java/com/example/timemanager/config/DebugTimeConfigTest.kt
 M docs/OPEN_ISSUES.md
 M docs/PROJECT_STATE.md
 M docs/testing/README.md
?? app/src/debug/res/
?? app/src/main/java/com/example/timemanager/domain/intervention/InterventionPresentation.kt
?? app/src/main/java/com/example/timemanager/permissions/AccessibilityRuntimeState.kt
?? app/src/test/java/com/example/timemanager/domain/intervention/InterventionPresentationPolicyTest.kt
?? docs/DEVELOPMENT_WORKFLOW.md
?? docs/DEVELOPMENT_WORKFLOW_STANDARDIZATION_REPORT.md
?? docs/plans/2026-10-14-accessibility-fallback.md
?? docs/plans/2026-10-15-debug-release-split.md
?? docs/plans/2026-10-15-minimum-release-roadmap.md
?? docs/testing/ACCESSIBILITY_FALLBACK_REPORT.md
?? docs/testing/DEBUG_RELEASE_DEVICE_RUNTIME_REPORT.md
?? docs/testing/DEBUG_RELEASE_SPLIT_REPORT.md
?? docs/testing/DEVELOPMENT_TEST_REPORT_TEMPLATE.md
```
