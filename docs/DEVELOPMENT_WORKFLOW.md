# 日常开发与稳定化流程

本流程用于持续开发，不是发布流程。设备操作的权威边界见 [DEVICE_SAFETY](../DEVICE_SAFETY.md)；代理入口见 [AGENTS](../AGENTS.md)，贡献者摘要见 [CONTRIBUTING](../CONTRIBUTING.md)。

## 三层状态

| 层次 | 含义 | 谁决定进入下一层 |
| --- | --- | --- |
| Working tree / feature branch | 开发中的代码，可以尚不稳定；通过 TimeManager Dev 反复验证 | Codex 报告验证结果，用户体验验收 |
| master | 经过基本测试和用户验收的源码稳定基线；应可构建、可运行 | 用户决定 commit、push 和是否合入，分别授权 |
| 手机 TimeManager | 用户长期使用的已安装 APK，是真实数据环境 | 用户单独发起稳定版更新 |

master 比手机 APK 更新是正常状态。一次开发可结束于源码验收和 Git 稳定化，不必更新手机稳定版。“稳定版”是使用角色，不表示本任务已为现有 APK 完成签名或质量验收。

## 0. 开始前检查

```text
git status
git branch --show-current
git log -1 --oneline
```

识别未提交修改和当前任务边界。明显功能开发优先建议 `feat/<feature>` / `fix/<bug>`；不擅自丢弃修改或强制切分支。只修改本任务必需的文件，保持 small diff、清晰职责和现有架构。

## 1. 本地验证

代码修改按影响至少运行 `gradlew.bat :app:testDebugUnitTest` 和 `git diff --check`；准备真机验证时运行 `gradlew.bat :app:assembleDebug`。涉及双包/依赖/Release 兼容时，酌情运行 `:app:assembleRelease`，这只是构建检查，不是发布或稳定版更新。

纯文档任务可使用内容、链接和差异检查代替代码构建/单测，报告中明确“未运行：仅文档修改”。复用的旧测试结果标明时间和范围，不能冒充本轮执行。

## 2. APK 身份与参数

| 构建 | applicationId | 名称 | 日常用途 |
| --- | --- | --- | --- |
| Debug | `com.fphoenix28.timemanager.debug` | TimeManager Dev | Run、开发验证、反复更新 |
| Release identity | `com.fphoenix28.timemanager` | TimeManager | 用户另行决定安装 / 更新的日常版 |

namespace / Kotlin package 保持 `com.example.timemanager`。Debug 的 applicationIdSuffix 为 `.debug`，versionNameSuffix 为 `-dev`。Android 按应用隔离数据和权限；不要人为共享两包的 Room、DataStore、SharedPreferences 或权限状态。

v0.9.0 发布准备使用 versionCode 3、Release 0.9.0 / Debug 0.9.0-dev。正式签名可从已忽略的本地 `keystore.properties` 读取，字段见根目录示例；没有文件时 Release 仍为 unsigned，文件不完整时报错，不自动创建密钥。历史报告中的 `com.example.timemanager` 为旧身份；新身份不覆盖旧安装，也不自动迁移旧数据。

安装前用 `aapt2 dump badging <apk>`、`aapt` 或 `apkanalyzer` 核实**实际 APK**：package 必须为 Dev，label 必须为 TimeManager Dev。不能核实就停止，不能只凭 APK 文件名、源码 namespace 或旧报告推断。

Debug 默认使用产品参数。测试时间覆盖仅在 Debug source set / 显式测试配置中启用；`BuildConfig.DEBUG` 不等于自动启用测试时间，更不能把短时间写入真实额度或 Session 配置。Release source set 使用产品参数。结束前还原临时覆盖和调试残留；可复用的 Debug-only 测试 fixture 与临时修改应明确区分。

## 3. 设备隔离与安装

完整阅读 DEVICE_SAFETY.md，核实设备 serial、APK 和测试前状态，先隔离双实例干预。已有监控总开关时优先用开关；没有时告知用户并由用户决定暂停稳定版，不能默认 force-stop。当前源码尚未实现统一监控总开关，本流程不新增它。

标准 Dev 安装命令：

```text
adb -s <verified-serial> install -r -t <debug-apk>
```

`-r` 覆盖 Dev 并尽量保留其数据；`-t` 接受 Debug testOnly APK，是正常开发参数，不是卸载绕行。先提醒手机确认，至少等待一分钟且安装成功后才继续。若标准安装失败或签名冲突，停止并报告，不卸载、不清数据、不运行 Gradle uninstall task。Release 不能依赖 `-t`。

Dev 首次使用需要自己的 Usage Access、Overlay、Notification、Accessibility 等授权；OriginOS 额外的应用列表/后台运行条件也应按实际状态检查。保留已有 Dev 数据和权限，不借用稳定版授权，不自动批量修改系统或其它 App。

## 4. Dev 真机验证循环

修改源码 → JVM 测试 → Debug 构建 → APK 身份核实 → 安装 Dev → 实测 → 发现问题后继续上述循环。

测试仅操作 Dev；允许其 force-stop、重启、前后台切换、截图、旋转、日志与状态读取，详细恢复规则见 DEVICE_SAFETY.md。每轮不自动 commit/push，也不升级手机稳定版。长期使用的 vivo 默认不运行 `connectedDebugAndroidTest`；只有完整核实 runner 安全且用户明确批准后才可考虑，已知卸载/清数据 cleanup 仍禁止。

测试前确认目标确在 **Dev 自己的**管理列表、额度/Session 配置已读回。对 Overlay、通知和 HOME 结果核实 owner package / UID；两个包的文案相似或通知 ID 相同不代表结果属于 Dev。服务/通知存在也不能单独证明实时计时在增加。

结束时停止 Dev 的测试监控、恢复临时参数和记录的测试设置；稳定版按用户手动操作或本任务已批准的方式恢复。失败也走同样的收尾，不扩大到卸载、数据清理或稳定版更新。

## 5. 报告与用户验收 Gate

每次真机验证按 [DEVELOPMENT_TEST_REPORT 模板](testing/DEVELOPMENT_TEST_REPORT_TEMPLATE.md)输出报告，存为 `docs/testing/YYYY-MM-DD-<task>.md`。清楚区分 passed、failed、not verified；记录恢复状态与未解问题，公开材料使用泛化设备标识和相对路径。原始截图、APK 和私有日志留在 Git 外。

**Codex testing passed ≠ user accepted。** 报告后等待用户真实体验；只有“验收通过”“效果没问题”或上下文明确的等价确认才算验收。不把“继续测试”或安装确认当作产品验收，不把仅构建成功当作功能完成。失败/阻塞保留在开放事项中，不标成已稳定。

## 6. Git 稳定化 Gate

- 验收后可以建议一个聚焦的 commit；stage/commit 仍需用户明确要求，审查本任务文件范围，不顺手纳入无关工作区修改。
- 一个 commit 对应一个经过基本验收的功能状态。排除临时时间覆盖、调试垃圾、APK、原始截图、私有日志及个人数据；构建产物不进入源码历史。
- commit 完成不授权 push。只有明确要求 push / 上传 GitHub / 提交到远程时才推送；允许本地已有 commit 而尚未 push。
- 是否合入 master 由用户决定。大型功能推荐分支 → 测试 → 验收 → 合入；小型低风险且已验证修改可在用户授权下直接 master commit，不强制 PR。
- 历史失败保留原结论；后续修复以对应报告和用户验收为准，不能因建立流程而自动标为通过。当前验收与待验证范围见 [PROJECT_STATE](PROJECT_STATE.md)。

## 7. 手机稳定版独立节奏

源码验收、commit、可选 push/master 集成即可结束一轮开发。只有用户另行发起“把这一版同步到稳定版”“更新原版 APK”等请求，才考虑安装 / 更新 `com.fphoenix28.timemanager`，并重新按设备安全规则确认范围和数据影响。旧身份不自动获得写授权。不要把稳定版更新、发布或 GitHub Release 接到日常测试循环末尾。
