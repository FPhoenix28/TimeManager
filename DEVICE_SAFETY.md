# TimeManager Device Safety

适用范围：ADB、Android Studio/Gradle 设备任务、设备自动化、instrumentation/UI runner，以及开始、结束、失败、取消时的隐式动作。真实 vivo X200s 是长期使用的用户手机，其应用数据和用户文件均为 **PERSISTENT USER DATA**。

## 1. SAFE DEVICE TESTING BY DEFAULT

默认允许安全真机测试，不再默认只读。满足以下条件即可继续正常开发，无需每一步向用户确认：

1. 目标 serial / 型号已经核实为当前开发用 vivo X200s。
2. 包相关写操作目标严格等于实际构建配置中的 TimeManager 最终 applicationId。
3. 不清除用户数据，不影响其它 App。
4. 工具全部隐式动作同样满足上述边界。

允许自动执行：

- `adb devices`、logcat（不清空）、getprop、pm path、包/Activity/Service/Window/Display 状态检查。
- 普通主机 build、Debug APK `install -r` 覆盖更新。
- 显式启动 TimeManager Activity/Service、启动/停止或 `am force-stop` TimeManager、重启后检查恢复行为。
- TimeManager 的普通 Debug、UI、Overlay 和 Accessibility 功能验证；UI 输入限定明确的测试交互，不操作其它 App 的内容。
- 截图、横竖屏切换；记录并恢复测试前的显示设置。
- TimeManager app-specific 目录内，本轮明确创建的测试文件的读写和清理，按第 4 节执行。

无需展示每一步正常 Preflight，也不因覆盖安装、启动、force-stop、旋转或重启而暂停请求批准。主机端代码修改、编译和本地单元测试不涉及设备批准。

## 2. 内部 Preflight 与设备 / 包边界

每轮设备测试开始时内部完成；设备连接、目标或构建变体变化后重新核实：

1. 从 `app/build.gradle(.kts)` 读取真实 applicationId，包含构建变体后缀；安装 APK 的最终 ID 必须一致。
2. 执行 `adb devices -l`，核对开发用 vivo X200s 的 serial、型号、连接状态。V2458A 是已知型号记录，不替代现场核实。
3. 所有 ADB 设备命令显式使用 `adb -s <已核实serial>`。存在多个设备时只在目标能够明确核实时继续；不能确定目标则停止，不使用默认设备。
4. 通过 `pm path` / `dumpsys package` 核实 TimeManager 安装状态。
5. 检查动作及工具的隐式行为：正常测试不包含卸载、清数据、用户文件删除、其它 App 修改或批量包操作。

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

通过内部 Preflight 后可直接执行 `adb -s <serial> install -r <明确TimeManager APK路径>`，预计保留应用数据。普通 `am start`、启动/停止 Service、`am force-stop <实际applicationId>` 和重启测试也无需额外确认。

覆盖安装会替换程序并可能重启进程，内存中的临时状态可能丢失；它不是用户数据备份。

`install -r` 失败时报告实际错误并停止；不自动 fallback 到 uninstall → install、clear-data，也不改签名或使用更高风险参数绕过失败。

若任务要求“成功保留新版，失败还原测试前版本”，先在主机保留测试前 APK 并确认备份完整，再更新。测试通过后覆盖正常参数 APK，失败则使用原 APK 覆盖回退；回退失败同样报告并停止，不卸载、不清数据。APK 回退不等于恢复测试期间已变化的业务数据。

## 6. 自动测试与 instrumentation cleanup

允许普通自动流程：**build → install -r → launch / UI验证 / rotate → logcat / dumpsys / screenshot**。正常结束、失败、取消均不自动卸载 APK 或清数据。

使用设备测试框架前，核实其实际版本的开始、正常结束、失败和取消路径中的 install、uninstall、clear-data、helper、权限/系统设置修改；不能仅凭任务名或“官方工具”判断安全。

事故复盘中的本地核查：AGP 9.3.1 默认 `android.injected.androidTest.leaveApksInstalledAfterRun=false`，目标 APK 被列为收尾卸载对象。因此这台长期使用的 vivo X200s **禁止运行当前默认 `connectedDebugAndroidTest`，以及任何已知会卸载目标 APK / 清应用数据的 instrumentation cleanup 流程**。

单独设置保留 APK 参数不足以证明整个 runner 安全。未经完整核实的 runner 不运行，不通过真机试运行探测是否会清数据。已核实无破坏性 cleanup 的自动测试可按设备 / 包边界执行，但新增测试/helper 包仍需独立明确授权。

## 7. Before Device Test：内部清单

- [ ] serial / vivo X200s 型号 / 连接状态明确，ADB 显式锁定目标。
- [ ] 最终 applicationId 来自实际构建配置，APK / 包写操作目标一致。
- [ ] 已核实安装状态。
- [ ] 正常流程没有 uninstall、clear-data、用户文件删除、其它 App 或批量包修改。
- [ ] 工具隐式动作和结束 / 失败 / 取消路径符合上述边界。
- [ ] 需要临时设置或测试文件时，记录原值 / 确切路径及恢复范围。
- [ ] 需要版本回退时，已保留完整的测试前 APK。
- [ ] 只有进入破坏性范围时才暂停，展示 COMMAND / TARGET / DATA IMPACT 并取得具体批准。

清单通过后直接进行安全测试；不要求用户逐项确认。

## 8. 历史事故与当前策略

保留 [2026-09-30 事故报告](docs/DEVICE_INCIDENT_REPORT-2026-09-30.md) 作为历史记录，ROOT CAUSE NOT PROVEN 的结论不变，默认 connected 测试 cleanup 仍为最强候选。

用户已调整策略为 SAFE DEVICE TESTING BY DEFAULT。历史暂停和“所有设备写操作逐次批准”不再是当前规则；日常安全测试按本文件直接执行，破坏性边界继续保留。
