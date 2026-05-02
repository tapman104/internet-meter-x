package com.internetspeed.meterlite.core.util

import android.content.Context
import android.graphics.*
import androidx.core.graphics.drawable.IconCompat
import java.util.Locale

class NotificationIconGenerator(private val context: Context) {

    /**
     * Creates a status bar icon with a 2-line layout:
     *   Line 1 (big):   speed value  e.g. "45"
     *   Line 2 (small): unit label   e.g. "KB/s"
     *
     * Canvas is a square at exactly the status bar icon spec (24dp).
     * Text is auto-scaled to fill width so it's as large as possible.
     */
    fun createSpeedIcon(speedDown: Long, @Suppress("UNUSED_PARAMETER") speedUp: Long): IconCompat {
        val dp = context.resources.displayMetrics.density
        // Standard Android status bar icon size: 24dp
        val size = (24 * dp).toInt()

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val (numStr, unitStr) = fmtSplit(speedDown)

        // --- Number paint (top ~70% of icon) ---
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            // Start large, shrink until text fits within 95% of width
            textSize = size * 0.70f
            while (measureText(numStr) > size * 0.95f) textSize -= 0.5f
        }

        // --- Unit paint (bottom ~42% of icon) ---
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textSize = size * 0.42f
            while (measureText(unitStr) > size * 0.98f) textSize -= 0.5f
        }

        val cx = size / 2f

        // Measure actual glyph heights
        val numBounds = Rect()
        numPaint.getTextBounds(numStr, 0, numStr.length, numBounds)
        val unitBounds = Rect()
        unitPaint.getTextBounds(unitStr, 0, unitStr.length, unitBounds)

        // Stack the two text blocks with no gap to maximise fill
        val gap = 0f
        val totalH = numBounds.height() + gap + unitBounds.height()
        val topY = (size - totalH) / 2f

        // Baseline = topY + numHeight (ascender offset)
        val numBaseline = topY + numBounds.height()
        val unitBaseline = numBaseline + gap + unitBounds.height()

        canvas.drawText(numStr, cx, numBaseline, numPaint)
        canvas.drawText(unitStr, cx, unitBaseline, unitPaint)

        return IconCompat.createWithBitmap(bitmap)
    }

    private fun fmtSplit(bps: Long): Pair<String, String> {
        if (bps < 512) return Pair("0", "B/s")
        val k = bps / 1024.0
        if (k < 999.5) return Pair(String.format(Locale.ENGLISH, "%.0f", k), "KB/s")
        val m = k / 1024.0
        return if (m < 9.95)
            Pair(String.format(Locale.ENGLISH, "%.1f", m), "MB/s")
        else if (m < 999.5)
            Pair(String.format(Locale.ENGLISH, "%.0f", m), "MB/s")
        else
            Pair(String.format(Locale.ENGLISH, "%.1f", m / 1024.0), "GB/s")
    }
}
