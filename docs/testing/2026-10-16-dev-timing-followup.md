# Dev timing and intervention follow-up — 2026-10-16

## Final verdict: bounded regression PASS / user acceptance pending

After reconnection, screenshot-only checks passed connected Accessibility HOME, persistent re-entry Gate, Continue, resumed timing and no repeated same-stage Gate. Normal-parameter Dev was installed successfully and checked after 74 seconds; temporary settings were restored and Dev test monitoring stopped. Stable APK/configuration was not modified. These are bounded regression results, not full product acceptance or a stable-update authorization.

### Reconnection completion

- The corrected permission page was visually verified as **已开启 · 已连接**, with the system's global Accessibility state enabled. Disabled configuration is separately reported when the own service is switched off.
- Earlier page-hierarchy capture was an interfering observation: configured/bound services and app-visible runtime readings disagreed around those captures, and the global Accessibility setting was observed disabled. After restarting/re-enabling Dev's own service and avoiding hierarchy capture/debugger suspension, this inconsistency did not recur during the successful check. The exact vendor/framework mechanism is not proven. Do not cite the earlier Gate disappearance as an independently confirmed product state-machine bug.
- Accessibility-sensitive checks now use host-only screenshots plus window/activity dumps, not `uiautomator dump`. The ignored local UI helper has a warning. No instrumentation/helper package was installed.
- Verified monitoring FGS existed before target entry. A too-fast Activity→Settings sequence had previously skipped normal monitor startup; that attempt is excluded from timing acceptance rather than counted as a timer failure.
- A fresh 20-second episode sent the target HOME. First re-entry displayed Dev's five-second Gate; screenshot showed the button disabled during countdown, then the persistent choice page remained after an additional seven seconds. Dev window ownership was confirmed.
- Continue dismissed Gate and requested target launch. OriginOS displayed an app-jump confirmation; selected **open once**, not always. After confirmation, the target was foreground, estimate advanced 545→560 seconds, and no Dev application-overlay window remained.
- HOME then another target entry also remained allowed without repeating the consumed same-stage Gate. No daily extension was granted in this connected check; base quota remained 180 minutes.
- Removed only this run's Canva management item through Dev UI. Final pre/post-update readback: tracked `[]`, limits `{}`, onboarding completed. Prior unrelated preferences/history were not cleared.
- Restored Dev Accessibility to off and battery policy to Smart; radio-state readback confirmed Smart selected. No display settings were changed.
- Normal Debug APK metadata verified again with SDK `aapt2`; installed with `-r -t`, manual confirmation, Success. First subsequent device operation was 74 seconds after initiation. Normal source has overrides=false and no Session override; the explicit unused fastRegression fixture is not the active configuration.
- Final Dev version 1.0-dev; production version 1.0 and its earlier last-update metadata unchanged. Dev was force-stopped after readback, and service dump reported no Dev services. No stable launch/stop/install was performed.
- Scoped debugger connections ended, suspended threads were resumed or their inspected process had already disconnected, and the temporary forwarding port was absent/removed. No debugger remains intentionally attached.

## Source and scope

- Branch: `fix/accessibility-fallback-interventions`; starting commit: `b905d33`.
- Preserved the existing dirty worktree; no reset, staging, commit, push or stable-package update.
- Permanent code changes: `utils/AccessibilityPermissionManager.kt`, new `permissions/EnabledAccessibilityServices.kt`, and its three JVM tests. Configured access now matches our exact package/service in `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`; actual connection remains independently tracked. The public setting is documented in the [Android API reference](https://developer.android.com/reference/android/provider/Settings.Secure#ENABLED_ACCESSIBILITY_SERVICES).
- Temporary Debug timing and service-dump diagnostics were removed from source. `BuildTimeConfig.value` is again normal, overrides=false; daily quota, +15-minute extensions, session progression, 04:00 and historical algorithms are unchanged.

## Host verification

- Final normal-source command: `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --console=plain` — SUCCESS, 58 seconds; 96 tasks, 20 executed / 76 up-to-date. Release is only a build check, not a signed distribution.
- Result XML: **29 suites / 147 tests, zero failures/errors/skips**; test task freshly executed. The three new matcher tests also passed separately.
- `git diff --check`: PASS.
- During initial temporary-time preparation, the full test run intentionally failed the existing `debugBuildDefaultsToProductionDurations` assertion (143/144 passed). That temporary-parameter failure is superseded by the final normal-source 147/147 result, not hidden or treated as a product fix.
- SDK `aapt2 dump badging` checked both temporary Debug installations: `com.example.timemanager.debug`, label TimeManager Dev, version 1.0-dev, debuggable; no testOnly flag in these artifacts.

## Device and isolation

- Verified development vivo X200s / Android 16; explicit serial on every ADB command, omitted here.
- Stable package was inspected read-only to choose a non-conflicting target. Canva (`cn.canva.editor`) was not in its managed list; no stable stop, install, permission or configuration changes were made.
- Dev initially had an empty managed list and quota map. Added Canva through Dev UI with 180 minutes, later edited only that new test item to 3 minutes. Saved values were read back from Dev's own DataStore.
- Only opened Canva's entry activity; did not edit its content, login or settings. Private screen content is not included in this report.
- Install commands: `adb -s <verified-serial> install -r -t <debug-apk>`. Both returned Success. First update waited 66 seconds before further device activity; second waited at least 60 seconds. Existing Dev onboarding/permissions/configuration survived the first update; second-update readback is blocked by disconnect.

## Actual cases

| Case | Evidence | Result |
| --- | --- | --- |
| Reproduce background timing stall | Dev recorded 5 seconds; service dump then explicitly reported package frozen | Reproduced vendor freeze |
| Allow Dev background battery, restart | Service became responsive; cumulative values advanced to 53, 80 and later higher values | PASS under temporary background allowance |
| Desktop boundary | Saved usage remained 80 seconds while desktop was foreground | PASS for this observation |
| Short Session reminder, Accessibility off | Dev-owned overlay displayed 20-second reminder; window owner was Dev UID, not stable | PASS |
| Wait inside in-place overlay | Runtime estimate held at 114 seconds across additional wait; dump timed=null, loopActive=true | PASS |
| Continue in place | Estimate advanced 114→129; no same-stage reminder remained visible | PASS |
| Change daily base while running | Dev saved 3 minutes without restart; at 180 seconds displayed exhausted overlay | PASS |
| Notification separation | Dev warning ID 13555, exhausted ID 23555 and ongoing FGS ID 1001 coexisted | PASS |
| Exit and over-limit re-entry | Exit returned to launcher; reopening Canva immediately displayed Dev daily overlay | PASS |
| +15-minute extension | Base remained 3; extension stored 900 seconds; notification showed today 03:17 / 18:00, remaining 14:43; estimate advanced | PASS |
| Connected service binding and HOME | System enabled/bound Dev Accessibility; reminder sent target to launcher | PASS |
| Initial connected re-entry observation | Re-entry sent target HOME without a persistent choice page while hierarchy/debugger checks disturbed runtime state | Initial failure; not isolated product evidence; superseded by screenshot-only retest |
| Configured permission consistency | System enabled/bound Dev, but old permission detection said disabled. New configured-service matcher keeps enabled status independent of connection | PASS on corrected page; exact vendor enumeration behavior not proven |
| Connected screenshot-only re-entry / Continue | Persistent countdown Gate, open-once vendor launch prompt, resumed usage and no same-stage repeat | PASS |
| Normal deployment and settings recovery | Normal APK Success, 74-second wait; empty test management/quota, onboarding retained, battery Smart, Accessibility off, Dev stopped | PASS |

The initial runtime detector failure from the earlier run was not consistently reproduced after removing vendor freeze and checking startup prerequisites. Do not claim every vendor/background condition is solved. The successful connected check required avoiding hierarchy-capture interference; no Gate or timing algorithm change was made.

## Recovery and remaining work

- Canva was removed through Dev's existing management UI before installing the permission fix: tracked list `[]`, quota map `{}`, its extension reset to 0. Existing unrelated Dev state/history was not cleared. Normal generated test history is retained by existing rules.
- Dev battery policy was initially Smart, temporarily Allow background, and finally restored to Smart.
- Dev Accessibility was initially off; temporarily enabled through its own system screen. Force-stop was observed to remove its enabled entry on OriginOS, then it was re-enabled for HOME testing. Finally switched off and post-update setting checked empty. Other services were not changed.
- Scoped Dev debugger was resumed, exited and its single forwarding port removed before the last update. No suspended debugger is left intentionally.
- No uninstall, pm clear, cleanup runner, user-file deletion, other-app configuration/data write or connected instrumentation.
- Immediate device follow-up and restoration completed; remaining gate is user acceptance, not automatic source/stable promotion.
- Not run: complete Focus/Deep matrix, real calls, 04:00 crossover, long-duration +8/+8/+5 sequence and historical waiting-time reconciliation. These are not implied by short checks.
- User acceptance PENDING. No Git or stable APK promotion authorized/performed.

## Git snapshot boundary

This task began with 29 tracked modified files plus pre-existing untracked implementation/docs. Its new permanent source edits are only the permission checker/matcher/tests listed above, plus this report and the state/issue pointers. Do not attribute the entire worktree to this task. See final handoff for any later recovery evidence.


### Final captured Git snapshot

Tracked diff (includes pre-existing work; untracked files are not counted):

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
 .../utils/AccessibilityPermissionManager.kt        |  21 ++--
 .../domain/focus/FocusEnforcementPolicyTest.kt     |  16 +++
 .../intervention/InterventionCoordinatorTest.kt    |  19 +++-
 .../monitor/SessionFrictionManagerTest.kt          |  68 ++++++++++++
 .../permissions/PermissionHealthStateTest.kt       |   9 +-
 .../timemanager/config/DebugTimeConfigTest.kt      |   6 ++
 docs/OPEN_ISSUES.md                                |   3 +
 docs/PROJECT_STATE.md                              |  26 ++++-
 docs/testing/README.md                             |  11 +-
 30 files changed, 486 insertions(+), 108 deletions(-)
```

Status:

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
 M app/src/main/java/com/example/timemanager/utils/AccessibilityPermissionManager.kt
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
?? app/src/main/java/com/example/timemanager/permissions/EnabledAccessibilityServices.kt
?? app/src/test/java/com/example/timemanager/domain/intervention/InterventionPresentationPolicyTest.kt
?? app/src/test/java/com/example/timemanager/permissions/EnabledAccessibilityServicesTest.kt
?? docs/DEVELOPMENT_WORKFLOW.md
?? docs/DEVELOPMENT_WORKFLOW_STANDARDIZATION_REPORT.md
?? docs/plans/2026-10-14-accessibility-fallback.md
?? docs/plans/2026-10-15-debug-release-split.md
?? docs/plans/2026-10-15-minimum-release-roadmap.md
?? docs/testing/2026-10-16-dev-timing-followup.md
?? docs/testing/ACCESSIBILITY_FALLBACK_REPORT.md
?? docs/testing/DEBUG_RELEASE_DEVICE_RUNTIME_REPORT.md
?? docs/testing/DEBUG_RELEASE_SPLIT_REPORT.md
?? docs/testing/DEVELOPMENT_TEST_REPORT_TEMPLATE.md
```
