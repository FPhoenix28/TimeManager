# Enhanced App Detail Timing Controls Implementation Plan

> Execution: inline in this session. The generic planning skill's optional execution subskills are unavailable; no subagent or commit is authorized.

**Goal:** Dev edits real per-app durations and global timing parameters through the existing app detail page, with Release parity.

**Architecture:** Keep minute keys and APIs for compatibility. Add optional Dev seconds keys and canonical seconds reads; Release ignores these keys. Debug source-set UI and a production-default global timing controller feed the existing runtime provider, not a second state machine.

**Tech Stack:** Kotlin, Compose Material3, Preferences DataStore, JUnit, build-variant source sets.

**Spec:** Owner's Enhanced App Detail Test Controls request, 2026-10-23.

## Constraints

- No device operations, installation, signing, Git commit/push/merge/tag or Phase4.3.
- Preserve usage algorithms, episode ownership, arbitration, 04:00 boundary and previous validation results.
- Daily/first reminder are per-app. Advanced timings are global and labeled accordingly.
- New Debug storage defaults to production; obsolete preset storage is not imported.
- Restoring global defaults does not change per-app configuration or runtime state.

## Tasks

- [x] Add failing duration conversion and real DataStore precision tests: 20s, 60s, 90s, legacy 10/15 minutes, remove/overwrite and Release isolation.
- [x] Add `DurationInput` (hours 0–99, minutes/seconds 0–59, total >0), canonical seconds fields and optional seconds preference reads/writes. Ordinary minute saves clear the precision override atomically.
- [x] Propagate seconds through detail/Home/management, monitor daily/first config and the compatibility policy projection; preserve all existing minute-call signatures.
- [x] Replace standalone tools/modes with Debug-only `AppDetailTimingControls` and reusable duration editors; add Release no-op counterparts. Keep normal Release editors unchanged.
- [x] Replace global mode storage with production-default advanced overrides and a live Flow provider. Keep legacy injected test fixture only for original tests.
- [x] Let the existing Daily overlay subscribe to the provider: the displayed extension increment is passed into its actual action callback. Existing extension totals remain untouched.
- [x] Validate actual friction progression and dynamic config semantics, extensions, repository persistence and Release behavior. Keep the original 448 test sources unchanged.
- [x] Run Debug JVM tests, Release JVM tests, assembleDebug, compileReleaseKotlin and diff checks; inspect manifest/source-set outputs. No Release APK packaging.
- [x] Write the requested public host-verification report and owner manual recipe; stop before installation.

## Dynamic semantics

`updateFirstReminder` only changes an episode with reminderCount == 0. `onReminderShown` commits the next threshold once; editing increments does not rewrite that committed threshold, but future successful commits use current parameters. Existing gate deadlines remain fixed. Idle reset still requires non-foreground plus elapsed time since engagement. Changing extension action size changes only a future explicit Extend action.
