# Open Source Readiness Report

Date: 2026-10-14. Scope: repository-only preparation; no product behavior or phone data changes. This report is updated as actual verification completes, not as a promise of future results.

## Completed

- Clean starting worktree; one local/default branch `master`, 14 commits reachable across the audited original refs, no tags.
- Original tracked inventory: 177 main source/resource files, 27 host-test files, two device-test source files, 20 Markdown files, 17 other build/tool files, two APKs and 75 raw screenshots. No tracked build/IDE/log/temp output in that inventory. Source includes variant-specific configuration in the build/tool category.
- Source/test/resources/Gradle wrapper, device-safety rules, historical incident and approved design retained.
- Current implementation, architecture, actionable open validation work and sanitized engineering reports replace mixed task journals.
- English-first README, standard Apache-2.0 LICENSE, NOTICE, changelog, contributing guide and issue templates added.

## History cleanup

Completed in a separate mirror using git-filter-repo 2.47.0. Retired `tasks/` and agent-specific `docs/superpowers/` paths were removed across public history, after moving durable conclusions to public docs. Exact device identifier/private host paths were generalized; all original author/committer emails were rewritten to the owner's explicitly supplied `249264302+FPhoenix28@users.noreply.github.com`.

All 14 original commits plus the cleanup commit were retained in order (15 at the rewrite checkpoint); no branch/tag was discarded (public remote has master only, no tags). Local Codex tree/checkpoint and remote-tracking refs are preserved in the private original backup, not published as project branches. Rewritten mirror contains only `refs/heads/master`.

Validation inspected all 434 reachable file blobs and complete author/committer metadata: no removed artifact path, old personal identifier, host absolute path or obvious credential candidate remained. All 77 old raw-image/APK blob objects were absent from the new reachable object set. `git fsck --full` passed. Product/build/device-safety trees were compared for **every mapped commit**, not only HEAD, and were unchanged. The full current tree was identical before/after rewrite. Repacked public history at this checkpoint was 937 objects / 1.81 MiB; size alone was not used as proof.

The exact private `FPhoenix28/TimeManager` remote was rechecked immediately before update: master only, no tags, only the owner listed as collaborator. Master was updated using an explicit expected-old-tip `--force-with-lease`, not a mirror push. Visibility was not changed and no Release/tag was created. The working repository was aligned to the identical rewritten tree; old internal checkpoint refs were removed only after their complete external backup was verified. Original unreachable objects were not manually pruned from the original working directory. Subsequent documentation-only commits increase commit/object counts without changing the validated product tree.

**Publication hold:** an authenticated GitHub REST lookup by the old pre-cleanup commit ID still returned HTTP 200 and the old personal author email after the rewrite. Clean reachable refs/fresh clones do not prove that GitHub has purged unreferenced server objects. The repository remains private; do not publish it yet under the requested no-old-private-data requirement. No old object ID or personal address is reproduced in this public report. See remaining risks below.

## Privacy audit

Initial complete reachable-object scan inspected 518 file blobs, including commit snapshots/internal local refs. Findings: two APKs, raw device images, exact device serial in nine text paths, host absolute paths in ten, and personal email in author/committer metadata. No obvious API credential/private-key/IP/phone-number candidate was found by the text patterns. Source `this@Class.method` syntax produced false email candidates and is not personal contact information.

The scan is evidence, not a guarantee about arbitrary unknown secrets. All raw device images are excluded regardless of their visible contents. The formal mockup was visually reviewed: illustrative app logos/data and generic tasks, not a raw device capture. Diagnostic strings in source are not runtime log dumps. Backup/mirror material containing originals remains private and must never be published.

## Files removed from Git

- Two tracked APKs under the retired device-testing tree.
- 75 raw PNGs, including invalid and personal-data-bearing captures.
- Retired task logs and old agent-specific plans; public conclusions and durable architecture are retained in new documents.
- The device-bound screenshot script is replaced by a parameterized reusable host tool with no hardcoded device or SDK path.

## Files retained locally

- Existing `tasks/` and old plans remain locally, ignored rather than deleted.
- External backup: `../TimeManager-local-artifacts-backup-2026-10-14/` — 83 artifact files copied and compared byte-for-byte, original task notes, old plans, all-ref Git bundle and original-history mirror. Bundle verification, readable refs and mirror object checks passed.
- Local APKs, raw captures and original private history are intentionally **not** public artifacts. The backup is not an application-data backup and contains no new phone extraction.

## Documentation added

README, LICENSE, NOTICE, CHANGELOG, CONTRIBUTING, PROJECT_STATE, OPEN_ISSUES, architecture overview, plan index/cleanup plan, third-party notes, five sanitized reports and testing index. AGENTS points to current-state/open-issue docs; DEVICE_SAFETY remains authoritative and unchanged. Incident uncertainty and safety restrictions are retained, with only private identifiers/paths generalized.

## Build verification

Current-tree verification completed: `:app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --offline --no-build-cache --rerun-tasks` succeeded, with all 96 actionable tasks executed. JUnit XML: **27 suites / 125 tests, 0 failures, 0 errors, 0 skipped**. `tools/Verify-TodoMigration.ps1` and `git diff --check` passed. Product source, resources, tests and Gradle/build configuration are unchanged from the starting commit. The parameterized screenshot tool passed syntax parsing only and was not run against a device.

An independent **clone from GitHub after the remote update** was then verified in `../TimeManager-fresh-validation-2026-10-14/`. Before building it had a clean Git status and no local.properties, .idea, .gradle or app/build. The installed SDK was provided by environment, not by a copied local file. The host's normal dependency/toolchain installation was available; no previous project build directory was copied.

Fresh-clone command: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --no-build-cache --no-configuration-cache --rerun-tasks`. **BUILD SUCCESSFUL in 2m 32s; 96 actionable tasks executed.** JUnit XML: **27 suites / 125 tests, 0 failures, 0 errors, 0 skipped**. Migration verification passed again; Git remained clean and local.properties was still absent. Debug and unsigned Release APKs were generated locally, not committed or installed. Validated product revision: `dd78e52cf54ce2e08887575478e069e958c4af34`; later final-report/README edits are documentation-only.

Native-library stripping reported its existing packaging warning, not a build failure. No connected instrumentation or phone operation occurred. Linux/macOS builds were not executed; README uses `sh ./gradlew` because the preserved wrapper lacks an executable bit.

## Remaining risks

- The approved design contains third-party marks; per-vector/template provenance is not fully mapped. Review before binary distribution; project licensing does not license third-party trademarks.
- Android backup is currently enabled; app-local data is not claimed to be excluded from OS backup.
- Cross-vendor/permission/large-font/landscape/incoming-call validation is incomplete; see OPEN_ISSUES. No store-policy approval or production signing was performed.
- Rewritten remote history requires collaborators to re-clone. Remote ownership/private status and collaborator list must be rechecked immediately before publishing cleaned refs.
- **Blocking public visibility:** GitHub still retains a queryable old commit object with the former personal email. Force-push does not itself erase cached/unreferenced server data. The owner should request server-side removal through [GitHub Support](https://support.github.com/) while keeping this repository private, then recheck the old object. Support eligibility is determined by GitHub, not guaranteed here. If it cannot be purged, publishing a separate newly created clean repository is an alternative requiring an explicit owner decision; no repository deletion/recreation is authorized or performed. [GitHub's official sensitive-data cleanup guidance](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository) documents this distinction.

## Recommended version

Recommend **v0.9.0**, title **TimeManager v0.9.0 — intentional friction preview**. This is a suggestion, not a created tag or release; legacy app `versionName=1.0` has not been changed.

Release-notes draft: Managed app tracking and quotas; unlimited daily +15-minute extensions; progressive Session Friction and once-per-stage re-entry; Focus/Deep Focus; lightweight todos; 7-/30-day statistics; permission guidance and refreshed Compose UI. Local-first, Android backup enabled, primarily verified on vivo X200s / Android 16. Unsigned Release output requires a reviewed signing/distribution process. See known validation limits before use.

## Before making repository public

Repository preparation/build validation is complete, but **not yet cleared for public visibility** because of GitHub's retained old object. Owner review and resolution of that server-side item are required. Do not upload local backup material, recreate the old history, publish raw captures or change visibility as part of this cleanup. Review license/provenance notes and unresolved device validation; choose release metadata/signing separately.

- [x] No APK in cleaned history
- [x] No raw device screenshots in cleaned history
- [x] No signing key found
- [x] No API secret found by audit
- [x] No personal email in cleaned commit metadata
- [x] No exact device serial in cleaned reachable text
- [x] README complete
- [x] LICENSE present
- [x] CHANGELOG present
- [x] PROJECT_STATE present
- [x] Fresh clone builds
- [x] Freshly executed tests pass
- [x] Repository still private (verified again after remote update)
- [ ] GitHub old private objects/cache no longer addressable — still blocked; owner action required
