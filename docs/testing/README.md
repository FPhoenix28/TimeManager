# Sanitized engineering reports

These are summaries of recorded validation, not new device tests performed during repository cleanup. Raw screenshots/APKs and exact device identifiers stay in private local storage, outside Git. Device: vivo X200s / Android 16; applicationId: `com.example.timemanager`.

| Report | Historical scope |
| --- | --- |
| [Session Friction](SESSION_FRICTION_REPORT.md) | Initial integration, installation/ADB interruptions and unresolved early observations. |
| [Todo](TODO_TEST_REPORT.md) | Additive migration, todo lifecycle and partial background-scheduling checks. |
| [Re-entry gate](REENTRY_GATE_REPORT.md) | Same-stage deduplication, next stage and genuine idle reset. |
| [Permission health](PERMISSION_HEALTH_REPORT.md) | Accessibility-off fallback, guidance and restoration. |
| [UI verification](UI_VERIFICATION.md) | Approved dark design, real-data binding and navigation smoke checks. |

Each historical test count belongs to that report's date. For this cleanup's actual host/fresh-clone results, use [readiness report](../OPEN_SOURCE_READINESS_REPORT.md). Host tests are not substitutes for unperformed device matrices. Current device rules supersede old per-operation approval/pause instructions, but never permit destructive cleanup.
