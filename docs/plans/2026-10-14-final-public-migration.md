# Final public repository migration plan

Execute inline, sequentially; this is repository migration, not product development. The owner explicitly approved the rename/new-private-repository flow and the optional no-image fallback.

## Constraints

Keep both repositories private. Never delete, archive or transfer the old repository. No phone operation, connected instrumentation, signing key, release, tag, product change or backup upload. The existing external backup is read-only for this task.

## Ordered tasks and acceptance

1. Verify clean local state, expected cleaned master, exact owner/private/default branch, unused archive name and fresh destination directories. Stop on mismatch.
2. Rename the old repository to `TimeManager-private-archive`; verify its unchanged repository identity, private visibility and master. Create an independent empty private `TimeManager` with no initialized files/wiki.
3. Clone only reachable archive master into a new candidate directory. Audit all reachable blobs, commit metadata, required docs and local links; the old pre-cleanup commit must be absent.
4. Review runtime/resource assets. Replace the illustrative branded image with a generic safe image if possible; otherwise remove it and its README display. Remove the original image from candidate history so the new repository does not distribute it through older commits. Preserve all product/build/safety trees.
5. Configure only the new candidate origin and push only master normally. Verify distinct repository IDs, private visibility, master-only/no-tag/no-release state and old-commit HTTP 404 specifically in the new repository.
6. Check the new server tree/commit metadata, then make a second independent clone. Run host tests, Debug/Release builds with both caches disabled, migration verification and whitespace checks; record actual XML counts.
7. Update readiness/project repository status and final migration report, push documentation normally, recheck final refs/identity/privacy and stop for owner review. No automatic Public transition.

## Completion

All new-repository isolation, privacy and fresh-build checks must pass. A failed old-commit isolation check is a stop condition, not a reason to publish anyway. Current expected prior host baseline is 125 tests; execution results, not the baseline, determine final status.
