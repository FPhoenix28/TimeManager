<div align="center">

# ⏳ TimeManager

**不是把应用锁死，而是在你刷太久的时候，提醒你重新做一次选择。**

一个开源的 Android 数字健康工具，帮助你减少无意识刷手机。

[English](README.md) · **简体中文**

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/License-Apache--2.0-blue)
![Open Source](https://img.shields.io/badge/Open%20Source-Yes-brightgreen)

</div>

---

## 它是干什么的？

你有没有过这种情况：

> 本来只是想打开一个 App 看两分钟，回过神来，半个小时已经过去了。

TimeManager 想解决的就是这件事。

它不会简单粗暴地把应用彻底封死，而是会在你**用太久、刷太久、停不下来**的时候，主动制造一个短暂的“冷静时间”，让你重新决定：

**继续用，还是先退出？**

一个典型的使用过程是：

**选择想管理的 App → 设置每日额度 / 连续使用提醒 → 使用过久 → 短暂冷静 → 重新选择**

---

## ✨ 你可以用它做什么？

### ⏱️ 给 App 设置每日使用额度

比如给某个娱乐 App 设置每天 2 小时。

达到额度后，TimeManager 会提醒你停下来。如果今天确实还想继续，可以临时 **+15 分钟**，不会偷偷修改你原本设置的每日额度。

### 🧊 打断“无意识一直刷”

你可以给每个 App 设置“连续使用多久后提醒”。

默认第一次提醒是 **10 分钟**。如果你继续使用，提醒会逐渐变得更频繁。

当完整干预能力开启时，TimeManager 会先把你带回桌面，并给你几秒钟冷静一下。再次进入这个 App 时，还会再给你一次短暂的思考机会。

它的目的不是禁止你继续，而是让“继续刷”从一个下意识动作，变成一次主动选择。

### 🎯 专注模式

选择一段时间和一组容易让你分心的 App。

专注期间，打开这些 App 会被拦下，适合学习、写作、做题或者工作。

### 🌙 静心模式

比普通专注更强的一种模式。

开启后会尽量阻止你进入普通娱乐 App，同时保留锁屏、输入法、电话、系统必要界面等基本功能。

提前退出需要长按确认，避免“一秒破功”。

### ✅ 顺手记待办

TimeManager 内置了一个很轻量的 Todo。

它不是要替代专业待办软件，而是让你在被提醒“别刷了”的时候，顺便看到：

> 我今天原本打算做什么？

待办也会在一天中的几个时间点进行提醒。

### 📊 看看时间都去哪了

可以查看：

- 今天用了多久
- 每个受管理 App 用了多久
- 最近 7 天 / 30 天趋势
- 单个 App 的历史使用情况

TimeManager 把每天的分界线设在 **凌晨 4 点**，所以熬夜到凌晨两三点不会突然被算成“新的一天”。

---

## 💡 为什么不直接把 App 锁死？

因为 TimeManager 的设计目标不是“替你做决定”。

很多时候，我们并不是真的不能用某个 App，而是：

- 本来只想看一眼
- 手已经习惯性地点开了
- 刷着刷着忘了时间
- 明明想退出，又顺手点了回来

所以 TimeManager 更关注的是：

> **在下意识行为和继续使用之间，插入一点阻力。**

你依然可以继续，但需要先意识到：

**“我现在真的还想继续吗？”**

这也是 TimeManager 和单纯 App Lock 最大的区别。

---

## 🔐 隐私

TimeManager 目前是一个**本地优先**的应用：

- 没有后端服务器客户端
- 没有统计分析 SDK
- 没有实现使用数据上传
- Manifest 没有申请 Internet 权限
- 使用历史、设置和待办默认保存在设备本地
- 无障碍服务不会读取屏幕内容（`canRetrieveWindowContent=false`）

需要说明的是：当前应用仍开启 Android 系统备份，所以部分本地数据可能进入系统备份或设备迁移流程。

---

## 🔑 为什么需要这些权限？

| 权限 | 用来做什么 |
| --- | --- |
| **使用情况访问权限** | 判断你正在使用哪个 App，以及使用了多久 |
| **悬浮窗** | 在其他 App 上方显示冷静页和提醒 |
| **通知** | 显示实时计时、额度提醒和待办提醒 |
| **无障碍** | 在需要时返回桌面、识别你重新进入 App，并实现专注/静心模式 |
| **已安装应用信息** | 让你从手机里的 App 中选择需要管理的应用 |

所有权限都需要你自己开启。

如果不开无障碍，基础的连续使用提醒仍然可以工作，但“自动退出、重新进入拦截、专注模式、静心模式”等能力会不完整。

---

## 📱 目前的状态

TimeManager 已经开源，核心功能可以正常使用，目前主要在 **vivo X200s / Android 16** 上进行开发和验证。

当前仍有一些需要继续验证的场景，例如：

- 更多 Android 厂商设备
- 大字体 / 横屏
- 权限中途被关闭
- 真实来电时的静心模式行为

👉 [查看当前已知问题](docs/OPEN_ISSUES.md)

目前仓库**还没有提供正式签名、可直接安装的 Release APK**。如果你不是 Android 开发者，可以先关注后续 Release。

---

<details>
<summary><strong>🛠️ 开发者信息：技术栈、构建与架构</strong></summary>

### 技术栈

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

### 大致架构

```text
使用情况 / 实时计时
        ↓
业务规则与状态
        ↓
InterventionCoordinator
        ↓
提醒页 / 悬浮窗 / 无障碍操作
```

更完整的实现说明见：

- [项目当前状态](docs/PROJECT_STATE.md)
- [架构概览](docs/architecture/OVERVIEW.md)
- [测试报告](docs/testing/README.md)

### 构建要求

- Android SDK Platform **37**
- 最低支持 API **26**
- 当前 Gradle daemon 使用 **JDK 25**
- Java 源码兼容级别为 11

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

当前 Release 构建生成的是**未签名 APK**。

主机单元测试不会操作手机。进行真机测试前请阅读 [DEVICE_SAFETY.md](DEVICE_SAFETY.md)。

</details>

---

## 🤝 参与项目

Bug、想法和改进建议都欢迎通过 GitHub Issues 提出。

- [贡献指南](CONTRIBUTING.md)
- [更新记录](CHANGELOG.md)
- [第三方资源说明](docs/THIRD_PARTY_NOTICES.md)

## 🤖 开发说明

TimeManager 由 **FPhoenix28** 发起、设计并维护。

开发过程中使用 **OpenAI Codex** 和 **ChatGPT** 辅助代码实现、调试、代码审查与文档整理。项目的产品方向、功能取舍、测试验证，以及最终合并到仓库中的修改，均由项目维护者负责。

## 📄 License

Copyright 2026 FPhoenix28.

项目作者自行创作的内容采用 [Apache License 2.0](LICENSE) 开源。第三方依赖、商标和资源仍遵循各自的许可与权利声明。
