package com.example.shortsgesturecontrol

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.floor
import kotlin.math.sin

/** Short, independently phased drops. Stateless elapsed-time motion, no background work. */
internal class RainEffect(private val density: Float) {
    private data class Drop(val speed: Float, val length: Float, val width: Float,
                            val opacity: Int, val phase: Double, val landingInset: Float)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val drops = Array(84) { index ->
        val near = index in 44..71
        Drop(
            speed = if (near) 310f + random(index, 1L) * 150f else 175f + random(index, 1L) * 110f,
            length = if (near) 6f + random(index, 2L) * 5f else 3f + random(index, 2L) * 4f,
            width = if (near) .9f + random(index, 3L) * .35f else .5f + random(index, 3L) * .25f,
            opacity = if (near) 118 + (random(index, 4L) * 38).toInt() else 58 + (random(index, 4L) * 35).toInt(),
            phase = random(index, 5L).toDouble(),
            landingInset = if (near) 3f + random(index, 6L) * 9f else 14f + random(index, 6L) * 22f
        )
    }

    private fun dp(value: Float) = value * density

    // Stable pseudo-random offsets, independent of the frame rate. A fresh
    // horizontal spawn each cycle avoids repeating vertical columns.
    private fun random(index: Int, cycle: Long): Float {
        var bits = cycle * 6364136223846793005L + (index + 1L) * 1442695040888963407L
        bits = (bits xor (bits ushr 30)) * -4658895280553007687L
        bits = (bits xor (bits ushr 27)) * -7723592293110705685L
        bits = bits xor (bits ushr 31)
        return ((bits ushr 40) and 0xffffffL).toFloat() / 16777216f
    }

    fun draw(canvas: Canvas, scene: RectF, now: Long, daylight: Float) {
        if (scene.width() <= 0f || scene.height() <= 0f) return
        val seconds = now.coerceAtLeast(0L) / 1000.0
        val night = daylight < .2f
        val red = if (night) 188 else 72
        val green = if (night) 217 else 141
        val blue = if (night) 248 else 192
        val count = if (night) 72 else 84
        for (index in 0 until count) {
            val drop = drops[index]
            val opacity = if (night) drop.opacity else (drop.opacity * 1.1f).toInt()
            val length = dp(drop.length)
            val startY = scene.top - length - dp(2f)
            val ground = scene.bottom - dp(drop.landingInset)
            val distance = ground - startY
            val fallSeconds = distance / dp(drop.speed)
            val splashSeconds = .16
            val period = fallSeconds + splashSeconds
            val clock = seconds / period + drop.phase
            val cycle = floor(clock).toLong()
            val age = (clock - cycle) * period
            val drift = distance * .12f
            val startX = scene.left + random(index, cycle + 17L) * (scene.width() + drift)
            paint.strokeWidth = dp(drop.width)
            if (age < fallSeconds) {
                val progress = (age / fallSeconds).toFloat()
                val x = startX - drift * progress
                val y = startY + distance * progress
                // The short tail points back along the actual velocity, unlike
                // the old left-slanted lines translating right as one sheet.
                paint.color = Color.argb(opacity / 3, red, green, blue)
                canvas.drawLine(x + length * .12f, y - length, x, y, paint)
                paint.color = Color.argb(opacity, red, green, blue)
                canvas.drawLine(x + length * .054f, y - length * .45f, x, y, paint)
            } else if (index in 44..71) {
                // Two tiny flicks at that drop's landing point, not an overlay
                // of synchronized rings. Far drops stay unobtrusive.
                val progress = ((age - fallSeconds) / splashSeconds).toFloat()
                val x = startX - drift
                val spread = dp(1f + 4f * progress)
                val lift = dp(2.8f) * sin(progress * Math.PI).toFloat()
                val y = ground - lift
                paint.strokeWidth = dp(.7f)
                paint.color = Color.argb((opacity * .65f * (1f - progress)).toInt(), red, green, blue)
                canvas.drawLine(x - spread, y, x - spread + dp(1f), y + dp(1f), paint)
                canvas.drawLine(x + spread, y, x + spread - dp(1f), y + dp(1f), paint)
            }
        }
    }
}
