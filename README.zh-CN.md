# TimeManager

[English](README.md) | **简体中文**

一个以“有意识的阻力（intentional friction）”而非强制封锁为核心理念的 Android 数字健康应用。

TimeManager 会在应用使用变得无意识或持续过久时主动制造一个短暂的停顿。你可以自行选择需要管理的应用、设置个人使用额度，并在提醒出现后决定下一步怎么做。它是一个仍在持续维护的个人项目，并不是无法绕过的家长控制或设备管理系统。

## 功能

- 管理指定应用，支持编辑每日使用额度并实时追踪使用时长。
- 当天额度可不限次数 **+15 分钟**；保存的基础额度不会因此改变。
- 每个应用都可以设置首次连续使用提醒；未设置时默认为 **10 分钟**，之后按 **+8、+8、+5 分钟**递进，再往后每 5 分钟提醒一次。
- Session Friction 连续使用干预：每个提醒阶段最多触发一次重新进入拦截；真正离开目标应用 5 分钟后会重置本轮提醒 Episode。
- 专注模式（Focus Mode）可限制选定应用；静心模式（Deep Focus）提供更强的限制，并保留必要系统应用例外和明确的提前退出流程。
- 轻量待办事项，支持首页/冷静页摘要和分阶段提醒。
- 基于 Room 的使用历史，支持系统历史回填、每日统计、7 天/30 天统计和单应用历史。
- 权限健康状态提示，并支持跟随系统的浅色/深色主题。

TimeManager 的一个“使用日”从设备当地时间 **04:00 持续到次日 04:00**。物理上的前台连续使用 Session 与提醒递进 Episode 是两个不同概念，详见[当前实现状态](docs/PROJECT_STATE.md)。

## 界面

应用采用克制的 Material 3 仪表盘设计，包括应用管理首页、单应用详情和 7/30 天统计页面。

当前仓库没有附带带品牌元素的公开展示图：此前的私有设计参考中包含第三方应用 Logo，因此已从本仓库历史中移除；原始真机测试截图同样不进入公开仓库。实际已完成的 UI 验证范围见 [UI 验证报告](docs/testing/UI_VERIFICATION.md)。

## 架构

核心干预链路：

```text
UsageStats / realtime timer
        ↓
repositories and evaluators
        ↓
process-scoped InterventionCoordinator
        ↓
overlay / Accessibility actions
```

UI 通过 ViewModel 观察状态。Preferences DataStore 保存配置和当天临时状态；Room 保存使用历史快照、待办和提醒回执；WorkManager 只负责待办提醒调度，不参与实时额度判断。显示冷静页或其他干预界面时，前台监控服务仍持续运行。

主要技术栈：

- Kotlin
- Jetpack Compose Material 3
- Coroutines / StateFlow
- Room
- Preferences DataStore
- WorkManager
- UsageStatsManager
- AccessibilityService
- Foreground Service
- WindowManager Overlay

更多信息见[架构概览](docs/architecture/OVERVIEW.md)。

## 权限说明

| 权限 / 能力 | 用途 |
| --- | --- |
| 使用情况访问权限（Usage Access） | 读取应用使用事件并识别前台应用使用情况。 |
| 悬浮窗（Display over other apps） | 在其他应用上方显示冷静页和专注模式提示。 |
| 通知（Notifications） | 显示常驻监控、额度提醒和待办提醒；Android 可能要求运行时授权。 |
| 无障碍（Accessibility） | 执行返回桌面（HOME）、检测应用重新进入，以及实现 Focus / Deep Focus 的限制能力。普通 Session Reminder 仅显示 Overlay 时并不强制要求该权限。 |
| 已安装应用可见性 | 用于选择已安装应用并读取其名称和图标。 |

所有权限均由用户根据应用内引导自行开启。

无障碍权限关闭时，普通连续使用提醒仍可降级为仅显示 Overlay；但自动返回桌面、重新进入拦截以及 Focus / Deep Focus 的完整限制能力将不可用。不同 Android 厂商的权限设置和后台行为可能存在差异。

## 隐私

当前应用源码中没有后端客户端、分析 SDK 或使用数据上传实现，Manifest 也没有申请 Internet 权限。配置、使用历史和待办事项均存储在本地。

AccessibilityService 只订阅窗口状态事件，并声明：

```text
canRetrieveWindowContent=false
```

因此不会读取屏幕内容。

**当前 Manifest 开启了 Android 系统备份。** “本地优先”并不意味着应用数据不会进入系统级备份或设备迁移。Debug 日志可能包含包名和时间信息，在分享日志或截图前请自行检查并脱敏。

本项目不宣称已经进行加密设计或正式安全审计。

## 构建

环境要求：

- 支持仓库当前 Android/Gradle 版本的 Android Studio，或 Android SDK 命令行工具。
- Android SDK Platform **37**；最低支持设备 API **26**。
- 首次构建时需要网络下载 Gradle、依赖和工具链。
- 需要可启动 Gradle 的 Java 运行时。仓库当前 daemon criteria 请求 **JDK 25**，可通过 Foojay 自动配置；如果自动配置不可用，请在本地安装对应 JDK。Java 源码兼容级别为 11，这与 Gradle daemon 使用的 JDK 版本不是同一概念。

Clone 仓库后，在 Android Studio 中打开并等待 Gradle Sync，然后选择本机 Android SDK。

命令行构建可以提供 `ANDROID_HOME`，或创建本地且不提交 Git 的 `local.properties`。构建不依赖私人签名密钥，也不依赖开发者机器上的绝对路径。

Windows：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

macOS / Linux：

```bash
sh ./gradlew
```

当前 Gradle Wrapper 没有 executable bit，因此使用 `sh ./gradlew`。

Debug 构建使用开发签名；当前 Release 构建生成的是**未签名 APK**，并不是可直接作为正式商店发行包使用的产物。仓库不会提交 APK。

当前 `versionName=1.0` 是遗留的构建元数据，并不代表已经发布 v1.0，也不构成稳定性承诺。

主机 JVM 单元测试不会向手机安装任何内容。进行真机测试前请阅读 [DEVICE_SAFETY.md](DEVICE_SAFETY.md)。尤其不要在长期使用的 vivo X200s 上运行当前默认的 `connectedDebugAndroidTest` 流程，因为其 cleanup 行为可能影响持久化的应用数据。

## 项目状态与贡献

项目主要在 vivo X200s / Android 16 上进行验证。目前不同厂商设备、完整权限撤销矩阵、大字体/横屏适配以及真实来电场景仍需要更广泛测试。

相关文档：

- [待处理事项](docs/OPEN_ISSUES.md)
- [脱敏测试报告](docs/testing/README.md)
- [更新记录](CHANGELOG.md)
- [贡献指南](CONTRIBUTING.md)

当前仓库是经过独立历史清理后的开源候选仓库；在所有者最终确认前仍保持 Private。仓库迁移本身不会发布 APK、创建 GitHub Release 或修改仓库可见性。

依赖项和视觉资源的归属说明见[第三方说明](docs/THIRD_PARTY_NOTICES.md)。

## 许可证

Copyright 2026 FPhoenix28.

项目作者自行创作的内容采用 [Apache License 2.0](LICENSE) 开源。第三方依赖、商标和其他资源仍遵循其各自的许可和权利声明。
