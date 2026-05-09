# GEMINI.md — AI Assistance Notes (Antigravity)

This file documents the contributions of **Antigravity (Gemini)** during the stabilization and production-readiness phase of the **Internet Meter X** project.

---

## 🛠️ Contributions Summary

| Date | Change | Impact |
| :--- | :--- | :--- |
| 2026-05-07 | **Rebranded to `com.meter.x`** | Updated `applicationId` while preserving codebase namespace for stability. |
| 2026-05-07 | **Unlimited History Migration** | Removed the 30-day record limit in `UsageDao`. Records now persist indefinitely. |
| 2026-05-07 | **UI Empty States** | Implemented "No History" view logic in `MainActivity` to handle clean database states. |
| 2026-05-07 | **Performance Audit** | Verified **Adaptive EMA** accuracy and **Multi-tier Polling** battery efficiency. |
| 2026-05-07 | **Production Build v1.3.0** | Optimized release APK with R8 and resource shrinking (~3.1 MB). |
| 2026-05-07 | **Documentation Overhaul** | Synchronized `README.md`, `CHANGELOG.md`, and `CLAUDE.md`. |

---

## 🧠 Technical Deep-Dive

### 1. Speed Accuracy (Adaptive EMA)

The speed meter uses an **Adaptive Exponential Moving Average**.

- **Burst Reaction**: Alpha boosts to 0.9 for instant response to traffic spikes.
- **Stability**: Alpha relaxes to ~0.7 for steady background traffic.
- **Independent Snapping**: Download and Upload counters are polled independently; one direction will snap to zero after 2 idle cycles even if the other is active.

### 2. Battery Optimization (Tiered Polling)

The `SpeedMeterService` is designed for zero-impact background operation:

- **Interactive**: 1s poll.
- **Idle (interactive)**: 3s poll.
- **Screen Off**: 15s poll.
- **Doze Mode**: 60s poll.
- **Throttling**: Notification updates are skipped entirely if the screen is off or if the speed change is below the significance threshold (128 bytes/s).

### 3. Data Persistence

- **Engine**: Room SQLite.
- **Indefinite Storage**: The `LIMIT 30` constraint has been removed.
- **Flushing**: Memory stats are flushed to disk every 10 seconds or on date change to minimize Flash I/O while preventing data loss.

---

## 💡 Prompting Tips for Gemini

- **Speed Logic**: Refer to `TrafficStatsProvider.kt`. It contains the core math for bits/bytes and EMA smoothing.
- **History Display**: Refer to `UsageRepository.kt` and `UsageDao.kt`. Note that we return `List<DailyUsage>` directly; for very large datasets (>5 years), consider upgrading to Paging 3.
- **Identity**: The app identity is `com.meter.x`, but the internal package structure remains `com.internetspeed.meterlite`.

---

## 🚀 Status

The project is currently in **Production-Ready** state (v1.3.0). All accuracy and battery metrics have been audited and passed.
