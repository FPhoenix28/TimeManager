# Once-per-stage re-entry gate — 2026-10-12

Scope: gate consumption and stable foreground transitions only; timing, daily limits, 04:00, Room and Compose host behavior were not changed by that fix. Device: vivo X200s / Android 16, package `com.example.timemanager`.

## Root cause and fix

The old gate relied on episode `isActive`, so leaving after a one-shot admission could trigger the same stage again. Raw window package changes also confused temporary windows with real exits.

- `SessionFrictionState` / Manager: `reentryGateArmed` is armed only by a newly committed reminder and consumed at gate creation. Close, Continue, Stay Home and expired tokens do not re-arm it; genuine idle reset discards the episode.
- Coordinator / Accessibility: stable full-screen external windows or the known launcher establish entry/exit; own overlays, IME, System UI and temporary windows do not. HOME echoes wait for a stable exit.
- Monitor: a fresh Daily check precedes Session gating; stale consumed requests admit instead of displaying another gate.

Two new regression cases failed on the old implementation before the fix. Afterwards all **113** Debug JVM tests passed, zero failures/errors; Debug/Release builds and whitespace checks succeeded. No connected instrumentation ran.

## Device evidence

Only an explicit temporary first threshold of 20 seconds was used for speed. Subsequent +8/+8/+5 intervals, five-second gate, three-second token, five-minute idle and Daily values stayed production values. No user configuration was overwritten.

- First-stage gate waited five seconds; Continue successfully brought the target managed app to foreground.
- Internal activity/back transitions did not HOME or re-gate. Same-stage Home/Recents re-entry remained admitted.
- The next reminder occurred after the genuine +8-minute increment; a new stage armed one gate. Stay Home consumed it and later entry did not re-gate.
- After a genuine absence longer than 300 seconds, re-entry was normal and subsequent usage reached the first threshold again, proving episode reset rather than retention of the previous stage.
- Monitoring remained foreground with fixed ID 1001 and real saved quotas, not a 1-minute test quota.

Normal timing was then successfully installed and observed beyond the temporary threshold without a test reminder. Temporary display/log settings were restored; existing configuration/history was preserved. No uninstall or clear-data was used.

## Evidence limits

Vendor restrictions prevented a complete real-event Debug Logcat sequence; empty logs are not proof of absent events. Rotation commands did not unlock the target's portrait policy and are not complete landscape evidence. Target notification entry, independent dialog, large-font/landscape and full Focus/Deep/Daily integration were not all retested on-device. Daily priority and nonconsumption have host-test evidence, not a newly forced real quota-exhaustion run.
