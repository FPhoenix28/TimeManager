# Final public repository migration plan

Execute inline, sequentially; this is repository migration, not product development. The owner explicitly approved the rename/new-private-repository flow and the optional no-image fallback.

## Constraints

Keep both repositories private. Never delete, archive or transfer the old repository. No phone operation, connected instrumentation, signing key, release, tag, product change or backup upload. The existing external backup is read-only for this task.

## Ordered tasks and acceptance

- [x] Verify clean local state, expected cleaned master, exact owner/private/default branch, unused archive name and fresh destination directories. Stop on mismatch.
- [x] Rename the old repository to `TimeManager-private-archive`; verify its unchanged repository identity, private visibility and master. Create an independent empty private `TimeManager` with no initialized files/wiki.
- [x] Clone only reachable archive master into a new candidate directory. Audit all reachable blobs, commit metadata, required docs and local links; the old pre-cleanup commit must be absent.
- [x] Review runtime/resource assets. Generic illustration generation failed; used the approved removal fallback and removed the original image from candidate history. Product/build/safety trees preserved.
- [x] Configure only the new candidate origin and push only master normally. Verify distinct repository IDs, private visibility, master-only/no-tag/no-release state and old-commit HTTP 404 specifically in the new repository.
- [x] Check the new server tree/commit metadata, then make a second independent clone. Actual result: 27 suites / 125 tests, zero failures/errors/skips, Debug/Release builds and migration passed; caches disabled.
- [x] Update readiness/project repository status and final migration report; final documentation is pushed normally and verified against the same private repository identity before owner handoff. No automatic Public transition.

## Completion

All new-repository isolation, privacy and fresh-build checks must pass. A failed old-commit isolation check is a stop condition, not a reason to publish anyway. Current expected prior host baseline is 125 tests; execution results, not the baseline, determine final status.
