<div align="center">

# ⏳ TimeManager

**Not a hard lock. A moment to rethink before you keep scrolling.**

An open-source Android digital wellbeing app for reducing mindless phone use.

**English** · [简体中文](README.zh-CN.md)

![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/License-Apache--2.0-blue)
![Open Source](https://img.shields.io/badge/Open%20Source-Yes-brightgreen)

</div>

---

## What does TimeManager do?

You know the feeling:

> You open an app for “just a minute” — and somehow half an hour disappears.

TimeManager is built for that moment.

Instead of permanently locking apps, it creates a short pause when usage becomes too long or too automatic, then asks you to make a conscious choice:

**Keep going, or stop for now?**

A typical flow looks like this:

**Choose apps → set limits / reminders → use for too long → pause → decide again**

---

## ✨ What can it do?

### ⏱️ Daily app limits

Set a daily allowance for the apps you want to manage.

When you reach it, TimeManager asks you to stop. If you really want more time today, you can add **15 minutes** without changing your saved daily limit.

### 🧊 Interrupt endless scrolling

Set how long you want to use an app continuously before getting a reminder.

The default first reminder is **10 minutes**. If you keep using the app, later reminders gradually become more frequent.

With the full intervention features enabled, TimeManager can take you back to the Home screen, give you a few seconds to cool down, and briefly pause you again when you immediately reopen the app.

The goal is not to forbid you from continuing — it is to turn an automatic habit into a conscious decision.

### 🎯 Focus Mode

Choose a duration and a set of distracting apps.

During the session, opening those apps is blocked, making it useful for studying, writing or focused work.

### 🌙 Deep Focus

A stronger mode for times when you really want to disconnect.

It blocks ordinary apps while keeping essential system experiences such as the lock screen, keyboard, calls and necessary system UI available.

Ending it early requires a deliberate press-and-hold confirmation.

### ✅ Lightweight todos

TimeManager includes a small Todo feature.

It is not meant to replace a full task manager. Its purpose is simple: when TimeManager reminds you to stop scrolling, it can also remind you what you actually wanted to do today.

### 📊 See where your time went

View:

- today's managed-app usage
- usage by app
- 7-day and 30-day trends
- per-app history

A TimeManager “day” starts at **04:00**, so staying up past midnight does not suddenly reset everything.

---

## 💡 Why not just lock apps?

Because TimeManager is not trying to make decisions for you.

A lot of unwanted phone use looks like this:

- “I’ll just check something.”
- your hand opens the app automatically
- you lose track of time
- you leave…
- and reopen it almost immediately

TimeManager focuses on the moment between impulse and action.

> **Add a little friction before the habit continues.**

You are still allowed to continue — but first, you notice that you are choosing to.

---

## 🔐 Privacy

TimeManager is currently **local-first**:

- no backend client
- no analytics SDK
- no usage-data upload implementation
- no Internet permission in the app manifest
- settings, usage history and todos are stored locally
- Accessibility does not read screen content (`canRetrieveWindowContent=false`)

Android system backup is currently enabled, so some local data may still be included in OS-managed backup or device-transfer flows.

---

## 🔑 Why does it need permissions?

| Permission | Why |
| --- | --- |
| **Usage Access** | Know which app is being used and for how long |
| **Display over other apps** | Show pause screens and interventions |
| **Notifications** | Show live usage, quota and todo reminders |
| **Accessibility** | Return to Home, detect re-entry, and enforce Focus / Deep Focus |
| **Installed app visibility** | Let you choose which installed apps to manage |

You enable these permissions yourself.

Without Accessibility, basic session reminders can still work, but automatic Home actions, re-entry pauses, Focus and Deep Focus are limited.

---

## 📱 Project status

TimeManager is open source and its core features are usable. Development and device testing have primarily been done on **vivo X200s / Android 16**.

Some areas still need broader validation, including:

- more Android vendors
- large text / landscape layouts
- permissions being revoked while running
- real incoming-call behavior during Deep Focus

👉 [See known open issues](docs/OPEN_ISSUES.md)

This repository **does not include a signed, ready-to-install Release APK**. For now, developers can build the project from source; non-developers may prefer to wait for a future Release.

---

<details>
<summary><strong>🛠️ Developer details: stack, build and architecture</strong></summary>

### Tech stack

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

### High-level architecture

```text
Usage data / realtime timer
        ↓
business rules and state
        ↓
InterventionCoordinator
        ↓
pause UI / overlays / Accessibility actions
```

More details:

- [Current project state](docs/PROJECT_STATE.md)
- [Architecture overview](docs/architecture/OVERVIEW.md)
- [Testing reports](docs/testing/README.md)

### Build

Requirements:

- Android SDK Platform **37**
- Minimum device API **26**
- Current Gradle daemon: **JDK 25**
- Java source compatibility: 11

Windows:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

macOS / Linux:

```bash
sh ./gradlew
```

The current Release build produces an **unsigned APK**.

Host unit tests do not install anything on a phone. Read [DEVICE_SAFETY.md](DEVICE_SAFETY.md) before device testing.

</details>

---

## 🤝 Contributing

Bug reports, ideas and improvements are welcome through GitHub Issues.

- [Contributing guide](CONTRIBUTING.md)
- [Changelog](CHANGELOG.md)
- [Third-party notices](docs/THIRD_PARTY_NOTICES.md)

## 🤖 Development note

TimeManager is initiated, designed and maintained by **FPhoenix28**.

**OpenAI Codex** and **ChatGPT** are used as AI-assisted development tools for implementation, debugging, code review and documentation. Product direction, feature decisions, testing and verification, and the final changes merged into this repository remain the maintainer's responsibility.

## 📄 License

Copyright 2026 FPhoenix28.

Project-authored material is licensed under the [Apache License 2.0](LICENSE). Third-party dependencies, trademarks and assets retain their own terms.
