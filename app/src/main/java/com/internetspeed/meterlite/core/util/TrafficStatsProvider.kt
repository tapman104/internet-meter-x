package com.internetspeed.meterlite.core.util

import android.net.TrafficStats
import android.os.SystemClock
import java.util.Locale
import kotlin.math.abs
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
     * Accuracy: Uses a variable smoothing factor that reacts instantly to large traffic 
     * spikes while maintaining smooth transitions for steady or slow-changing traffic.
     */
    fun getSnapshot(): TrafficSnapshot {
        val currentTotalRx = TrafficStats.getTotalRxBytes()
        val currentTotalTx = TrafficStats.getTotalTxBytes()
        val currentMobileRx = TrafficStats.getMobileRxBytes()
        val currentMobileTx = TrafficStats.getMobileTxBytes()
        val currentTime = SystemClock.elapsedRealtime()

        val timeDiffMillis = currentTime - lastTime
        
        // Ensure a minimum interval to avoid extreme fluctuations
        if (timeDiffMillis < 300) { // Slightly increased minimum window for more stable delta
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

        // Accuracy: Adaptive EMA
        // If the instant speed is significantly different from the last smoothed speed,
        // we use a much higher alpha to catch the burst immediately.
        fun getAdaptiveAlpha(instant: Long, last: Long): Double {
            if (last <= 0) return 1.0 // Instant reaction from zero
            
            val diff = abs(instant - last)
            val ratio = diff.toDouble() / last
            
            // Base time constant: 0.8s for steady traffic
            var baseAlpha = 1.0 - exp(-timeDiffSec / 0.8)
            
            // If change > 50%, boost alpha up to 0.9 for instant reaction
            return when {
                ratio > 2.0 -> 0.9       // Huge burst: react almost instantly
                ratio > 0.5 -> baseAlpha + (0.9 - baseAlpha) * ((ratio - 0.5) / 1.5)
                instant == 0L -> 0.8     // Faster drop to zero
                else -> baseAlpha
            }.coerceIn(0.0, 1.0)
        }

        val alphaDown = getAdaptiveAlpha(instantDown, lastSpeedDown)
        val alphaUp = getAdaptiveAlpha(instantUp, lastSpeedUp)

        // Accuracy: hard-drop to 0 after 2 consecutive zero-traffic samples
        if (instantDown == 0L && instantUp == 0L) consecutiveZeroCount++ else consecutiveZeroCount = 0
        val forceZero = consecutiveZeroCount >= 2

        val speedDown = when {
            forceZero -> 0L
            lastSpeedDown == -1L -> instantDown
            else -> (instantDown * alphaDown + lastSpeedDown * (1 - alphaDown)).toLong()
        }
        val speedUp = when {
            forceZero -> 0L
            lastSpeedUp == -1L -> instantUp
            else -> (instantUp * alphaUp + lastSpeedUp * (1 - alphaUp)).toLong()
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
        
        val kb = b / 1024.0
        if (kb < 9.95) return formatDecimal(kb, precision) + " K $unit"
        if (kb < 999.5) return Math.round(kb).toString() + " K $unit"
        
        val mb = kb / 1024.0
        if (mb < 9.95) return formatDecimal(mb, precision) + " M $unit"
        if (mb < 999.5) return Math.round(mb).toString() + " M $unit"
        
        val gb = mb / 1024.0
        return formatDecimal(gb, precision) + " G $unit"
    }

    fun formatBytes(bytes: Long, precision: Int = 1): String {
        val b = if (bytes < 0) 0L else bytes
        if (b < 1024) return "$b B"
        
        val kb = b / 1024.0
        if (kb < 1023.95) return formatDecimal(kb, precision) + " KB"
        val mb = kb / 1024.0
        if (mb < 1023.95) return formatDecimal(mb, precision) + " MB"
        val gb = mb / 1024.0
        if (gb < 1023.95) return formatDecimal(gb, precision) + " GB"
        val tb = gb / 1024.0
        return formatDecimal(tb, precision) + " TB"
    }

    private fun formatDecimal(value: Double, precision: Int): String {
        return if (precision == 1) {
            String.format(Locale.ENGLISH, "%.1f", value)
        } else {
            String.format(Locale.ENGLISH, "%.2f", value)
        }
    }
}
