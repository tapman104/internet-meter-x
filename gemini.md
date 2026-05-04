# GEMINI.md — AI Assistance Notes

This file documents how Gemini (Google DeepMind) was used during development of **Internet Speed Meter Lite** and serves as a guide for future AI-assisted work on this project.

---

## What the AI assistant did

| Session | Change | File(s) |
|---------|--------|---------|
| 2026-05-04 | Initial build of debug APK via `.\gradlew.bat assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| 2026-05-04 | **Accuracy**: Implemented Adaptive EMA in `TrafficStatsProvider` to react faster to traffic bursts while smoothing steady flow. | `TrafficStatsProvider.kt` |
| 2026-05-04 | **Battery**: Implemented Notification Icon Caching in `SpeedMeterService` to skip redundant bitmap rendering. | `SpeedMeterService.kt` |
| 2026-05-04 | **Battery**: Stricter adaptive polling (60s in Doze, 15s screen-off, 3s idle) to reduce CPU wakeups. | `SpeedMeterService.kt` |
| 2026-05-04 | Fixed compilation error: Added missing `kotlin.math.abs` import. | `TrafficStatsProvider.kt` |
| 2026-05-04 | **UI/UX**: Refactored Settings into categories (Notifications, General, About, Support). Added GitHub, PayPal, and Coffee links. | `SettingsActivity.kt`, `activity_settings.xml` |
| 2026-05-04 | **UI/UX**: Integrated History into Home screen with dynamic "View More" and simplified total usage display. | `MainActivity.kt`, `HistoryAdapter.kt`, `activity_main.xml` |
| 2026-05-04 | **Settings**: Added "Show in bits" and "Data precision" controls; added Reset Data functionality. | `SettingsActivity.kt`, `SettingsManager.kt` |
| 2026-05-04 | **Settings**: Added Notification Priority control (Normal vs High). | `SettingsActivity.kt`, `SpeedMeterService.kt` |

---

## Coding conventions observed

- **Adaptive EMA**: Reacts instantly to changes > 50% but uses a 0.8s time constant for steady traffic.
- **Resource Reuse**: Pre-allocated bitmaps/canvases and cached icons are used to minimize GC pressure and IPC overhead.
- **Lazy UI**: Notifications are only updated if the formatted string or unit has changed visually.
- **Doze Friendly**: Respects system idle modes by drastically reducing polling frequency when the device is not being used.

---

## Key files to know

| File | Purpose |
|------|---------|
| `app/src/main/java/…/core/util/TrafficStatsProvider.kt` | Adaptive EMA logic and speed formatting |
| `app/src/main/java/…/core/service/SpeedMeterService.kt` | Service lifecycle, adaptive polling, and notification management |
| `app/src/main/java/…/core/util/NotificationIconGenerator.kt` | Efficient status-bar icon rendering (Canvas/Bitmap reuse) |

---

## Prompting tips for this project

- When optimizing for battery, focus on `SpeedMeterService` polling delays and `updateNotificationIfNeeded`.
- When improving accuracy, tweak the `getAdaptiveAlpha` ratios in `TrafficStatsProvider.kt`.
- Use `.\gradlew.bat` for all build commands on Windows.

---

## AI model

**Gemini 3 Flash** (Google DeepMind).
