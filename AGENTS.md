# 项目协作规则

我们不是写安全论文。保持实现和校验与任务风险相称，避免过度防御、反复罗列极小概率情况；不生成散列校验或相关算法。

## 项目状态与文档

了解当前实现时先读 [PROJECT_STATE.md](docs/PROJECT_STATE.md)，待验证事项见 [OPEN_ISSUES.md](docs/OPEN_ISSUES.md)。新计划放在 `docs/plans/`，可公开测试结论放在 `docs/testing/`；`tasks/` 已退休，仅保留本地材料。提交前检查日志、截图和报告，使用泛化设备标识及相对路径；APK、原始真机截图和个人数据留在 Git 之外。

## 真机操作：SAFE DEVICE TESTING BY DEFAULT

日常完整流程和验收/Git Gate 见下文；设备操作的具体边界仍以 DEVICE_SAFETY.md 为准。

涉及 ADB、Android Studio 真机运行、设备自动化、instrumentation 或测试 cleanup 前，完整阅读 [DEVICE_SAFETY.md](DEVICE_SAFETY.md)。该文件是设备操作分类、内部 Preflight 和破坏性操作批准流程的权威规则。

- **正常测试使用 Dev 包**：Android Studio / Codex 默认只测试 `com.fphoenix28.timemanager.debug`（TimeManager Dev）。核实 vivo X200s 和最终 applicationId 后，可直接 `install -r -t`、启动/停止或 force-stop Dev、测试 Activity/Service/Overlay/Accessibility、旋转、截图、读取状态和日志，以及操作 Dev 本轮明确创建的 app-specific 测试文件。无需逐项请求确认；安装时提醒用户处理 USB 提示，至少等待一分钟且确认成功后再操作。
- **稳定版不是实验环境**：`com.fphoenix28.timemanager`（TimeManager）的安装、恢复、暂停、force-stop、权限或数据写操作，须有当前任务中的明确授权；不因旧任务许可自动延用。旧开发包 `com.example.timemanager` 及其 Debug 包是独立安装，不属于新身份的默认测试授权，也不自动迁移或删除。是否升级稳定版由用户单独发起。Release 不得依赖 `-t`；Debug 的 `-t` 是正常 testOnly 安装参数，不关闭 Android Studio 的 testOnly 行为。
- **LOCK DEVICE SERIAL / EXACT APPLICATION ID**：ADB 操作显式指定已核实 serial；包相关写操作仅限项目最终 applicationId。设备或包名不确定时停止，不能从应用显示名称猜测，也不能默认授权测试/helper 包。
- **日常测试硬边界**：NO AUTO UNINSTALL、NO AUTO PM CLEAR、NO USER DATA DELETE、NO BULK PACKAGE MODIFICATION、NO OTHER APP MODIFICATION、NO DESTRUCTIVE FALLBACK。手机用户数据是 PERSISTENT USER DATA。
- **破坏性操作先批准**：卸载、清数据、用户文件删除、wipe、批量包/权限修改或其它 App 写操作不属于正常测试授权。先停止并展示 COMMAND、TARGET、DATA IMPACT，取得针对该操作的明确批准；普通“继续测试”不适用。清空 TimeManager 数据仍需明确回复“确认清空TimeManager数据”。
- **覆盖安装失败即停止**：报告错误，不自动卸载重装、清数据或升级为破坏性重试。测试结束、失败或取消也不得触发 APK 卸载/数据清理。
- **核实测试框架的隐式动作**：这台长期使用的 vivo X200s 不运行任何已知会卸载目标 APK 或清数据的 instrumentation cleanup；当前默认 `connectedDebugAndroidTest` 属于禁用流程。仅在完整核实 runner 不卸载/清数据且用户明确批准后才可考虑该流程。普通 build → Dev install -r -t → launch → logcat/rotate/UI 验证可以自动进行。
- **可逆设置要恢复**：记录本轮临时显示/测试设置，结束时恢复；不把 Debug 参数写入真实用户配置，也不清理既有应用数据或整个用户目录。

## Default local development and device-testing workflow

日常开发遵循 [DEVELOPMENT_WORKFLOW.md](docs/DEVELOPMENT_WORKFLOW.md)，这是开发流程，不是发布流程。

1. 开始先执行 `git status`、`git branch --show-current`、`git log -1 --oneline`，保留无关和未提交修改。明显功能开发优先 `feat/*` / `fix/*` 分支，不为换分支丢弃工作区。
2. 只改本任务所需文件。代码任务按影响运行 `:app:testDebugUnitTest`、`git diff --check` 和 `:app:assembleDebug`；必要的 `:app:assembleRelease` 只是构建检查。纯文档任务检查内容、链接与差异，明确测试未运行。
3. 安装前用 SDK 工具核实 APK 的包名为 `com.fphoenix28.timemanager.debug`、名称为 TimeManager Dev；无法核实就停止。正常开发安装为 `adb -s <verified-serial> install -r -t <debug-apk>`，身份和失败处理见设备规则。
4. 真机测试前按 DEVICE_SAFETY.md 隔离双实例干预，不能仅凭页面文案把稳定版弹窗当作 Dev 结果。测试后用 [DEVELOPMENT_TEST_REPORT 模板](docs/testing/DEVELOPMENT_TEST_REPORT_TEMPLATE.md)报告实测、失败和未验证项。
5. **用户验收是独立 Gate**：Codex 测试通过不等于用户验收。交付测试报告和可体验的 Dev，等待用户确认效果；失败或阻塞不标为验收通过。
6. **Git 操作分开授权**：验收后可建议稳定化；只有用户明确要求才 stage/commit，只有明确要求 push/上传远程才 push。commit 不自动授权 push，也不自动合入 master。提交前去掉临时覆盖参数、调试残留及私有产物。
7. `master` 是已基本测试、用户验收的源码基线；小型低风险变更不强制 PR，大功能推荐分支 → 测试 → 验收 → 用户决定合入。手机 TimeManager 是另一条更新节奏，master 更新不授权升级手机稳定版。

## 历史事故记录

保留 [2026-09-30 事故报告](docs/DEVICE_INCIDENT_REPORT-2026-09-30.md)，结论仍为 ROOT CAUSE NOT PROVEN，默认 connected 测试 cleanup 是最强候选。历史暂停记录不再作为正常开发测试的持续阻塞；当前执行策略以以上规则和 DEVICE_SAFETY.md 为准。
