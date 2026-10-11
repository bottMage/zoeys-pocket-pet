package com.example.shortsgesturecontrol

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface

/** Lightweight themed speech artwork; the caller retains all pet/egg anchors and timers. */
internal class CloudSpeechBubble(private val density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
    }
    private val cloud = Path()
    private val tail = Path()
    private val fill = LinearGradient(0f, 0f, 0f, dp(36f),
        Color.rgb(255, 253, 247), Color.rgb(229, 217, 250), Shader.TileMode.CLAMP)

    private fun dp(value: Float) = value * density

    fun draw(canvas: Canvas, label: String, centerX: Float, bottom: Float, viewportWidth: Float, textColor: Int) {
        text.textSize = dp(11f)
        val available = (viewportWidth - dp(14f)).coerceAtLeast(dp(1f))
        val w = (text.measureText(label) + dp(38f)).coerceIn(dp(76f), dp(166f)).coerceAtMost(available)
        val h = dp(36f)
        val left = (centerX - w / 2f).coerceIn(dp(7f), viewportWidth - dp(7f) - w)
        val tip = (centerX - left).coerceIn(w * .22f, w * .78f)

        // One scalloped silhouette, not overlapping outlined circles. Union the
        // speech pointer before stroking so it has no seam across its base.
        cloud.rewind()
        cloud.moveTo(w * .12f, h * .84f)
        cloud.cubicTo(-w * .02f, h * .85f, -w * .02f, h * .36f, w * .13f, h * .32f)
        cloud.cubicTo(w * .10f, h * .07f, w * .27f, -h * .02f, w * .34f, h * .14f)
        cloud.cubicTo(w * .39f, -h * .05f, w * .59f, -h * .05f, w * .65f, h * .14f)
        cloud.cubicTo(w * .74f, -h * .02f, w * .90f, h * .09f, w * .88f, h * .32f)
        cloud.cubicTo(w * 1.02f, h * .35f, w * 1.02f, h * .84f, w * .88f, h * .85f)
        cloud.cubicTo(w * .85f, h * 1.02f, w * .68f, h * 1.03f, w * .61f, h * .91f)
        cloud.cubicTo(w * .54f, h * 1.05f, w * .37f, h * 1.05f, w * .30f, h * .92f)
        cloud.cubicTo(w * .23f, h * 1.03f, w * .13f, h * 1.00f, w * .12f, h * .84f)
        cloud.close()
        tail.rewind()
        tail.moveTo(tip - dp(7f), h * .78f)
        tail.quadTo(tip - dp(4f), h + dp(3f), tip, h + dp(8f))
        tail.quadTo(tip + dp(3f), h + dp(1f), tip + dp(7f), h * .78f)
        tail.close()
        cloud.op(tail, Path.Op.UNION)

        canvas.save()
        canvas.translate(left, bottom - h)
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(38, 65, 39, 92)
        canvas.save()
        canvas.translate(0f, dp(2f))
        canvas.drawPath(cloud, paint)
        canvas.restore()
        paint.color = Color.WHITE
        paint.shader = fill
        canvas.drawPath(cloud, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.5f)
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.rgb(92, 58, 120)
        canvas.drawPath(cloud, paint)
        paint.style = Paint.Style.FILL

        // Keep text inside the broad central area, clear of the scalloped edges.
        val textWidth = text.measureText(label)
        if (textWidth > w - dp(30f)) text.textSize *= (w - dp(30f)).coerceAtLeast(dp(1f)) / textWidth
        text.color = textColor
        val baseline = h * .54f - (text.ascent() + text.descent()) / 2f
        canvas.drawText(label, w / 2f, baseline, text)
        canvas.restore()
    }
}
