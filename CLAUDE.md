# CLAUDE.md — AI Assistance Notes

This file documents how GitHub Copilot (Claude) was used during development of **Internet Speed Meter Lite** and serves as a guide for future AI-assisted work on this project.

---

## What the AI assistant did

| Session | Change | File(s) |
|---------|--------|---------|
| 2026-05-04 | Restored missing `gradle-wrapper.jar`, configured SDK in `local.properties`, and built debug APK | `gradle/wrapper/…`, `local.properties`, `app/build/…` |
| 2026-05-02 | Built debug APK via `.\gradlew.bat assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| 2026-05-02 | Refined UI: Added app title, removed "Live Speed" section, fixed card constraints | `ui/MainActivity.kt`, `res/layout/activity_main.xml` |
| 2026-05-02 | Implemented Settings Modal (Bottom Sheet) with Mbps/MBs, Priority, and Boot toggles | `app/src/main/res/layout/settings_bottom_sheet.xml`, `ui/SettingsBottomSheetDialogFragment.kt` |
| 2026-05-02 | Created `SettingsManager` for persistent preference storage using SharedPreferences | `core/util/SettingsManager.kt` |
| 2026-05-02 | Fixed `MainActivity` initialization crash (`powerManager`) and icon unit sync bugs | `ui/MainActivity.kt`, `core/service/SpeedMeterService.kt` |
| 2026-05-02 | Updated `TrafficStatsProvider` and `NotificationIconGenerator` for bit-per-second support | `core/util/…` |
| 2026-05-02 | Generated project-level `.gitignore` for Android/Gradle/IDE artifacts | `.gitignore` |
| 2026-05-02 | Increased status-bar speed icon text sizes (`0.60→0.70` number, `0.36→0.42` unit) and removed 1 px inter-line gap to match system icon visual weight | `app/src/main/java/…/core/util/NotificationIconGenerator.kt` |
| 2026-05-02 | Rewrote `README.md` with accurate architecture, permission table, and troubleshooting | `README.md` |
| 2026-05-02 | Created `CHANGELOG.md` (Keep a Changelog format) | `CHANGELOG.md` |
| 2026-05-02 | Created this file | `CLAUDE.md` |
| 2026-05-07 | **Rebranded to com.meter.x**, removed 30-day history limit, added history empty states, and audited battery/accuracy performance. | `app/build.gradle`, `UsageDao.kt`, `MainActivity.kt` |

---

## Coding conventions observed

- **Kotlin** throughout — no Java source files.
- `sans-serif-condensed` + `BOLD` typeface used for all status-bar icon text.
- Text sizes expressed as **fractions of the canvas pixel dimension** so they scale automatically with display density.
- Shrink-loop pattern (`while (measureText(str) > limit) textSize -= 0.5f`) guards against wide strings clipping.
- Room used for local persistence; no remote/cloud dependency.
- Single-activity architecture (`MainActivity`) — no fragments yet.

---

## Key files to know

| File | Purpose |
|------|---------|
| `app/src/main/java/…/core/util/NotificationIconGenerator.kt` | All status-bar icon rendering logic |
| `app/src/main/java/…/core/util/TrafficStatsProvider.kt` | Speed polling and formatting |
| `app/src/main/java/…/core/service/SpeedMeterService.kt` | Foreground service + notification |
| `app/src/main/java/…/core/receiver/BootReceiver.kt` | Boot auto-start |
| `app/src/main/java/…/data/UsageDatabase.kt` | Room database |
| `app/src/main/java/…/ui/MainActivity.kt` | UI entry point |

---

## Prompting tips for this project

- When asking about the icon, reference `NotificationIconGenerator.kt` and the 24 dp canvas spec.
- When asking about service lifecycle (battery, kill, restart), reference `SpeedMeterService` + `BootReceiver` together.
- Build commands: always use `.\gradlew.bat` on Windows PowerShell (not `./gradlew`).
- The app package identity (applicationId) is `com.meter.x`.
- The code namespace is `com.internetspeed.meterlite` (kept to avoid refactoring noise).

---

## AI model

GitHub Copilot powered by **Claude Sonnet 4.6** (Anthropic).
