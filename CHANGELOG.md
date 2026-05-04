# Changelog

All notable changes to **Internet Speed Meter Lite** are documented here.  
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).  
Versions follow [Semantic Versioning](https://semver.org/).

---

## [1.2.1] — 2026-05-04

### Fixed
- **Usage Reset**: Fixed bug where live counters wouldn't reset when "Reset all data" was clicked.
- **Initialization**: Fixed bug where notification would stay at "Initializing..." if no traffic occurred immediately.
- **Speed Precision**: Improved significance threshold to show low speeds (e.g. 100 B/s) correctly.
- **Unit Precision**: Applied consistent precision logic to GB and TB units in usage reporting.
- **Build System**: Restored missing `gradle-wrapper.jar` and resolved SDK configuration issues.

### Added
- **Build Artifacts**: Successfully generated the debug APK (`app-debug.apk`).

### Changed
- **UI Cleanup**: Removed redundant settings (Bits, Precision, Priority) for a cleaner "Lite" experience.
- **UX**: Switched to `MaterialAlertDialogBuilder` for system dialogs.
- **Notification**: Defaulted priority to `LOW` for better system integration.

---

## [1.2.0] — 2026-05-03

### Added
- **Reset All Data**: Implemented full database and preference reset functionality in settings with confirmation dialog.
- **Real-time Settings Synchronization**: Added `OnSharedPreferenceChangeListener` to `SpeedMeterService` to ensure settings like "Show in bits" or "Decimal precision" reflect immediately in the status bar.

### Changed
- **Data Unit Precision**: Refactored `TrafficStatsProvider` to support user-selectable decimal precision (1 or 2 decimal points) across the entire app.
- **Build System**: Updated build configurations for better compatibility with Windows environment.

### Fixed
- **SettingsActivity Compilation**: Resolved ViewBinding type mismatch errors that prevented successful builds.
- **Android 14 Foreground Crash**: Added mandatory `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` for API 34+ compatibility in `SpeedMeterService`.
- **Decimal Precision Bug**: Fixed issue where the "Data unit precision" setting was being ignored in both the UI and notification.

---

## [1.1.1] — 2026-05-02

### Added
- **App Title**: Added "Internet Meter X" title to the home screen header for better visual balance.

### Changed
- **UI Refinement**: Removed the redundant "Live Speed" section from the main activity to simplify the dashboard.
- **Layout Optimization**: Converted all cards in `activity_main.xml` to use `0dp` width with parent constraints for improved responsiveness.

### Fixed
- **Code Cleanup**: Removed orphaned `observeLiveSpeed` logic and excessive whitespace in `MainActivity.kt`.

---

## [1.1.0] — 2026-05-02

### Added
- **Settings Modal**: Material 3 Bottom Sheet for app configuration.
- **Unit Customization**: Support for toggling between Bytes (MB/s) and Bits (Mbps) in both UI and status bar icon.
- **Notification Priority**: User-selectable priority (Max vs Low) for the persistent foreground notification.
- **Start on Boot Toggle**: Setting to enable/disable auto-start behavior on device reboot.
- **SettingsManager**: Persistent preference storage using `SharedPreferences`.

### Fixed
- **MainActivity Crash**: Fixed `UninitializedPropertyAccessException` for `powerManager` during app startup.
- **Icon Unit Sync**: Ensured status bar icon units immediately reflect changes in unit preference settings.
- **Initialization Logic**: Restored critical power management initialization in `MainActivity.onCreate()`.

---

## [1.0.0] — 2026-05-02

### Added
- **Foreground speed-meter service** (`SpeedMeterService`) — polls `TrafficStats` on a coroutine loop and posts a persistent notification.
- **Custom status-bar icon** (`NotificationIconGenerator`) — renders a two-line bitmap (value + unit) at 24 dp, sized to match system icon visual weight.
- **Boot auto-start** — `BootReceiver` restarts the service after reboot (`BOOT_COMPLETED` / `QUICKBOOT_POWERON`).
- **Per-interface tracking** (`ConnectivityProvider`) — distinguishes WiFi vs. mobile traffic.
- **Traffic formatter** (`TrafficStatsProvider.formatSpeed`) — auto-selects B/s, KB/s, MB/s, or GB/s.
- **Room database** (`UsageDatabase`) — stores hourly/daily usage totals for history.
- **MainActivity** — single-activity UI with live ↓/↑ speed readout and usage history.
- **Material You theming** — `Theme.InternetSpeedMeterLite` with dynamic colour support.
- **`.gitignore`** — excludes `app/build/`, `local.properties`, `.idea/`, keystore files.
- **README.md** — full architecture, build steps, permissions table, and troubleshooting guide.

### Changed
- Status-bar icon number text size raised from `0.60×` to `0.70×` canvas height.
- Status-bar icon unit text size raised from `0.36×` to `0.42×` canvas height.
- Inter-line gap between value and unit removed (was 1 px) to maximise fill.

---

[Unreleased]: https://github.com/your-org/internet-apk/compare/v1.2.1...HEAD
[1.2.1]: https://github.com/your-org/internet-apk/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/your-org/internet-apk/compare/v1.1.1...v1.2.0
[1.1.1]: https://github.com/your-org/internet-apk/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/your-org/internet-apk/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/your-org/internet-apk/releases/tag/v1.0.0
