package com.example.shortsgesturecontrol

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.ArrayList
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

private const val RIG_MESH_COLUMNS = 12
private const val RIG_MESH_ROWS = 12

class MainActivity : Activity() {
    private lateinit var gameView: PetGameView
    private lateinit var updateManager: AppUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(65, 44, 112)
        window.navigationBarColor = Color.rgb(35, 24, 63)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        updateManager = AppUpdateManager(this)
        gameView = PetGameView(this, updateManager)
        setContentView(gameView)
        gameView.postDelayed({ gameView.checkForUpdates(showNoUpdate = false) }, 650L)
    }

    override fun onResume() {
        super.onResume()
        updateManager.onHostResume()
    }

    override fun onPause() {
        updateManager.onHostPause()
        gameView.savePet()
        super.onPause()
    }
}

/**
 * Optional self-updates from the repository's GitHub Releases page.  A release
 * tag must be v<versionCode> and include an APK asset signed with this app's
 * existing signing key.
 */
private class AppUpdateManager(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activity = context as? Activity
    private val updatePrefs = context.getSharedPreferences("zoey_pet_updates", Context.MODE_PRIVATE)
    private var hostResumed = false
    private var unknownSourceSettingsOpened = false

    fun onHostResume() {
        hostResumed = true
        resumePendingInstall()
    }

    fun onHostPause() {
        hostResumed = false
    }

    fun check(showNoUpdate: Boolean) {
        Thread {
            try {
                val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 12_000
                    readTimeout = 12_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "ZoeysPocketPet-Updater")
                }
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                val release = JSONObject(response)
                val versionCode = release.optString("tag_name").removePrefix("v").toIntOrNull()
                val assets = release.optJSONArray("assets")
                var downloadUrl: String? = null
                if (assets != null) {
                    for (index in 0 until assets.length()) {
                        val asset = assets.getJSONObject(index)
                        if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url")
                            break
                        }
                    }
                }
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val installedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
                mainHandler.post {
                    when {
                        versionCode != null && versionCode > installedVersion && !downloadUrl.isNullOrBlank() -> showUpdate(versionCode, downloadUrl)
                        showNoUpdate -> Toast.makeText(context, "You're all up to date.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                if (showNoUpdate) mainHandler.post {
                    Toast.makeText(context, "Couldn't check for updates right now.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun showUpdate(versionCode: Int, url: String) {
        val activity = context as? Activity ?: return
        if (activity.isFinishing) return
        AlertDialog.Builder(activity)
            .setTitle("An update is ready")
            .setMessage("Version $versionCode is available. Would you like to download it now?")
            .setNegativeButton("NOT NOW", null)
            .setPositiveButton("DOWNLOAD") { _, _ -> download(url) }
            .show()
    }

    private fun download(url: String) {
        val downloadDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: run {
                Toast.makeText(context, "Couldn't create the update download.", Toast.LENGTH_LONG).show()
                return
            }
        if (!downloadDirectory.exists()) downloadDirectory.mkdirs()
        val updateFile = File(downloadDirectory, UPDATE_FILE_NAME)
        val partialFile = File(downloadDirectory, "$UPDATE_FILE_NAME.part")
        updateFile.delete()
        partialFile.delete()
        // Persist only the final path. If the process is killed during a
        // transfer, onResume will not mistake a partial APK for a complete one.
        updatePrefs.edit().putString(PENDING_UPDATE_PATH, updateFile.absolutePath).apply()
        Toast.makeText(context, "Downloading update…", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    setRequestProperty("Accept", "application/octet-stream")
                    setRequestProperty("User-Agent", "ZoeysPocketPet-Updater")
                }
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Update download returned HTTP ${connection.responseCode}")
                }
                connection.inputStream.use { input ->
                    FileOutputStream(partialFile).use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE)
                        output.fd.sync()
                    }
                }
                connection.disconnect()
                if (!partialFile.renameTo(updateFile)) {
                    throw IllegalStateException("Unable to finalize update APK")
                }
                // This is called by the downloader itself, not by a
                // DownloadManager broadcast that may be lost or routed to the
                // Downloads app. If the Activity is paused, the persisted final
                // path is picked up by onHostResume.
                mainHandler.post { tryInstall(updateFile) }
            } catch (_: Exception) {
                partialFile.delete()
                updateFile.delete()
                updatePrefs.edit().remove(PENDING_UPDATE_PATH).apply()
                mainHandler.post {
                    Toast.makeText(context, "The update download didn't finish.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun resumePendingInstall() {
        val pendingPath = updatePrefs.getString(PENDING_UPDATE_PATH, null) ?: return
        val file = File(pendingPath)
        if (!file.exists()) {
            updatePrefs.edit().remove(PENDING_UPDATE_PATH).apply()
            return
        }
        val pendingInfo = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        val installedInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val installedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            installedInfo.longVersionCode
        } else {
            installedInfo.versionCode.toLong()
        }
        val pendingVersion = pendingInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
        } ?: 0L
        // If the user manually opened and installed this APK, the file remains
        // in our private update directory. Do not prompt for that same version
        // again on the first resume of the newly installed app.
        if (pendingVersion == 0L || pendingVersion <= installedVersion) {
            updatePrefs.edit().remove(PENDING_UPDATE_PATH).apply()
            file.delete()
            return
        }
        tryInstall(file)
    }

    private fun tryInstall(file: File) {
        val host = activity ?: return
        if (!hostResumed || host.isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && host.isDestroyed)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            if (unknownSourceSettingsOpened) {
                Toast.makeText(context, "Allow installs from Zoey's Pocket Pet, then return here.", Toast.LENGTH_LONG).show()
                return
            }
            unknownSourceSettingsOpened = true
            host.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Toast.makeText(context, "Allow installs from Zoey's Pocket Pet, then return here.", Toast.LENGTH_LONG).show()
            return
        }
        unknownSourceSettingsOpened = false
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
            clipData = android.content.ClipData.newRawUri("APK", apkUri)
        }
        try {
            host.startActivity(installIntent)
            updatePrefs.edit().remove(PENDING_UPDATE_PATH).apply()
        } catch (_: android.content.ActivityNotFoundException) {
            // A few vendor ROMs expose only ACTION_VIEW for APKs. Keep the
            // direct installer as the normal path, but still pass the APK URI
            // and MIME type if that device lacks ACTION_INSTALL_PACKAGE.
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                clipData = android.content.ClipData.newRawUri("APK", apkUri)
            }
            try {
                host.startActivity(fallback)
                updatePrefs.edit().remove(PENDING_UPDATE_PATH).apply()
            } catch (_: Exception) {
                Toast.makeText(context, "Couldn't open the installer. Try the update again.", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Couldn't open the installer. Try the update again.", Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/bottMage/zoeys-pocket-pet/releases/latest"
        const val UPDATE_FILE_NAME = "zoeys-pocket-pet-update.apk"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val PENDING_UPDATE_PATH = "pending_update_path"
    }
}

private class PetGameView(context: Context, private val updateManager: AppUpdateManager) : View(context) {
    private val appContext = context
    private val prefs = context.getSharedPreferences("zoey_pet", Context.MODE_PRIVATE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rigPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        alpha = 255
    }
    // Kept only for the setup-screen art; the playground uses the textured
    // GPU rig below for every motion state.
    private val petShapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val petLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = PaintTypeface.rounded() }
    private val pet = PetState(prefs)
    private val petArtCache = HashMap<PetKind, Bitmap>()
    private val rigArtCache = HashMap<PetKind, Bitmap>()
    private val rigArtBottomCache = HashMap<PetKind, Int>()
    private val rigMeshVertices = FloatArray((RIG_MESH_COLUMNS + 1) * (RIG_MESH_ROWS + 1) * 2)
    private val rigArtResources = mapOf(
        PetKind.CAT to R.drawable.rig_cat,
        PetKind.DOG to R.drawable.rig_dog,
        PetKind.BUNNY to R.drawable.rig_bunny,
        PetKind.HAMSTER to R.drawable.rig_hamster,
        PetKind.DRAGON to R.drawable.rig_dragon
    )
    private val rigSheetCache = HashMap<PetKind, RigSheet>()
    private val rigPartResources = mapOf(
        PetKind.CAT to RigPartResources(
            head = R.drawable.rigpart_cat_head,
            body = R.drawable.rigpart_cat_body,
            tail = R.drawable.rigpart_cat_tail,
            legs = listOf(R.drawable.rigpart_cat_leg0, R.drawable.rigpart_cat_leg1, R.drawable.rigpart_cat_leg2, R.drawable.rigpart_cat_leg3),
            backParts = listOf(R.drawable.rigpart_cat_extra0)
        ),
        PetKind.DOG to RigPartResources(
            head = R.drawable.rigpart_dog_head,
            body = R.drawable.rigpart_dog_body,
            tail = R.drawable.rigpart_dog_tail,
            legs = listOf(R.drawable.rigpart_dog_leg0, R.drawable.rigpart_dog_leg1, R.drawable.rigpart_dog_leg2, R.drawable.rigpart_dog_leg3),
            backParts = listOf(R.drawable.rigpart_dog_extra0)
        ),
        PetKind.BUNNY to RigPartResources(
            head = R.drawable.rigpart_bunny_head,
            body = R.drawable.rigpart_bunny_body,
            tail = R.drawable.rigpart_bunny_tail,
            legs = listOf(R.drawable.rigpart_bunny_leg0, R.drawable.rigpart_bunny_leg1, R.drawable.rigpart_bunny_leg2, R.drawable.rigpart_bunny_leg3),
            backParts = listOf(R.drawable.rigpart_bunny_extra0, R.drawable.rigpart_bunny_extra1)
        ),
        PetKind.HAMSTER to RigPartResources(
            head = R.drawable.rigpart_hamster_head,
            body = R.drawable.rigpart_hamster_body,
            tail = R.drawable.rigpart_hamster_tail,
            legs = listOf(R.drawable.rigpart_hamster_leg0, R.drawable.rigpart_hamster_leg1, R.drawable.rigpart_hamster_leg2, R.drawable.rigpart_hamster_leg3),
            backParts = listOf(R.drawable.rigpart_hamster_extra0)
        ),
        PetKind.DRAGON to RigPartResources(
            head = R.drawable.rigpart_dragon_head,
            body = R.drawable.rigpart_dragon_body,
            tail = R.drawable.rigpart_dragon_tail,
            legs = listOf(R.drawable.rigpart_dragon_leg0, R.drawable.rigpart_dragon_leg1, R.drawable.rigpart_dragon_leg2, R.drawable.rigpart_dragon_leg3),
            backParts = listOf(R.drawable.rigpart_dragon_extra0, R.drawable.rigpart_dragon_extra1),
            frontParts = listOf(R.drawable.rigpart_dragon_front0)
        )
    )
    private val petArtResources = mapOf(
        PetKind.CAT to R.drawable.companion_cat,
        PetKind.DOG to R.drawable.companion_dog,
        PetKind.BUNNY to R.drawable.companion_bunny,
        PetKind.HAMSTER to R.drawable.companion_hamster,
        PetKind.DRAGON to R.drawable.companion_dragon
    )
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
    private var motionMode = MotionMode.REST
    private var motionModeUntil = 0L
    private var motionLastAt = SystemClock.uptimeMillis()
    private var motionModeStartedAt = motionLastAt
    private var walkPhase = 0f
    private var playgroundCache: Bitmap? = null
    private var setupMode = !pet.created
    private var setupKind = pet.kind
    private var setupName = pet.name

    private fun petArtwork(kind: PetKind): Bitmap = petArtCache.getOrPut(kind) {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 2
            inScaled = false
        }
        BitmapFactory.decodeResource(resources, petArtResources.getValue(kind), options)
            ?: error("Unable to load artwork for ${kind.label}")
    }

    private fun rigArtwork(kind: PetKind): Bitmap = rigArtCache.getOrPut(kind) {
        BitmapFactory.decodeResource(resources, rigArtResources.getValue(kind))
            ?: error("Unable to load rig artwork for ${kind.label}")
    }

    private fun rigArtworkBottom(kind: PetKind): Int = rigArtBottomCache.getOrPut(kind) {
        val bitmap = rigArtwork(kind)
        for (y in bitmap.height - 1 downTo 0) {
            for (x in 0 until bitmap.width) {
                if (Color.alpha(bitmap.getPixel(x, y)) != 0) return@getOrPut y + 1
            }
        }
        bitmap.height
    }

    private data class RigPartResources(
        val head: Int,
        val body: Int,
        val tail: Int,
        val legs: List<Int>,
        val backParts: List<Int> = emptyList(),
        val frontParts: List<Int> = emptyList()
    )

    private data class RigSheet(
        val head: Bitmap,
        val body: Bitmap,
        val tail: Bitmap,
        val legs: List<Bitmap>,
        val backParts: List<Bitmap>,
        val frontParts: List<Bitmap>
    )

    private fun rigSheet(kind: PetKind): RigSheet = rigSheetCache.getOrPut(kind) {
        val parts = rigPartResources.getValue(kind)
        fun load(resourceId: Int): Bitmap = BitmapFactory.decodeResource(resources, resourceId)
            ?: error("Unable to load rig part for ${kind.label}")
        RigSheet(
            head = load(parts.head),
            body = load(parts.body),
            tail = load(parts.tail),
            legs = parts.legs.map(::load),
            backParts = parts.backParts.map(::load),
            frontParts = parts.frontParts.map(::load)
        )
    }

    init {
        isFocusable = true
        pet.updateFromClock()
        motionModeUntil = motionLastAt + 1800L
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        playgroundCache = null
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
            postInvalidateOnAnimation()
            return
        }
        drawHeader(canvas)
        drawPlayground(canvas)
        drawPet(canvas, now)
        drawActionEffects(canvas, now)
        drawMessage(canvas, now)
        drawStats(canvas)
        drawActions(canvas)
        if (now - lastSaved > 30_000L) savePet()
        postInvalidateOnAnimation()
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

        val updates = headerUpdateRect()
        paint.color = Color.argb(54, 48, 27, 89)
        canvas.drawRoundRect(updates, dp(16f), dp(16f), paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(10f)
        textPaint.typeface = PaintTypeface.bold()
        textPaint.color = Color.WHITE
        canvas.drawText("UPDATES", updates.centerX(), updates.centerY() + dp(3f), textPaint)

        val pill = RectF(width - dp(114f), dp(18f), width - dp(20f), dp(57f))
        paint.color = Color.argb(70, 48, 27, 89)
        canvas.drawRoundRect(pill, dp(19f), dp(19f), paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = dp(13f)
        textPaint.typeface = PaintTypeface.bold()
        textPaint.color = Color.WHITE
        canvas.drawText("RESET", pill.centerX(), dp(41f), textPaint)

        // Keep the installed build visible without taking space from the pet.
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = dp(9f)
        textPaint.typeface = PaintTypeface.bold()
        textPaint.color = Color.argb(190, 255, 255, 255)
        canvas.drawText("v${BuildConfig.VERSION_NAME}", width - dp(22f), dp(74f), textPaint)
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
            val artSize = dp(38f)
            val artRect = RectF(left + dp(4f), rect.centerY() - artSize / 2f, left + dp(4f) + artSize, rect.centerY() + artSize / 2f)
            paint.color = Color.WHITE
            paint.isFilterBitmap = true
            canvas.drawBitmap(petArtwork(kinds[i]), null, artRect, paint)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.textSize = dp(10f)
            textPaint.typeface = PaintTypeface.bold()
            textPaint.color = if (setupKind == kinds[i]) kinds[i].dark else Color.WHITE
            canvas.drawText(kinds[i].label, artRect.right + dp(2f), rect.centerY() + dp(4f), textPaint)
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

    private fun drawPlayground(canvas: Canvas) {
        val cached = playgroundCache ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            drawPlaygroundScene(Canvas(bitmap))
            playgroundCache = bitmap
        }
        paint.shader = null
        paint.alpha = 255
        canvas.drawBitmap(cached, 0f, 0f, paint)
    }

    private fun drawPlaygroundScene(canvas: Canvas) {
        val top = dp(77f)
        val bottom = statsTop()
        val scene = RectF(dp(18f), top, width - dp(18f), bottom - dp(10f))
        paint.color = Color.argb(35, 56, 44, 82)
        canvas.drawRoundRect(RectF(scene.left, scene.top + dp(5f), scene.right, scene.bottom + dp(5f)), dp(26f), dp(26f), paint)
        paint.color = Color.WHITE
        canvas.drawRoundRect(scene, dp(26f), dp(26f), paint)

        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(scene, dp(26f), dp(26f), Path.Direction.CW) })
        paint.shader = LinearGradient(
            0f, scene.top, 0f, scene.bottom,
            Color.rgb(225, 241, 255), Color.rgb(250, 239, 249), Shader.TileMode.CLAMP
        )
        canvas.drawRect(scene, paint)
        paint.shader = null

        paint.color = Color.argb(195, 255, 255, 255)
        drawCloud(canvas, scene.left + dp(18f), scene.top + dp(76f), .38f)
        drawCloud(canvas, scene.right - dp(82f), scene.top + dp(126f), .32f)

        val farHill = Path().apply {
            moveTo(scene.left, scene.bottom - dp(67f))
            cubicTo(scene.left + dp(85f), scene.bottom - dp(100f), scene.right - dp(110f), scene.bottom - dp(20f), scene.right, scene.bottom - dp(73f))
            lineTo(scene.right, scene.bottom)
            lineTo(scene.left, scene.bottom)
            close()
        }
        paint.color = Color.rgb(205, 236, 215)
        canvas.drawPath(farHill, paint)
        val nearHill = Path().apply {
            moveTo(scene.left, scene.bottom - dp(38f))
            cubicTo(scene.left + dp(110f), scene.bottom - dp(73f), scene.right - dp(112f), scene.bottom - dp(19f), scene.right, scene.bottom - dp(49f))
            lineTo(scene.right, scene.bottom)
            lineTo(scene.left, scene.bottom)
            close()
        }
        paint.color = Color.rgb(184, 225, 194)
        canvas.drawPath(nearHill, paint)

        // Give the feet a readable plane: a shallow grassy foreground with a
        // soft horizon and irregular blades makes contact easier to perceive
        // than the previous uninterrupted pastel hill.
        val ground = Path().apply {
            moveTo(scene.left, scene.bottom - dp(62f))
            cubicTo(scene.left + dp(78f), scene.bottom - dp(72f), scene.right - dp(120f), scene.bottom - dp(53f), scene.right, scene.bottom - dp(64f))
            lineTo(scene.right, scene.bottom)
            lineTo(scene.left, scene.bottom)
            close()
        }
        paint.color = Color.rgb(145, 201, 154)
        canvas.drawPath(ground, paint)
        paint.color = Color.argb(48, 82, 139, 91)
        paint.strokeWidth = dp(3f)
        val contactPlaneY = scene.bottom - dp(48f)
        canvas.drawLine(scene.left + dp(12f), contactPlaneY, scene.right - dp(12f), contactPlaneY, paint)
        paint.color = Color.rgb(125, 181, 135)
        paint.strokeWidth = dp(1.2f)
        for (i in 0 until 25) {
            val x = scene.left + dp(9f) + i * dp(18f)
            val base = scene.bottom - dp(9f) - (i % 3) * dp(3f)
            canvas.drawLine(x, base, x + dp(if (i % 2 == 0) -2f else 2f), base - dp(7f + (i % 4)), paint)
        }
        paint.strokeWidth = dp(1f)
        canvas.restore()
    }

    private fun drawCloud(canvas: Canvas, x: Float, y: Float, scale: Float) {
        canvas.drawCircle(x, y, dp(22f) * scale, paint)
        canvas.drawCircle(x + dp(22f) * scale, y - dp(8f) * scale, dp(28f) * scale, paint)
        canvas.drawCircle(x + dp(52f) * scale, y, dp(20f) * scale, paint)
        canvas.drawRoundRect(RectF(x - dp(5f) * scale, y, x + dp(60f) * scale, y + dp(18f) * scale), dp(10f), dp(10f), paint)
    }

    /**
     * Draw the walking pet as a tiny 2-D rig instead of selecting another
     * complete bitmap. The body is one continuously rendered shape, while
     * each leg follows a sinusoidal stride with a planted foot at either end
     * of its cycle. This keeps the silhouette coherent at every display
     * refresh and gives the pet real weight without a pose jump.
     */
    private fun drawProceduralPet(
        canvas: Canvas,
        kind: PetKind,
        centerX: Float,
        groundY: Float,
        artWidth: Float,
        direction: Float,
        phase: Float,
        playful: Boolean
    ) {
        val scale = artWidth / 220f
        val bodyBob = if (playful) -abs(sin(phase * 2f)) * 3f else sin(phase * 2f) * 1.5f
        canvas.save()
        canvas.translate(centerX, groundY)
        canvas.scale(if (direction > 0f) -scale else scale, scale)
        canvas.translate(0f, bodyBob)

        val base = kind.primary
        val light = kind.light
        val dark = kind.dark

        // Tail and wings sit behind the torso.
        petLinePaint.color = base
        petLinePaint.strokeWidth = if (kind == PetKind.DRAGON) 15f else 18f
        val tail = Path().apply {
            moveTo(50f, -66f)
            when (kind) {
                PetKind.CAT -> cubicTo(105f, -132f, 118f, -8f, 72f, -40f)
                PetKind.DOG -> cubicTo(107f, -106f, 119f, -14f, 74f, -38f)
                PetKind.DRAGON -> cubicTo(110f, -90f, 126f, -4f, 68f, -27f)
                else -> cubicTo(94f, -100f, 113f, -30f, 73f, -38f)
            }
        }
        canvas.drawPath(tail, petLinePaint)
        if (kind == PetKind.BUNNY || kind == PetKind.HAMSTER) {
            petShapePaint.color = light
            canvas.drawOval(RectF(57f, -93f, 105f, -38f), petShapePaint)
        }
        if (kind == PetKind.DRAGON) {
            petShapePaint.color = Color.rgb(20, 116, 154)
            val wing = Path().apply {
                moveTo(15f, -78f)
                cubicTo(31f, -154f, 70f, -190f, 112f, -147f)
                lineTo(92f, -115f)
                lineTo(65f, -119f)
                lineTo(43f, -67f)
                close()
            }
            canvas.drawPath(wing, petShapePaint)
            petLinePaint.color = Color.rgb(11, 79, 117)
            petLinePaint.strokeWidth = 2.5f
            canvas.drawLine(27f, -91f, 83f, -149f, petLinePaint)
            canvas.drawLine(43f, -83f, 96f, -139f, petLinePaint)
            val wingTip = Path().apply {
                moveTo(82f, -119f)
                lineTo(110f, -104f)
                lineTo(101f, -138f)
                close()
            }
            petShapePaint.color = Color.rgb(20, 116, 154)
            canvas.drawPath(wingTip, petShapePaint)
        }

        // The rear pair is drawn first so the near legs overlap the torso.
        drawRigLeg(canvas, 42f, phase + Math.PI.toFloat(), dark, 1f)
        drawRigLeg(canvas, 57f, phase + Math.PI.toFloat() + 0.45f, base, 1f)

        petShapePaint.color = base
        canvas.drawOval(RectF(-69f, -105f, 69f, -27f), petShapePaint)
        petShapePaint.color = Color.argb(72, Color.red(light), Color.green(light), Color.blue(light))
        canvas.drawOval(RectF(-42f, -82f, 45f, -20f), petShapePaint)

        // Species-specific ears and horns move with the torso.
        when (kind) {
            PetKind.BUNNY -> {
                petShapePaint.color = base
                canvas.save()
                canvas.rotate(-18f, -72f, -158f)
                canvas.drawOval(RectF(-92f, -220f, -57f, -126f), petShapePaint)
                canvas.restore()
                canvas.save()
                canvas.rotate(13f, -42f, -165f)
                canvas.drawOval(RectF(-61f, -222f, -23f, -126f), petShapePaint)
                canvas.restore()
                petShapePaint.color = Color.rgb(250, 153, 143)
                canvas.drawOval(RectF(-83f, -204f, -65f, -142f), petShapePaint)
                canvas.drawOval(RectF(-52f, -207f, -32f, -143f), petShapePaint)
            }
            PetKind.CAT -> {
                petShapePaint.color = base
                val ear = Path().apply {
                    moveTo(-91f, -145f)
                    lineTo(-80f, -207f)
                    lineTo(-46f, -157f)
                    close()
                }
                canvas.drawPath(ear, petShapePaint)
                val ear2 = Path().apply {
                    moveTo(-54f, -154f)
                    lineTo(-22f, -202f)
                    lineTo(-15f, -139f)
                    close()
                }
                canvas.drawPath(ear2, petShapePaint)
                petShapePaint.color = Color.rgb(241, 153, 166)
                canvas.drawOval(RectF(-78f, -190f, -62f, -159f), petShapePaint)
                canvas.drawOval(RectF(-45f, -184f, -28f, -155f), petShapePaint)
            }
            PetKind.DOG -> {
                petShapePaint.color = dark
                canvas.save()
                canvas.rotate(-17f, -92f, -150f)
                canvas.drawOval(RectF(-119f, -190f, -78f, -112f), petShapePaint)
                canvas.restore()
                canvas.save()
                canvas.rotate(18f, -33f, -148f)
                canvas.drawOval(RectF(-52f, -193f, -15f, -111f), petShapePaint)
                canvas.restore()
            }
            PetKind.HAMSTER -> {
                petShapePaint.color = Color.rgb(239, 157, 113)
                canvas.drawCircle(-82f, -162f, 25f, petShapePaint)
                canvas.drawCircle(-32f, -165f, 24f, petShapePaint)
                petShapePaint.color = Color.rgb(255, 183, 159)
                canvas.drawCircle(-82f, -162f, 14f, petShapePaint)
                canvas.drawCircle(-32f, -165f, 13f, petShapePaint)
            }
            PetKind.DRAGON -> {
                petShapePaint.color = light
                val horn = Path().apply {
                    moveTo(-79f, -172f)
                    lineTo(-69f, -219f)
                    lineTo(-49f, -174f)
                    close()
                }
                canvas.drawPath(horn, petShapePaint)
                val horn2 = Path().apply {
                    moveTo(-42f, -176f)
                    lineTo(-24f, -217f)
                    lineTo(-13f, -165f)
                    close()
                }
                canvas.drawPath(horn2, petShapePaint)
                petShapePaint.color = dark
                val crest = Path().apply {
                    moveTo(-11f, -104f)
                    lineTo(2f, -124f)
                    lineTo(10f, -103f)
                    lineTo(23f, -119f)
                    lineTo(30f, -94f)
                    close()
                }
                canvas.drawPath(crest, petShapePaint)
            }
        }

        // Head and muzzle.
        petShapePaint.color = base
        canvas.drawOval(RectF(-105f, -181f, -24f, -87f), petShapePaint)
        petShapePaint.color = light
        canvas.drawOval(RectF(-111f, -143f, -54f, -96f), petShapePaint)

        // Near legs are on top of the body and are the main readable motion.
        drawRigLeg(canvas, -50f, phase, base, 1f)
        drawRigLeg(canvas, -27f, phase + 0.45f, light, 1f)

        // Face stays crisp while the rig moves continuously.
        petShapePaint.color = Color.WHITE
        canvas.drawOval(RectF(-88f, -162f, -58f, -121f), petShapePaint)
        petShapePaint.color = dark
        canvas.drawOval(RectF(-79f, -157f, -65f, -130f), petShapePaint)
        petShapePaint.color = Color.WHITE
        canvas.drawCircle(-73f, -151f, 4f, petShapePaint)
        petShapePaint.color = dark
        canvas.drawOval(RectF(-111f, -130f, -98f, -120f), petShapePaint)
        petLinePaint.color = dark
        petLinePaint.strokeWidth = 3.5f
        val smile = Path().apply {
            moveTo(-98f, -117f)
            cubicTo(-90f, -106f, -78f, -105f, -70f, -116f)
        }
        canvas.drawPath(smile, petLinePaint)
        canvas.restore()
    }

    private fun drawRigLeg(canvas: Canvas, hipX: Float, phase: Float, color: Int, widthScale: Float) {
        val stride = sin(phase)
        val lift = max(0f, sin(phase))
        val kneeX = hipX + stride * 9f + 4f
        val kneeY = -43f - lift * 7f
        val ankleX = hipX + stride * 20f
        val ankleY = -4f - lift * 12f
        petLinePaint.color = color
        petLinePaint.strokeWidth = 18f * widthScale
        canvas.drawLine(hipX, -58f, kneeX, kneeY, petLinePaint)
        canvas.drawLine(kneeX, kneeY, ankleX, ankleY, petLinePaint)
        petShapePaint.color = color
        canvas.drawOval(RectF(ankleX - 13f, ankleY - 9f, ankleX + 13f, ankleY + 3f), petShapePaint)
    }

    /**
     * Render the original side-view artwork as a GPU mesh. This is one
     * texture per species, not a sequence of replacement pictures: the mesh
     * vertices continuously deform the body, lower limbs, and tail between
     * display frames while the original design remains intact.
     */
    private fun drawTexturedRigPet(
        canvas: Canvas,
        kind: PetKind,
        centerX: Float,
        groundY: Float,
        artWidth: Float,
        direction: Float,
        phase: Float
    ) {
        val bitmap = rigArtwork(kind)
        val scale = artWidth / bitmap.width
        val left = centerX - artWidth / 2f
        val top = groundY - rigArtworkBottom(kind) * scale
        val twoPi = Math.PI.toFloat() * 2f
        val animated = if (phase == 0f) 0f else 1f
        var vertex = 0
        for (row in 0..RIG_MESH_ROWS) {
            val ny = row / RIG_MESH_ROWS.toFloat()
            for (column in 0..RIG_MESH_COLUMNS) {
                val nx = column / RIG_MESH_COLUMNS.toFloat()
                val sourceX = bitmap.width * nx
                val sourceY = bitmap.height * ny
                val upperBody = (1f - ny / .78f).coerceIn(0f, 1f) * animated
                val groundWeight = ((ny - .56f) / .44f).coerceIn(0f, 1f)
                val frontWeight = (1f - nx).coerceIn(0f, 1f)
                val legPhase = phase + if (frontWeight > .5f) 0f else Math.PI.toFloat()
                val stride = sin(legPhase + nx * .7f)
                val tailWeight = ((nx - .60f) / .40f).coerceIn(0f, 1f) * animated
                var x = left + sourceX * scale
                var y = top + sourceY * scale

                // A very small body settle keeps weight readable without
                // detaching the paws from the cached ground plane.
                y += sin(phase * 2f) * 5.5f * upperBody * scale
                x += sin(phase + ny * twoPi) * 4.5f * upperBody * scale

                // Deform only the lower silhouette for a continuous stride.
                // Different x zones receive opposite motion, so the near and
                // far legs do not move as one rigid sticker.
                x += stride * 22f * groundWeight * scale
                y -= max(0f, sin(legPhase)) * 12f * groundWeight * scale
                x += sin(phase * .72f + ny * 3f) * 15f * tailWeight * scale
                y += cos(phase * .72f + nx * twoPi) * 5f * tailWeight * scale

                if (direction > 0f) x = centerX - (x - centerX)
                rigMeshVertices[vertex++] = x
                rigMeshVertices[vertex++] = y
            }
        }
        canvas.save()
        canvas.rotate(sin(phase) * 1.8f, centerX, groundY)
        canvas.scale(1f + sin(phase) * .018f, 1f - sin(phase) * .012f, centerX, groundY)
        canvas.drawBitmapMesh(
            bitmap,
            RIG_MESH_COLUMNS,
            RIG_MESH_ROWS,
            rigMeshVertices,
            0,
            null,
            0,
            rigPaint
        )
        canvas.restore()
    }

    /** A real cutout rig: each limb is a separate original-art texture with a joint pivot. */
    private fun drawBoneRigPet(
        canvas: Canvas,
        kind: PetKind,
        centerX: Float,
        groundY: Float,
        artWidth: Float,
        direction: Float,
        phase: Float
    ) {
        val sheet = rigSheet(kind)
        val unit = artWidth / 220f
        val bodyBob = sin(phase * 2f) * 1.5f
        val frontStride = sin(phase) * 17f
        val rearStride = sin(phase + Math.PI.toFloat()) * 15f
        canvas.save()
        canvas.translate(centerX, groundY)
        canvas.scale(if (direction > 0f) -unit else unit, unit)
        canvas.translate(0f, bodyBob)

        // Wings and tail are rear bones.
        if (kind == PetKind.DRAGON) {
            drawBonePart(canvas, sheet.backParts[0], RectF(12f, -170f, 112f, -65f), 20f, -82f, sin(phase * .7f) * 4f)
            drawBonePart(canvas, sheet.backParts[1], RectF(18f, -157f, 95f, -62f), 24f, -80f, sin(phase * .7f + 1f) * 3f)
        }
        drawBonePart(canvas, sheet.tail, RectF(48f, -101f, 124f, -18f), 53f, -72f, sin(phase) * 12f)

        // Far legs move first and disappear behind the torso.
        drawBonePart(canvas, sheet.legs[0], RectF(-68f, -77f, -29f, 3f), -50f, -58f, rearStride)
        drawBonePart(canvas, sheet.legs[1], RectF(23f, -77f, 63f, 3f), 43f, -58f, frontStride)

        drawBonePart(canvas, sheet.body, RectF(-67f, -108f, 68f, -26f), 0f, -54f, sin(phase * 2f) * 1.2f)
        drawBonePart(canvas, sheet.head, RectF(-111f, -186f, -18f, -78f), -64f, -89f, sin(phase * 2f) * 1.6f)

        // Ears/horns are attached to the head bone.
        when (kind) {
            PetKind.BUNNY -> {
                drawBonePart(canvas, sheet.backParts[0], RectF(-101f, -222f, -59f, -121f), -72f, -156f, sin(phase * 2f) * 1.6f)
                drawBonePart(canvas, sheet.backParts[1], RectF(-64f, -224f, -12f, -121f), -40f, -157f, sin(phase * 2f + .4f) * 1.6f)
            }
            PetKind.CAT -> drawBonePart(canvas, sheet.backParts[0], RectF(-91f, -210f, -5f, -124f), -49f, -148f, sin(phase * 2f) * 1.5f)
            PetKind.DOG -> drawBonePart(canvas, sheet.backParts[0], RectF(-116f, -198f, -8f, -101f), -64f, -146f, sin(phase * 2f) * 1.5f)
            PetKind.HAMSTER -> drawBonePart(canvas, sheet.backParts[0], RectF(-102f, -184f, -4f, -115f), -55f, -145f, sin(phase * 2f) * 1.5f)
            PetKind.DRAGON -> drawBonePart(canvas, sheet.frontParts[0], RectF(-84f, -224f, -5f, -149f), -45f, -166f, sin(phase * 2f) * 1.2f)
        }

        // Near legs are the readable stride and sit above the body.
        drawBonePart(canvas, sheet.legs[2], RectF(-57f, -78f, -18f, 3f), -41f, -58f, frontStride)
        drawBonePart(canvas, sheet.legs[3], RectF(39f, -78f, 80f, 3f), 58f, -58f, rearStride)
        canvas.restore()
    }

    private fun drawBonePart(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF,
        pivotX: Float,
        pivotY: Float,
        rotation: Float
    ) {
        canvas.save()
        canvas.rotate(rotation, pivotX, pivotY)
        canvas.drawBitmap(bitmap, null, destination, rigPaint)
        canvas.restore()
    }

    private fun drawPet(canvas: Canvas, now: Long) {
        val top = dp(77f)
        val bottom = statsTop() - dp(10f)
        val sceneLeft = dp(18f)
        val sceneRight = width - dp(18f)
        val dt = ((now - motionLastAt).coerceAtLeast(0L)).coerceAtMost(50L) / 1000f
        motionLastAt = now
        var gaitBlend = 1f
        if (now >= motionModeUntil && activeAction == null) {
            motionMode = when (motionMode) {
                MotionMode.REST -> if ((now / 1000L) % 3L == 0L) MotionMode.STAND else MotionMode.WALK
                MotionMode.WALK -> if ((now / 1000L) % 2L == 0L) MotionMode.CURIOUS else MotionMode.REST
                MotionMode.CURIOUS -> MotionMode.REST
                MotionMode.STAND -> MotionMode.WALK
            }
            motionModeUntil = now + when (motionMode) {
                MotionMode.WALK -> 2600L + (now % 1800L)
                MotionMode.REST -> 1900L + (now % 1700L)
                MotionMode.CURIOUS -> 900L + (now % 700L)
                MotionMode.STAND -> 1100L + (now % 800L)
            }
            motionModeStartedAt = now
            if (motionMode == MotionMode.WALK) {
                // Always enter the gait on a planted pose. The phase advances
                // continuously below; it is not an image-frame clock.
                walkPhase = 0f
            }
            if (motionMode == MotionMode.WALK && motionX <= .08f) motionDirection = 1f
            if (motionMode == MotionMode.WALK && motionX >= .92f) motionDirection = -1f
        }
        if (motionMode == MotionMode.WALK && activeAction == null) {
            val walkProgress = ((motionModeUntil - now) / 500f).coerceIn(0f, 1f)
            val startBlend = min(1f, (now - motionModeStartedAt).coerceAtLeast(0L) / 500f)
            gaitBlend = min(startBlend, walkProgress.coerceIn(0f, 1f))
            val speed = .30f * gaitBlend
            motionX += motionDirection * dt * speed
            walkPhase = (walkPhase + dt * 5.2f * gaitBlend) % (Math.PI.toFloat() * 2f)
            if (motionX <= .06f) { motionX = .06f; motionDirection = 1f }
            if (motionX >= .94f) { motionX = .94f; motionDirection = -1f }
        }

        val stageScale = when (pet.stage) {
            "BABY" -> .90f
            "YOUNG" -> .96f
            "TEEN" -> 1f
            "EVOLVED" -> 1.08f
            else -> 1f
        }
        val artWidth = min(width - dp(72f), dp(246f)) * stageScale
        val groundY = bottom - dp(48f)
        val minCenterX = max(sceneLeft + artWidth / 2f, artWidth / 2f + dp(4f))
        val maxCenterX = min(sceneRight - artWidth / 2f, width - artWidth / 2f - dp(4f))
        val centerX = minCenterX + motionX * (maxCenterX - minCenterX)
        val seconds = (now - animationStart) / 1000f
        val walking = motionMode == MotionMode.WALK && activeAction == null
        // Keep the feet planted while resting.  A whole-body vertical bob reads
        // as hovering, especially against the simple ground in this scene.
        val idleBob = 0f
        val rootY = groundY + idleBob

        // Two soft contact shapes read as weight on the grass without using a
        // per-frame shadow shader, which would make the animation less smooth.
        paint.color = Color.argb(34, 67, 57, 82)
        canvas.drawOval(
            RectF(centerX - artWidth * .32f, groundY + dp(1f), centerX + artWidth * .32f, groundY + dp(14f)),
            paint
        )
        paint.color = Color.argb(62, 67, 57, 82)
        canvas.drawOval(
            RectF(centerX - artWidth * .22f, groundY - dp(1f), centerX + artWidth * .22f, groundY + dp(7f)),
            paint
        )
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        val phase = when {
            walking -> walkPhase
            activeAction == Action.PLAY -> seconds * 5.2f
            motionMode == MotionMode.CURIOUS -> sin(seconds * 1.8f) * .16f
            else -> 0f
        }
        drawBoneRigPet(
            canvas = canvas,
            kind = pet.kind,
            centerX = centerX,
            groundY = rootY,
            artWidth = artWidth,
            direction = motionDirection,
            phase = phase
        )

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = dp(13f)
        val name = pet.name.uppercase()
        val nameWidth = textPaint.measureText(name) + dp(26f)
        val nameBaseline = min(bottom - dp(14f), groundY + dp(34f))
        paint.color = Color.argb(225, 255, 255, 255)
        canvas.drawRoundRect(
            RectF(centerX - nameWidth / 2f, nameBaseline - dp(21f), centerX + nameWidth / 2f, nameBaseline + dp(7f)),
            dp(14f), dp(14f), paint
        )
        textPaint.color = pet.kind.dark
        canvas.drawText(name, centerX, nameBaseline, textPaint)
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
                if (newPetRect().contains(event.x, event.y) || headerResetRect().contains(event.x, event.y) || headerUpdateRect().contains(event.x, event.y)) pressedAction = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val action = buttons.firstOrNull { it.rect.contains(event.x, event.y) }?.action
                if (action != null && action == pressedAction) perform(action)
                if (newPetRect().contains(event.x, event.y) || headerResetRect().contains(event.x, event.y)) confirmNewPet()
                if (headerUpdateRect().contains(event.x, event.y)) checkForUpdates(showNoUpdate = true)
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

    private fun headerUpdateRect(): RectF = RectF(width - dp(214f), dp(21f), width - dp(121f), dp(54f))

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

    fun checkForUpdates(showNoUpdate: Boolean) {
        updateManager.check(showNoUpdate)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private data class ActionButton(val action: Action, val rect: RectF)

    private enum class Action { FEED, PLAY, BATH, SLEEP }

    private enum class MotionMode { REST, WALK, CURIOUS, STAND }

    private enum class PetKind(val label: String, val light: Int, val primary: Int, val dark: Int) {
        CAT("CAT", Color.rgb(239, 220, 190), Color.rgb(189, 139, 105), Color.rgb(108, 74, 75)),
        DOG("DOG", Color.rgb(255, 239, 205), Color.rgb(214, 174, 123), Color.rgb(113, 78, 65)),
        BUNNY("BUNNY", Color.rgb(255, 207, 214), Color.rgb(245, 166, 186), Color.rgb(157, 83, 116)),
        HAMSTER("HAMSTER", Color.rgb(255, 222, 164), Color.rgb(227, 168, 91), Color.rgb(142, 92, 53)),
        DRAGON("DRAGON", Color.rgb(255, 236, 201), Color.rgb(45, 190, 205), Color.rgb(21, 91, 120))
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
