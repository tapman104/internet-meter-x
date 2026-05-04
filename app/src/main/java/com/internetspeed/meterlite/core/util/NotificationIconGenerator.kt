package com.internetspeed.meterlite.core.util

import android.content.Context
import android.graphics.*
import androidx.core.graphics.drawable.IconCompat
import java.util.Locale
import android.graphics.PorterDuff

class NotificationIconGenerator(private val context: Context) {

    private val dp = context.resources.displayMetrics.density
    // Standard Android status bar icon size: 24dp
    private val size = (24 * dp).toInt()

    // Battery Efficiency: Pre-allocate Paint and Rect objects to avoid GC pressure during frequent polling
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }

    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }

    private val numBounds = Rect()
    private val unitBounds = Rect()

    // Battery: Pre-allocate one Bitmap+Canvas; clear and redraw each tick instead of
    // creating a new Bitmap every second (avoids GC pressure and heap churn).
    private val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)

    /**
     * Creates a status bar icon with a 2-line layout:
     *   Line 1 (big):   speed value  e.g. "45"
     *   Line 2 (small): unit label   e.g. "KB/s"
     */
    fun createSpeedIcon(speedDown: Long, @Suppress("UNUSED_PARAMETER") speedUp: Long, showInBits: Boolean = false): IconCompat {
        val (numStr, unitStr) = fmtSplit(speedDown, showInBits)

        // Clear the reused bitmap before drawing.
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // Reset and scale text sizes dynamically to fit the width
        numPaint.textSize = size * 0.70f
        while (numPaint.measureText(numStr) > size * 0.95f && numPaint.textSize > 4f) {
            numPaint.textSize -= 0.5f
        }

        unitPaint.textSize = size * 0.42f
        while (unitPaint.measureText(unitStr) > size * 0.98f && unitPaint.textSize > 4f) {
            unitPaint.textSize -= 0.5f
        }

        val cx = size / 2f

        // Measure actual glyph heights for precise vertical stacking
        numPaint.getTextBounds(numStr, 0, numStr.length, numBounds)
        unitPaint.getTextBounds(unitStr, 0, unitStr.length, unitBounds)

        val gap = 0.5f * dp
        val totalH = numBounds.height() + gap + unitBounds.height()
        val topOffset = (size - totalH) / 2f

        // Draw number: Baseline calculation to center the visual ink
        val numY = topOffset - numBounds.top
        canvas.drawText(numStr, cx, numY, numPaint)

        // Draw unit: Placed below the number
        val unitY = numY + gap + unitBounds.height() + numBounds.bottom
        canvas.drawText(unitStr, cx, unitY, unitPaint)

        return IconCompat.createWithBitmap(bitmap)
    }

    /**
     * Accuracy: Splits speed into numeric and unit parts with better precision at low speeds.
     * Prevents "0" display for speeds between 1-512 bytes.
     */
    private fun fmtSplit(bytes: Long, showInBits: Boolean): Pair<String, String> {
        var b = if (bytes < 0) 0L else bytes
        val unitSuffix = if (showInBits) "b/s" else "B/s"
        
        if (showInBits) b *= 8

        // Accuracy: Below 1000 units, show raw value (e.g. "45 B/s") instead of rounding to 0
        if (b < 1000) return Pair(b.toString(), unitSuffix)
        
        val k = b / 1024.0
        if (k < 9.95) {
            // "1.5 K" is much more accurate than "2 K" or "1 K"
            return Pair(String.format(Locale.ENGLISH, "%.1f", k), "K$unitSuffix")
        }
        if (k < 999.5) {
            return Pair(String.format(Locale.ENGLISH, "%.0f", k), "K$unitSuffix")
        }
        
        val m = k / 1024.0
        if (m < 9.95) {
            return Pair(String.format(Locale.ENGLISH, "%.1f", m), "M$unitSuffix")
        }
        if (m < 999.5) {
            return Pair(String.format(Locale.ENGLISH, "%.0f", m), "M$unitSuffix")
        }
        
        val g = m / 1024.0
        return Pair(String.format(Locale.ENGLISH, "%.1f", g), "G$unitSuffix")
    }
}
