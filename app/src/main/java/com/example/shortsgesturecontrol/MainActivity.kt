package com.example.shortsgesturecontrol

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.app.DownloadManager
import android.net.ConnectivityManager
import android.net.Network
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
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private lateinit var gameView: PetGameView
    private lateinit var auth: FirebaseAuth
    private var googleSignInClient: GoogleSignInClient? = null
    private lateinit var connectivityManager: ConnectivityManager
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                if (::gameView.isInitialized && gameView.hasCloudAccount()) gameView.syncCloud()
            }
        }
    }

    companion object {
        private const val GOOGLE_SIGN_IN_REQUEST = 7401
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(65, 44, 112)
        window.navigationBarColor = Color.rgb(35, 24, 63)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        auth = FirebaseAuth.getInstance()
        googleSignInClient = buildGoogleSignInClient()
        gameView = PetGameView(this) { maybePromptForCloudBackup() }
        setContentView(gameView)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        gameView.postDelayed({ gameView.checkForUpdates(showNoUpdate = false) }, 650L)
        gameView.postDelayed({
            if (auth.currentUser != null) gameView.restoreCloudAtStartup()
            else if (gameView.hasCreatedPet()) maybePromptForCloudBackup()
            else maybePromptForCloudRestore()
        }, 1800L)
    }

    override fun onPause() {
        gameView.savePet()
        super.onPause()
    }

    override fun onDestroy() {
        if (::connectivityManager.isInitialized) connectivityManager.unregisterNetworkCallback(networkCallback)
        super.onDestroy()
    }

    private fun buildGoogleSignInClient(): GoogleSignInClient? {
        val clientIdResource = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (clientIdResource == 0) return null
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(clientIdResource))
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(this, options)
    }

    private fun maybePromptForCloudBackup() {
        if (isFinishing || auth.currentUser != null || googleSignInClient == null || !gameView.hasCreatedPet()) return
        AlertDialog.Builder(this)
            .setTitle("Keep progress safe")
            .setMessage("Sign in with Google to back up ${gameView.petName()} and restore it on another phone. If a backup already exists, it will be restored instead of replaced.")
            .setNegativeButton("NOT NOW", null)
            .setPositiveButton("SIGN IN") { _, _ -> startGoogleSignIn() }
            .show()
    }

    private fun maybePromptForCloudRestore() {
        if (isFinishing || auth.currentUser != null || googleSignInClient == null || gameView.hasCreatedPet()) return
        AlertDialog.Builder(this)
            .setTitle("Restore your pet?")
            .setMessage("Sign in with Google to check for your saved pet. If no backup exists, you can create a new one.")
            .setNegativeButton("NEW PET", null)
            .setPositiveButton("SIGN IN") { _, _ -> startGoogleSignIn() }
            .show()
    }

    private fun startGoogleSignIn() {
        val client = googleSignInClient
        if (client == null) {
            Toast.makeText(this, "Google backup is still being configured.", Toast.LENGTH_LONG).show()
            return
        }
        startActivityForResult(client.signInIntent, GOOGLE_SIGN_IN_REQUEST)
    }

    @Deprecated("Uses the Google Sign-In activity result API for Android 8 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != GOOGLE_SIGN_IN_REQUEST) return
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            val token = account.idToken
            if (token.isNullOrBlank()) throw IllegalStateException("Google did not return an ID token")
            auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        gameView.restoreAfterSignIn()
                    } else {
                        Toast.makeText(this, "Google sign-in failed. Progress is still saved on this phone.", Toast.LENGTH_LONG).show()
                    }
                }
        } catch (_: Exception) {
            Toast.makeText(this, "Google sign-in was cancelled.", Toast.LENGTH_SHORT).show()
        }
    }
}

/**
 * Optional self-updates from the repository's GitHub Releases page.  A release
 * tag must be v<versionCode> and include an APK asset signed with this app's
 * existing signing key.
 */
private class AppUpdateManager(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun check(showNoUpdate: Boolean) {
        Thread {
            try {
                val release = try {
                    JSONObject(readUrl(UPDATE_MANIFEST_URL))
                } catch (_: Exception) {
                    JSONObject(readUrl(LATEST_RELEASE_URL))
                }
                val versionCode = release.optInt("versionCode", 0).takeIf { it > 0 }
                    ?: release.optString("tag_name").removePrefix("v").toIntOrNull()
                var downloadUrl = release.optString("apkUrl").takeIf { it.isNotBlank() }
                if (downloadUrl.isNullOrBlank()) {
                    val assets = release.optJSONArray("assets")
                    if (assets != null) {
                        for (index in 0 until assets.length()) {
                            val asset = assets.getJSONObject(index)
                            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                                downloadUrl = asset.optString("browser_download_url")
                                break
                            }
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

    private fun readUrl(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "ZoeysPocketPet-Updater")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
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
        val updateFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), UPDATE_FILE_NAME)
        if (updateFile.exists()) updateFile.delete()
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Zoey's Pocket Pet update")
            .setDescription("Downloading, then opening the installer")
            // Without an APK MIME type Android treats the completed download as
            // a generic file and sends the user to the Downloads app.
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, UPDATE_FILE_NAME)
        val downloadId = manager.enqueue(request)
        Toast.makeText(context, "Downloading update…", Toast.LENGTH_SHORT).show()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) != downloadId) return
                receiverContext.unregisterReceiver(this)
                val query = DownloadManager.Query().setFilterById(downloadId)
                manager.query(query).use { cursor ->
                    if (!cursor.moveToFirst() || cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) != DownloadManager.STATUS_SUCCESSFUL) {
                        Toast.makeText(context, "The update download didn't finish.", Toast.LENGTH_SHORT).show()
                        return
                    }
                }
                // A DownloadManager broadcast is not allowed to launch UI on
                // recent Android versions.  Returning to the active Activity
                // makes the system package installer a permitted user-visible
                // handoff instead of leaving a file in Downloads.
                mainHandler.post { install(updateFile) }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun install(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Toast.makeText(context, "Allow installs from Zoey's Pocket Pet, then check for updates again.", Toast.LENGTH_LONG).show()
            return
        }
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val UPDATE_MANIFEST_URL = "https://raw.githubusercontent.com/bottMage/zoeys-pocket-pet/main/update.json"
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/bottMage/zoeys-pocket-pet/releases/latest"
        const val UPDATE_FILE_NAME = "zoeys-pocket-pet-update.apk"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}

private class PetGameView(context: Context, private val onPetCreated: () -> Unit) : View(context) {
    private val appContext = context
    private val prefs = context.getSharedPreferences("zoey_pet", Context.MODE_PRIVATE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = PaintTypeface.rounded() }
    private val pet = PetState(prefs)
    private val cloudSave = CloudSaveManager()
    private val petArtCache = HashMap<PetKind, Bitmap>()
    private val walkFrameCache = HashMap<String, Bitmap>()
    private val walkFrameBottomCache = HashMap<String, Int>()
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
    private var setupMode = !pet.created
    private var setupKind = pet.kind
    private var setupName = pet.name
    // A clean install can show the setup screen before Google sign-in finishes.
    // Keep the cloud copy authoritative for that first restore so a new pet
    // cannot overwrite the existing account backup.
    private var preferCloudRestore = !pet.created
    // Never write to Firestore until the first read for this session succeeds.
    // This protects an existing backup from onPause(), retries, and setup UI.
    private var cloudSyncReady = false
    private var menuOpen = false
    private var menuAnimationStart = 0L
    private var menuOpening = true

    private fun petArtwork(kind: PetKind): Bitmap = petArtCache.getOrPut(kind) {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 2
            inScaled = false
        }
        BitmapFactory.decodeResource(resources, petArtResources.getValue(kind), options)
            ?: error("Unable to load artwork for ${kind.label}")
    }

    private fun walkFrameArtwork(kind: PetKind, frame: Int): Bitmap {
        val index = frame.mod(WALK_FRAME_COUNT)
        val cacheKey = "${kind.name}_$index"
        return walkFrameCache.getOrPut(cacheKey) {
            val resourceId = resources.getIdentifier("walk_${kind.name.lowercase()}_$index", "drawable", context.packageName)
            check(resourceId != 0) { "Missing walk frame: $cacheKey" }
            BitmapFactory.decodeResource(resources, resourceId)
                ?: error("Unable to decode walk frame: $cacheKey")
        }
    }

    /**
     * Each animation cel has a transparent border.  Anchor the lowest visible
     * pixel, rather than the edge of that border, to the grass so a paw never
     * appears to hover when a cel has slightly different padding.
     */
    private fun walkFrameBottom(kind: PetKind, frame: Int): Int {
        val index = frame.mod(WALK_FRAME_COUNT)
        val cacheKey = "${kind.name}_$index"
        return walkFrameBottomCache.getOrPut(cacheKey) {
            val bitmap = walkFrameArtwork(kind, index)
            for (y in bitmap.height - 1 downTo 0) {
                for (x in 0 until bitmap.width) {
                    if (Color.alpha(bitmap.getPixel(x, y)) != 0) return@getOrPut y + 1
                }
            }
            bitmap.height
        }
    }

    init {
        isFocusable = true
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        pet.updateFromClock()
        motionModeUntil = motionLastAt + 1800L
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
        if (menuOpen || menuAnimationStart != 0L) drawMenu(canvas, now)
        if (now - lastSaved > 30_000L) savePet()
        postInvalidateDelayed(33L)
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
        val updates = headerUpdateRect()
        val titleLeft = dp(66f)
        val titleRight = updates.left - dp(16f)
        val titleWidth = (titleRight - titleLeft).coerceAtLeast(dp(100f))
        val titleCenter = (titleLeft + titleRight) / 2f

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = Color.rgb(68, 43, 90)
        textPaint.textSize = dp(22f)
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = min(dp(22f), dp(22f) * titleWidth / textPaint.measureText("ZOEY'S POCKET PET"))
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("ZOEY'S POCKET PET", titleCenter, dp(38f), textPaint)
        textPaint.textSize = dp(13f)
        textPaint.typeface = PaintTypeface.rounded()
        textPaint.color = Color.rgb(105, 78, 116)
        textPaint.textSize = min(dp(13f), dp(13f) * titleWidth / textPaint.measureText("A tiny friend made just for you"))
        canvas.drawText("A tiny friend made just for you", titleCenter, dp(59f), textPaint)

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
        textPaint.textSize = dp(10f)
        textPaint.typeface = PaintTypeface.bold()
        textPaint.color = Color.rgb(105, 78, 116)
        canvas.drawText("v${BuildConfig.VERSION_NAME}", width - dp(22f), dp(74f), textPaint)

        val menu = menuButtonRect()
        paint.color = Color.argb(70, 48, 27, 89)
        canvas.drawRoundRect(menu, dp(16f), dp(16f), paint)
        paint.color = Color.rgb(68, 43, 90)
        val lineLeft = menu.left + dp(12f)
        val lineRight = menu.right - dp(12f)
        for (line in 0..2) {
            val y = menu.top + dp(13f) + line * dp(6f)
            canvas.drawRoundRect(RectF(lineLeft, y, lineRight, y + dp(2f)), dp(1f), dp(1f), paint)
        }
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

    private fun drawPlayground(canvas: Canvas, now: Long) {
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
        canvas.restore()
    }

    private fun drawCloud(canvas: Canvas, x: Float, y: Float, scale: Float) {
        canvas.drawCircle(x, y, dp(22f) * scale, paint)
        canvas.drawCircle(x + dp(22f) * scale, y - dp(8f) * scale, dp(28f) * scale, paint)
        canvas.drawCircle(x + dp(52f) * scale, y, dp(20f) * scale, paint)
        canvas.drawRoundRect(RectF(x - dp(5f) * scale, y, x + dp(60f) * scale, y + dp(18f) * scale), dp(10f), dp(10f), paint)
    }

    private fun drawPet(canvas: Canvas, now: Long) {
        val top = dp(77f)
        val bottom = statsTop() - dp(10f)
        val sceneLeft = dp(18f)
        val sceneRight = width - dp(18f)
        val dt = ((now - motionLastAt).coerceAtLeast(0L)).coerceAtMost(120L) / 1000f
        motionLastAt = now
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
            if (motionMode == MotionMode.WALK && motionX <= .08f) motionDirection = 1f
            if (motionMode == MotionMode.WALK && motionX >= .92f) motionDirection = -1f
        }
        if (motionMode == MotionMode.WALK && activeAction == null) {
            val walkProgress = ((motionModeUntil - now) / 500f).coerceIn(0f, 1f)
            val startBlend = min(1f, (now - motionModeStartedAt).coerceAtLeast(0L) / 500f)
            // Give each gait cycle enough forward travel to match the foot
            // cadence.  The blend still eases into and out of each walk.
            val speed = .11f * min(startBlend, walkProgress.coerceIn(0f, 1f))
            motionX += motionDirection * dt * speed
            if (motionX <= 0f) { motionX = 0f; motionDirection = 1f }
            if (motionX >= 1f) { motionX = 1f; motionDirection = -1f }
        }

        val stageScale = when (pet.stage) {
            "BABY" -> .90f
            "YOUNG" -> .96f
            "TEEN" -> 1f
            "EVOLVED" -> 1.08f
            else -> 1f
        }
        val artWidth = min(width - dp(42f), dp(296f)) * stageScale
        val groundY = bottom - dp(42f)
        // The cel is a 512px canvas with transparent padding. Constraining
        // its full bitmap made the pet appear trapped in a smaller box. Use
        // the visible silhouette envelope so the actual pet can use the full
        // scene while its transparent edges sit safely outside it.
        val visibleReach = artWidth * .42f
        val minCenterX = sceneLeft + visibleReach
        val maxCenterX = sceneRight - visibleReach
        val centerX = minCenterX + motionX * (maxCenterX - minCenterX)
        val seconds = (now - animationStart) / 1000f
        val walking = motionMode == MotionMode.WALK && activeAction == null
        // Keep the feet planted while resting.  A whole-body vertical bob reads
        // as hovering, especially against the simple ground in this scene.
        val idleBob = 0f
        val playBounce = if (activeAction == Action.PLAY) -abs(sin(seconds * 12f)) * dp(9f) else 0f
        val rootY = groundY + idleBob + playBounce

        // A tight, dark contact shadow anchors every paw to the grass.
        paint.color = Color.argb(58, 67, 57, 82)
        canvas.drawOval(
            RectF(centerX - artWidth * .25f, groundY - dp(3f), centerX + artWidth * .25f, groundY + dp(7f)),
            paint
        )
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.color = Color.WHITE
        // 10 fps gives the drawn cels time to read as a deliberate gait rather
        // than a frantic, glitchy run.
        val frame = if (walking || activeAction == Action.PLAY) ((now / 105L) % WALK_FRAME_COUNT).toInt() else 0
        val bitmap = walkFrameArtwork(pet.kind, frame)
        val artScale = artWidth / bitmap.width
        val visibleBottom = walkFrameBottom(pet.kind, frame)
        val artTop = rootY - visibleBottom * artScale
        val artBottom = artTop + bitmap.height * artScale
        val artRect = RectF(centerX - artWidth / 2f, artTop, centerX + artWidth / 2f, artBottom)
        canvas.save()
        // The artwork faces left by default.  Mirror it only while travelling
        // right; the old condition reversed that relationship.
        if (motionDirection > 0f && walking) canvas.scale(-1f, 1f, centerX, rootY)
        canvas.drawBitmap(bitmap, null, artRect, paint)
        canvas.restore()

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

        val reset = newPetRect()
        val title = "${pet.name.uppercase()}'S LITTLE CHECK-IN"
        val titleLeft = dp(22f)
        val titleRight = reset.left - dp(14f)
        val titleWidth = (titleRight - titleLeft).coerceAtLeast(dp(100f))
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = dp(17f)
        textPaint.color = Color.rgb(68, 43, 90)
        textPaint.textSize = min(dp(17f), dp(17f) * titleWidth / textPaint.measureText(title))
        canvas.drawText(title, titleLeft, top + dp(29f), textPaint)
        textPaint.textSize = dp(12f)
        textPaint.typeface = PaintTypeface.rounded()
        textPaint.color = Color.rgb(111, 82, 123)
        canvas.drawText("${pet.stage} • AGE ${pet.ageLabel} • CARE ${pet.carePercent.roundToInt()}%", dp(22f), top + dp(48f), textPaint)
        textPaint.textSize = dp(11f)
        textPaint.typeface = PaintTypeface.bold()
        textPaint.color = Color.rgb(105, 78, 116)
        canvas.drawText("EVOLUTION ${pet.evolutionProgress.roundToInt()}% • ${pet.evolutionHint}", dp(22f), top + dp(61f), textPaint)

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
            // Leave a clear gap below the evolution hint.  The old first row
            // started almost on top of that line at compact phone widths.
            val y = top + dp(88f) + row * dp(43f)
            textPaint.textSize = dp(11f)
            textPaint.typeface = PaintTypeface.bold()
            textPaint.color = Color.rgb(105, 82, 113)
            val label = values[i].second
            val value = "${values[i].first.roundToInt()}%"
            val labelWidth = textPaint.measureText(label)
            val valueWidth = textPaint.measureText(value)
            val groupWidth = labelWidth + dp(10f) + valueWidth
            val groupLeft = x + (colWidth - groupWidth) / 2f
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, groupLeft, y, textPaint)
            canvas.drawText(value, groupLeft + labelWidth + dp(10f), y, textPaint)
            textPaint.textAlign = Paint.Align.LEFT
            paint.color = Color.rgb(237, 225, 233)
            canvas.drawRoundRect(RectF(x, y + dp(8f), x + colWidth, y + dp(15f)), dp(4f), dp(4f), paint)
            paint.color = colors[i]
            canvas.drawRoundRect(RectF(x, y + dp(8f), x + colWidth * values[i].first / 100f, y + dp(15f)), dp(4f), dp(4f), paint)
        }
    }

    private fun drawActions(canvas: Canvas) {
        buttons.clear()
        val top = height - dp(160f)
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
            // Draw a full, offset shadow behind the button instead of the
            // heavy horizontal strip that made the controls look cluttered.
            paint.color = Color.argb(24, 67, 39, 95)
            canvas.drawRoundRect(RectF(rect.left, rect.top + dp(4f), rect.right, rect.bottom + dp(7f)), dp(18f), dp(18f), paint)
            paint.color = if (pressedAction == actions[i]) Color.rgb(255, 255, 255) else fills[i]
            canvas.drawRoundRect(rect, dp(18f), dp(18f), paint)
            textPaint.typeface = PaintTypeface.bold()
            textPaint.textSize = dp(17f)
            textPaint.color = Color.rgb(76, 49, 94)
            val iconSlot = dp(22f)
            textPaint.textSize = dp(13f)
            val labelWidth = textPaint.measureText(labels[i])
            val groupWidth = iconSlot + dp(12f) + labelWidth
            val groupLeft = rect.centerX() - groupWidth / 2f
            textPaint.textSize = dp(17f)
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(glyphs[i], groupLeft + iconSlot / 2f, rect.top + dp(35f), textPaint)
            textPaint.textSize = dp(13f)
            textPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(labels[i], groupLeft + iconSlot + dp(12f), rect.top + dp(35f), textPaint)
        }
    }

    private fun drawMenu(canvas: Canvas, now: Long) {
        val menuWidth = dp(282f)
        val progress = ((now - menuAnimationStart).coerceAtLeast(0L) / 220f).coerceIn(0f, 1f)
        val eased = progress * progress * (3f - 2f * progress)
        val visibleProgress = if (menuOpening) eased else 1f - eased
        val offset = (visibleProgress - 1f) * menuWidth

        paint.color = Color.argb((150f * visibleProgress).roundToInt(), 25, 19, 42)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        canvas.save()
        canvas.translate(offset, 0f)
        paint.color = Color.rgb(255, 249, 246)
        canvas.drawRoundRect(RectF(0f, 0f, menuWidth + dp(24f), height.toFloat()), 0f, 0f, paint)
        paint.color = Color.rgb(68, 43, 90)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = dp(21f)
        textPaint.color = Color.rgb(68, 43, 90)
        canvas.drawText("MENU", dp(24f), dp(54f), textPaint)
        textPaint.typeface = PaintTypeface.rounded()
        textPaint.textSize = dp(12f)
        textPaint.color = Color.rgb(111, 82, 123)
        canvas.drawText(
            if (cloudSave.isSignedIn()) "Cloud backup connected" else "Cloud backup not connected",
            dp(24f), dp(78f), textPaint
        )

        val settings = menuSettingsRect()
        paint.color = Color.rgb(238, 224, 239)
        canvas.drawRoundRect(settings, dp(17f), dp(17f), paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = dp(14f)
        textPaint.color = Color.rgb(76, 49, 94)
        canvas.drawText("SETTINGS", settings.centerX(), settings.centerY() + dp(5f), textPaint)
        canvas.restore()

        if (progress < 1f) postInvalidateOnAnimation()
        if (!menuOpen && progress >= 1f) menuAnimationStart = 0L
    }

    private fun setMenuOpen(open: Boolean) {
        menuOpen = open
        menuOpening = open
        menuAnimationStart = SystemClock.uptimeMillis()
        invalidate()
    }

    private fun showSettings() {
        val activity = appContext as? Activity ?: return
        AlertDialog.Builder(activity)
            .setTitle("Settings")
            .setMessage(if (cloudSave.isSignedIn()) "Google backup is connected." else "Google backup is not connected yet.")
            .setNegativeButton("CLOSE", null)
            .setPositiveButton("RESET DATA") { _, _ -> showResetChoices() }
            .show()
    }

    private fun showResetChoices() {
        val activity = appContext as? Activity ?: return
        val choices = arrayOf("Local data only", "Cloud backup only", "Local + cloud data")
        AlertDialog.Builder(activity)
            .setTitle("What should be reset?")
            .setItems(choices) { _, which -> confirmReset(which) }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun confirmReset(which: Int) {
        val activity = appContext as? Activity ?: return
        val descriptions = arrayOf(
            "This clears this phone only. Your cloud backup stays available.",
            "This deletes the cloud backup. The pet on this phone stays.",
            "This permanently deletes the phone copy and cloud backup."
        )
        AlertDialog.Builder(activity)
            .setTitle("Reset data?")
            .setMessage(descriptions[which])
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("RESET") { _, _ ->
                when (which) {
                    0 -> resetLocalData()
                    1 -> resetCloudData()
                    else -> resetBoth()
                }
            }
            .show()
    }

    private fun resetLocalData(showToast: Boolean = true) {
        cloudSave.signOut()
        pet.resetLocal()
        setupMode = true
        setupKind = pet.kind
        setupName = pet.name
        pressedAction = null
        activeAction = null
        if (showToast) Toast.makeText(appContext, "Local pet data reset. Cloud backup was kept.", Toast.LENGTH_LONG).show()
        invalidate()
    }

    private fun resetCloudData() {
        cloudSave.delete { deleted ->
            if (deleted) cloudSave.signOut()
            Toast.makeText(
                appContext,
                if (deleted) "Cloud backup deleted. This phone's pet was kept." else "Cloud backup could not be deleted.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun resetBoth() {
        cloudSave.delete { deleted ->
            if (deleted) {
                cloudSave.signOut()
                resetLocalData(showToast = false)
                Toast.makeText(appContext, "Local and cloud data reset.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(appContext, "Cloud data was not deleted; nothing was reset.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun statsTop(): Float = height - dp(330f)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (menuOpen || menuAnimationStart != 0L) return handleMenuTouch(event)
        if (setupMode) return handleSetupTouch(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (menuButtonRect().contains(event.x, event.y)) {
                    setMenuOpen(true)
                    return true
                }
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

    private fun handleMenuTouch(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        if (menuSettingsRect().contains(event.x, event.y)) {
            setMenuOpen(false)
            postDelayed({ showSettings() }, 230L)
        } else if (event.x > dp(282f)) {
            setMenuOpen(false)
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
            post { onPetCreated() }
            invalidate()
        }
        return true
    }

    private fun newPetRect(): RectF = RectF(width - dp(94f), statsTop() + dp(14f), width - dp(18f), statsTop() + dp(46f))

    private fun menuButtonRect(): RectF = RectF(dp(12f), dp(18f), dp(54f), dp(57f))

    private fun menuSettingsRect(): RectF = RectF(dp(24f), dp(112f), dp(258f), dp(164f))

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
        cloudSave.upload()
        lastSaved = SystemClock.uptimeMillis()
    }

    fun syncCloud() {
        val restoringFreshInstall = preferCloudRestore
        cloudSave.restore(preferCloud = restoringFreshInstall) { result ->
            if (restoringFreshInstall) {
                finishCloudRestore(result)
            } else {
                if (result == CloudRestoreResult.RESTORED) invalidate()
                if (result == CloudRestoreResult.FAILED) {
                    Toast.makeText(appContext, "Cloud backup could not sync yet; this phone still has your progress.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun restoreCloudAtStartup() {
        cloudSave.restore(preferCloud = preferCloudRestore) { result ->
            finishCloudRestore(result)
        }
    }

    fun restoreAfterSignIn() {
        cloudSave.restore(preferCloud = preferCloudRestore) { result ->
            finishCloudRestore(result)
        }
    }

    private fun finishCloudRestore(result: CloudRestoreResult) {
        when (result) {
            CloudRestoreResult.RESTORED -> {
                preferCloudRestore = false
                cloudSyncReady = true
                setupMode = false
                setupKind = pet.kind
                setupName = pet.name
                invalidate()
                Toast.makeText(appContext, "Cloud progress restored.", Toast.LENGTH_LONG).show()
            }
            CloudRestoreResult.NO_CLOUD_BACKUP -> {
                preferCloudRestore = false
                cloudSyncReady = true
                if (hasCreatedPet()) {
                    Toast.makeText(appContext, "Google backup enabled.", Toast.LENGTH_SHORT).show()
                }
            }
            CloudRestoreResult.KEPT_LOCAL -> {
                preferCloudRestore = false
                cloudSyncReady = true
                invalidate()
                Toast.makeText(appContext, "Google backup synced.", Toast.LENGTH_SHORT).show()
            }
            CloudRestoreResult.FAILED -> {
                Toast.makeText(appContext, "Cloud backup could not sync yet; this phone still has its local progress.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun hasCreatedPet(): Boolean = pet.created && pet.hatched

    fun hasCloudAccount(): Boolean = cloudSave.isSignedIn()

    fun petName(): String = pet.name

    fun checkForUpdates(showNoUpdate: Boolean) {
        AppUpdateManager(appContext).check(showNoUpdate)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private data class ActionButton(val action: Action, val rect: RectF)

    private enum class Action { FEED, PLAY, BATH, SLEEP }

    private enum class CloudRestoreResult {
        RESTORED,
        NO_CLOUD_BACKUP,
        KEPT_LOCAL,
        FAILED
    }

    companion object {
        private const val WALK_FRAME_COUNT = 12
    }

    private enum class MotionMode { REST, WALK, CURIOUS, STAND }

    private enum class PetKind(val label: String, val light: Int, val primary: Int, val dark: Int) {
        CAT("CAT", Color.rgb(239, 220, 190), Color.rgb(189, 139, 105), Color.rgb(108, 74, 75)),
        DOG("DOG", Color.rgb(255, 239, 205), Color.rgb(214, 174, 123), Color.rgb(113, 78, 65)),
        BUNNY("BUNNY", Color.rgb(255, 207, 214), Color.rgb(245, 166, 186), Color.rgb(157, 83, 116)),
        HAMSTER("HAMSTER", Color.rgb(255, 222, 164), Color.rgb(227, 168, 91), Color.rgb(142, 92, 53)),
        DRAGON("DRAGON", Color.rgb(194, 235, 177), Color.rgb(106, 184, 126), Color.rgb(47, 104, 82))
    }

    private inner class CloudSaveManager {
        private val auth = FirebaseAuth.getInstance()
        private val firestore = FirebaseFirestore.getInstance()

        fun isSignedIn(): Boolean = auth.currentUser != null

        fun signOut() {
            auth.signOut()
            cloudSyncReady = false
        }

        fun upload() {
            val user = auth.currentUser ?: return
            if (!cloudSyncReady || !pet.hasCreatedPet()) return
            if (pet.savedAt == 0L) pet.save()
            val data = pet.cloudData().toMutableMap()
            data["updatedAt"] = FieldValue.serverTimestamp()
            firestore.collection("users").document(user.uid)
                .collection("pets").document("main")
                .set(data, SetOptions.merge())
        }

        fun restore(preferCloud: Boolean, onComplete: (CloudRestoreResult) -> Unit) {
            val user = auth.currentUser
            if (user == null) {
                onComplete(CloudRestoreResult.FAILED)
                return
            }
            firestore.collection("users").document(user.uid)
                .collection("pets").document("main")
                .get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) {
                        cloudSyncReady = true
                        if (pet.hasCreatedPet()) upload()
                        onComplete(CloudRestoreResult.NO_CLOUD_BACKUP)
                    } else {
                        val cloudHasPet = snapshot.getBoolean("created") == true && snapshot.getBoolean("hatched") == true
                        val cloudSavedAt = snapshot.getLong("savedAt") ?: 0L
                        when {
                            cloudHasPet && (preferCloud || cloudSavedAt > pet.savedAt) -> {
                                pet.loadCloud(snapshot.data.orEmpty())
                                pet.save()
                                cloudSyncReady = true
                                onComplete(CloudRestoreResult.RESTORED)
                            }
                            pet.hasCreatedPet() && pet.savedAt > cloudSavedAt -> {
                                cloudSyncReady = true
                                upload()
                                onComplete(CloudRestoreResult.KEPT_LOCAL)
                            }
                            cloudHasPet -> {
                                cloudSyncReady = true
                                onComplete(CloudRestoreResult.KEPT_LOCAL)
                            }
                            pet.hasCreatedPet() -> {
                                cloudSyncReady = true
                                upload()
                                onComplete(CloudRestoreResult.NO_CLOUD_BACKUP)
                            }
                            else -> {
                                cloudSyncReady = true
                                onComplete(CloudRestoreResult.NO_CLOUD_BACKUP)
                            }
                        }
                    }
                }
                .addOnFailureListener { onComplete(CloudRestoreResult.FAILED) }
        }

        fun delete(onComplete: (Boolean) -> Unit) {
            val user = auth.currentUser
            if (user == null) {
                onComplete(true)
                return
            }
            firestore.collection("users").document(user.uid)
                .collection("pets").document("main")
                .delete()
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        }
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
        private var lastSavedAt = prefs.getLong("saved_at", 0L)
        private var evolutionEvent = false

        val savedAt: Long
            get() = lastSavedAt

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

        fun resetLocal() {
            prefs.edit().clear().apply()
            hunger = 78f
            joy = 82f
            energy = 74f
            clean = 88f
            growth = 0f
            generation = 0
            ageMillis = 0L
            goodCareMillis = 0L
            totalCareMillis = 0L
            name = "Mochi"
            kind = PetKind.BUNNY
            created = false
            hatched = false
            lastUpdate = System.currentTimeMillis()
            lastSavedAt = 0L
            evolutionEvent = false
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

        fun cloudData(): Map<String, Any> = mapOf(
            "hunger" to hunger.toDouble(),
            "joy" to joy.toDouble(),
            "energy" to energy.toDouble(),
            "clean" to clean.toDouble(),
            "growth" to growth.toDouble(),
            "generation" to generation.toLong(),
            "ageMillis" to ageMillis,
            "goodCareMillis" to goodCareMillis,
            "totalCareMillis" to totalCareMillis,
            "name" to name,
            "kind" to kind.name,
            "created" to created,
            "hatched" to hatched,
            "lastUpdate" to lastUpdate,
            "savedAt" to lastSavedAt
        )

        fun hasCreatedPet(): Boolean = created && hatched

        fun loadCloud(data: Map<String, Any>) {
            fun number(key: String, fallback: Float): Float = (data[key] as? Number)?.toFloat() ?: fallback
            fun long(key: String, fallback: Long): Long = (data[key] as? Number)?.toLong() ?: fallback

            hunger = number("hunger", hunger).coerceIn(0f, 100f)
            joy = number("joy", joy).coerceIn(0f, 100f)
            energy = number("energy", energy).coerceIn(0f, 100f)
            clean = number("clean", clean).coerceIn(0f, 100f)
            growth = number("growth", growth).coerceIn(0f, 100f)
            generation = long("generation", generation.toLong()).toInt().coerceIn(0, 2)
            ageMillis = long("ageMillis", ageMillis)
            goodCareMillis = long("goodCareMillis", goodCareMillis)
            totalCareMillis = long("totalCareMillis", totalCareMillis)
            name = ((data["name"] as? String)?.take(14))?.ifBlank { name } ?: name
            (data["kind"] as? String)?.let { value ->
                kind = PetKind.values().firstOrNull { it.name == value } ?: kind
            }
            created = data["created"] as? Boolean ?: created
            hatched = data["hatched"] as? Boolean ?: hatched
            lastUpdate = long("lastUpdate", lastUpdate)
            lastSavedAt = long("savedAt", lastSavedAt)
        }

        fun save() {
            lastSavedAt = System.currentTimeMillis()
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
                .putLong("saved_at", lastSavedAt)
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
