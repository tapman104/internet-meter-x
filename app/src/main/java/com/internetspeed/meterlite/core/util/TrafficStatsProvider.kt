package com.internetspeed.meterlite.core.util

import android.net.TrafficStats
import android.os.SystemClock
import java.util.Locale

data class Speed(val down: Long, val up: Long)

class TrafficStatsProvider {

    private var lastTotalRx: Long = TrafficStats.getTotalRxBytes()
    private var lastTotalTx: Long = TrafficStats.getTotalTxBytes()
    private var lastMobileRx: Long = TrafficStats.getMobileRxBytes()
    private var lastMobileTx: Long = TrafficStats.getMobileTxBytes()
    private var lastTime: Long = SystemClock.elapsedRealtime()

    data class TrafficSnapshot(
        val speedDown: Long, // bytes/sec
        val speedUp: Long,   // bytes/sec
        val diffRx: Long,    // total raw bytes since last check
        val diffTx: Long,
        val mobileRx: Long,  // mobile only raw bytes
        val mobileTx: Long
    )

    fun getSnapshot(): TrafficSnapshot {
        val currentTotalRx = TrafficStats.getTotalRxBytes()
        val currentTotalTx = TrafficStats.getTotalTxBytes()
        val currentMobileRx = TrafficStats.getMobileRxBytes()
        val currentMobileTx = TrafficStats.getMobileTxBytes()
        val currentTime = SystemClock.elapsedRealtime()

        val timeDiffMillis = currentTime - lastTime
        val timeDiffSec = timeDiffMillis / 1000.0
        
        if (timeDiffMillis <= 0) {
            return TrafficSnapshot(0, 0, 0, 0, 0, 0)
        }

        // Handle counters reset (e.g. reboot)
        val totalRxDiff = calculateDiff(currentTotalRx, lastTotalRx)
        val totalTxDiff = calculateDiff(currentTotalTx, lastTotalTx)
        val mobileRxDiff = calculateDiff(currentMobileRx, lastMobileRx)
        val mobileTxDiff = calculateDiff(currentMobileTx, lastMobileTx)

        val speedDown = (totalRxDiff / timeDiffSec).toLong()
        val speedUp = (totalTxDiff / timeDiffSec).toLong()

        lastTotalRx = currentTotalRx
        lastTotalTx = currentTotalTx
        lastMobileRx = currentMobileRx
        lastMobileTx = currentMobileTx
        lastTime = currentTime

        return TrafficSnapshot(speedDown, speedUp, totalRxDiff, totalTxDiff, mobileRxDiff, mobileTxDiff)
    }

    private fun calculateDiff(current: Long, last: Long): Long {
        if (current == TrafficStats.UNSUPPORTED.toLong() || last == TrafficStats.UNSUPPORTED.toLong()) return 0
        // If current < last, we assume the counter reset (e.g. reboot)
        return if (current >= last) current - last else current
    }

    fun formatSpeed(bytes: Long): String {
        val b = if (bytes < 0) 0L else bytes
        if (b < 1024) return "$b B/s"
        val kb = b / 1024.0
        if (kb < 1023.95) return String.format(Locale.ENGLISH, "%.1f KB/s", kb)
        val mb = kb / 1024.0
        if (mb < 1023.95) return String.format(Locale.ENGLISH, "%.1f MB/s", mb)
        val gb = mb / 1024.0
        return String.format(Locale.ENGLISH, "%.1f GB/s", gb)
    }

    fun formatBytes(bytes: Long): String {
        val b = if (bytes < 0) 0L else bytes
        if (b < 1024) return "$b B"
        val kb = b / 1024.0
        if (kb < 1023.95) return String.format(Locale.ENGLISH, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1023.95) return String.format(Locale.ENGLISH, "%.1f MB", mb)
        val gb = mb / 1024.0
        if (gb < 1023.95) return String.format(Locale.ENGLISH, "%.2f GB", gb)
        val tb = gb / 1024.0
        return String.format(Locale.ENGLISH, "%.2f TB", tb)
    }
}
