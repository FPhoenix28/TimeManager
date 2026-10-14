# Open-source readiness cleanup

Scope: repository contents, documentation, privacy and Git history only. Product code and device data stay unchanged.

1. Audit the tracked tree, all reachable history and commit metadata. Record concrete findings without publishing private values.
2. Copy raw local test material outside the repository. Create and validate an all-ref Git bundle and mirror before untracking anything.
3. Retire `tasks/`, consolidate current architecture and open validation work, and preserve sanitized engineering reports and useful plans.
4. Add README, Apache-2.0 license, changelog, contribution guide, issue templates and artifact ignores. Preserve device-safety rules and the incident's uncertainty.
5. Run host unit tests, migration verification, Debug and Release builds. Compare product source with the starting commit.
6. Rewrite only a separate mirror using explicit artifact paths and privacy substitutions, including the owner-confirmed noreply address. Validate every public ref and reachable text blob.
7. Recheck the exact private remote and collaborator list; publish only the cleaned master with an explicit lease. Never change visibility or create a release.
8. Clone into a fresh directory and build without local.properties, IDE state or build-cache reuse. Record the actual test count and unresolved limitations.

Completion requires a checked readiness report, preserved local backups, unchanged product source, clean public history and verified fresh-clone builds. A failed prerequisite is reported rather than silently bypassed.
