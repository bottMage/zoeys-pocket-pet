package com.example.shortsgesturecontrol

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.min
import kotlin.math.sin

/** Overlapping whole clouds, not duplicated wave contours. Cached vector artwork only. */
internal class RainCloudCover(private val density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun cloudShape(variant: Int) = Path().apply {
        moveTo(-66f, 22f)
        cubicTo(-94f, 22f, -99f, -10f, -78f, -23f)
        cubicTo(-79f, -46f, -45f, -51f, -31f, -30f)
        val crown = when (variant) { 0 -> -68f; 1 -> -57f; else -> -76f }
        cubicTo(-25f, crown, 27f, crown, 34f, -30f)
        if (variant == 1) {
            cubicTo(49f, -43f, 72f, -32f, 73f, -13f)
            cubicTo(102f, -9f, 105f, 22f, 77f, 29f)
        } else {
            cubicTo(57f, -55f, 89f, -37f, 82f, -12f)
            cubicTo(105f, -2f, 100f, 24f, 77f, 29f)
        }
        cubicTo(36f, 38f, -29f, 35f, -66f, 22f)
        close()
    }
    private val shapes = Array(3) { cloudShape(it) }
    private data class Cloud(val x: Float, val y: Float, val size: Float,
                             val shape: Int, val shade: Int, val phase: Float)
    private val clouds = arrayOf(
        Cloud(-.08f, 58f, .90f, 0, 0, .2f),
        Cloud(.27f, 65f, .96f, 2, 0, 1.7f),
        Cloud(.65f, 55f, .88f, 1, 0, 3.1f),
        Cloud(1.05f, 66f, .98f, 0, 0, 4.6f),
        Cloud(-.02f, 87f, .96f, 1, 1, 2.2f),
        Cloud(.26f, 99f, .80f, 0, 2, 4.1f),
        Cloud(.52f, 84f, .99f, 2, 1, .7f),
        Cloud(.79f, 100f, .86f, 1, 2, 3.4f),
        Cloud(1.06f, 89f, .94f, 0, 1, 5.1f)
    )
    private fun gradient(top: Int, bottom: Int) = LinearGradient(0f, -60f, 0f, 35f, top, bottom, Shader.TileMode.CLAMP)
    private val dayFills = arrayOf(
        gradient(Color.rgb(111, 133, 155), Color.rgb(85, 108, 133)),
        gradient(Color.rgb(158, 176, 192), Color.rgb(120, 142, 163)),
        gradient(Color.rgb(145, 166, 184), Color.rgb(105, 130, 153))
    )
    private val nightFills = arrayOf(
        gradient(Color.rgb(55, 68, 95), Color.rgb(38, 51, 76)),
        gradient(Color.rgb(94, 111, 138), Color.rgb(63, 81, 108)),
        gradient(Color.rgb(81, 100, 128), Color.rgb(52, 73, 99))
    )
    private fun haze(color: Int) = LinearGradient(0f, 0f, 0f, 1f,
        intArrayOf(color, color, color and 0x00ffffff),
        floatArrayOf(0f, .48f, 1f), Shader.TileMode.CLAMP)
    private val dayHaze = haze(Color.rgb(109, 129, 151))
    private val nightHaze = haze(Color.rgb(39, 48, 75))

    fun draw(canvas: Canvas, scene: RectF, now: Long, daylight: Float) {
        val height = min(140f * density, scene.height() * .44f)
        if (height <= 0f || scene.width() <= 0f) return
        val night = daylight < .2f
        val time = now.coerceAtLeast(0L) / 1000.0
        // Diffuse cover has no visible lower edge. Whole clouds in front have
        // their own complete silhouettes, offsets, sizes and shaded volumes.
        canvas.save()
        canvas.translate(scene.left, scene.top)
        canvas.scale(scene.width(), height)
        paint.color = Color.WHITE
        paint.shader = if (night) nightHaze else dayHaze
        canvas.drawRect(0f, 0f, 1f, 1f, paint)
        canvas.restore()
        val fit = min(1f, min(scene.width() / (330f * density), scene.height() / (330f * density)))
        for (cloud in clouds) {
            val drift = sin(time / 35.0 + cloud.phase).toFloat() * 4f * density * fit
            val scale = cloud.size * density * fit
            canvas.save()
            canvas.translate(scene.left + scene.width() * cloud.x + drift, scene.top + cloud.y * density * fit)
            canvas.scale(scale, scale)
            paint.shader = if (night) nightFills[cloud.shade] else dayFills[cloud.shade]
            canvas.drawPath(shapes[cloud.shape], paint)
            canvas.restore()
        }
        paint.shader = null
    }

    fun shadeScene(canvas: Canvas, scene: RectF, daylight: Float) {
        paint.color = Color.argb(if (daylight < .2f) 8 else 32, 30, 46, 66)
        canvas.drawRect(scene, paint)
    }
}
