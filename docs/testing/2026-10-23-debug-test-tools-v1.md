# DEBUG_TEST_TOOLS_V1_REPORT

Date: 2026-10-23. Branch: `feat/rules-engine`; release baseline v0.9.0. Existing Phase1–4.2 worktree changes were preserved. This is an independent host-only iteration; Phase4.2 device gate and Phase4.3 remain paused, not passed by these results.

## Result

| Field | Result |
| --- | --- |
| Debug-only tools implemented | YES |
| Test Tools entry | second launcher: **TimeManager Test Tools**; `com.example.timemanager.debugtools.DebugTestToolsActivity` in the Dev package |
| Main production UI modified | NO; main screens/main manifest unchanged |
| Release contains test UI | NO — merged manifest and compiled Release class archive checked |
| Runtime test modes | NORMAL / FAST / CUSTOM |
| Default | NORMAL / Use test timing OFF; no startup opt-in |
| Session first / progression adjustable | YES / YES |
| Rest reset / Gate cooldown adjustable | YES / YES |
| Daily extension adjustable | YES; base daily quota not overridden |
| Direct internal-state mutation controls | NO |
| Debug settings persisted | YES — separate Debug SharedPreferences implementation; host storage-contract recreation tested, actual Android process-restart behavior awaits owner validation |
| Reset to production | PASS in host controller tests; UI wired to the same reset |
| Production timing unchanged with mode OFF | PASS — field equality, original tests and runtime timing tests |
| Release timing unchanged | PASS — independent Release source-set test asserts all production fields |
| Existing production tests changed | NO; original 448 Debug tests/expectations retained |
| New tests | 20 Debug + 1 Release |
| Total Debug JVM | **468 tests / 52 suites** |
| Total Release JVM | **446 tests / 49 suites** |
| Failures / errors / skipped | **0 / 0 / 0** in each variant |
| assembleDebug | PASS |
| Release compile isolation | PASS — compileReleaseKotlin and Release host tests, no Release APK/signing task |
| Device touched / APK installed | NO / NO |
| Commit / Push / Merge / Tag | NO / NO / NO / NO |
| READY_FOR_OWNER_INSTALL | YES — host verified; actual device acceptance NOT_RUN |
| Recommended initial manual preset | Fast, with a high normal daily quota for the first Session check |

## CURRENT_TIMING_SOURCE_MAP

Paths below are under `app/src/` unless otherwise stated.

| Parameter / source | Previous read | Current consumption |
| --- | --- | --- |
| `config/TimeConfig` | shared immutable durations, no progression fields | same model plus three positive progression durations; shared immutable `production()` factory |
| Debug `config/BuildTimeConfig.value` | fixed production snapshot | live controller value, production before initialization; `provider.current()` reads one immutable effective config |
| Release `config/BuildTimeConfig` | fixed production fields | fixed production value/provider; init/log hooks no-op; no preferences or debug types |
| `TimeManagerApplication` | no timing initialization | initialize variant provider once before Service/Activity runtime consumers |
| first Session intervention | Monitor constructor-stored TimeConfig | Monitor reads current override or unchanged per-app saved Session setting; no business setting write |
| progression | Friction's literal +480/+480/+300s | same successful-show stage transition, current config selects first/second/repeating interval |
| rest reset | Friction constructor captured idle milliseconds | same not-foreground + last-engagement predicate, live duration |
| Session gate cooldown | Friction constructor captured gate milliseconds | snapshot at existing `beginGate`; end timestamp remains fixed for that visible gate |
| bypass expiry | Friction constructor captured 3s | existing token algorithm; provider parameter stays production 3s and has no Test Tools control |
| notification thresholds | SessionTracker constructor captured 900/1800s | live thresholds at existing update; existing per-session receipts are not cleared |
| notification wording | NotificationHelper captured TimeConfig | read same effective durations when forming message; test wording explicitly says seconds |
| Daily future increment | LimitInterventionManager captured config | new Daily request reads current extension amount; no changes to base/effective-limit formulas |
| shown Daily action | request already stores extension amount | click explicitly grants that request's displayed amount, so a config switch cannot create a text/value mismatch |
| Daily action text | `LimitReachedScreen` formats request extension | unchanged; already supports seconds, including “延长 30 秒” |

There is no second usage timer, episode writer, rules engine or limit ledger. `SessionFrictionManager` remains sole owner of progression/admission state. The same existing `AppTimer`, foreground detector, evaluator, HOME confirmation and Overlay host are used.

## Debug-only architecture and persistence

- `src/debug/AndroidManifest.xml` adds one independent MAIN/LAUNCHER Activity. MainActivity does not gain a settings row or conditional Debug screen.
- `DebugTimingController` owns a StateFlow of environment settings, not business state. Pure controller tests use an in-memory implementation of the storage contract.
- Android `DebugTimingPreferences` owns only the private preference file **debug_test_timing_v1**, with mode and seconds-valued custom parameters. It never reads/writes business DataStore, Room, Profile, LimitRepository or SessionSettingsRepository.
- Only an explicit UI action enables FAST/CUSTOM. Debug build identity alone does not activate testing. A chosen Debug mode/custom values persist across process restart by design; a fresh installation/file defaults to NORMAL.
- Reset clears this Debug preference file and publishes NORMAL immediately. Switching Normal uses production while retaining a saved custom preset for later; Reset additionally removes it.
- The separate Release source set has no controller/preferences implementation, launcher declaration or UI classes. It cannot consult the Debug preference file; applicationIds remain `com.fphoenix28.timemanager.debug` and `com.fphoenix28.timemanager`.
- No extra timing-config switches were scattered into business `BuildConfig.DEBUG` branches. The variant provider owns that boundary; Release log hook is empty. Debug logs `TEST_TIMING_ACTIVE` or `PRODUCTION_TIMING` with effective fields at Monitor start/config changes, not on every unchanged tick.

## Normal / Fast / Custom

| Parameter | Normal (production) | Fast | Custom bounds |
| --- | --- | --- | --- |
| First Session cold pause | user's existing per-app value; default 10min only when absent | 20s | 5–600s |
| First progression increment | 480s | 15s | 1–600s |
| Second increment | 480s | 15s | 1–600s |
| Repeating increment | 300s | 10s | 1–600s |
| Away/idle reset | 300s | 30s | 1–3600s |
| Session cooldown | 5s | 2s | 1–5s, never 0 |
| Daily Extend amount | 900s | 30s | 10–900s |
| Notification thresholds (not cold-pause thresholds) | 900/1800s | 15/30s | positive 1–3600s; second > first |
| One-shot bypass expiry | 3s | 3s | no control |
| Daily base quota | user's saved value | user's saved value | normal app-details editor; 1min minimum sufficient |
| Daily cold-page action wait | existing 5s | existing 5s | not changed in v1 |

The original `fastRegression` injection-only fixture retains all existing values/expectations, including its 5s gate and production progression. The **runtime Fast preset** reuses that fixture and explicitly supplies the requested 2s gate / 15,15,10s progression. No old production fixture expected value was changed.

Custom editors are drafts until **Apply Custom**. Selecting Custom recalls the last saved valid Custom configuration, not unsaved text. Empty, invalid, negative, zero and out-of-range values are rejected before persistence. All UI fields are labeled seconds; notification thresholds are separately labeled.

While active, the tools page prominently shows **TEST TIMING ACTIVE**, selected mode and effective parameters. No “trigger now”, fake usage, fake stage, arm-gate, mark-reached, consume-token, direct extension or dismiss-Daily test controls exist.

## Dynamic-change semantics

Configuration changes apply at the next existing timing operation / decision, without restarting Service or replacing managers:

1. **Before first Session reminder:** Monitor's existing `updateFirstReminder` picks up the new first threshold. If real episode usage already exceeds it, the next valid tick may trigger normally. Usage is not changed.
2. **After first reminder:** existing count and already committed next threshold remain. Changing first threshold does not rewind history. The next successful stage commit uses the then-current progression interval. Future episodes use current first threshold.
3. **Gate already visible:** its captured end timestamp remains; no mid-countdown duration jump. Config changes do not clear/close it or mint a bypass.
4. **Idle:** the next ordinary expiry check uses current duration and the same real foreground / last engagement predicate. No background reset timer was added.
5. **Notifications:** next update reads current thresholds, while already-sent receipts remain. Changing a parameter does not resend an already delivered physical-session stage.
6. **Daily extension:** existing extension totals never change when modes change. A new Daily page captures the current future increment. An already visible page keeps both its displayed amount and granted amount; the following new page uses the new amount. Example: existing extension 900s stays 900s when switching Fast; a newly shown +30s page grants another 30s only after the normal user action.
7. **Reset:** future parameters immediately become production. Already shown deadlines, committed next episode threshold and legitimately granted extension remain existing business state; Reset is deliberately not an episode/usage/allowance reset. A completely new production episode still follows the ordinary away-reset lifecycle.

Focus/Deep priority, Daily-over-Session arbitration, stage commit only after successful presentation, one re-entry per stage, base+extension, HOME launcher confirmation, Fail Open, Room and the 04:00 day are unchanged. No new source modification was made to RulesEvaluator, monitor arbitration or Accessibility.

## Host verification

Executed, using the local project-compatible JDK (no global environment change):

```text
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:processReleaseMainManifest :app:testReleaseUnitTest --console=plain
git diff --check
```

Latest run: BUILD SUCCESSFUL. XML totals: Debug **468 / 52 suites**, Release **446 / 49 suites**, each 0 failure/error/skipped. Existing 448 Debug tests remain unchanged and pass; new tests add 20 Debug and one Release-specific test. Release total consists of shared source-set tests plus its provider test, not the Debug-specific tools tests.

New tests:

- `testDebug/.../config/DebugTimingSettingsTest.kt`: defaults/presets/custom bounds, mode-off isolation, storage-contract recreation, reset, future increment values and immutable displayed Daily request.
- `testDebug/.../config/RuntimeTimingTest.kt`: actual Friction manager progression 20/35/50/60/70s, one successful commit per show, first threshold update without usage reset, future progression changes without rewind, fixed shown gate deadline, real idle predicate and live notification thresholds with preserved receipts.
- `testDebug/.../config/DebugTimingFormTest.kt`: field round-trip, invalid text, endpoint and trim behavior.
- `testRelease/.../config/ReleaseTimeConfigTest.kt`: Release identity and every production timing field/provider result.

Extension tests here verify the selected increment values (30, twice 60, production 900), existing-total independence and frozen displayed request contract. They do **not** claim a newly instrumented Android DataStore grant test or a real device Extend click in this iteration. Existing additive persistence implementation is unchanged; the new call site passes the shown request amount to it.

Test-first sequence recorded: new tests initially failed compilation because the new provider/controller APIs did not exist; after implementation, focused and complete suites passed. The first combined verification command also found no `testReleaseUnitTest` task in the current AGP configuration. Inspected the installed AGP 9.3.1 public API and added only a Release host-test enable block in `app/build.gradle.kts`; the task was generated and all Release tests then passed. No dependency, SDK level, applicationId or signing configuration was changed. No release-signing material was opened/printed, and no Release package/sign task ran.

### Variant/artifact checks

- SDK aapt2 badging: Debug APK `com.fphoenix28.timemanager.debug`, label **TimeManager Dev**, version **0.9.0-dev**, code **3**.
- Both launcher activities found: MainActivity / TimeManager Dev and DebugTestToolsActivity / **TimeManager Test Tools**.
- Merged Release manifest: package `com.fphoenix28.timemanager`; **zero** Test Tools/debugtools matches.
- Compiled Release runtime class archive: **no** DebugTiming settings/preferences/controller or Debug tools Activity/form classes.
- Release provider bytecode initializes `TimeConfig.production()`; initialize/log hooks are no-op. Independent Release provider test passed.
- APK remains ignored/generated at `app/build/outputs/apk/debug/app-debug.apk`; no APK staged or committed. No Release APK was rebuilt.
- Device/UI acceptance: **NOT_RUN**. This report does not alter any missing Phase4.2 result.

## Files changed by this iteration

New Debug files:

- `app/src/debug/AndroidManifest.xml`
- `app/src/debug/java/com/example/timemanager/config/DebugTimingSettings.kt`
- `app/src/debug/java/com/example/timemanager/config/DebugTimingPreferences.kt`
- `app/src/debug/java/com/example/timemanager/debugtools/DebugTestToolsActivity.kt`
- `app/src/debug/java/com/example/timemanager/debugtools/DebugTimingForm.kt`

New shared interface: `app/src/main/java/com/example/timemanager/config/TimeConfigProvider.kt`.

Modified: both variant `BuildTimeConfig.kt`; `TimeManagerApplication.kt`; `TimeConfig.kt`; `InterventionRuntime.kt`; `SessionFrictionManager.kt`; `SessionTracker.kt`; `NotificationHelper.kt`; `LimitInterventionManager.kt`; `UsageMonitorService.kt`; `app/build.gradle.kts` (host tests only). Service changes in this iteration are limited to provider wiring/change-only logging and passing the displayed extension amount; its pre-existing Rules Engine diff belongs to Phase4.2 and was not rewritten.

Added the four test files listed above, this report, and `docs/plans/2026-10-23-debug-test-tools-v1.md`. Prior worktree changes/reports remain, including the unpassed device gate. No AGENTS/device safety changes were needed.

## 简单人工测试步骤（owner 安装后执行）

### 先看入口与默认值

1. 安装本轮 Dev APK，不覆盖日常版。
2. 桌面打开 **TimeManager Test Tools**。首次应为 Normal，测试开关关闭。
3. 点击 Fast，应看到 **TEST TIMING ACTIVE**。不要开启日常版对同一目标的干预。

### Normal Session / 下一阶段

1. 在 Dev 中选择一个临时管理应用，每日额度设高一些。
2. Test Tools 选 Fast，打开该应用实际使用约20秒。
3. 应正常返回桌面、显示 Session 冷静页，等待2秒后可选择继续。
4. 继续真实使用，后续提醒点为累计35、50、60、70秒；冷静页等待和离开期间不算实际使用。
5. 不点击任何“立即触发”按钮——本工具没有此类入口。

### Daily 与 Session 接近同时到期

1. 选今天尚未使用、没有旧 Episode 的临时目标；不要清历史数据来制造条件。
2. 在 Dev 正常设置每日额度 **1分钟**。
3. Test Tools 在 Custom 中把首次冷静提醒设 **60秒**，其他值可沿用 Fast，点击应用。
4. 打开目标连续使用至边界，预期每日额度页优先。已有今日使用量、已有阶段或初始系统统计差异都会改变到期顺序；必须查看实际日志才能认定“两者同一轮满足”。
5. 仅选择 Fast（首轮20秒）再使用到1分钟，不自动等于同时到期测试：20秒的 Session 冷静页已经改变了使用过程。

### Daily 延长

1. Fast 下，在 Dev 正常把临时目标每日额度设为1分钟，实际使用到额度耗尽。
2. 每日页面应写 **延长30秒**，不是“延长15分钟”。等待该页原有5秒后点一次。
3. 新有效额度应增加30秒；继续实际使用到新边界，再点一次则再增加30秒。
4. 已有延长不会因切换工具参数而被改写。若页面在切换前就已显示，请先完成该页选择，下一张新页面才使用新延长量。

### 离开重置 / 恢复正式时间

1. Fast 下离开目标应用满30秒且不再尝试打开，再进入应开启新的提醒 Episode；期间重新尝试打开会重新计算离开时长。
2. 测试完成点击 **Reset to Production Timing**，确认 Normal、开关关闭、警示消失。
3. 此时未来参数恢复正式值，但不会删除使用记录、已延长额度或已有提醒历史。需要全新正式 Episode 时按正常离开5分钟的规则等待，不清数据。

Stable-over 的指定多轮状态仍可能无法通过正常 UI 构造；本工具没有 Dismiss Daily / Force Over Limit / Skip Popup 等控制，也不宣称该 Gate 已通过。后续若需要专门场景工具，应独立设计。完成本报告后 STOP，等待 owner 安装/审查；不自动安装、验证 Phase4.2 或开展 Phase4.3。

## Owner-authorized install-only follow-up — 2026-10-23

The owner subsequently requested installing this test build. This is a new, explicit installation authorization, not part of the earlier host-only iteration above.

- Preflight verified the expected primary vivo X200s online, the existing Dev installation, and the actual APK identity: `com.fphoenix28.timemanager.debug`, TimeManager Dev, 0.9.0-dev / code 3. SDK inspection found both the ordinary launcher and TimeManager Test Tools launcher.
- Installed the already verified Debug APK using `adb -s <verified-device> install -r -t <debug-apk>`. The install returned **Success**, and the owner confirmed the USB installation prompt.
- Waited more than one minute from installation start before the post-install read. No uninstall, data clear, fallback installation, app launch, permission/configuration change or functional test was performed. Release was untouched.
- The subsequent read-only package/version/entry check could not complete because ADB again reported the verified device unavailable. Therefore post-install device metadata is **NOT_VERIFIED**; this does not retract the explicit successful installer result and owner confirmation.
- **Installation: PASS. Functional/device acceptance: NOT_RUN.** Earlier Device touched/installed=NO fields describe the host-only implementation stage; this separately authorized follow-up touched the device for Dev installation only.
- No source edits, rebuild, commit, push, Phase4.2 scenario validation or Phase4.3 work. Owner can open TimeManager Test Tools and perform acceptance independently.
