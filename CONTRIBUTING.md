# Contributing

1. Fork the repository and create a focused branch.
2. Read [PROJECT_STATE](docs/PROJECT_STATE.md) and preserve existing behavior outside your change.
3. Build and run host tests using the commands in [README](README.md).
4. Add a targeted regression test for a behavior fix and describe what you actually verified.
5. Open a pull request with the change, rationale and validation results. Prefer `feat:`, `fix:`, `refactor:`, `perf:`, `test:`, `docs:`, `chore:` or `build:` commit prefixes.

Use fake data in tests. Keep APK/AABs, signing keys, credentials, raw device screenshots, private notifications and local diagnostics out of commits. Review/redact logs and images before sharing them.

Before any real-device workflow, read [DEVICE_SAFETY.md](DEVICE_SAFETY.md). Keep user data persistent: no automatic uninstall, clear-data, other-app modification or destructive fallback. Default connected instrumentation is prohibited on the user's vivo X200s; host tests are the normal baseline.

For docs, current implementation belongs in `docs/PROJECT_STATE.md`, unresolved work in `docs/OPEN_ISSUES.md`, plans in `docs/plans/`, and sanitized conclusions in `docs/testing/`. Do not resume the retired `tasks/` logs.
