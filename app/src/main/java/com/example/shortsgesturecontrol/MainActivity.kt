package com.example.shortsgesturecontrol

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.text.InputType
import android.widget.EditText
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var gameView: PetGameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(65, 44, 112)
        window.navigationBarColor = Color.rgb(35, 24, 63)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        gameView = PetGameView(this)
        setContentView(gameView)
    }

    override fun onPause() {
        gameView.savePet()
        super.onPause()
    }
}

private class PetGameView(context: Context) : View(context) {
    private val appContext = context
    private val prefs = context.getSharedPreferences("zoey_pet", Context.MODE_PRIVATE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = PaintTypeface.rounded() }
    private val pet = PetState(prefs)
    private val buttons = ArrayList<ActionButton>()
    private var message = "Hi Zoey! I'm so happy to see you!"
    private var messageUntil = 0L
    private var messageColor = Color.WHITE
    private var animationStart = SystemClock.uptimeMillis()
    private var lastSaved = animationStart
    private var pressedAction: Action? = null
    private var activeAction: Action? = null
    private var actionUntil = 0L
    private var motionX = .5f
    private var motionDirection = 1f
    private var motionMode = MotionMode.IDLE
    private var motionModeUntil = 0L
    private var motionLastAt = SystemClock.uptimeMillis()
    private var setupMode = !pet.created
    private var setupKind = pet.kind
    private var setupName = pet.name

    init {
        isFocusable = true
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        pet.updateFromClock()
        motionModeUntil = motionLastAt + 2200L
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        pet.updateFromClock()
        if (!setupMode && pet.consumeEvolutionEvent()) {
            activeAction = Action.PLAY
            actionUntil = now + 2400L
            message = "Amazing! ${pet.name} evolved!"
            messageColor = Color.rgb(130, 82, 185)
            messageUntil = now + 5000L
            savePet()
        }
        drawBackground(canvas)
        if (setupMode) {
            drawSetup(canvas, now)
            postInvalidateDelayed(100L)
            return
        }
        drawHeader(canvas)
        drawPlayground(canvas, now)
        drawPet(canvas, now)
        drawActionEffects(canvas, now)
        drawMessage(canvas, now)
        drawStats(canvas)
        drawActions(canvas)
        if (now - lastSaved > 30_000L) savePet()
        postInvalidateDelayed(50L)
    }

    private fun drawBackground(canvas: Canvas) {
        val gradient = LinearGradient(0f, 0f, 0f, height.toFloat(), Color.rgb(125, 91, 190), Color.rgb(249, 190, 204), Shader.TileMode.CLAMP)
        paint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
        paint.color = Color.argb(38, 255, 255, 255)
        for (i in 0 until 11) {
            val x = ((i * 97 + 21) % max(1, width)).toFloat()
            val y = 92f + ((i * 71) % max(1, height / 2)).toFloat()
            canvas.drawCircle(x, y, if (i % 3 == 0) 3.5f else 2f, paint)
        }
    }

    private fun drawHeader(canvas: Canvas) {
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.WHITE
        textPaint.textSize = dp(22f)
        textPaint.typeface = PaintTypeface.bold()
        canvas.drawText("ZOEY'S POCKET PET", dp(22f), dp(38f), textPaint)
        textPaint.textSize = dp(13f)
        textPaint.typeface = PaintTypeface.rounded()
        textPaint.color = Color.argb(220, 255, 255, 255)
        canvas.drawText("A tiny friend made just for you", dp(23f), dp(59f), textPaint)

        val pill = RectF(width - dp(114f), dp(18f), width - dp(20f), dp(57f))
        paint.color = Color.argb(70, 48, 27, 89)
        canvas.drawRoundRect(pill, dp(19f), dp(19f), paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(13f)
        textPaint.typeface = PaintTypeface.bold()
        textPaint.color = Color.WHITE
        canvas.drawText("RESET", pill.centerX(), dp(41f), textPaint)
    }

    private fun drawSetup(canvas: Canvas, now: Long) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = dp(25f)
        textPaint.color = Color.WHITE
        canvas.drawText("MAKE YOUR LITTLE FRIEND", width / 2f, dp(42f), textPaint)
        textPaint.textSize = dp(13f)
        textPaint.typeface = PaintTypeface.rounded()
        textPaint.color = Color.argb(225, 255, 255, 255)
        canvas.drawText("Choose a pet, give it a name, then hatch it!", width / 2f, dp(66f), textPaint)

        val cx = width / 2f
        val cy = dp(190f) + sin((now - animationStart) / 1000f * 2f) * dp(4f)
        drawEgg(canvas, cx, cy)

        val nameRect = RectF(dp(28f), dp(305f), width - dp(28f), dp(357f))
        paint.color = Color.argb(70, 54, 30, 92)
        canvas.drawRoundRect(nameRect, dp(18f), dp(18f), paint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = PaintTypeface.rounded()
        textPaint.textSize = dp(11f)
        textPaint.color = Color.argb(210, 255, 255, 255)
        canvas.drawText("PET NAME  •  TAP TO EDIT", nameRect.left + dp(18f), nameRect.top + dp(18f), textPaint)
        textPaint.textSize = dp(19f)
        textPaint.typeface = PaintTypeface.bold()
        textPaint.color = Color.WHITE
        canvas.drawText(setupName, nameRect.left + dp(18f), nameRect.top + dp(41f), textPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(13f)
        textPaint.typeface = PaintTypeface.bold()
        textPaint.color = Color.WHITE
        canvas.drawText("CHOOSE A LOOK", width / 2f, dp(390f), textPaint)
        val gap = dp(8f)
        val optionWidth = (width - dp(36f) - gap * 2f) / 3f
        val kinds = PetKind.values()
        for (i in kinds.indices) {
            val row = i / 3
            val col = i % 3
            val left = dp(18f) + col * (optionWidth + gap)
            val rowTop = dp(405f) + row * dp(57f)
            val rect = RectF(left, rowTop, left + optionWidth, rowTop + dp(49f))
            paint.color = if (setupKind == kinds[i]) Color.WHITE else Color.argb(62, 54, 30, 92)
            canvas.drawRoundRect(rect, dp(16f), dp(16f), paint)
            textPaint.textSize = dp(12f)
            textPaint.color = if (setupKind == kinds[i]) kinds[i].dark else Color.WHITE
            canvas.drawText(kinds[i].label, rect.centerX(), rect.centerY() + dp(4f), textPaint)
        }

        val hatch = RectF(dp(34f), height - dp(86f), width - dp(34f), height - dp(25f))
        paint.color = Color.rgb(255, 208, 137)
        canvas.drawRoundRect(hatch, dp(24f), dp(24f), paint)
        textPaint.textSize = dp(17f)
        textPaint.color = Color.rgb(92, 52, 91)
        canvas.drawText("HATCH ${setupName.uppercase()}!", hatch.centerX(), hatch.centerY() + dp(6f), textPaint)
    }

    private fun drawEgg(canvas: Canvas, cx: Float, cy: Float) {
        paint.color = Color.argb(65, 80, 38, 90)
        canvas.drawOval(RectF(cx - dp(70f), cy + dp(88f), cx + dp(70f), cy + dp(110f)), paint)
        val eggGradient = RadialGradient(cx - dp(25f), cy - dp(35f), dp(115f), Color.rgb(255, 241, 198), Color.rgb(239, 133, 177), Shader.TileMode.CLAMP)
        paint.shader = eggGradient
        canvas.drawOval(RectF(cx - dp(66f), cy - dp(95f), cx + dp(66f), cy + dp(98f)), paint)
        paint.shader = null
        paint.color = Color.argb(100, 255, 255, 255)
        canvas.drawOval(RectF(cx - dp(40f), cy - dp(65f), cx - dp(14f), cy - dp(43f)), paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = dp(24f)
        textPaint.color = Color.argb(210, 255, 255, 255)
        canvas.drawText("?", cx, cy + dp(13f), textPaint)
    }

    private fun drawPlayground(canvas: Canvas, now: Long) {
        val top = dp(77f)
        val bottom = statsTop()
        val screen = RectF(dp(18f), top, width - dp(18f), bottom - dp(10f))
        paint.color = Color.rgb(79, 73, 92)
        canvas.drawRoundRect(RectF(screen.left - dp(4f), screen.top - dp(4f), screen.right + dp(4f), screen.bottom + dp(4f)), dp(15f), dp(15f), paint)
        paint.color = Color.rgb(211, 220, 195)
        canvas.drawRoundRect(screen, dp(11f), dp(11f), paint)

        // Quiet LCD-style background marks; the creature is the focus.
        paint.color = Color.argb(55, 93, 113, 83)
        for (i in 0 until 8) {
            val x = screen.left + dp(22f) + i * dp(39f)
            val y = screen.top + dp(26f) + (i % 2) * dp(20f)
            canvas.drawRect(x, y, x + dp(3f), y + dp(3f), paint)
        }
        paint.color = Color.rgb(126, 143, 105)
        canvas.drawRect(screen.left + dp(18f), screen.bottom - dp(43f), screen.right - dp(18f), screen.bottom - dp(40f), paint)
        canvas.drawRect(screen.left + dp(34f), screen.bottom - dp(39f), screen.right - dp(34f), screen.bottom - dp(36f), paint)
    }

    private fun drawCloud(canvas: Canvas, x: Float, y: Float, scale: Float) {
        canvas.drawCircle(x, y, dp(22f) * scale, paint)
        canvas.drawCircle(x + dp(22f) * scale, y - dp(8f) * scale, dp(28f) * scale, paint)
        canvas.drawCircle(x + dp(52f) * scale, y, dp(20f) * scale, paint)
        canvas.drawRoundRect(RectF(x - dp(5f) * scale, y, x + dp(60f) * scale, y + dp(18f) * scale), dp(10f), dp(10f), paint)
    }

    private fun drawAnimatedPixelPet(canvas: Canvas, now: Long) {
        val top = dp(77f)
        val bottom = statsTop()
        val sceneLeft = dp(32f)
        val sceneRight = width - dp(32f)
        val dt = ((now - motionLastAt).coerceAtLeast(0L)).coerceAtMost(120L) / 1000f
        motionLastAt = now
        if (now >= motionModeUntil && activeAction == null) {
            motionMode = if (motionMode == MotionMode.IDLE) MotionMode.WALK else MotionMode.IDLE
            motionModeUntil = now + if (motionMode == MotionMode.WALK) 1800L else 2400L
            if (motionMode == MotionMode.WALK && motionX <= .08f) motionDirection = 1f
            if (motionMode == MotionMode.WALK && motionX >= .92f) motionDirection = -1f
        }
        if (motionMode == MotionMode.WALK && activeAction == null) {
            motionX += motionDirection * dt * .16f
            if (motionX <= .06f) { motionX = .06f; motionDirection = 1f }
            if (motionX >= .94f) { motionX = .94f; motionDirection = -1f }
        }

        val stageScale = when (pet.stage) {
            "BABY" -> .82f
            "YOUNG" -> .92f
            "TEEN" -> 1f
            "EVOLVED" -> 1.08f
            else -> 1f
        }
        val pixel = dp(7f) * stageScale
        val sprite = when (pet.kind) {
            PetKind.CAT -> arrayOf(
                "     ##++++##       ",
                "    ###++++###      ",
                "   ############     ",
                "  ##+##++++##+##    ",
                "  ##+##++++##+##    ",
                "  ##++++####++##    ",
                "   ##++++++++##     ",
                "   ############     ",
                "  ##############    ",
                " ################   ",
                "##############  ##  ",
                "  ##  ##  ##  ###   ",
                " ##   ##   ##       ",
                "##    ##    ##      "
            )
            PetKind.DOG -> arrayOf(
                " ##++++++++++++##   ",
                "###++++++++++++###  ",
                "##++##########++##  ",
                "##+##++++++##+###  ",
                "##+##++++++##+###  ",
                "##++##########++##  ",
                " ###++++++++++###   ",
                "  ##############    ",
                " ######++++######   ",
                "######++++++######  ",
                "  ##  ##  ##  ##    ",
                " ##   ##  ##   ##   "
            )
            PetKind.BUNNY -> arrayOf(
                "    ##      ##      ",
                "    ##      ##      ",
                "   ###      ###     ",
                "   ###      ###     ",
                "  ##  ######  ##    ",
                " ##  ##++++##  ##   ",
                " ##  ##++++##  ##   ",
                " ##++++++++++++##   ",
                "  ##############    ",
                "   ####++++####     ",
                "   ##  ####  ##     ",
                "  ##   ####   ##    ",
                " ##    ## ##   ##   "
            )
            PetKind.HAMSTER -> arrayOf(
                "   ####++++####     ",
                "  ######++######    ",
                " ##+##############  ",
                "##++##++##++##++##  ",
                "##++##############  ",
                "##++####++####++##  ",
                " ##+##############  ",
                "  ######++######    ",
                "   #### #######     ",
                "  ##  ##  ##  ##    ",
                " ##   ##  ##   ##   "
            )
            PetKind.DRAGON -> arrayOf(
                "        ##          ",
                "       ###          ",
                "  ##   ####   ##    ",
                " ###   ####  ###    ",
                "###   ######  ###   ",
                "    ##++++##        ",
                "   ##++++++##       ",
                "   ##########   ##  ",
                "    ########  ######",
                "      ###           ",
                "     ####  ####     ",
                "    ##  ## ##  ##   ",
                "   ##   ## ##   ##  ",
                "  ##            ##  "
            )
        }
        val pixelInk = Color.rgb(47, 57, 45)
        val pixelShade = Color.rgb(82, 94, 73)
        val spriteWidth = sprite.maxOf { it.length }
        val centerX = sceneLeft + motionX * (sceneRight - sceneLeft)
        val centerY = (top + bottom) * .53f + if (motionMode == MotionMode.IDLE) sin(now / 430f) * dp(2f) else 0f
        val left = centerX - spriteWidth * pixel / 2f
        val spriteTop = centerY - sprite.size * pixel / 2f
        paint.isAntiAlias = false
        for (row in sprite.indices) {
            for (column in sprite[row].indices) {
                val cell = sprite[row][column]
                if (cell == '#' || cell == '+') {
                    paint.color = if (cell == '#') pixelInk else pixelShade
                    val stepOffset = if (motionMode == MotionMode.WALK && row >= sprite.size - 3) {
                        if ((column + (now / 180L).toInt()) % 2 == 0) dp(1f) else -dp(1f)
                    } else 0f
                    canvas.drawRect(left + column * pixel, spriteTop + row * pixel + stepOffset, left + (column + 1) * pixel - dp(.6f), spriteTop + (row + 1) * pixel - dp(.6f) + stepOffset, paint)
                }
            }
        }
        paint.isAntiAlias = true
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = dp(13f)
        textPaint.color = Color.rgb(78, 75, 88)
        canvas.drawText(pet.name.uppercase(), centerX, centerY + dp(112f), textPaint)
    }

    private fun drawPixelPet(canvas: Canvas, now: Long) {
        drawAnimatedPixelPet(canvas, now)
        return

        // Earlier block renderer retained below only as a reference while the
        // hand-authored animated bitmap sprites are refined.
        val top = dp(77f)
        val bottom = statsTop()
        val seconds = (now - animationStart) / 1000f
        val jump = if (activeAction == Action.PLAY) sin(seconds * 12f) * dp(4f) else 0f
        val cx = dp(32f) + motionX * (width - dp(64f))
        val cy = (top + bottom) * .53f + jump
        val pixel = dp(8f) * when (pet.stage) {
            "BABY" -> .82f
            "YOUNG" -> .92f
            "TEEN" -> 1f
            "EVOLVED" -> 1.06f
            else -> 1f
        }

        paint.isAntiAlias = false
        val sprite = when (pet.kind) {
            PetKind.CAT -> arrayOf(
                "   ##        ##   ",
                "  ###        ###  ",
                "  ###        ###  ",
                "  ##############  ",
                " ##  ##    ##  ## ",
                " ##  ##    ##  ## ",
                " ################  ",
                " ##     ##     ## ",
                " ##    ####    ## ",
                "  ##############  ",
                "    ##  ##  ##    ",
                "   ##   ##   ##   "
            )
            PetKind.DOG -> arrayOf(
                " ##            ## ",
                "###            ###",
                "###  ########  ###",
                "##  ##########  ##",
                "##  ##  ##  ##  ##",
                "##  ##########  ##",
                "##   ########   ##",
                " ###############  ",
                "  ####### ######  ",
                "   ####   ####    ",
                "  ## ##   ## ##   ",
                " ##  ##   ##  ##  "
            )
            PetKind.BUNNY -> arrayOf(
                "   ###      ###   ",
                "   ###      ###   ",
                "   ###      ###   ",
                "   ###      ###   ",
                "   ###########    ",
                "  #############   ",
                "  ##  ## ##  ##   ",
                "  ##   ###   ##   ",
                "  ##  #####  ##   ",
                "   ###########    ",
                "    ## ### ##     ",
                "   ##  ###  ##    "
            )
            PetKind.HAMSTER -> arrayOf(
                "    ####  ####    ",
                "   ###### ######  ",
                "  ##############  ",
                " ##  ##    ##  ## ",
                " ##  ########  ## ",
                " ##   ######   ## ",
                "  ##############  ",
                "   #### ######    ",
                "  ###  ###  ###   ",
                " ##   ###  ### ## "
            )
            PetKind.DRAGON -> arrayOf(
                "        ##          ",
                "       ###          ",
                "  ##   ####   ##    ",
                " ###   ####  ###    ",
                "###   ######  ###   ",
                "     #########      ",
                "    ###########     ",
                "    ##########      ",
                "     #######        ",
                "      ###   ######  ",
                "     ###            ",
                "    ##   ##         ",
                "   ##   ###         "
            )
        }
        val spriteWidth = sprite.maxOf { it.length }
        val left = cx - spriteWidth * pixel / 2f
        val spriteTop = cy - sprite.size * pixel / 2f
        val ink = Color.rgb(47, 57, 45)
        for (row in sprite.indices) {
            for (column in sprite[row].indices) {
                if (sprite[row][column] == '#') {
                    paint.color = ink
                    canvas.drawRect(
                        left + column * pixel,
                        spriteTop + row * pixel,
                        left + (column + 1) * pixel - dp(.7f),
                        spriteTop + (row + 1) * pixel - dp(.7f),
                        paint
                    )
                }
            }
        }
        paint.isAntiAlias = true

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = dp(13f)
        textPaint.color = Color.rgb(78, 75, 88)
        canvas.drawText(pet.name.uppercase(), cx, cy + dp(112f), textPaint)
    }

    private fun drawPixelCreature(canvas: Canvas, p: Float) {
        // Classic LCD pet ink: one dark tone made from square pixels.
        val ink = Color.rgb(47, 57, 45)
        when (pet.kind) {
            PetKind.DRAGON -> drawPixelDragon(canvas, p, ink, ink, ink)
            PetKind.CAT -> drawPixelCat(canvas, p, ink, ink, ink)
            PetKind.DOG -> drawPixelDog(canvas, p, ink, ink, ink)
            PetKind.BUNNY -> drawPixelBunny(canvas, p, ink, ink, ink)
            PetKind.HAMSTER -> drawPixelHamster(canvas, p, ink, ink, ink)
        }
    }

    private fun drawPixelDragon(canvas: Canvas, p: Float, outline: Int, body: Int, light: Int) {
        // A connected side-view dragon: head and snout on the left, four feet,
        // one large wing, a long tail, and unmistakable horns.
        pixelPath(canvas, p, floatArrayOf(1f, 0f, 8f, -11f, 8f, 2f, 5f, 5f), outline)
        pixelPath(canvas, p, floatArrayOf(2f, 0f, 6f, -7f, 6f, 2f, 4f, 3f), body)

        pixelRect(canvas, 0f, 0f, p, 3f, 0f, 7f, 3f, outline)
        pixelRect(canvas, 0f, 0f, p, 8f, 1f, 7f, 2f, outline)
        pixelRect(canvas, 0f, 0f, p, 9f, 1f, 5f, 1f, body)
        pixelRect(canvas, 0f, 0f, p, 13f, -1f, 3f, 3f, outline)
        pixelRect(canvas, 0f, 0f, p, 14f, 0f, 2f, 1f, Color.rgb(255, 223, 105))

        pixelRect(canvas, 0f, 0f, p, -5f, -1f, 10f, 9f, outline)
        pixelRect(canvas, 0f, 0f, p, -4f, 0f, 8f, 7f, body)
        pixelRect(canvas, 0f, 0f, p, -1f, 2f, 4f, 4f, light)

        pixelRect(canvas, 0f, 0f, p, -4f, 6f, 3f, 5f, outline)
        pixelRect(canvas, 0f, 0f, p, 2f, 6f, 3f, 5f, outline)
        pixelRect(canvas, 0f, 0f, p, -3f, 7f, 1f, 3f, body)
        pixelRect(canvas, 0f, 0f, p, 3f, 7f, 1f, 3f, body)
        pixelRect(canvas, 0f, 0f, p, -5f, 11f, 4f, 1f, Color.rgb(255, 223, 105))
        pixelRect(canvas, 0f, 0f, p, 2f, 11f, 4f, 1f, Color.rgb(255, 223, 105))

        pixelRect(canvas, 0f, 0f, p, -8f, -6f, 5f, 6f, outline)
        pixelRect(canvas, 0f, 0f, p, -7f, -5f, 4f, 4f, body)
        pixelRect(canvas, 0f, 0f, p, -12f, -4f, 5f, 3f, outline)
        pixelRect(canvas, 0f, 0f, p, -11f, -3f, 4f, 1f, light)
        pixelRect(canvas, 0f, 0f, p, -6f, -8f, 2f, 3f, Color.rgb(255, 223, 105))
        pixelRect(canvas, 0f, 0f, p, -3f, -8f, 2f, 3f, Color.rgb(255, 223, 105))
        pixelRect(canvas, 0f, 0f, p, -6f, -4f, 2f, 2f, Color.rgb(38, 43, 45))
        pixelRect(canvas, 0f, 0f, p, -12f, -1f, 2f, 1f, Color.rgb(255, 223, 105))

        if (pet.generation >= 1) {
            pixelRect(canvas, 0f, 0f, p, -1f, -2f, 2f, 2f, Color.rgb(255, 223, 105))
            pixelRect(canvas, 0f, 0f, p, 1f, -3f, 2f, 2f, Color.rgb(255, 223, 105))
        }
    }

    private fun drawPixelCat(canvas: Canvas, p: Float, outline: Int, body: Int, light: Int) {
        pixelPath(canvas, p, floatArrayOf(-6f, -5f, -8f, -11f, -2f, -8f, 2f, -8f, 8f, -11f, 6f, -5f), outline)
        pixelRect(canvas, 0f, 0f, p, -5f, -8f, 3f, 3f, light)
        pixelRect(canvas, 0f, 0f, p, 2f, -8f, 3f, 3f, light)
        pixelRect(canvas, 0f, 0f, p, -7f, -4f, 14f, 11f, outline)
        pixelRect(canvas, 0f, 0f, p, -6f, -3f, 12f, 9f, body)
        pixelRect(canvas, 0f, 0f, p, -6f, 7f, 12f, 4f, outline)
        pixelRect(canvas, 0f, 0f, p, -4f, 7f, 3f, 4f, body)
        pixelRect(canvas, 0f, 0f, p, 3f, 7f, 3f, 4f, body)
        pixelRect(canvas, 0f, 0f, p, -4f, -1f, 2f, 2f, Color.rgb(38, 43, 45))
        pixelRect(canvas, 0f, 0f, p, 3f, -1f, 2f, 2f, Color.rgb(38, 43, 45))
        pixelRect(canvas, 0f, 0f, p, -1f, 2f, 2f, 1f, Color.rgb(255, 157, 180))
        pixelRect(canvas, 0f, 0f, p, 7f, 1f, 5f, 2f, outline)
        pixelRect(canvas, 0f, 0f, p, 11f, -1f, 4f, 2f, body)
        pixelRect(canvas, 0f, 0f, p, 14f, -3f, 2f, 5f, outline)
    }

    private fun drawPixelDog(canvas: Canvas, p: Float, outline: Int, body: Int, light: Int) {
        pixelRect(canvas, 0f, 0f, p, -9f, -4f, 4f, 9f, outline)
        pixelRect(canvas, 0f, 0f, p, 5f, -4f, 4f, 9f, outline)
        pixelRect(canvas, 0f, 0f, p, -8f, -3f, 2f, 7f, body)
        pixelRect(canvas, 0f, 0f, p, 6f, -3f, 2f, 7f, body)
        pixelRect(canvas, 0f, 0f, p, -7f, -5f, 14f, 11f, outline)
        pixelRect(canvas, 0f, 0f, p, -6f, -4f, 12f, 9f, body)
        pixelRect(canvas, 0f, 0f, p, -4f, 1f, 8f, 4f, light)
        pixelRect(canvas, 0f, 0f, p, -4f, -1f, 2f, 2f, Color.rgb(38, 43, 45))
        pixelRect(canvas, 0f, 0f, p, 3f, -1f, 2f, 2f, Color.rgb(38, 43, 45))
        pixelRect(canvas, 0f, 0f, p, -1f, 3f, 2f, 2f, outline)
        pixelRect(canvas, 0f, 0f, p, -5f, 7f, 10f, 4f, outline)
        pixelRect(canvas, 0f, 0f, p, -3f, 7f, 2f, 4f, body)
        pixelRect(canvas, 0f, 0f, p, 2f, 7f, 2f, 4f, body)
        pixelRect(canvas, 0f, 0f, p, 8f, 2f, 5f, 2f, outline)
        pixelRect(canvas, 0f, 0f, p, 12f, 1f, 3f, 3f, body)
    }

    private fun drawPixelBunny(canvas: Canvas, p: Float, outline: Int, body: Int, light: Int) {
        pixelRect(canvas, 0f, 0f, p, -8f, -15f, 4f, 12f, outline)
        pixelRect(canvas, 0f, 0f, p, 4f, -15f, 4f, 12f, outline)
        pixelRect(canvas, 0f, 0f, p, -7f, -14f, 2f, 9f, light)
        pixelRect(canvas, 0f, 0f, p, 5f, -14f, 2f, 9f, light)
        pixelRect(canvas, 0f, 0f, p, -8f, -5f, 16f, 12f, outline)
        pixelRect(canvas, 0f, 0f, p, -7f, -4f, 14f, 10f, body)
        pixelRect(canvas, 0f, 0f, p, -4f, -1f, 2f, 2f, Color.rgb(38, 43, 45))
        pixelRect(canvas, 0f, 0f, p, 2f, -1f, 2f, 2f, Color.rgb(38, 43, 45))
        pixelRect(canvas, 0f, 0f, p, -1f, 2f, 2f, 1f, Color.rgb(255, 157, 180))
        pixelRect(canvas, 0f, 0f, p, -5f, 7f, 10f, 4f, outline)
        pixelRect(canvas, 0f, 0f, p, -3f, 7f, 2f, 4f, body)
        pixelRect(canvas, 0f, 0f, p, 2f, 7f, 2f, 4f, body)
        pixelRect(canvas, 0f, 0f, p, 8f, 2f, 4f, 2f, outline)
        pixelRect(canvas, 0f, 0f, p, 11f, 1f, 3f, 2f, light)
    }

    private fun drawPixelHamster(canvas: Canvas, p: Float, outline: Int, body: Int, light: Int) {
        pixelRect(canvas, 0f, 0f, p, -8f, -7f, 4f, 4f, outline)
        pixelRect(canvas, 0f, 0f, p, 4f, -7f, 4f, 4f, outline)
        pixelRect(canvas, 0f, 0f, p, -7f, -6f, 2f, 2f, light)
        pixelRect(canvas, 0f, 0f, p, 5f, -6f, 2f, 2f, light)
        pixelRect(canvas, 0f, 0f, p, -8f, -4f, 16f, 12f, outline)
        pixelRect(canvas, 0f, 0f, p, -7f, -3f, 14f, 10f, body)
        pixelRect(canvas, 0f, 0f, p, -6f, 1f, 4f, 4f, light)
        pixelRect(canvas, 0f, 0f, p, 3f, 1f, 4f, 4f, light)
        pixelRect(canvas, 0f, 0f, p, -4f, -1f, 2f, 2f, Color.rgb(38, 43, 45))
        pixelRect(canvas, 0f, 0f, p, 3f, -1f, 2f, 2f, Color.rgb(38, 43, 45))
        pixelRect(canvas, 0f, 0f, p, -1f, 3f, 2f, 1f, Color.rgb(255, 157, 180))
        pixelRect(canvas, 0f, 0f, p, -5f, 7f, 10f, 4f, outline)
        pixelRect(canvas, 0f, 0f, p, -3f, 7f, 2f, 4f, body)
        pixelRect(canvas, 0f, 0f, p, 2f, 7f, 2f, 4f, body)
    }

    private fun pixelRect(canvas: Canvas, cx: Float, cy: Float, p: Float, x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = Color.rgb(47, 57, 45)
        canvas.drawRect(cx + x * p, cy + y * p, cx + (x + w) * p, cy + (y + h) * p, paint)
    }

    private fun pixelPath(canvas: Canvas, p: Float, points: FloatArray, color: Int) {
        paint.color = Color.rgb(47, 57, 45)
        val path = Path()
        path.moveTo(points[0] * p, points[1] * p)
        var i = 2
        while (i < points.size) {
            path.lineTo(points[i] * p, points[i + 1] * p)
            i += 2
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawPet(canvas: Canvas, now: Long) {
        drawPixelPet(canvas, now)
        return

        // The previous soft mascot renderer is intentionally kept below as a
        // fallback reference while the game moves to its LCD/pixel-pet style.
        val playTop = dp(77f)
        val playBottom = statsTop()
        val seconds = (now - animationStart) / 1000f
        val bob = sin(seconds * 2.1f) * dp(4f)
        val cx = width * .5f
        val reactionBob = when (activeAction) {
            Action.PLAY -> sin(seconds * 13f) * dp(7f)
            Action.FEED -> sin(seconds * 9f) * dp(3f)
            else -> 0f
        }
        val cy = (playTop + playBottom) * .54f + bob + reactionBob
        val stageScale = when (pet.stage) {
            "BABY" -> .86f
            "YOUNG" -> .92f
            "TEEN" -> .98f
            "EVOLVED" -> 1.04f
            else -> 1f
        }
        val scale = min(width / dp(390f), 1.08f) * stageScale

        paint.color = Color.argb(70, 80, 38, 90)
        canvas.drawOval(RectF(cx - dp(76f) * scale, cy + dp(87f) * scale, cx + dp(76f) * scale, cy + dp(111f) * scale), paint)

        drawTail(canvas, cx, cy, scale)
        drawAnimalEars(canvas, cx, cy, scale)

        // A dark outline and one flat body color make this feel more like a classic virtual pet.
        paint.color = pet.kind.dark
        canvas.drawOval(RectF(cx - dp(101f) * scale, cy - dp(91f) * scale, cx + dp(101f) * scale, cy + dp(103f) * scale), paint)
        paint.color = pet.kind.primary
        canvas.drawOval(RectF(cx - dp(92f) * scale, cy - dp(82f) * scale, cx + dp(92f) * scale, cy + dp(94f) * scale), paint)

        paint.color = pet.kind.light
        canvas.drawOval(RectF(cx - dp(56f) * scale, cy + dp(18f) * scale, cx + dp(56f) * scale, cy + dp(78f) * scale), paint)
        drawAnimalFeatures(canvas, cx, cy, scale)
        drawEvolutionFeatures(canvas, cx, cy, scale)

        val eyeY = cy - dp(25f) * scale
        paint.color = pet.kind.dark
        if (activeAction == Action.SLEEP) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(4f) * scale
            paint.strokeCap = Paint.Cap.ROUND
            canvas.drawArc(RectF(cx - dp(55f) * scale, eyeY - dp(4f) * scale, cx - dp(25f) * scale, eyeY + dp(13f) * scale), 15f, 150f, false, paint)
            canvas.drawArc(RectF(cx + dp(25f) * scale, eyeY - dp(4f) * scale, cx + dp(55f) * scale, eyeY + dp(13f) * scale), 15f, 150f, false, paint)
            paint.style = Paint.Style.FILL
        } else {
            canvas.drawOval(RectF(cx - dp(55f) * scale, eyeY - dp(15f) * scale, cx - dp(25f) * scale, eyeY + dp(17f) * scale), paint)
            canvas.drawOval(RectF(cx + dp(25f) * scale, eyeY - dp(15f) * scale, cx + dp(55f) * scale, eyeY + dp(17f) * scale), paint)
            paint.color = Color.WHITE
            canvas.drawCircle(cx - dp(45f) * scale, eyeY - dp(7f) * scale, dp(5f) * scale, paint)
            canvas.drawCircle(cx + dp(35f) * scale, eyeY - dp(7f) * scale, dp(5f) * scale, paint)
        }

        paint.color = Color.argb(120, 255, 255, 255)
        canvas.drawOval(RectF(cx - dp(62f) * scale, cy - dp(70f) * scale, cx - dp(34f) * scale, cy - dp(51f) * scale), paint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = dp(14f)
        textPaint.color = Color.WHITE
        canvas.drawText(pet.name.uppercase(), cx, cy + dp(128f), textPaint)
    }

    private fun drawAnimalEars(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
        paint.color = pet.kind.dark
        when (pet.kind) {
            PetKind.CAT, PetKind.DRAGON -> {
                val left = Path().apply { moveTo(cx - dp(54f) * scale, cy - dp(61f) * scale); lineTo(cx - dp(94f) * scale, cy - dp(116f) * scale); lineTo(cx - dp(80f) * scale, cy - dp(34f) * scale); close() }
                val right = Path().apply { moveTo(cx + dp(54f) * scale, cy - dp(61f) * scale); lineTo(cx + dp(94f) * scale, cy - dp(116f) * scale); lineTo(cx + dp(80f) * scale, cy - dp(34f) * scale); close() }
                canvas.drawPath(left, paint)
                canvas.drawPath(right, paint)
                paint.color = pet.kind.light
                canvas.drawCircle(cx - dp(72f) * scale, cy - dp(68f) * scale, dp(11f) * scale, paint)
                canvas.drawCircle(cx + dp(72f) * scale, cy - dp(68f) * scale, dp(11f) * scale, paint)
            }
            PetKind.DOG -> {
                canvas.drawOval(RectF(cx - dp(105f) * scale, cy - dp(48f) * scale, cx - dp(47f) * scale, cy + dp(50f) * scale), paint)
                canvas.drawOval(RectF(cx + dp(47f) * scale, cy - dp(48f) * scale, cx + dp(105f) * scale, cy + dp(50f) * scale), paint)
                paint.color = pet.kind.primary
                canvas.drawOval(RectF(cx - dp(96f) * scale, cy - dp(42f) * scale, cx - dp(54f) * scale, cy + dp(42f) * scale), paint)
                canvas.drawOval(RectF(cx + dp(54f) * scale, cy - dp(42f) * scale, cx + dp(96f) * scale, cy + dp(42f) * scale), paint)
            }
            PetKind.BUNNY -> {
                canvas.drawOval(RectF(cx - dp(88f) * scale, cy - dp(145f) * scale, cx - dp(43f) * scale, cy - dp(28f) * scale), paint)
                canvas.drawOval(RectF(cx + dp(43f) * scale, cy - dp(145f) * scale, cx + dp(88f) * scale, cy - dp(28f) * scale), paint)
                paint.color = pet.kind.light
                canvas.drawOval(RectF(cx - dp(78f) * scale, cy - dp(133f) * scale, cx - dp(54f) * scale, cy - dp(39f) * scale), paint)
                canvas.drawOval(RectF(cx + dp(54f) * scale, cy - dp(133f) * scale, cx + dp(78f) * scale, cy - dp(39f) * scale), paint)
            }
            PetKind.HAMSTER -> {
                canvas.drawCircle(cx - dp(70f) * scale, cy - dp(60f) * scale, dp(34f) * scale, paint)
                canvas.drawCircle(cx + dp(70f) * scale, cy - dp(60f) * scale, dp(34f) * scale, paint)
                paint.color = pet.kind.light
                canvas.drawCircle(cx - dp(70f) * scale, cy - dp(60f) * scale, dp(23f) * scale, paint)
                canvas.drawCircle(cx + dp(70f) * scale, cy - dp(60f) * scale, dp(23f) * scale, paint)
            }
        }
    }

    private fun drawAnimalFeatures(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
        when (pet.kind) {
            PetKind.CAT -> {
                paint.color = pet.kind.light
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(2f) * scale
                canvas.drawLine(cx - dp(43f) * scale, cy + dp(19f) * scale, cx - dp(86f) * scale, cy + dp(12f) * scale, paint)
                canvas.drawLine(cx + dp(43f) * scale, cy + dp(19f) * scale, cx + dp(86f) * scale, cy + dp(12f) * scale, paint)
                paint.style = Paint.Style.FILL
                paint.color = Color.rgb(255, 153, 178)
                canvas.drawCircle(cx, cy + dp(15f) * scale, dp(6f) * scale, paint)
            }
            PetKind.DOG -> {
                paint.color = Color.rgb(255, 239, 205)
                canvas.drawCircle(cx - dp(23f) * scale, cy + dp(13f) * scale, dp(25f) * scale, paint)
                canvas.drawCircle(cx + dp(23f) * scale, cy + dp(13f) * scale, dp(25f) * scale, paint)
                paint.color = Color.rgb(67, 44, 67)
                canvas.drawCircle(cx, cy + dp(10f) * scale, dp(8f) * scale, paint)
            }
            PetKind.BUNNY -> {
                paint.color = Color.rgb(255, 150, 180)
                canvas.drawCircle(cx, cy + dp(18f) * scale, dp(6f) * scale, paint)
            }
            PetKind.HAMSTER -> {
                paint.color = Color.argb(175, 255, 145, 166)
                canvas.drawCircle(cx - dp(63f) * scale, cy + dp(21f) * scale, dp(17f) * scale, paint)
                canvas.drawCircle(cx + dp(63f) * scale, cy + dp(21f) * scale, dp(17f) * scale, paint)
            }
            PetKind.DRAGON -> {
                paint.color = Color.rgb(255, 231, 132)
                canvas.drawCircle(cx - dp(60f) * scale, cy - dp(105f) * scale, dp(8f) * scale, paint)
                canvas.drawCircle(cx + dp(60f) * scale, cy - dp(105f) * scale, dp(8f) * scale, paint)
                paint.color = Color.rgb(255, 237, 189)
                canvas.drawCircle(cx, cy + dp(16f) * scale, dp(6f) * scale, paint)
            }
        }
    }

    private fun drawEvolutionFeatures(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
        if (pet.generation == 0) return
        when (pet.kind) {
            PetKind.CAT -> {
                paint.color = pet.kind.dark
                paint.strokeWidth = dp(4f) * scale
                paint.strokeCap = Paint.Cap.ROUND
                paint.style = Paint.Style.STROKE
                canvas.drawLine(cx - dp(18f) * scale, cy - dp(62f) * scale, cx - dp(11f) * scale, cy - dp(74f) * scale, paint)
                canvas.drawLine(cx, cy - dp(60f) * scale, cx, cy - dp(76f) * scale, paint)
                canvas.drawLine(cx + dp(18f) * scale, cy - dp(62f) * scale, cx + dp(11f) * scale, cy - dp(74f) * scale, paint)
                paint.style = Paint.Style.FILL
            }
            PetKind.DOG -> {
                paint.color = Color.rgb(73, 151, 176)
                canvas.drawRoundRect(RectF(cx - dp(67f) * scale, cy + dp(51f) * scale, cx + dp(67f) * scale, cy + dp(66f) * scale), dp(6f), dp(6f), paint)
                paint.color = Color.rgb(255, 224, 102)
                canvas.drawCircle(cx, cy + dp(58f) * scale, dp(7f) * scale, paint)
            }
            PetKind.BUNNY -> {
                paint.color = Color.rgb(255, 219, 92)
                canvas.drawCircle(cx + dp(68f) * scale, cy - dp(83f) * scale, dp(9f) * scale, paint)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.textSize = dp(13f) * scale
                textPaint.color = Color.WHITE
                canvas.drawText("✦", cx + dp(68f) * scale, cy - dp(78f) * scale, textPaint)
            }
            PetKind.HAMSTER -> {
                paint.color = Color.rgb(255, 219, 92)
                canvas.drawOval(RectF(cx - dp(78f) * scale, cy - dp(5f) * scale, cx - dp(65f) * scale, cy + dp(22f) * scale), paint)
                canvas.drawOval(RectF(cx + dp(65f) * scale, cy - dp(5f) * scale, cx + dp(78f) * scale, cy + dp(22f) * scale), paint)
            }
            PetKind.DRAGON -> {
                paint.color = pet.kind.dark
                val leftWing = Path().apply { moveTo(cx - dp(72f) * scale, cy + dp(45f) * scale); lineTo(cx - dp(132f) * scale, cy - dp(12f) * scale); lineTo(cx - dp(120f) * scale, cy + dp(69f) * scale); close() }
                val rightWing = Path().apply { moveTo(cx + dp(72f) * scale, cy + dp(45f) * scale); lineTo(cx + dp(132f) * scale, cy - dp(12f) * scale); lineTo(cx + dp(120f) * scale, cy + dp(69f) * scale); close() }
                canvas.drawPath(leftWing, paint)
                canvas.drawPath(rightWing, paint)
                paint.color = pet.kind.primary
                canvas.drawCircle(cx - dp(112f) * scale, cy + dp(21f) * scale, dp(7f) * scale, paint)
                canvas.drawCircle(cx + dp(112f) * scale, cy + dp(21f) * scale, dp(7f) * scale, paint)
            }
        }
    }

    private fun drawTail(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
        if (pet.kind == PetKind.BUNNY || pet.kind == PetKind.HAMSTER) {
            paint.color = Color.WHITE
            canvas.drawCircle(cx + dp(94f) * scale, cy + dp(57f) * scale, dp(24f) * scale, paint)
            return
        }
        val tail = Path().apply {
            moveTo(cx + dp(72f) * scale, cy + dp(42f) * scale)
            cubicTo(cx + dp(135f) * scale, cy + dp(88f) * scale, cx + dp(135f) * scale, cy - dp(15f) * scale, cx + dp(103f) * scale, cy - dp(2f) * scale)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = dp(24f) * scale
        paint.color = pet.kind.dark
        canvas.drawPath(tail, paint)
        paint.strokeWidth = dp(14f) * scale
        paint.color = pet.kind.primary
        canvas.drawPath(tail, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawActionEffects(canvas: Canvas, now: Long) {
        val action = activeAction ?: return
        if (now >= actionUntil) {
            activeAction = null
            return
        }
        val seconds = (now - (actionUntil - 1600L)) / 1000f
        val cx = dp(32f) + motionX * (width - dp(64f))
        val cy = (dp(77f) + statsTop()) * .54f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.bold()
        when (action) {
            Action.FEED -> {
                textPaint.textSize = dp(28f)
                textPaint.color = Color.rgb(255, 238, 190)
                canvas.drawText("●", cx + sin(seconds * 5f) * dp(20f), cy - dp(104f) - seconds * dp(7f), textPaint)
                textPaint.textSize = dp(15f)
                canvas.drawText("YUM!", cx, cy - dp(132f), textPaint)
            }
            Action.PLAY -> {
                textPaint.textSize = dp(24f)
                textPaint.color = Color.rgb(255, 244, 166)
                canvas.drawText("★", cx - dp(94f), cy - dp(25f) + sin(seconds * 6f) * dp(10f), textPaint)
                canvas.drawText("★", cx + dp(94f), cy - dp(43f) + cos(seconds * 5f) * dp(10f), textPaint)
                textPaint.textSize = dp(15f)
                canvas.drawText("WHEEE!", cx, cy - dp(132f), textPaint)
            }
            Action.BATH -> {
                textPaint.textSize = dp(24f)
                textPaint.color = Color.argb(220, 255, 255, 255)
                canvas.drawText("○  ○  ○", cx, cy - dp(118f) - sin(seconds * 4f) * dp(8f), textPaint)
                textPaint.textSize = dp(15f)
                canvas.drawText("SPARKLY!", cx, cy - dp(142f), textPaint)
            }
            Action.SLEEP -> {
                textPaint.textSize = dp(22f)
                textPaint.color = Color.rgb(239, 237, 255)
                canvas.drawText("Z  Z", cx + dp(70f), cy - dp(90f) - seconds * dp(5f), textPaint)
                textPaint.textSize = dp(15f)
                canvas.drawText("SWEET DREAMS", cx, cy - dp(132f), textPaint)
            }
        }
    }

    private fun drawMessage(canvas: Canvas, now: Long) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.rounded()
        textPaint.textSize = dp(12f)
        textPaint.color = if (now < messageUntil) Color.rgb(47, 57, 45) else Color.rgb(87, 96, 75)
        val shortMessage = if (message.length > 31) message.take(28) + "..." else message
        canvas.drawText(shortMessage, width / 2f, dp(101f), textPaint)
    }

    private fun drawStats(canvas: Canvas) {
        val top = statsTop()
        paint.color = Color.argb(245, 255, 249, 246)
        canvas.drawRoundRect(RectF(0f, top, width.toFloat(), height.toFloat()), dp(28f), dp(28f), paint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = dp(17f)
        textPaint.color = Color.rgb(68, 43, 90)
        canvas.drawText("${pet.name.uppercase()}'S LITTLE CHECK-IN", dp(22f), top + dp(29f), textPaint)
        textPaint.textSize = dp(12f)
        textPaint.typeface = PaintTypeface.rounded()
        textPaint.color = Color.rgb(130, 102, 140)
        canvas.drawText("${pet.stage} • AGE ${pet.ageLabel} • CARE ${pet.carePercent.roundToInt()}%", dp(22f), top + dp(48f), textPaint)
        textPaint.textSize = dp(10f)
        textPaint.color = Color.rgb(160, 133, 155)
        canvas.drawText("EVOLUTION ${pet.evolutionProgress.roundToInt()}% • ${pet.evolutionHint}", dp(22f), top + dp(61f), textPaint)

        val reset = newPetRect()
        paint.color = Color.rgb(244, 226, 238)
        canvas.drawRoundRect(reset, dp(15f), dp(15f), paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = dp(11f)
        textPaint.color = Color.rgb(122, 69, 123)
        canvas.drawText("NEW PET", reset.centerX(), reset.centerY() + dp(4f), textPaint)

        val values = listOf(pet.hunger to "HUNGER", pet.joy to "JOY", pet.energy to "ENERGY", pet.clean to "CLEAN")
        val colors = intArrayOf(Color.rgb(245, 143, 90), Color.rgb(239, 91, 145), Color.rgb(117, 106, 220), Color.rgb(67, 177, 155))
        val colWidth = (width - dp(44f)) / 2f
        for (i in values.indices) {
            val col = i % 2
            val row = i / 2
            val x = dp(22f) + col * (colWidth + dp(10f))
            val y = top + dp(68f) + row * dp(42f)
            textPaint.textSize = dp(11f)
            textPaint.typeface = PaintTypeface.bold()
            textPaint.color = Color.rgb(105, 82, 113)
            canvas.drawText(values[i].second, x, y, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${values[i].first.roundToInt()}%", x + colWidth, y, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
            paint.color = Color.rgb(237, 225, 233)
            canvas.drawRoundRect(RectF(x, y + dp(8f), x + colWidth, y + dp(15f)), dp(4f), dp(4f), paint)
            paint.color = colors[i]
            canvas.drawRoundRect(RectF(x, y + dp(8f), x + colWidth * values[i].first / 100f, y + dp(15f)), dp(4f), dp(4f), paint)
        }
    }

    private fun drawActions(canvas: Canvas) {
        buttons.clear()
        val top = height - dp(150f)
        val gap = dp(10f)
        val left = dp(18f)
        val buttonWidth = (width - left * 2f - gap) / 2f
        val buttonHeight = dp(55f)
        val actions = listOf(Action.FEED, Action.PLAY, Action.BATH, Action.SLEEP)
        val fills = intArrayOf(Color.rgb(255, 225, 170), Color.rgb(255, 193, 216), Color.rgb(190, 232, 220), Color.rgb(198, 205, 255))
        val labels = listOf("FEED", "PLAY", "BATH", "SLEEP")
        val glyphs = listOf("+", "★", "✦", "Z")
        for (i in actions.indices) {
            val x = left + (i % 2) * (buttonWidth + gap)
            val y = top + (i / 2) * (buttonHeight + gap)
            val rect = RectF(x, y, x + buttonWidth, y + buttonHeight)
            buttons.add(ActionButton(actions[i], rect))
            paint.color = if (pressedAction == actions[i]) Color.rgb(255, 255, 255) else fills[i]
            canvas.drawRoundRect(rect, dp(18f), dp(18f), paint)
            paint.color = Color.argb(32, 67, 39, 95)
            canvas.drawRoundRect(RectF(rect.left, rect.bottom - dp(5f), rect.right, rect.bottom + dp(2f)), dp(5f), dp(5f), paint)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = PaintTypeface.bold()
            textPaint.textSize = dp(17f)
            textPaint.color = Color.rgb(76, 49, 94)
            canvas.drawText(glyphs[i], rect.left + dp(17f), rect.top + dp(35f), textPaint)
            textPaint.textSize = dp(13f)
            canvas.drawText(labels[i], rect.left + dp(45f), rect.top + dp(34f), textPaint)
        }
    }

    private fun statsTop(): Float = height - dp(330f)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (setupMode) return handleSetupTouch(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedAction = buttons.firstOrNull { it.rect.contains(event.x, event.y) }?.action
                if (newPetRect().contains(event.x, event.y) || headerResetRect().contains(event.x, event.y)) pressedAction = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val action = buttons.firstOrNull { it.rect.contains(event.x, event.y) }?.action
                if (action != null && action == pressedAction) perform(action)
                if (newPetRect().contains(event.x, event.y) || headerResetRect().contains(event.x, event.y)) confirmNewPet()
                pressedAction = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedAction = null
                invalidate()
                return true
            }
        }
        return true
    }

    private fun handleSetupTouch(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val nameRect = RectF(dp(28f), dp(305f), width - dp(28f), dp(357f))
        if (nameRect.contains(event.x, event.y)) {
            editName()
            return true
        }
        val gap = dp(9f)
        val optionWidth = (width - dp(36f) - gap * 2f) / 3f
        PetKind.values().forEachIndexed { index, kind ->
            val row = index / 3
            val col = index % 3
            val left = dp(18f) + col * (optionWidth + gap)
            val rowTop = dp(405f) + row * dp(57f)
            if (RectF(left, rowTop, left + optionWidth, rowTop + dp(49f)).contains(event.x, event.y)) {
                setupKind = kind
                invalidate()
                return true
            }
        }
        val hatch = RectF(dp(34f), height - dp(86f), width - dp(34f), height - dp(25f))
        if (hatch.contains(event.x, event.y)) {
            pet.hatch(setupName, setupKind)
            setupMode = false
            message = "Welcome, ${pet.name}! Let's grow together."
            messageUntil = SystemClock.uptimeMillis() + 5000L
            savePet()
            invalidate()
        }
        return true
    }

    private fun newPetRect(): RectF = RectF(width - dp(94f), statsTop() + dp(14f), width - dp(18f), statsTop() + dp(46f))

    private fun headerResetRect(): RectF = RectF(width - dp(114f), dp(18f), width - dp(20f), dp(57f))

    private fun editName() {
        val input = EditText(appContext).apply {
            setText(setupName)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = "Pet name"
        }
        AlertDialog.Builder(appContext)
            .setTitle("Name your pet")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                setupName = input.text.toString().trim().take(14).ifBlank { "Mochi" }
                invalidate()
            }
            .show()
    }

    private fun confirmNewPet() {
        AlertDialog.Builder(appContext)
            .setTitle("Start a new pet?")
            .setMessage("Your current pet will stay in this game until you choose a new one. Start over now?")
            .setNegativeButton("KEEP PET", null)
            .setPositiveButton("NEW PET") { _, _ ->
                pet.prepareNewPet()
                setupKind = pet.kind
                setupName = pet.name
                setupMode = true
                invalidate()
            }
            .show()
    }

    private fun perform(action: Action) {
        val result = pet.apply(action)
        activeAction = action
        actionUntil = SystemClock.uptimeMillis() + 1600L
        message = result.first
        messageColor = result.second
        messageUntil = SystemClock.uptimeMillis() + 3500L
        savePet()
    }

    fun savePet() {
        pet.save()
        lastSaved = SystemClock.uptimeMillis()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private data class ActionButton(val action: Action, val rect: RectF)

    private enum class Action { FEED, PLAY, BATH, SLEEP }

    private enum class MotionMode { IDLE, WALK }

    private enum class PetKind(val label: String, val light: Int, val primary: Int, val dark: Int) {
        CAT("CAT", Color.rgb(239, 220, 190), Color.rgb(189, 139, 105), Color.rgb(108, 74, 75)),
        DOG("DOG", Color.rgb(255, 239, 205), Color.rgb(214, 174, 123), Color.rgb(113, 78, 65)),
        BUNNY("BUNNY", Color.rgb(255, 207, 214), Color.rgb(245, 166, 186), Color.rgb(157, 83, 116)),
        HAMSTER("HAMSTER", Color.rgb(255, 222, 164), Color.rgb(227, 168, 91), Color.rgb(142, 92, 53)),
        DRAGON("DRAGON", Color.rgb(194, 235, 177), Color.rgb(106, 184, 126), Color.rgb(47, 104, 82))
    }

    private class PetState(private val prefs: android.content.SharedPreferences) {
        var hunger = readMetric("hunger", 78f)
        var joy = readMetric("joy", 82f)
        var energy = readMetric("energy", 74f)
        var clean = readMetric("clean", 88f)
        var growth = prefs.getFloat("growth", 0f)
        var generation = prefs.getInt("generation", 0).coerceIn(0, 2)
        private var ageMillis = prefs.getLong("age_millis", 0L)
        private var goodCareMillis = prefs.getLong("good_care_millis", 0L)
        private var totalCareMillis = prefs.getLong("total_care_millis", 0L)
        var name = prefs.getString("name", "Mochi") ?: "Mochi"
        var kind = prefs.getString("kind", PetKind.BUNNY.name)?.let { value ->
            PetKind.values().firstOrNull { it.name == value }
        } ?: PetKind.BUNNY
        var created = prefs.getBoolean("created", false)
        var hatched = prefs.getBoolean("hatched", false)
        private var lastUpdate = prefs.getLong("last_update", System.currentTimeMillis())
        private var evolutionEvent = false

        val stage: String
            get() = when {
                !hatched -> "EGG"
                generation >= 2 -> "EVOLVED"
                generation == 1 -> "TEEN"
                ageHours < 12f -> "BABY"
                else -> "YOUNG"
            }

        val level: Int
            get() = when (stage) {
                "BABY", "YOUNG" -> 1
                "TEEN" -> 2
                "EVOLVED" -> 3
                else -> 0
            }

        val ageHours: Float
            get() = ageMillis / 3_600_000f

        val ageLabel: String
            get() = when {
                ageHours < 1f -> "${(ageMillis / 60_000L).coerceAtLeast(0L)}m"
                ageHours < 24f -> "${ageHours.roundToInt()}h"
                else -> "${(ageHours / 24f).roundToInt()}d"
            }

        val carePercent: Float
            get() = if (totalCareMillis <= 0L) 0f else (goodCareMillis.toDouble() / totalCareMillis * 100.0).toFloat().coerceIn(0f, 100f)

        val evolutionProgress: Float
            get() {
                if (generation >= 2) return 100f
                val ageTargetHours = if (generation == 0) 12f else 72f
                val careTarget = if (generation == 0) 55f else 72f
                val agePart = (ageHours / ageTargetHours * 100f).coerceAtMost(100f)
                val carePart = (carePercent / careTarget * 100f).coerceAtMost(100f)
                return min(agePart, carePart).coerceIn(0f, 100f)
            }

        val evolutionHint: String
            get() = when {
                generation >= 2 -> "Fully evolved"
                generation == 0 && ageHours < 12f -> "Needs ${max(0f, 12f - ageHours).roundToInt()}h + good care"
                generation == 0 -> "Keep care above 55%"
                generation == 1 && ageHours < 72f -> "Needs ${max(0f, 72f - ageHours).roundToInt()}h + patience"
                else -> "Keep care above 72%"
            }

        private val careAverage: Float
            get() = (hunger + joy + energy + clean) / 4f

        fun updateFromClock() {
            val now = System.currentTimeMillis()
            val elapsedMillis = ((now - lastUpdate).coerceAtLeast(0L)).coerceAtMost(7L * 24L * 60L * 60L * 1000L)
            if (elapsedMillis == 0L) return
            if (created && hatched) {
                val minutes = elapsedMillis / 60_000f
                hunger = (hunger - minutes * 0.13f).coerceIn(0f, 100f)
                joy = (joy - minutes * 0.08f).coerceIn(0f, 100f)
                energy = (energy - minutes * 0.10f).coerceIn(0f, 100f)
                clean = (clean - minutes * 0.06f).coerceIn(0f, 100f)
                val hours = elapsedMillis / 3_600_000f
                growth = (growth + hours * (0.75f + careAverage / 100f * 1.25f)).coerceAtMost(100f)
                ageMillis = (ageMillis + elapsedMillis).coerceAtMost(365L * 24L * 60L * 60L * 1000L)
                totalCareMillis = (totalCareMillis + elapsedMillis).coerceAtMost(365L * 24L * 60L * 60L * 1000L)
                if (careAverage >= 65f) goodCareMillis = (goodCareMillis + elapsedMillis).coerceAtMost(totalCareMillis)
                advanceIfReady()
            }
            lastUpdate = now
        }

        fun apply(action: Action): Pair<String, Int> {
            updateFromClock()
            val result = when (action) {
                Action.FEED -> { hunger = (hunger + 18f).coerceAtMost(100f); joy = (joy + 3f).coerceAtMost(100f); growth = (growth + 0.35f).coerceAtMost(100f); "Nom nom! Tasty treats!" to Color.rgb(172, 86, 40) }
                Action.PLAY -> { joy = (joy + 16f).coerceAtMost(100f); energy = (energy - 9f).coerceAtLeast(0f); hunger = (hunger - 4f).coerceAtLeast(0f); growth = (growth + 0.45f).coerceAtMost(100f); "Wheee! That was fun!" to Color.rgb(191, 54, 112) }
                Action.BATH -> { clean = (clean + 22f).coerceAtMost(100f); joy = (joy + 4f).coerceAtMost(100f); growth = (growth + 0.25f).coerceAtMost(100f); "Sparkly clean! ✦" to Color.rgb(39, 135, 119) }
                Action.SLEEP -> { energy = (energy + 25f).coerceAtMost(100f); joy = (joy + 2f).coerceAtMost(100f); growth = (growth + 0.3f).coerceAtMost(100f); "Sweet dreams, little one." to Color.rgb(75, 78, 173) }
            }
            return result
        }

        fun hatch(newName: String, newKind: PetKind) {
            name = newName.ifBlank { "Mochi" }
            kind = newKind
            created = true
            hatched = true
            hunger = 82f
            joy = 88f
            energy = 84f
            clean = 92f
            growth = 0f
            generation = 0
            ageMillis = 0L
            goodCareMillis = 0L
            totalCareMillis = 0L
            lastUpdate = System.currentTimeMillis()
        }

        fun prepareNewPet() {
            name = "Mochi"
            kind = PetKind.BUNNY
            created = false
            hatched = false
            growth = 0f
            generation = 0
            ageMillis = 0L
            goodCareMillis = 0L
            totalCareMillis = 0L
            lastUpdate = System.currentTimeMillis()
        }

        private fun advanceIfReady() {
            if (generation == 0 && ageHours >= 12f && carePercent >= 55f && careAverage >= 60f) {
                generation = 1
                evolutionEvent = true
            } else if (generation == 1 && ageHours >= 72f && carePercent >= 72f && careAverage >= 70f) {
                generation = 2
                evolutionEvent = true
            }
        }

        fun consumeEvolutionEvent(): Boolean {
            val happened = evolutionEvent
            evolutionEvent = false
            return happened
        }

        fun save() {
            prefs.edit()
                .putFloat("hunger", hunger)
                .putFloat("joy", joy)
                .putFloat("energy", energy)
                .putFloat("clean", clean)
                .putFloat("growth", growth)
                .putInt("generation", generation)
                .putLong("age_millis", ageMillis)
                .putLong("good_care_millis", goodCareMillis)
                .putLong("total_care_millis", totalCareMillis)
                .putString("name", name)
                .putString("kind", kind.name)
                .putBoolean("created", created)
                .putBoolean("hatched", hatched)
                .putLong("last_update", lastUpdate)
                .apply()
        }

        private fun readMetric(key: String, fallback: Float): Float = try {
            prefs.getFloat(key, fallback)
        } catch (_: ClassCastException) {
            prefs.getInt(key, fallback.roundToInt()).toFloat()
        }
    }
}

private object PaintTypeface {
    fun rounded() = android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL)
    fun bold() = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
}
