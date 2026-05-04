package com.internetspeed.meterlite.core.util

import android.net.TrafficStats
import android.os.SystemClock
import java.util.Locale
import kotlin.math.exp

data class Speed(val down: Long, val up: Long)

class TrafficStatsProvider {

    private var lastTotalRx: Long = TrafficStats.getTotalRxBytes()
    private var lastTotalTx: Long = TrafficStats.getTotalTxBytes()
    private var lastMobileRx: Long = TrafficStats.getMobileRxBytes()
    private var lastMobileTx: Long = TrafficStats.getMobileTxBytes()
    private var lastTime: Long = SystemClock.elapsedRealtime()

    private var lastSpeedDown: Long = -1
    private var lastSpeedUp: Long = -1

    // Accuracy: track consecutive zero-traffic polls so we can hard-drop to 0
    // instead of slowly EMA-decaying, which makes the display lag behind reality.
    private var consecutiveZeroCount = 0

    data class TrafficSnapshot(
        val speedDown: Long, // bytes/sec (smoothed)
        val speedUp: Long,   // bytes/sec (smoothed)
        val diffRx: Long,    // total raw bytes since last check
        val diffTx: Long,
        val mobileRx: Long,  // mobile only raw bytes
        val mobileTx: Long
    )

    /**
     * Calculates the current network speed with Adaptive EMA (Exponential Moving Average) smoothing.
     * Smoothing helps reduce jitter caused by bursty network reports, while adaptivity 
     * ensures responsiveness even when polling intervals change.
     */
    fun getSnapshot(): TrafficSnapshot {
        val currentTotalRx = TrafficStats.getTotalRxBytes()
        val currentTotalTx = TrafficStats.getTotalTxBytes()
        val currentMobileRx = TrafficStats.getMobileRxBytes()
        val currentMobileTx = TrafficStats.getMobileTxBytes()
        val currentTime = SystemClock.elapsedRealtime()

        val timeDiffMillis = currentTime - lastTime
        
        // Ensure a minimum interval to avoid extreme fluctuations
        if (timeDiffMillis < 200) {
            return TrafficSnapshot(
                maxOf(0, lastSpeedDown), 
                maxOf(0, lastSpeedUp), 
                0, 0, 0, 0
            )
        }

        val timeDiffSec = timeDiffMillis / 1000.0

        // Handle counters reset (e.g. reboot)
        val totalRxDiff = calculateDiff(currentTotalRx, lastTotalRx)
        val totalTxDiff = calculateDiff(currentTotalTx, lastTotalTx)
        val mobileRxDiff = calculateDiff(currentMobileRx, lastMobileRx)
        val mobileTxDiff = calculateDiff(currentMobileTx, lastMobileTx)

        val instantDown = (totalRxDiff / timeDiffSec).toLong()
        val instantUp = (totalTxDiff / timeDiffSec).toLong()

        // Adaptive EMA Smoothing
        // Accuracy: tighter time-constant → faster tracking of real speed changes.
        // τ = 0.8 s (was 1.2 s) keeps the display responsive while still smoothing jitter.
        val timeConstant = 0.8
        val alpha = (1.0 - exp(-timeDiffSec / timeConstant)).coerceIn(0.0, 1.0)
        
        // If speed is 0, we drop to 0 faster to feel more responsive when traffic stops
        val adjustedAlpha = if (instantDown == 0L && instantUp == 0L) maxOf(alpha, 0.8) else alpha

        // Accuracy: hard-drop to 0 after 2 consecutive zero-traffic samples rather
        // than waiting for the EMA to decay — eliminates phantom "ghost" speed.
        if (instantDown == 0L && instantUp == 0L) consecutiveZeroCount++ else consecutiveZeroCount = 0
        val forceZero = consecutiveZeroCount >= 2

        val speedDown = when {
            forceZero      -> 0L
            lastSpeedDown == -1L -> instantDown
            else -> (instantDown * adjustedAlpha + lastSpeedDown * (1 - adjustedAlpha)).toLong()
        }
        val speedUp = when {
            forceZero    -> 0L
            lastSpeedUp == -1L -> instantUp
            else -> (instantUp * adjustedAlpha + lastSpeedUp * (1 - adjustedAlpha)).toLong()
        }

        lastTotalRx = currentTotalRx
        lastTotalTx = currentTotalTx
        lastMobileRx = currentMobileRx
        lastMobileTx = currentMobileTx
        lastTime = currentTime
        lastSpeedDown = speedDown
        lastSpeedUp = speedUp

        return TrafficSnapshot(speedDown, speedUp, totalRxDiff, totalTxDiff, mobileRxDiff, mobileTxDiff)
    }

    private fun calculateDiff(current: Long, last: Long): Long {
        if (current == TrafficStats.UNSUPPORTED.toLong() || last == TrafficStats.UNSUPPORTED.toLong()) return 0
        // If current < last, we assume the counter reset (e.g. reboot)
        return if (current >= last) current - last else current
    }

    fun formatSpeed(bytes: Long, showInBits: Boolean = false, precision: Int = 1): String {
        var b = if (bytes < 0) 0L else bytes
        val unit = if (showInBits) "b/s" else "B/s"
        
        if (showInBits) {
            b *= 8
        }

        if (b < 1000) return "$b $unit"
        
        val fmt = "%.${precision}f"
        val kb = b / 1024.0
        if (kb < 9.95) return String.format(Locale.ENGLISH, "$fmt K$unit", kb)
        if (kb < 999.5) return String.format(Locale.ENGLISH, "%.0f K$unit", kb)
        
        val mb = kb / 1024.0
        if (mb < 9.95) return String.format(Locale.ENGLISH, "$fmt M$unit", mb)
        if (mb < 999.5) return String.format(Locale.ENGLISH, "%.0f M$unit", mb)
        
        val gb = mb / 1024.0
        return String.format(Locale.ENGLISH, "$fmt G$unit", gb)
    }

    fun formatBytes(bytes: Long, precision: Int = 1): String {
        val b = if (bytes < 0) 0L else bytes
        if (b < 1024) return "$b B"
        
        val fmt = "%.${precision}f"
        val kb = b / 1024.0
        if (kb < 1023.95) return String.format(Locale.ENGLISH, "$fmt KB", kb)
        val mb = kb / 1024.0
        if (mb < 1023.95) return String.format(Locale.ENGLISH, "$fmt MB", mb)
        val gb = mb / 1024.0
        if (gb < 1023.95) return String.format(Locale.ENGLISH, "%.2f GB", gb)
        val tb = gb / 1024.0
        return String.format(Locale.ENGLISH, "%.2f TB", tb)
    }
}
