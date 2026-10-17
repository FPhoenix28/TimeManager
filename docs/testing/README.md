# Sanitized engineering reports

Follow the [development workflow](../DEVELOPMENT_WORKFLOW.md) and [DEVICE_SAFETY](../../DEVICE_SAFETY.md). New device runs use the [DEVELOPMENT_TEST_REPORT template](DEVELOPMENT_TEST_REPORT_TEMPLATE.md) and target TimeManager Dev (`com.fphoenix28.timemanager.debug` for v0.9.0+), not the user's stable app. Pre-v0.9.0 reports retain their former application IDs as historical evidence; those IDs are not current default targets.

These are summaries of recorded validation. Raw screenshots/APKs and exact device identifiers stay in private local storage, outside Git. Older vivo X200s / Android 16 reports naming `com.example.timemanager` describe historical tests, not permission to use that stable package for new testing. Retain their evidence and incident conclusions; current workflow rules govern new actions.

| Current reference | Scope |
| --- | --- |
| [v0.9.0 signed RC / freeze acceptance](2026-10-17-v0.9.0-signed-rc.md) | Owner confirms signed Release installation, smoke test and acceptance PASS; 159 host tests reused without later runtime changes. Local freeze commit/rebuild authorized, not remote publication. Earlier signing failure retained as historical evidence. |
| [v0.9.0 preparation](2026-10-17-v0.9.0-release-preparation.md) | New application identities, 159 tests, build/manifest audit and initial unsigned-signing configuration checks. |
| [Accepted Session fix / stable update / Git handoff](2026-10-17-stable-update-git-handoff.md) | Owner accepted the latest Session fix; 155 host tests reused, Release rebuilt, daily cover-update returned Success. No post-install functional test; owner authorized two local commits, not push/master integration. |
| [Session HOME confirmation](2026-10-17-session-home-confirmation.md) | Actual launcher confirmation and explicit Session desktop action; owner accepted. |
| [Latest Dev regression](2026-10-16-dev-timing-followup.md) | Bounded timing, fallback Session/Daily/+15 and screenshot-only connected HOME/Gate/Continue checks passed; normal Dev/settings restored. Full coverage and owner acceptance remain separate. |
| [Local daily 1.0.1 update](2026-10-16-stable-local-update.md) | Authorized data-preserving installation returned Success; owner requested self-testing, so no post-install automation or retention verification is claimed. |
| [Debug / Release split](DEBUG_RELEASE_SPLIT_REPORT.md) | Package/label isolation, build and initial device checks. |

| Report | Historical scope |
| --- | --- |
| [Earlier Dev runtime acceptance](DEBUG_RELEASE_DEVICE_RUNTIME_REPORT.md) | Earlier 2026-10-16 partial/failed timing run; follow-up above supersedes the current verdict without rewriting the original evidence. |
| [Session Friction](SESSION_FRICTION_REPORT.md) | Initial integration, installation/ADB interruptions and unresolved early observations. |
| [Todo](TODO_TEST_REPORT.md) | Additive migration, todo lifecycle and partial background-scheduling checks. |
| [Re-entry gate](REENTRY_GATE_REPORT.md) | Same-stage deduplication, next stage and genuine idle reset. |
| [Permission health](PERMISSION_HEALTH_REPORT.md) | Accessibility-off fallback, guidance and restoration. |
| [UI verification](UI_VERIFICATION.md) | Approved dark design, real-data binding and navigation smoke checks. |

Each historical test count belongs to that report's date. For the open-source cleanup's host/fresh-clone results, use [readiness report](../OPEN_SOURCE_READINESS_REPORT.md). Host tests do not substitute for device checks or user acceptance. No report authorizes automatic commit/push, stable-app updates or destructive cleanup.
