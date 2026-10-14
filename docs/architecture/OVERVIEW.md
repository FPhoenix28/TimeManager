# Architecture and retained decisions

The implementation, not an old task log, is authoritative. Package names below are under `com.example.timemanager`.

## Ownership

| Module | Owns |
| --- | --- |
| `data/repository/UsageStatsReader`, `UsageRepository`, `UsageTimeRepository` | System event reading, cached app metadata and unified cumulative usage. |
| `monitor/ForegroundAppDetector`, `timer/AppTimer`, `monitor/SessionTracker` | Actual foreground observation and physical session timing. |
| `monitor/SessionFrictionManager` | The single reminder episode state, stages, idle expiry and one-shot admission. |
| `domain/limit` | Effective daily allowance, threshold evaluation and daily overlay decisions. |
| `domain/intervention/InterventionCoordinator` | Process-scoped arbitration and messages between independent services. No retained Service/View/Activity. |
| `service/UsageMonitorService` | Realtime orchestration and ongoing notification; overlay failure must not stop monitoring. |
| `service/TimeManagerAccessibilityService` | Stable app-entry events, HOME, re-entry and Focus/Deep enforcement. It does not duplicate quota math. |
| `ui/intervention`, `ui/focus` | Reused responsive overlay hosts, Compose owner lifecycle and presentation. |
| `data/preferences`, `data/local` | DataStore configuration/temporary state; Room usage history/todos/receipts. |
| `domain/history`, `domain/statistics` | Throttled backfill and pure analysis of existing snapshots. |
| `domain/todo`, `reminders` | Shared todo summary and WorkManager stage scheduling. |
| `permissions`, `ui/*` | Capability health, ViewModels and Compose presentation. |

## Decisions worth preserving

1. **Physical session ≠ reminder episode.** Only actual foreground time contributes; a brief HOME/re-entry does not reset progressive friction. The 04:00 boundary resets daily state, not a continuing episode.
2. **One stage, one re-entry gate.** `reentryGateArmed` is tied to a successfully shown stage and consumed when the gate is created. A token expiry, dialog or repeated window event cannot re-arm it.
3. **One quota source.** User base quota plus cycle-scoped extension produces the effective quota; Debug fixtures never persist replacement user values. Missing session settings use a default only at read time.
4. **One overlay decision at a time.** Deep/Focus preempt daily/session interventions. Compose overlay hosts have explicit Lifecycle/SavedState/ViewModelStore owners. Display failure remains separate from monitoring lifetime.
5. **Snapshots, not repeated addition.** Room's package/day key and high-water upsert prevent duplicate history. System event backfill consumes existing usage semantics instead of adding another timer.
6. **Safety remains an engineering requirement.** Keep the [incident record](../DEVICE_INCIDENT_REPORT-2026-09-30.md) and [device rules](../../DEVICE_SAFETY.md), including their uncertainty and prohibition on unsafe connected-test cleanup.

## Navigation and presentation

MainActivity owns saved page and return-source state; page transitions are lightweight fades. ViewModels and cached metadata are reused, and UI does not query PackageManager during list scrolling. Home is a lightweight dashboard; details edit existing settings; statistics observes Room history. This cleanup does not replace navigation or restructure product source.
