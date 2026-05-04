# Internet Speed Meter Lite

A lightweight Android app that displays real-time network download/upload speeds directly in the status bar as a custom notification icon.

---

## Features

- **Live status-bar speed icon** — draws a two-line bitmap icon (value + unit, e.g. `19 / KB/s`) sized to match the visual weight of system icons (WiFi, signal, battery).
- **Usage tracking** — clean dashboard showing today's and yesterday's traffic totals (WiFi and Mobile breakdown).
- **Settings & Customization** — toggle between **Bits (Mbps)** and **Bytes (MB/s)**, adjust notification priority, and enable/disable auto-start on boot.
- **Foreground service** (`SpeedMeterService`) — polls `TrafficStats` on a coroutine loop; survives screen-off and battery optimisation where permitted.
- **Boot auto-start** — `BootReceiver` restarts the service after device reboot, respecting user preferences.
- **Per-interface breakdown** — tracks WiFi and mobile traffic separately via `ConnectivityProvider`.
- **Usage history** — stores hourly/daily traffic totals in a Room database (`UsageDatabase`) for the history screen.
- **Material You theming** — single `MainActivity` with "Internet Meter X" branding and dynamic color support.

---

## Architecture

```
app/src/main/
├── AndroidManifest.xml
├── java/com/internetspeed/meterlite/
│   ├── SpeedMeterApp.kt          # Application class, notification channel setup
│   ├── ui/
│   │   └── MainActivity.kt       # Single-activity entry point
│   ├── core/
│   │   ├── service/
│   │   │   └── SpeedMeterService.kt      # Foreground service, polling loop
│   │   ├── receiver/
│   │   │   └── BootReceiver.kt           # BOOT_COMPLETED receiver
│   │   └── util/
│   │       ├── NotificationIconGenerator.kt  # Bitmap status-bar icon renderer
│   │       ├── TrafficStatsProvider.kt       # TrafficStats wrapper + formatter
│   │       └── ConnectivityProvider.kt       # WiFi / mobile interface detection
│   └── data/
│       ├── UsageDatabase.kt      # Room database
│       ├── dao/                  # DAO interfaces
│       ├── entity/               # Room entities
│       └── repository/           # Repository layer
└── res/
    ├── layout/                   # XML layouts
    └── values/                   # Colors, themes, strings, dimens
```

**Stack:** Kotlin · Android SDK 34 · Room 2.6 · Lifecycle 2.7 · Coroutines 1.7 · ViewBinding

---

## Requirements

| Tool | Version |
|------|---------|
| JDK | 8 (targeting `VERSION_1_8`) |
| Android SDK | compile/target SDK 34, min SDK 24 |
| Gradle wrapper | included (`gradlew` / `gradlew.bat`) |

---

## Build & Run

```powershell
# Debug APK
.\gradlew assembleDebug

# Install on connected device / emulator
.\gradlew installDebug
# — or —
adb install -r app\build\outputs\apk\debug\app-debug.apk

# Release APK (configure signing in app/build.gradle first)
.\gradlew assembleRelease

# Clean everything
.\gradlew clean
```

Output APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## Permissions

| Permission | Reason |
|------------|--------|
| `INTERNET` | Network access |
| `ACCESS_NETWORK_STATE` | Detect WiFi vs mobile |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Keep service alive |
| `POST_NOTIFICATIONS` | Show status-bar notification icon |
| `RECEIVE_BOOT_COMPLETED` | Auto-start on reboot |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent service kill |

---

## Development Notes

- **Do not commit** anything under `app/build/` — covered by `.gitignore`.
- Speed icon rendering is in `NotificationIconGenerator.kt`. Text sizes are expressed as fractions of the 24 dp canvas so they auto-scale with display density.
- `TrafficStatsProvider.formatSpeed()` handles B/s → KB/s → MB/s → GB/s formatting.
- Logcat tag: search for `SpeedMeterService` or `NotificationIconGenerator`.

---

## Troubleshooting

- **Gradle sync fails** — verify JDK path in Android Studio (**File → Project Structure → SDK Location**) and re-sync.
- **`adb devices` shows nothing** — run `adb kill-server` then `adb start-server`, or enable USB debugging on the device.
- **Service stops in background** — grant "Unrestricted battery usage" to the app in device Settings.

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

---

## AI Assistance

See [CLAUDE.md](CLAUDE.md) for notes on how GitHub Copilot / Claude was used in this project.
