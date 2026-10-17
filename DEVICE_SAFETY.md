# TimeManager Device Safety

适用范围：ADB、Android Studio/Gradle 设备任务、设备自动化、instrumentation/UI runner，以及开始、结束、失败、取消时的隐式动作。真实 vivo X200s 是长期使用的用户手机，其应用数据和用户文件均为 **PERSISTENT USER DATA**。

## 1. SAFE DEVICE TESTING BY DEFAULT

默认允许安全真机测试，不再默认只读。满足以下条件即可继续正常开发，无需每一步向用户确认：

1. 目标 serial / 型号已经核实为当前开发用 vivo X200s。
2. 日常包相关写操作目标严格等于 Debug 构建的最终 applicationId：`com.example.timemanager.debug`（TimeManager Dev）。
3. 不清除用户数据，不影响其它 App。
4. 工具全部隐式动作同样满足上述边界。

允许自动执行：

- `adb devices`、logcat（不清空）、getprop、pm path、包/Activity/Service/Window/Display 状态检查。
- 普通主机 build、Debug APK `install -r -t` 安装或覆盖更新。
- 显式启动 Dev Activity/Service、启动/停止或 `am force-stop com.example.timemanager.debug`、重启后检查恢复行为。
- TimeManager 的普通 Debug、UI、Overlay 和 Accessibility 功能验证；UI 输入限定明确的测试交互，不操作其它 App 的内容。
- 截图、横竖屏切换；记录并恢复测试前的显示设置。
- TimeManager app-specific 目录内，本轮明确创建的测试文件的读写和清理，按第 4 节执行。

无需展示每一步正常 Preflight，也不因覆盖安装、启动、force-stop、旋转或重启而暂停请求批准。主机端代码修改、编译和本地单元测试不涉及设备批准。

## 1.1 Debug package vs stable package safety boundary

| 角色 | 应用 | applicationId | 默认用途 |
| --- | --- | --- | --- |
| 开发测试版 | TimeManager Dev | `com.example.timemanager.debug` | Android Studio Run、Codex 测试、可重复覆盖安装 |
| 手机日常稳定版 | TimeManager | `com.example.timemanager` | 用户长期使用，不是默认实验环境 |

两个包的数据、Usage Access、Overlay、通知、Accessibility 授权各自独立。Dev 首次安装单独授权，后续 `install -r -t` 尽量保留 Dev 自身状态；不能借用或重置稳定版权限来完成 Dev 测试。

稳定版的安装、恢复、force-stop、暂停、权限修改及数据库/DataStore/SharedPreferences/Room 等写操作，需要用户在**当前任务**明确批准；普通“继续测试”不包含这些操作，旧任务许可不自动延用。即使可逆也优先避免。卸载、清数据等还须遵守第 3 节的具体操作批准要求。

稳定源码和手机 APK 不必同步。只有用户另行要求“同步到稳定版”“更新原版 APK”等时，才进入稳定版更新；测试成功、用户验收、commit、push 或 master 更新均不构成该请求。本文件不规定发布流程，也不为现有手机 APK 自动换签名或升级。

## 1.2 双实例干预隔离

- 测试前确认两个包不会同时对目标应用进行干预。已有可用监控总开关时，优先由用户暂停稳定版（Monitoring OFF），Dev 开启；测试结束 Dev 关闭，稳定版恢复此前运行状态。只有当前任务明确授权时，Codex 才操作稳定版开关。
- **没有监控总开关时，不默认 force-stop 稳定版。** 告知用户双实例可能干扰，由用户决定手动暂停或另行授权具体隔离动作；未隔离则暂停冲突测试，不把原版当测试替身。
- 记录监控状态及恢复方式。结束后优先用 Dev 开关停止测试监控；没有开关可停止 Dev 自身，不自动清理数据。稳定版由用户恢复，或仅按本任务明确批准的恢复动作处理。
- 对弹窗、HOME、通知等结果确认实际 owner package / UID 属于 Dev；名称或相同 Notification ID 不足以区分实例。无法归属时记为未验证，而不是通过。

## 2. 内部 Preflight 与设备 / 包边界

每轮设备测试开始时内部完成；设备连接、目标或构建变体变化后重新核实：

1. 从 `app/build.gradle(.kts)` 读取最终 applicationId；用 `aapt2` / `aapt` / `apkanalyzer` 等 SDK 工具核实待装 APK 的 package=`com.example.timemanager.debug`、label=TimeManager Dev。工具不可用、无法确认或不一致时停止，不能凭文件名或 namespace 猜测。
2. 执行 `adb devices -l`，核对开发用 vivo X200s 的 serial、型号、连接状态。V2458A 是已知型号记录，不替代现场核实。
3. 所有 ADB 设备命令显式使用 `adb -s <已核实serial>`。存在多个设备时只在目标能够明确核实时继续；不能确定目标则停止，不使用默认设备。
4. 通过 `pm path` / `dumpsys package` 核实 TimeManager 安装状态。
5. 检查动作及工具的隐式行为：正常测试不包含卸载、清数据、用户文件删除、其它 App 修改或批量包操作。
6. 按第 1.2 节确认双实例隔离、结果归属及测试结束后的恢复方式。

目标设备、包名或工具行为不确定时先停止并查明。`.test`、orchestrator、helper 等包不等于项目 applicationId，不因“测试需要”默认获准。

## 3. Destructive Device Operation：先明确批准

以下不属于默认授权，执行前必须展示 **COMMAND、TARGET、DATA IMPACT**，并取得用户对该具体操作的明确批准：

- `adb uninstall`、`pm uninstall`（含 `--user`）、Gradle uninstall task。
- `pm clear`、`cmd package clear`、任何 clear-data。
- 会卸载 APK 或清数据的测试 framework cleanup；这台 vivo 的已知破坏性 instrumentation 流程另受第 6 节禁用规则约束。
- `rm -rf`、`find ... -delete`、递归/批量删除、删除用户目录，以及删除 `/sdcard`、`/storage/emulated`、DCIM、Downloads、Documents 等用户内容。
- factory reset、wipe、fastboot erase、recovery wipe 或底层数据恢复操作。
- 批量 package uninstall / clear / permission modification。
- 针对其它 App 的写操作，包括 force-stop、disable/enable、修改权限或配置。

本项目正常测试坚持：**NO AUTO UNINSTALL / NO AUTO PM CLEAR / NO USER DATA DELETE / NO BULK PACKAGE MODIFICATION / NO OTHER APP MODIFICATION / NO DESTRUCTIVE FALLBACK**。其它 App 写操作若由用户另行明确要求，须作为独立目标专项批准，不能从 TimeManager 测试授权推断。

批准只适用于已说明的确切操作。失败、取消或“继续测试”不能扩大批准范围。设备修改不使用通配符或查询生成的批量目标。

需要清空 TimeManager 数据时，说明会删除 DataStore、Room 历史、Managed App、额度、Focus 等本地状态；仅明确回复 **“确认清空TimeManager数据”** 才构成该动作批准。

## 4. 用户文件与 app-specific 测试文件

照片、Downloads、Documents、DCIM、Music、Movies、其它 App 文件及 TimeManager 已有业务数据，不属于默认可清理范围。

本轮测试文件可以直接操作，但必须：

- 位于 TimeManager 自己的 app-specific 目录，或 `/sdcard/Android/data/<实际applicationId>/...` 下明确的测试位置。
- 记录本轮创建的确切路径，避免覆盖既有文件。
- 结束时只清理这些明确的测试文件；不清理整个目录，不扩展到 DataStore、Room 或用户内容。

临时旋转 / Debug 设置记录原值，结束恢复；Debug 快速时间参数不得写入正式额度或 Session 配置。Accessibility 验证不得覆盖系统设置中的其它服务条目。

## 5. 安装、重启与回退

通过内部 Preflight 后可直接执行 `adb -s <serial> install -r -t <明确Debug APK路径>`，仅安装 `com.example.timemanager.debug`，预计保留已有 Dev 数据，不覆盖正式应用。`-t` 是接受 Android Studio testOnly Debug APK 的正常参数，不关闭 testOnly，也不使用 `android.injected.testOnly=false`。Dev 的普通 `am start`、启动/停止 Service、force-stop 和重启测试无需额外确认。

安装前提醒用户处理手机上的 USB 安装提示；自安装开始至少等待一分钟，并确认安装成功后才继续设备操作。Release APK 不使用 `-t`；若 Release 被标记为 testOnly，视为构建错误，停止排查，不尝试用 `-t` 安装。

覆盖安装会替换程序并可能重启进程，内存中的临时状态可能丢失；它不是用户数据备份。

安装失败时报告实际错误并停止；不自动 fallback 到 uninstall → install、clear-data，也不改签名或升级参数绕过失败。Debug 正常路径已包含 `-t`，无需因 testOnly 再次尝试普通 `-r`。

若旧流程只用了 `-r` 并返回 `INSTALL_FAILED_TEST_ONLY`，先重新确认确为 Dev APK，再走标准 `-r -t` 路径；这不是卸载/清数据授权。`-r -t` 仍失败或出现签名冲突就停止，不能自行卸载旧包。Release 被标记 testOnly 仍按构建错误处理。

若当前任务明确要求 Dev 版本回退，事先确认有可用的测试前 Dev APK；不是每轮强制备份或更新稳定版。测试后恢复临时时间参数、调试改动和记录的临时设置；按约定保留新版或用旧 Dev APK 覆盖回退。回退失败同样报告并停止，不卸载、不清数据。APK 回退不等于恢复测试期间已变化的业务数据。

## 6. 自动测试与 instrumentation cleanup

允许普通自动流程：**build → Dev install -r -t → launch / UI验证 / rotate → logcat / dumpsys / screenshot**。正常结束、失败、取消均不自动卸载 APK 或清数据。

使用设备测试框架前，核实其实际版本的开始、正常结束、失败和取消路径中的 install、uninstall、clear-data、helper、权限/系统设置修改；不能仅凭任务名或“官方工具”判断安全。

事故复盘中的本地核查：AGP 9.3.1 默认 `android.injected.androidTest.leaveApksInstalledAfterRun=false`，目标 APK 被列为收尾卸载对象。因此这台长期使用的 vivo X200s **禁止运行当前默认 `connectedDebugAndroidTest`，以及任何已知会卸载目标 APK / 清应用数据的 instrumentation cleanup 流程**。

单独设置保留 APK 参数不足以证明整个 runner 安全。未经完整核实的 runner 不运行，不通过真机试运行探测是否会清数据。`connectedDebugAndroidTest` 只有完整核实 runner 不卸载/清数据且用户明确批准后才可考虑；已知破坏性 cleanup 仍禁止。新增测试/helper 包仍需独立明确授权。

## 7. Before Device Test：内部清单

- [ ] serial / vivo X200s 型号 / 连接状态明确，ADB 显式锁定目标。
- [ ] 最终 applicationId 来自实际构建配置，APK / 包写操作目标一致。
- [ ] SDK 工具已核实 APK 的 Dev 包名与显示名称；不能核实时停止。
- [ ] 已核实安装状态。
- [ ] 正常流程没有 uninstall、clear-data、用户文件删除、其它 App 或批量包修改。
- [ ] 工具隐式动作和结束 / 失败 / 取消路径符合上述边界。
- [ ] 需要临时设置或测试文件时，记录原值 / 确切路径及恢复范围。
- [ ] 需要版本回退时，已保留完整的测试前 APK。
- [ ] 双实例干预已隔离，实际结果属于 Dev，恢复方式已明确。
- [ ] 稳定版操作有当前任务单独授权；破坏性操作已展示 COMMAND / TARGET / DATA IMPACT 并取得具体批准。

清单通过后直接进行安全测试；不要求用户逐项确认。

## 7.1 结束与验收

结束后检查临时参数/显示设置是否恢复、Dev 测试监控是否停下、稳定版是否按已批准方式恢复；失败或无法恢复时如实报告，不扩大操作范围。每次真机验证使用 [DEVELOPMENT_TEST_REPORT 模板](docs/testing/DEVELOPMENT_TEST_REPORT_TEMPLATE.md)，只写实际执行的命令和证据，未确认的数据保留状态写 unknown。测试循环可反复进行，不触发自动 commit/push。用户体验验收与 Git 稳定化按 [开发流程](docs/DEVELOPMENT_WORKFLOW.md)分别进行。

## 8. 历史事故与当前策略

保留 [2026-09-30 事故报告](docs/DEVICE_INCIDENT_REPORT-2026-09-30.md) 作为历史记录，ROOT CAUSE NOT PROVEN 的结论不变，默认 connected 测试 cleanup 仍为最强候选。

用户已调整策略为 SAFE DEVICE TESTING BY DEFAULT。历史暂停和“所有设备写操作逐次批准”不再是当前规则；日常安全测试按本文件直接执行，破坏性边界继续保留。
