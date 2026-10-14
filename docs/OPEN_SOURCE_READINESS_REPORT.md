# Open Source Readiness Report

Date: 2026-10-14. Scope: repository-only preparation; no product behavior or phone data changes. This report is updated as actual verification completes, not as a promise of future results.

## Completed

- Clean starting worktree; one local/default branch `master`, 14 commits reachable across the audited original refs, no tags.
- Original tracked inventory: 177 main source/resource files, 27 host-test files, two device-test source files, 20 Markdown files, 17 other build/tool files, two APKs and 75 raw screenshots. No tracked build/IDE/log/temp output in that inventory. Source includes variant-specific configuration in the build/tool category.
- Source/test/resources/Gradle wrapper, device-safety rules and historical incident retained. The original branded design is retained privately, not in the independent public candidate.
- Current implementation, architecture, actionable open validation work and sanitized engineering reports replace mixed task journals.
- English-first README, standard Apache-2.0 LICENSE, NOTICE, changelog, contributing guide and issue templates added.

## History cleanup

Completed in a separate mirror using git-filter-repo 2.47.0. Retired `tasks/` and agent-specific `docs/superpowers/` paths were removed across public history, after moving durable conclusions to public docs. Exact device identifier/private host paths were generalized; all original author/committer emails were rewritten to the owner's explicitly supplied `249264302+FPhoenix28@users.noreply.github.com`.

All 14 original commits plus the cleanup commit were retained in order (15 at the rewrite checkpoint); no branch/tag was discarded (public remote has master only, no tags). Local Codex tree/checkpoint and remote-tracking refs are preserved in the private original backup, not published as project branches. Rewritten mirror contains only `refs/heads/master`.

Validation inspected all 434 reachable file blobs and complete author/committer metadata: no removed artifact path, old personal identifier, host absolute path or obvious credential candidate remained. All 77 old raw-image/APK blob objects were absent from the new reachable object set. `git fsck --full` passed. Product/build/device-safety trees were compared for **every mapped commit**, not only HEAD, and were unchanged. The full current tree was identical before/after rewrite. Repacked public history at this checkpoint was 937 objects / 1.81 MiB; size alone was not used as proof.

The exact private `FPhoenix28/TimeManager` remote was rechecked immediately before update: master only, no tags, only the owner listed as collaborator. Master was updated using an explicit expected-old-tip `--force-with-lease`, not a mirror push. Visibility was not changed and no Release/tag was created. The working repository was aligned to the identical rewritten tree; old internal checkpoint refs were removed only after their complete external backup was verified. Original unreachable objects were not manually pruned from the original working directory. Subsequent documentation-only commits increase commit/object counts without changing the validated product tree.

**Historical reason for migration:** the original repository still returned an old pre-cleanup object with its former personal author email after history rewriting. That original repository has now been renamed and remains permanently private pending the owner's later decision. It is not the new independent candidate described below. No old object ID or personal address is reproduced in this public report.

## Public repository migration

- Old repository: `FPhoenix28/TimeManager-private-archive`, ID **1338334318**, Private, not GitHub-archived/deleted/transferred. Rename preserved master at the approved cleaned baseline.
- New repository: `FPhoenix28/TimeManager`, ID **1418544365**, independently created empty and **Private pending owner review**. No fork/template import, initialized README/license/gitignore, wiki, release or tag was created.
- Candidate came from a fresh, single-branch archive clone at the approved baseline. Initial all-ref audit: 439 file blobs, noreply author/committer metadata, no old personal identifier, raw artifacts or known pre-cleanup commit in its reachable object set.
- The branded design image was removed from candidate history using git-filter-repo; all product/build/safety files remain identical. Documentation/image attribution was updated. The previous private repository's cached objects are not copied as candidate refs.
- Only `master` was pushed normally from the isolated candidate; no mirror, backup/archive refs, tags or releases were pushed. The first-push/build revision is `e980d4b79f0958f5f7453f88f05fdf90b9be4449`; subsequent completion-report changes are documentation-only.
- Authenticated GitHub checks confirmed distinct repository IDs, both Private, default `master`, and only `master` / zero tags / zero releases in the new repository. The new repository lists only the owner as collaborator.
- **Old private archive:** the known pre-cleanup commit still returns HTTP **200**. Its old objects are not claimed purged, and it must remain private.
- **New independent public candidate:** that same pre-cleanup commit returns HTTP **404**, with the current new commit returning HTTP 200 as a positive control. This verifies isolation under the new repository identity, not deletion from the old server cache.
- Server tree review covered 247 files and all 18 commits at the build revision; author/committer addresses are the approved noreply address. The independent clone's complete history scan inspected **445 reachable file blobs**, with no removed raw artifacts, branded design image, old private identifiers, host paths or obvious credential candidates. `git fsck --full`, local Markdown links and product/build/safety-tree comparisons passed.
- The second fresh clone from the NEW repository passed tests, Debug/Release builds and migration checks as recorded below. Both repositories remain Private pending owner review; no visibility change or old-repository deletion was performed.

## Privacy audit

Initial complete reachable-object scan inspected 518 file blobs, including commit snapshots/internal local refs. Findings: two APKs, raw device images, exact device serial in nine text paths, host absolute paths in ten, and personal email in author/committer metadata. No obvious API credential/private-key/IP/phone-number candidate was found by the text patterns. Source `this@Class.method` syntax produced false email candidates and is not personal contact information.

The scan is evidence, not a guarantee about arbitrary unknown secrets. All raw device images are excluded regardless of their visible contents. The former illustrative branded mockup has also been excluded from candidate history. Diagnostic strings in source are not runtime log dumps; session bypass tokens are business-state variables, not credentials. Backup/mirror material containing originals remains private and must never be published. The existing external backup was only checked for existence/access during this migration and was not modified.

## Files removed from Git

- Two tracked APKs under the retired device-testing tree.
- 75 raw PNGs, including invalid and personal-data-bearing captures.
- Retired task logs and old agent-specific plans; public conclusions and durable architecture are retained in new documents.
- The device-bound screenshot script is replaced by a parameterized reusable host tool with no hardcoded device or SDK path.
- The original branded documentation PNG is absent from the independent candidate's full reachable history, not merely from its latest tree.

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

### Independent NEW-repository validation

A second fresh network clone of `FPhoenix28/TimeManager` was made in `../TimeManager-public-validation-2026-10-14/` at `e980d4b79f0958f5f7453f88f05fdf90b9be4449`. Before building, Git was clean and `local.properties`, `.idea`, `.gradle` and `app/build` were absent. The SDK/runtime were supplied through the ordinary host environment; no old repository, private backup, previous project build directory, secret file or local.properties was used for this build. Normal shared dependency/toolchain installations were available.

Executed: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --no-build-cache --no-configuration-cache --rerun-tasks`.

Actual result: **BUILD SUCCESSFUL in 3m 5s; 96 actionable tasks, 96 executed.** Parsed this run's JUnit XML: **27 suites, 125 tests, 0 failures, 0 errors, 0 skipped**. `tools/Verify-TodoMigration.ps1` and `git diff --check` passed. Git stayed clean; local.properties remained absent. Debug and unsigned Release APKs exist only in ignored local build output and were not installed. Later report commits change documentation only, not the built product tree. See [final migration report](FINAL_PUBLIC_MIGRATION_REPORT.md).

## Remaining risks

- Branded documentation imagery has been removed. Launcher/template and Material-style vector attribution is documented after asset inspection; project licensing does not transfer Google's marks or substitute for final binary-distribution review.
- Android backup is currently enabled; app-local data is not claimed to be excluded from OS backup.
- Cross-vendor/permission/large-font/landscape/incoming-call validation is incomplete; see OPEN_ISSUES. No store-policy approval or production signing was performed.
- Development should use the independent candidate clone, not the old archive checkout. The original checkout's origin points to the private archive. The new repository's private status, identity, refs and owner-only collaborator list were verified before completion; publishing later remains the owner's manual decision.
- The old private archive can still retain cached/unreferenced objects; it must not be published or used as a mirror-push source. New-repository isolation is checked against that distinct repository ID and the old pre-cleanup commit. This does not claim that the old server cache was purged. [GitHub's cleanup guidance](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository) explains why rewriting an old repository alone is not a purge.

## Recommended version

Recommend **v0.9.0**, title **TimeManager v0.9.0 — intentional friction preview**. This is a suggestion, not a created tag or release; legacy app `versionName=1.0` has not been changed.

Release-notes draft: Managed app tracking and quotas; unlimited daily +15-minute extensions; progressive Session Friction and once-per-stage re-entry; Focus/Deep Focus; lightweight todos; 7-/30-day statistics; permission guidance and refreshed Compose UI. Local-first, Android backup enabled, primarily verified on vivo X200s / Android 16. Unsigned Release output requires a reviewed signing/distribution process. See known validation limits before use.

## Before making repository public

The independent candidate has passed migration isolation and new-repository fresh-build checks and remains private pending owner review. Do not upload private backups, merge old history, mirror-push the archive, publish raw captures or automatically change visibility. The old private archive is not a public release source. Review attribution notes and unresolved device validation; choose release metadata/signing separately.

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
- [x] Old repository renamed and retained Private; cached old objects there are not claimed purged
- [x] Old pre-cleanup commit is not addressable in the NEW independent TimeManager repository (authenticated HTTP 404; new-head positive control HTTP 200)
- [x] Fresh clone of the NEW independent repository passes tests/builds/migration
