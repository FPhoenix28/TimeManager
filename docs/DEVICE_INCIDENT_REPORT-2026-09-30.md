# DEVICE INCIDENT REPORT

日期：2026-09-30。时间均为 Asia/Shanghai（UTC+08:00）；原始 Codex/UTP UTC 时间已换算。

公开整备注：仅泛化设备序列号和主机私人路径，保留原有证据、责任、ROOT CAUSE NOT PROVEN 结论和安全边界。第 9–12 节描述事故当时的规则；当前正常测试策略以根目录 DEVICE_SAFETY.md 为准。此报告不是运行历史危险命令的授权。

## 结论与证据边界

**ROOT CAUSE NOT PROVEN。** 未保留那次实际 `pm uninstall` 调用及其成功返回，不能将推断写成已证实卸载操作。

最强候选已定位到具体路径，而非泛指“测试工具”：**11:18:23 发起的 `:app:connectedDebugAndroidTest`，11:21:01 被取消后进入 UTP `AndroidTestApkInstallerPlugin.afterAll`，该版本默认会卸载目标 APK，且不保留数据。** 日志确认进入该插件的收尾阶段并发生取消；包随后消失。具体卸载成功与精确执行时刻仍缺直接日志。

已确认的流程失误：我在未检查真实设备 cleanup 行为、未解释数据影响、未取得针对隐式卸载的批准时，运行了 connected instrumentation。不能用“没有手写 uninstall”免除这项责任。

## 1. 事故时间窗口

| 本地时间 | 已记录事件 | 证据 |
| --- | --- | --- |
| 11:13:00–11:13:10 | 查询测试包、模拟器列表与连接设备 | Codex ordinal 8800、8809 |
| 11:15:41–11:16:11 | 首次测试任务因参数解析失败，未执行测试 | ordinal 8862、8865 |
| 11:16:17–11:17:27 | 修正参数后测试成功，共 1 个测试 | ordinal 8869–8884；Gradle 日志 |
| 11:17:52、11:18:08 | 加强断言后的两次运行均因测试源码编译失败结束 | ordinal 8897、8911 |
| 11:18:23 | 再次发起相同 connected 测试 | ordinal 8925 |
| 11:18:45.567 | 真机 runner 开始目标测试，进程 PID 28184 | 保存的 logcat 第 1 行 |
| 11:20:57 | 只读进程查询仍看到 `com.example.timemanager` | ordinal 8962 |
| 11:21:01.633 | 对运行中的主机 Gradle 会话发送 Ctrl+C | ordinal 8966，session 87634 |
| 11:21:02.914 | UTP 记录 ABORTED / TEST_INTERRUPTED | test-result.textproto 第 2、10、18 行 |
| 约 11:21:02–11:21:03 | driver 取消；APK installer 插件在 onAfterAll 中被取消 | daemon 日志第 453–483 行 |
| 11:21:08 起 | 包列表、pm path、dumpsys 查询不再找到项目包 | ordinal 8973 起 |
| 11:22:15–11:22:34 | 两个 user 0 私有数据目录均报不存在 | ordinal 9036–9051 |

最后看到进程与首次发现包缺失之间约 11 秒。不能从这一窗口推出卸载精确到某一秒。第一次成功测试也存在正常收尾卸载路径，因此原有持久化数据可能在 11:17 的收尾就已受影响；该次之后没有安装/数据快照，无法确定首次数据丢失时刻。

## 2. 当时执行的测试流程

临时 `ResponsiveOverlayLayoutTest` 将现有冷静页放入固定 720dp × 360dp Compose 容器。第一版宽松断言通过；加强布局断言后修复两个编译错误，再次运行时 runner 卡住，最终取消。临时测试源码之后通过主机文件补丁移除；不是手机清理脚本。

实际模式是：**物理 vivo 设备上的 Gradle connected instrumentation → UTP → AndroidJUnitRunner**。不是 Android Studio 点击运行，也不是 managed emulator。项目 runner 在 `app/build.gradle.kts` 声明，AGP 版本来自 `gradle/libs.versions.toml`：9.3.1；Gradle daemon 版本 9.5.0。

## 3. 最终 PackageManager 状态

本轮重新只读核查的真实 applicationId：`com.example.timemanager`，来自 `app/build.gradle.kts` 的 defaultConfig。

设备唯一连接：serial `<verified-device-serial>`，product PD2415M，model V2458A（vivo X200s），Android 16。所有本轮设备查询显式使用该 serial。

| 查询 | 结果 |
| --- | --- |
| `pm path --user 0 com.example.timemanager` | 无路径 |
| `dumpsys package com.example.timemanager` | `Unable to find package` |
| `pm list packages -u --user 0 com.example.timemanager` | 无条目，包含未安装记录的查询也没有发现 |
| `pm list packages -u --user 999 com.example.timemanager` | 无条目 |
| `pm list users` | 0 机主、666 XSpace、999 应用分身 |
| user 666 包查询 | Shell 无权访问；未尝试获取额外权限 |

可证实：user 0 不是一个可查询到的 `installed=false` 残留状态，系统全局 dumpsys 也找不到该包；user 999 没有查询结果。不能将无法读取的 user 666 宣称为已完整核实。没有执行 install-existing。

## 4. 数据状态与影响范围

事故后及本轮只读 `ls -ld` 均返回：

```text
/data/user/0/com.example.timemanager: No such file or directory
/data/user_de/0/com.example.timemanager: No such file or directory
```

TimeManager 原有 user 0 私有 DataStore/Room 数据当前不可用，按已丢失处理；没有做数据恢复或读取其它 App 私有内容。没有前后数据库副本，不能报告精确丢失记录数。

本次可用执行记录未发现其它第三方 App 被卸载或手机用户文件被删除；这不等于做过全手机前后完整性对照。框架曾尝试修改 `androidx.test.services` 的 appops（失败），说明其隐式动作还超出项目 applicationId 边界。

## 5. 找到的命令与主机记录

### Gradle 完整命令

11:15:41 执行（参数解析失败）：

```powershell
$env:JAVA_HOME='<jdk-home>'; .\gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.timemanager.ui.intervention.ResponsiveOverlayLayoutTest --offline
```

后四次执行的完整命令相同，时刻分别为 11:16:17、11:17:52、11:18:08、11:18:23：

```powershell
$env:JAVA_HOME='<jdk-home>'; .\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.timemanager.ui.intervention.ResponsiveOverlayLayoutTest' --offline
```

11:21:01 对 session 87634 发送 `\u0003`（Ctrl+C）；这是主机取消动作，不是手写设备卸载命令，但不能假设取消时没有设备 cleanup。

### 本轮横屏尝试的显式 ADB 命令清单

原始调用可执行文件均为 `<android-sdk>/platform-tools/adb.exe`，当时未带 `-s`。下表列出完整 ADB 参数和主机结果筛选摘要；完整 PowerShell 筛选表达式仍可按时间/ordinal 在原始 Codex 记录中核查。这些均为查询。11:13 的 `emulator.exe -list-avds` 也只是查询。

| 时间 | 参数及主机筛选 |
| --- | --- |
| 11:13:00 | `shell pm path com.example.timemanager.test` |
| 11:13:10 | `devices` |
| 11:20:13 | `shell dumpsys activity activities`，主机 `Select-String 'topResumedActivity'`、取首条 |
| 11:20:13 | `logcat -d -t 300`，主机筛选 `ResponsiveOverlayLayoutTest\|ComposeTest\|TEST_START\|TEST_END\|FATAL EXCEPTION`、取末 25 条 |
| 11:20:56 | `shell ps -A`，主机筛选 `timemanager\|test.services\|orchestrator\|instrument` |
| 11:21:08 | `shell pm list packages`，主机筛选 `timemanager\|test` |
| 11:21:15 | `shell pm path com.example.timemanager` |
| 11:21:22 | `shell cmd package list packages -3`，主机筛选 `example\|TimeManager` |
| 11:21:28 | `shell dumpsys package com.example.timemanager`，主机筛选包/版本/user 0、取首 12 条 |
| 11:21:36 | `shell pm list packages`，主机取首 8 条 |
| 11:21:45 | `shell pm list packages --user 0`，主机筛选 `timemanager` |
| 11:21:51 | `shell pm list users` |
| 11:22:02 | `shell pm path --user 0 com.example.timemanager` |
| 11:22:07 | `shell ps -A`，主机筛选 `com.example.timemanager` |
| 11:22:15 | `shell ls -ld /data/user/0/com.example.timemanager` |
| 11:22:33 | `shell pm list packages -u --user 0`，主机筛选 `com.example.timemanager` |
| 11:22:33 | `shell ls -ld /data/user_de/0/com.example.timemanager` |
| 11:22:33 | `shell cmd package list packages --user 0 -f`，主机筛选 `com.example.timemanager` |
| 11:22:45 | `shell pm list packages --user 999`，主机筛选 `timemanager` |

未发现本事故窗口内 Codex 显式执行 adb install/install-multiple/uninstall、pm clear、force-stop、手机 rm/wipe 或 Gradle uninstall task。搜索到的关键词出现在读日志/文档等内容时，不当作已执行设备动作。

框架输出明确记录了隐式尝试：`appops set androidx.test.services MANAGE_EXTERNAL_STORAGE allow`，返回失败 `No UID ... user 0`。install 与可能的 uninstall 是框架内部设备操作，不会自动出现在上表的手写命令记录中。

### 其它主机端证据检查

- Codex JSONL：完整核查 2026-09-30 11:11–11:23 相关执行记录，本报告引用 ordinal 定位原始调用/返回。
- PowerShell PSReadLine 历史最后修改于 10:48:27，早于事故窗口；无此次 connected 任务记录。存在无时间戳的其它旧设备/媒体传输命令，不能归入本次事故。
- Android Studio `idea.log` 最后修改于 2026-09-27 10:09:19，未找到 09-30 操作；未支持“IDE 独立 runner 导致事故”的说法。
- 主机 Gradle daemon 日志保留 driver 取消与 APK installer 收尾记录；测试结果和保存的 logcat 保留最后一轮运行记录。
- 项目脚本/配置中未发现本轮自建卸载或设备清理脚本。可用临时目录未找到可重建本轮命令的 cleanup 脚本或 runner 配置；UTP 卸载由插件代码执行，不要求存在 shell 脚本。
- 当前主机 `adb.log` 不包含事故卸载记录；设备现存 events logcat 中也未找到该包的事故卸载日志。没有清空 logcat。

## 6. 已确认的工具行为

通过本机缓存的 **AGP 9.3.1 正式 sources.jar** 和对应 **UTP 32.3.1 插件字节码**，只读核实以下链路：

1. `BooleanOption.kt`：`ANDROID_TEST_LEAVE_APKS_INSTALLED_AFTER_RUN` 对应 `android.injected.androidTest.leaveApksInstalledAfterRun`，默认 **false**。
2. `DeviceProviderInstrumentTestTask.java:920–923` 将上述选项传给 KeepInstalledApks；`:239` 用 `!getKeepInstalledApks().get()` 创建 runner 的 `uninstallApksAfterTest`。
3. `UtpTestRunner.kt:106`、`UtpTestUtils.kt:127` 将此值传至 UTP；`UtpConfigFactoryKt$createApkInstallerPlugin$2` 为 **targetAppApks** 设置 `uninstallAfterTest`（字节码 379–644，尤其 627），并非只针对测试 APK。
4. `AndroidTestApkInstallerPlugin.afterAll` 检查该标志（字节码 88），从 APK 读取包名（160），调用 `AndroidDeviceControllerExtKt.uninstall$default`（206）。
5. 本机 `android-device-provider-local-0.0.9-alpha04.jar` 中该默认方法令 keepData=false；实现构造 `pm uninstall <package>`，仅 keepData=true 才添加 `-k`。

因此，对当前 target APK，该路径会构造 **`pm uninstall com.example.timemanager`（不带 -k）**。这是已核实代码将构造的命令，不是找到的那次成功执行记录。

项目及可读用户 Gradle 配置未发现 leaveApksInstalledAfterRun=true；五条实际任务命令均未设置保留参数。配置说明默认路径具备破坏性，不说明每一次具体调用都已完成。

开始阶段：框架自动安装测试与目标 APK；已观察到 runner 成功启动，未保存完整 install 参数。正常结束：默认卸载配置生效。取消阶段：daemon 明确记录 APK installer onAfterAll 被调用并取消，但缺具体卸载成功返回。不能把 onAfterAll 的取消记录当成卸载成功回执。

## 7. 最可能原因与缺失证据

**最强候选：connectedDebugAndroidTest 的默认 target APK 卸载 cleanup。** 支持证据是默认配置、目标 APK 标志、插件具体卸载实现、取消收尾记录、进程存在到包缺失的短时间窗口，以及数据目录缺失。

缺失：本次生成的 runner 最终配置、实际 `pm uninstall` 设备命令日志及返回值、PackageManager 当时删除事件、第一次成功测试后的包/数据快照。没有这些，就不能最终证明是 11:17 正常收尾还是 11:21 取消收尾首先破坏数据，也不能宣布已排除一切其它来源。

用户明确禁止设备写操作，因此没有再次运行测试复现卸载、增加真机 instrumentation 或尝试恢复；本轮只做既有记录与实际插件代码的取证。规则已落地不等于事故已通过重现证明，也不等于测试框架已完成安全修复验证。

## 8. 哪一步本应被阻止

在 11:16:17 首次实际运行 connected 测试之前，应核实目标 APK 是否保留、cleanup 是否清数据及是否修改 helper/system 状态，并展示设备/包名/命令/数据影响取得专项确认。默认卸载真实项目 App 的模式本应直接停用。

仅查询过 `adb devices`、看到唯一手机，不足以完成这个检查。Ctrl+C 也不能视为无设备影响的退出方式。

## 9. 永久规则落地

根目录新增 `DEVICE_SAFETY.md`：完整只读默认、批准流程、禁止自动操作、安装策略、工具 cleanup 核实与 Before Device Test 清单。新增 `AGENTS.md`：关键边界和必须先读安全文档的入口。

这些是持久化仓库指令，不是手机端权限隔离或命令拦截器。本轮没有增加测试工具、包装器或修改 Gradle 配置，也没有伪称“危险 cleanup 已技术禁用”。当前 runner 应视为不可用。

## 10. 以后可自动执行的操作

只读设备状态、包/用户查询、dumpsys、getprop、settings get、读取 logcat、无设置参数的 wm size/density。主机端编译和本地测试按任务范围进行，不触发设备任务。

## 11. 以后需要明确确认的操作

任何设备安装/更新、启动/停止、输入/旋转、项目权限修改、测试临时文件创建/确切删除、任何带隐式设备写操作的测试工具。先 Preflight、展示完整动作及数据影响，取得专项批准；不可自动扩大到其它包。

清 TimeManager 数据另需明确提示 DataStore/Room/配置损失，并获得“确认清空TimeManager数据”原句批准。安装失败不自动转卸载重装。

## 12. 永久禁止自行执行与停止点

不自动 uninstall、pm clear、设备文件删除、通配符/批量修改、其它包修改、wipe、root/底层恢复、清数据/卸载 cleanup。完整范围以根目录安全文档为准。

**本轮到此停止：不重装、不恢复数据、不继续横屏适配、不运行任何设备测试或写操作。** 现有业务代码和预先存在的 Statistics 改动保持原样。

## 原始证据位置

- Codex：保存在本地主机会话日志（私人路径不公开，按上述 ordinal 定位）。
- Gradle：`<gradle-user-home>/daemon/9.5.0/daemon-3856.out.log`，重点 453–486 行、505–515 行。
- UTP：`app/build/outputs/androidTest-results/connected/debug/V2458A - 16/test-result.textproto`。
- instrumentation：同目录 `testlog/test-results.log` 以及 `logcat-com.example.timemanager.ui.intervention.ResponsiveOverlayLayoutTest-dailyLimitExitButtonStaysInsideShortWideViewport.txt`。
- AGP 源码：本机 Gradle 缓存 `com.android.tools.build/gradle/9.3.1` 内 `gradle-9.3.1-sources.jar`。
- UTP 实现：本机 Gradle 缓存 `com.android.tools.utp` 中 APK installer / gradle-work-action 32.3.1，以及 `com.google.testing.platform` 的 device-provider-local 0.0.9-alpha04。

调查仅使用现存日志、源码/字节码及只读设备查询；本轮 `adb devices -l` 自动启动的是主机 ADB server，不是安装或设备数据修改。
