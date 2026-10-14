# Session Friction validation — 2026-10-11

Sanitized from the original engineering log. Device: vivo X200s / Android 16. Package: `com.example.timemanager`. Test targets are described generically; no private usage totals or raw screenshots are published.

## Recorded results

- Initial normal Debug update succeeded; foreground monitoring remained active with notification ID 1001 and existing configuration visible.
- A temporary fixture shortened the first reminder to 20 seconds and idle reset to 30 seconds in calculation only. Daily settings and extension values were not overwritten in DataStore.
- One update was rejected by the phone (`INSTALL_FAILED_ABORTED: User rejected permissions`). Testing stopped without uninstall/clear-data fallback. Later approved updates succeeded.
- First actual-use threshold → HOME → five-second overlay → automatic close on the launcher was observed. Re-entry displayed a waiting gate.
- Continue encountered a vendor app-launch confirmation window in an early run. Requesting launch was not recorded as proof of actual target admission. Later stage-consumption verification is in [the subsequent report](REENTRY_GATE_REPORT.md).
- ADB transport intermittently disappeared while Windows still enumerated the phone/interface; this did not prove a physical cable disconnection. Only host transport recovery was attempted.
- A rotation attempt captured transition/invalid frames, not stable landscape evidence. Invalid screenshots were not counted as passing evidence.

## Host and closeout

At this historical stage: 91 Debug JVM tests with zero failures/errors and successful Debug/Release builds (some reruns were up-to-date, not new test executions). Normal timing was restored in source and successfully installed at closeout; a target-app observation longer than the temporary 20-second threshold produced no test overlay. Monitoring/notification stayed active.

Temporary display/log settings were restored. No uninstall, clear-data, connected instrumentation, helper installation or other-app content/configuration change was performed. Existing user data was preserved; ordinary test usage naturally updated usage history.

## Verification limits

This early run did not establish full one-shot admission after vendor prompts, all intervention priorities, stable landscape, production five-minute idle boundary or long progressive stages. Do not treat the original temporary-disconnection checkpoint as the final installation state. Later reports supersede only the specific paths they verified.
