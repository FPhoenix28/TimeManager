# DEBUG_APP_DETAIL_TIMING_CONTROLS_REPORT

Date: 2026-10-23. Branch: `feat/rules-engine`. Base HEAD: `cf4cad6`.
Scope: host-side implementation and verification only. Existing Phase 1–4.2 and Home fixes in the working tree are preserved; this is not a release or device acceptance report.

## CURRENT_APP_DETAIL_TIMING_MAP — before this iteration

| Parameter | Actual source | Unit / production value | Per-app | Release editable |
| --- | --- | --- | --- | --- |
| Daily base quota | AppLimitPreferences → LimitRepository → detail/monitor | Int minutes, explicitly saved user value | YES | YES |
| First Session intervention | SessionSettingsPreferences → SessionSettingsRepository | Int minutes, absent = 10 min, editor 5–180 min | YES | YES |
| Stage2 increment | TimeConfig → SessionFrictionManager.onReminderShown | Long seconds, 480 | NO | NO |
| Stage3 increment | Same provider/owner | Long seconds, 480 | NO | NO |
| Repeating increment | Same provider/owner | Long seconds, 300 | NO | NO |
| Rest reset | TimeConfig → SessionFrictionManager.expireIdleEpisodes | Long seconds, 300 | NO | NO |
| Gate cooldown | TimeConfig → SessionFrictionManager.beginGate | Long seconds, 5 | NO | NO |
| Daily extension action | TimeConfig → LimitInterventionManager | Long seconds, 900 | NO | NO |
| Session notifications | TimeConfig → SessionTracker / NotificationHelper | Long seconds, 900 / 1800 | NO | NO |

The superseded Debug tools had global NORMAL/FAST/CUSTOM settings and a global first-intervention override. Those interactions were rejected by the owner. Their runtime preference file is no longer read; there is no automatic import of short timings into the new configuration.

## Detail UI and scope

Normal Dev route: Management → tap the managed app row (or its existing Edit action) → existing App Detail. Home's existing detail entry also remains. Return preserves the entry source. Release management actions retain their original editing behavior.

Daily base and first Session reminder rows open the same reusable hours/minutes/seconds editor in Dev. Each draft is saved explicitly; typing does not write storage. Hours: 0–99; minutes and seconds: 0–59; total: 1–359999 seconds. Zero/negative/invalid values are rejected. Existing legacy zero-quota reads and Release input semantics are not migrated or reinterpreted. Dev's add-app minute editor rejects zero; precise editing is available in detail after adding.

The existing detail list contains a Dev-only advanced block labeled **global**, affecting every managed app. Notification thresholds are folded under More test parameters and explicitly distinguished from cold-page interventions. No separate screen, launcher, Activity, mode selector or enable-testing switch remains.

### Exposed

- Per app: daily base and first Session intervention, precise to seconds.
- Global Dev: second-reminder increment, third-reminder increment, repeating increment, idle reset, Session gate cooldown, next Daily extension amount.
- Global Dev, folded: first and second Session notification thresholds. Notification2 must be later than Notification1.

### Intentionally not exposed

- One-shot bypass expiry: 3 seconds, internal admission safety.
- Coordinator HOME/entry command timeout: 2000 ms, Android execution handshake.
- Accessibility debounce: 700 ms, event-loop suppression.
- Daily display retry: 5000 ms, recovery scheduling rather than a product threshold.
- Daily cold-page wait: 5 seconds, separate from the requested Session gate cooldown.
- Monitor polling, foreground/launcher confirmation mechanics, permission health polling and history flush intervals: execution/data-acquisition internals, not product timing controls.

There are no trigger-now, usage, episode, reminder-count, stage, gate, token, extension-total or previous-over-limit mutation controls.

## Storage and real runtime chain

Existing DataStore files and minute keys remain intact:

- `app_limits`: `daily_limit_minutes:<packageName>`.
- `session_settings`: `session_reminder_minutes:<packageName>`.

Dev adds optional Long-second fields in those same per-app stores:

- `daily_limit_seconds:<packageName>`.
- `session_reminder_seconds:<packageName>`.

Dev reads seconds when present, otherwise converts the legacy minute value ×60. Absent first-reminder configuration remains 600 seconds. A normal minute save atomically clears the corresponding precision override; removing management clears both forms through the existing repositories. Seconds-only saves do not overwrite legacy minute keys. AppLimit.limitSeconds and AppSessionSettings.reminderSeconds are canonical for runtime consumption; their minute properties remain compatibility projections, not the source of second-precision calculations.

Release's variant boundary disables precise overrides. It continues reading its original minute keys and uses production global timing. Debug and Release already have separate application identities/data directories. Host isolation tests additionally seed both key types into a test store and prove Release ignores the Dev-second fields.

Daily: detail editor → ViewModel → LimitRepository → DataStore Flow → Service seconds map → effective base + existing extension → evaluator/notification/intervention. Home, detail and management consume the canonical seconds too.

First Session: detail editor → ViewModel → SessionSettingsRepository → DataStore Flow → Service seconds map → existing SessionFrictionManager. The monitor compatibility snapshot also carries optional seconds, so Rules Engine projection does not truncate 20 seconds to zero or report a false daily policy mismatch. Legacy minute-only inputs keep their existing behavior. No pure rules calculation changed.

Advanced overrides use the Debug-only `debug_app_detail_timing_v2` preferences, defaulting field-for-field to production. No first-Session global override is stored. No business Profile, quota or Session settings field stores these global overrides. Legacy injected fast fixtures remain solely to preserve existing tests, not as selectable runtime presets.

## Provider and dynamic semantics

BuildTimeConfig exposes a common TimeConfigProvider. Debug uses the global settings controller; Release always uses TimeConfig.production(). Runtime callers use the same existing friction/tracker/limit mechanisms.

- Editing first reminder updates an existing episode only before its first committed reminder. Accumulated usage and stage history are retained.
- A committed next threshold is not recalculated when increments change. Subsequent successful reminder commits use the latest increment. This rule is explained in the advanced UI.
- An already-open gate keeps its captured deadline. Future gates use the current cooldown.
- Idle reset reads the current interval but retains the original non-foreground and last-engagement predicate.
- Global reset clears only the v2 advanced override file and publishes production parameters. Per-app settings, usage, episode state, receipts and extension totals are not reset.
- The existing Daily overlay observes the provider for its extension label. Its Continue callback passes the displayed amount to the existing grant action, keeping label and actual increment consistent. Changing the action amount never changes an existing extension total.
- Production extensions remain unlimited +900 seconds. Debug may choose +30 seconds; two explicit actions add 60 seconds. An existing 1800 seconds plus a new +30 becomes 1830, not 30.

No change to foreground detection, usage accounting, HOME behavior, priority, admission, 04:00 boundaries, Room history or notification IDs. Overlay Compose Host is unchanged; only its existing content's timing subscription/action parameter is updated.

## Verification

Initial duration-conversion tests failed as expected before DurationInput existed; the implementation then passed. Final commands:

```text
gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:compileReleaseKotlin :app:testReleaseUnitTest --console=plain
git diff --check
```

Results: Debug **478 tests / 55 suites**, Release **452 tests / 52 suites**. Failures/errors/skipped: **0 / 0 / 0** in both. Debug build and Release compilation: PASS. No signed Release packaging task was run.

Original 448 host tests were retained without source/expected-value changes. The preceding Home's 4 and runtime timing's 7 tests also remain. This iteration adds 19 tests participating in Debug (18 Debug-only + 1 shared projection test), and 1 Release-only isolation test: **20 new test methods**. It replaces 13 tests for the removed standalone form/mode interaction. Therefore the prior Debug count 472 becomes 472 − 13 + 19 = 478, not 491.

Covered: h/m/s conversion (20/60/90/3723 seconds), invalid/zero/max; actual DataStore per-package roundtrips and cold reopening; legacy 10/15-minute reads; overwrite/removal; canonical repository/effective-limit/friction consumption; detail UI-state precision; reset preserving real per-app data and episode; production/Debug extension accumulation; real controller feeding friction progression 20/35/50/60/70/80 and notification helpers; production parameter restoration; existing stage/deadline/idle semantics; seconds-preserving compatibility projection; Release ignoring seconds and using production timing.

Artifact inspection: Debug APK identity `com.fphoenix28.timemanager.debug`, label `TimeManager Dev`, version `0.9.0-dev` / code 3. The merged manifest has one launcher, MainActivity. No DebugTestToolsActivity remains. Release compiler outputs contain no DebugTiming controller/storage/UI editor/DurationInput classes; the Release detail hooks render no controls and delegate formatting to the unchanged formatter. The Release source set has no advanced labels or preference implementation.

This is host evidence, not rendered-device UI verification or owner acceptance. Phase4.2 Daily+Session, stable-over and full +15 device acceptance remain **NOT ACCEPTED / NOT RUN as previously recorded**; no result in that report was upgraded.

## Files changed in this iteration

All source paths below are relative to `app/src/`.

- New `debug/java/com/example/timemanager/config/{DurationInput,DebugTimingOverrides}.kt`; revised DebugTimingPreferences and BuildTimeConfig.
- New Debug and Release `java/com/example/timemanager/ui/detail/AppDetailTimingControls.kt` variant hooks.
- Common config/TimeConfigProvider: live timing Flow; Release BuildTimeConfig: precise-setting boundary.
- Common data/model: AppLimit, AppSessionSettings canonical second values.
- Common data/preferences: AppLimitPreferences, SessionSettingsPreferences precise keys; InterventionPreferences constructor injection for real host persistence tests only.
- Common data/repository: LimitRepository, SessionSettingsRepository canonical reads/writes.
- Common domain/limit/LimitInterventionManager: seconds entry method, minute method retained.
- Common monitor/shadow/LegacyRulesSnapshotAdapter: optional precise inputs, no engine-rule rewrite.
- Common service/UsageMonitorService: seconds configuration maps and displayed extension callback amount.
- Common ui/detail: existing Screen, UiState and ViewModel; common ui/home Screen/ViewModel display/consume seconds while retaining the earlier managed-zero-usage fix.
- Common ui/limit Screen/UiState/ViewModel and MainActivity: Dev detail entry, precise list display and return source; Release entry unchanged.
- Common ui/intervention/InterventionOverlayController: live extension label/action value; no host lifecycle or trigger change.
- New tests: Debug DurationInputTest, AppDetailPrecisionTest, DebugTimingOverridesTest; shared PolicySecondsPrecisionTest; Release ReleasePrecisionIsolationTest.
- Removed superseded, previously uncommitted Debug Manifest entry, DebugTestToolsActivity, DebugTimingForm, DebugTimingSettings and their form/mode tests. Existing historical Debug Tools report/plan remain historical, superseded by this report.
- This plan and report. Other pre-existing working-tree changes are not claimed as new work here.

## Final checklist

| Requested field | Result |
| --- | --- |
| Independent Test Tools screen created | NO |
| Existing App Detail enhanced | YES |
| Debug-only advanced section | YES |
| Release advanced section visible | NO |
| Daily quota supports hours / minutes / seconds | YES / YES / YES |
| First Session supports hours/minutes/seconds | YES |
| Second-stage / third-stage / repeating increment editable | YES / YES / YES |
| Rest reset / Gate cooldown / Daily extension amount editable | YES / YES / YES |
| Notification timing editable | YES, folded and separately labeled |
| Other exposed parameters | None beyond the audited groups |
| Per-app parameters | Daily base, first Session threshold |
| Global Debug parameters | Increments, reset, cooldown, extension action, notification thresholds |
| Duration storage unit | Canonical Long seconds with legacy Int minute keys preserved |
| Legacy minute settings preserved | PASS |
| 20-second Session roundtrip | PASS |
| 1-minute Daily roundtrip | PASS |
| Debug extension 30s | PASS |
| Production extension 900s | PASS |
| Production Session progression unchanged | PASS |
| Runtime state mutation controls added | NO |
| Release timing behavior unchanged | PASS (host validation) |
| Existing 448 tests preserved | YES |
| New tests | 20 unique methods; 19 in Debug, 1 additional Release-only |
| Total Debug JVM | 478 |
| Failures/errors/skipped | 0/0/0 |
| assembleDebug | PASS |
| Release compile isolation | PASS |
| Device touched / installed | NO / NO |
| Commit / Push / Merge / Tag | NO / NO / NO / NO |
| READY_FOR_OWNER_INSTALL | YES; installation and rendered/manual acceptance pending |

## Recommended first manual configuration

After the owner separately approves installation: select a managed app in Dev → application detail. Save Daily **00:01:00**, first Session **00:00:20**. In global advanced parameters save second increase **00:00:15**, third increase **00:00:15**, repeat **00:00:10**, idle reset **00:00:30**, cooldown **00:00:02**, extension **00:00:30**.

Use the real foreground/app-entry and Continue/Exit flow. Expected Session thresholds: 20,35,50,60,70,80 seconds of actual episode engagement; simultaneous Daily remains higher priority. At Daily exhaustion the extension action should display 30 seconds and add 30 to today's existing total. These are owner manual expectations, not claims that this iteration ran them on a device.

Restore formal defaults resets only global advanced timings; Daily stays 60 seconds and first Session stays 20 seconds until normally edited. Stop here: no installation, Phase4.2 device continuation, Phase4.3 or Git operation.
