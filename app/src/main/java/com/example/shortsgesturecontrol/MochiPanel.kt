package com.example.shortsgesturecontrol

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.min

/** Native, scrollable controls with explicit colors, independent of the phone's button theme. */
internal class MochiPanel(private val activity: Activity, title: String, subtitle: String) {
    companion object {
        val INK = Color.rgb(65, 36, 85)
        val MUTED = Color.rgb(112, 88, 125)
        val PURPLE = Color.rgb(108, 66, 170)
        val LILAC = Color.rgb(235, 225, 253)
        val MINT = Color.rgb(220, 247, 236)
        val PINK = Color.rgb(255, 226, 239)
        val GOLD = Color.rgb(255, 241, 207)
        val PAPER = Color.rgb(255, 248, 252)
    }

    fun px(dp: Int): Int = (dp * activity.resources.displayMetrics.density + .5f).toInt()

    val root = column().apply {
        background = rounded(PAPER, 26)
        clipToOutline = true
        isFocusableInTouchMode = true
    }
    val body = column().apply { setPadding(px(18), px(6), px(18), px(12)) }
    val scroll = ScrollView(activity).apply {
        isFillViewport = true
        clipToPadding = false
        addView(body)
    }
    val dialog: AlertDialog = AlertDialog.Builder(activity).create().apply { setView(root, 0, 0, 0, 0) }

    init {
        val header = column().apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(LILAC, PINK)).apply {
                val radius = px(26).toFloat()
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            }
            setPadding(px(20), px(20), px(20), px(18))
            addView(label(title, 25f, bold = true))
            addView(label(subtitle, 13f, MUTED).apply { setPadding(0, px(5), 0, 0) })
        }
        root.addView(header)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = column().apply {
            setPadding(px(18), px(6), px(18), px(12))
            addView(button("Done", LILAC) { dialog.dismiss() })
        }
        root.addView(footer)
    }

    fun show() {
        root.requestFocus()
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            val metrics = activity.resources.displayMetrics
            setLayout(min(metrics.widthPixels - px(28), px(560)), min((metrics.heightPixels * .87f).toInt(), px(760)))
        }
    }

    fun column(): LinearLayout = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    fun row(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun label(text: String, size: Float = 14f, color: Int = INK, bold: Boolean = false): TextView =
        TextView(activity).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    fun rounded(color: Int, radius: Int = 18): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = px(radius).toFloat()
    }

    fun button(text: String, color: Int = PURPLE, action: () -> Unit): Button = Button(activity).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        setTextColor(if (color == PURPLE) Color.WHITE else INK)
        minWidth = 0
        minimumWidth = 0
        minHeight = px(48)
        minimumHeight = px(48)
        setPadding(px(12), px(8), px(12), px(8))
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(36, 65, 36, 85)), rounded(color, 14), null)
        backgroundTintList = null
        stateListAnimator = null
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    fun heading(parent: LinearLayout, text: String) {
        parent.addView(label(text.uppercase(java.util.Locale.ROOT), 11f, PURPLE, true).apply {
            letterSpacing = .10f
            setPadding(px(2), px(15), 0, px(7))
        })
    }

    fun setting(title: String, description: String, color: Int, action: () -> Unit): Button =
        button(title, color, action).apply {
            val content = SpannableString("$title\n$description")
            content.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            content.setSpan(RelativeSizeSpan(12f / 14f), title.length + 1, content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            content.setSpan(ForegroundColorSpan(MUTED), title.length + 1, content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text = content
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            minHeight = px(68)
            minimumHeight = px(68)
            setPadding(px(14), px(10), px(14), px(10))
            setLineSpacing(px(3).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(6) }
        }

    fun card(parent: LinearLayout, color: Int = Color.WHITE): LinearLayout = column().apply {
        background = rounded(color)
        setPadding(px(14), px(12), px(14), px(12))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = px(8) })
    }

    fun gap(parent: LinearLayout, height: Int = 8) {
        parent.addView(View(activity), LinearLayout.LayoutParams(1, px(height)))
    }

    fun pair(parent: LinearLayout, first: Button, second: Button) {
        val row = row()
        row.addView(first, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = px(8) })
        row.addView(second, LinearLayout.LayoutParams(0, -2, 1f))
        parent.addView(row)
    }
}
