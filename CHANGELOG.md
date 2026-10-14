# Changelog

## Unreleased

### Added

- Managed app selection, daily quotas and per-app session-reminder settings.
- Live monitoring, quota warnings, overlay interventions and temporary daily extensions.
- Progressive Session Friction with per-stage re-entry gating and idle reset.
- Focus and Deep Focus, lightweight todos, permission health and Room-backed usage history/statistics.
- Searchable app selection, responsive overlay layouts and a unified light/dark Compose UI.

### Fixed

- Duplicate usage entries that produced duplicate list keys.
- Effective daily-limit calculation after extensions and isolation of explicit Debug timing fixtures from saved user settings.
- Repeated same-stage re-entry gates and missing overlay-only reminders when Accessibility is off.

### Repository

- Added public-facing documentation, Apache-2.0 licensing, contribution guidance and issue templates.
- Retired local task logs, preserved sanitized test conclusions and excluded raw device artifacts and install packages.
- Rewrote public branch history in a separate mirror to remove private identifiers, personal commit email and raw test artifacts; clean fresh-clone builds passed. Publication remains on hold until GitHub's retained old objects are addressed.

These entries summarize the existing implementation and this repository cleanup; they do not imply that these product changes were all made during the cleanup. Earlier commit messages mention `v0.1 stable` and `stable 1.1`, but there are no release tags. Exact historical SemVer boundaries are not reconstructed.
