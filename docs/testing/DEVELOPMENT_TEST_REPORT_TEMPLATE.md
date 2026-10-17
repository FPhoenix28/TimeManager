# DEVELOPMENT_TEST_REPORT

复制到 `docs/testing/YYYY-MM-DD-<task>.md`，填写实际事实；不适用或未执行的字段写明原因。以下是模板，不是已完成测试的记录。设备 serial、原始截图/日志、APK、个人数据不写入公开报告。

## Task / source

- Task / date:
- Branch / starting commit:
- Starting worktree edits preserved:
- Changed files for this task (separate from pre-existing edits):

## Host validation

- Unit tests: command; PASS / FAIL / NOT RUN; count (or unknown); failures/errors/skips; cached vs freshly executed:
- Debug build: command; SUCCESS / FAILED / NOT RUN:
- Optional Release build check (not publication):
- `git diff --check`:

## Debug APK / device / installation

- APK metadata tool and output summary:
- Package ID: must be `com.fphoenix28.timemanager.debug` for v0.9.0+ builds.
- Display name: must be TimeManager Dev.
- testOnly: true / false / unknown (reason if unknown).
- Device model / Android version (generic identifier only):
- Install command used (redact serial/local private paths):
- Install: SUCCESS / FAILED / NOT RUN; phone confirmation and elapsed wait:
- Existing Dev data preserved: yes / no / unknown; evidence/limits:
- Permissions actually used / changed; initial and final state:
- Stable/Dev intervention isolation and agreed restoration:
- Temporary time parameters and confirmation they did not overwrite real configuration:

## Test cases actually run

| Case / precondition | Actual action | Evidence / owner package | PASS / FAIL / NOT VERIFIED |
| --- | --- | --- | --- |
| Fill with an actually executed case | Action taken, not planned | Observation, not assumption | Result |

- Passed:
- Failed and remaining cause (mark unknown / NOT PROVEN where appropriate):
- Not verified / blocked:
- Recovery/cleanup: temporary parameters/settings restored; Dev test monitoring stopped; stable monitoring restored by user or explicitly approved action; any incomplete recovery:
- Stable TimeManager touched? YES / NO; exact operation and current-task approval if YES:
- Stable TimeManager data touched? YES / NO (unknown if not established); separate configuration writes, deletion and natural usage changes:
- Destructive operations used? YES / NO; if YES, the specific prior approval:

## User acceptance / Git

- User acceptance: PENDING / ACCEPTED / REJECTED; user's explicit confirmation if accepted:
- Commit authorization/action: none / explicitly requested; no automatic commit:
- Push authorization/action: none / explicitly requested; no automatic push:
- Master integration / stable APK update: not implied; record only separately requested actions:

## Git snapshots

Include actual `git diff --stat` and `git status` output. Explain pre-existing edits and new untracked files; do not attribute the entire worktree to this task.
