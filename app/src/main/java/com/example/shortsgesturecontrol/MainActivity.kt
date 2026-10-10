package com.example.shortsgesturecontrol

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
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
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.net.Uri
import android.location.Location
import android.location.LocationManager
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
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
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalTime
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
                if (::gameView.isInitialized) {
                    gameView.refreshWeather()
                    if (gameView.hasCloudAccount()) gameView.syncCloud()
                }
            }
        }
    }

    companion object {
        private const val GOOGLE_SIGN_IN_REQUEST = 7401
        private const val WEATHER_PERMISSION_REQUEST = 7402
        private const val CARE_NOTIFICATION_PERMISSION_REQUEST = 7403
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(65, 44, 112)
        window.navigationBarColor = Color.rgb(35, 24, 63)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        auth = FirebaseAuth.getInstance()
        googleSignInClient = buildGoogleSignInClient()
        gameView = PetGameView(this) {
            maybePromptForCloudBackup()
            gameView.postDelayed({ maybeRequestCareReminderPermission() }, 1200L)
            gameView.postDelayed({ gameView.maybeShowTutorialIfNeeded() }, 800L)
        }
        setContentView(gameView)
        CareReminderScheduler.ensureScheduled(this)
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        gameView.postDelayed({ startWeatherSync() }, 3200L)
        gameView.postDelayed({ gameView.checkForUpdates(showNoUpdate = false) }, 650L)
        gameView.postDelayed({ maybeRequestCareReminderPermission() }, 4200L)
        gameView.postDelayed({
            if (auth.currentUser != null) gameView.restoreCloudAtStartup()
            else if (gameView.hasCreatedPet()) maybePromptForCloudBackup()
            else maybePromptForCloudRestore()
        }, 1800L)
        gameView.postDelayed({ gameView.maybeShowTutorialIfNeeded() }, 5200L)
    }

    override fun onPause() {
        gameView.pauseForActivity()
        gameView.savePet()
        CareReminderScheduler.markActivityHidden(this)
        // Check immediately on exit so a stat that reached 20% during the
        // visible session does not wait for the periodic background check.
        CareReminderScheduler.enqueueImmediateCheck(this)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::gameView.isInitialized) {
            CareReminderScheduler.markActivityVisible(this)
            gameView.resumeForActivity()
            gameView.resumePendingInstall()
        }
    }

    override fun onDestroy() {
        if (::connectivityManager.isInitialized) connectivityManager.unregisterNetworkCallback(networkCallback)
        super.onDestroy()
    }

    private fun startWeatherSync() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), WEATHER_PERMISSION_REQUEST)
        } else {
            gameView.refreshWeather()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == WEATHER_PERMISSION_REQUEST) gameView.refreshWeather()
    }

    fun maybeRequestCareReminderPermission(force: Boolean = false) {
        if (!::gameView.isInitialized || !gameView.hasCreatedPet()) return
        CareReminderScheduler.ensureScheduled(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) return
        val promptPrefs = getSharedPreferences("zoey_pet", Context.MODE_PRIVATE)
        if (!force && promptPrefs.getBoolean("care_notification_prompted", false)) return
        if (!hasWindowFocus()) {
            gameView.postDelayed({ maybeRequestCareReminderPermission(force) }, 1500L)
            return
        }
        promptPrefs.edit().putBoolean("care_notification_prompted", true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), CARE_NOTIFICATION_PERMISSION_REQUEST)
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
    private val updatePrefs = context.getSharedPreferences("zoey_update", Context.MODE_PRIVATE)
    // Accessed only on the UI thread, including completion delivery.
    private var checking = false
    private var showCheckResult = false
    private var updateDialog: AlertDialog? = null
    private var downloadDialog: AlertDialog? = null
    private var downloadProgress: ProgressBar? = null
    private var downloadLabel: TextView? = null

    private fun updateFile(): File = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
        UPDATE_FILE_NAME
    )

    fun check(showNoUpdate: Boolean) {
        showCheckResult = showCheckResult || showNoUpdate
        if (checking) return
        checking = true
        if (showNoUpdate) Toast.makeText(context, "Checking for updates…", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val installedVersion = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
                val release = UpdateChecker.check(installedVersion, {
                    val manifest = JSONObject(UpdateChecker.readFreshJson(UpdateChecker.MANIFEST))
                    UpdateChecker.Release(manifest.getInt("versionCode"), manifest.getString("apkUrl"))
                }, { UpdateChecker.publishedRelease(installedVersion) })
                Pair(installedVersion, release)
            }
            mainHandler.post {
                val report = showCheckResult
                checking = false
                showCheckResult = false
                result.fold(onSuccess = { (installedVersion, release) ->
                    when {
                        release.version > installedVersion -> showUpdate(release.version, release.apkUrl)
                        report -> Toast.makeText(context, "You're all up to date.", Toast.LENGTH_SHORT).show()
                    }
                }, onFailure = {
                    if (report) Toast.makeText(context, "Couldn't confirm the latest version. Please try again.", Toast.LENGTH_LONG).show()
                })
            }
        }.start()
    }

    private fun showUpdate(versionCode: Int, url: String) {
        val activity = context as? Activity ?: return
        if (activity.isFinishing || activity.isDestroyed || updateDialog?.isShowing == true) return
        updateDialog = AlertDialog.Builder(activity)
            .setTitle("An update is ready")
            .setMessage("Version $versionCode is available. Would you like to download it now?")
            .setNegativeButton("NOT NOW", null)
            .setPositiveButton("DOWNLOAD") { _, _ -> download(versionCode, url) }
            .show()
    }

    private fun download(versionCode: Int, url: String) {
        // Some phones reject both GitHub hosts in DownloadManager. Download
        // through the app instead, following redirects ourselves, then hand
        // the completed private file to the normal package installer.
        val sources = listOf(url, UpdateChecker.rawAssetUrl(versionCode)).distinct()
        val updateFile = updateFile()
        if (updateFile.exists()) updateFile.delete()
        showDownloadProgress()
        Thread {
            var lastError: Exception? = null
            var downloaded = false
            for ((index, source) in sources.withIndex()) {
                try {
                    mainHandler.post { beginDownloadAttempt(index) }
                    downloadToFile(source, updateFile) { bytes, total ->
                        mainHandler.post { updateDownloadProgress(bytes, total) }
                    }
                    downloaded = true
                    break
                } catch (error: Exception) {
                    lastError = error
                    Log.w(TAG, "Update source failed: $source", error)
                }
            }
            if (downloaded) {
                mainHandler.post {
                    dismissDownloadProgress()
                    install(updateFile)
                }
            } else {
                mainHandler.post {
                    dismissDownloadProgress()
                    Toast.makeText(context, "Update download failed. Please try again.", Toast.LENGTH_LONG).show()
                }
                Log.w(TAG, "All update sources failed", lastError)
            }
        }.start()
    }

    private fun showDownloadProgress() {
        val activity = context as? Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val label = TextView(activity).apply {
            gravity = Gravity.CENTER
            text = "Connecting…"
            setPadding(0, 0, 0, dp(10f).toInt())
        }
        val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            max = 100
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24f).toInt(), dp(4f).toInt(), dp(24f).toInt(), dp(8f).toInt())
            addView(label, LinearLayout.LayoutParams(-1, -2))
            addView(progress, LinearLayout.LayoutParams(-1, dp(8f).toInt()))
        }
        downloadLabel = label
        downloadProgress = progress
        downloadDialog = AlertDialog.Builder(activity)
            .setTitle("Downloading update")
            .setView(content)
            .setCancelable(false)
            .create()
        downloadDialog?.show()
    }

    private fun beginDownloadAttempt(index: Int) {
        downloadProgress?.isIndeterminate = true
        downloadProgress?.progress = 0
        downloadLabel?.text = if (index == 0) "Connecting…" else "Trying backup source…"
    }

    private fun updateDownloadProgress(bytes: Long, total: Long) {
        val progress = downloadProgress ?: return
        if (total > 0) {
            progress.isIndeterminate = false
            progress.progress = ((bytes * 100L) / total).coerceIn(0L, 100L).toInt()
            downloadLabel?.text = "${progress.progress}%  •  ${formatBytes(bytes)} / ${formatBytes(total)}"
        } else {
            progress.isIndeterminate = true
            downloadLabel?.text = "${formatBytes(bytes)} downloaded"
        }
    }

    private fun dismissDownloadProgress() {
        downloadDialog?.dismiss()
        downloadDialog = null
        downloadProgress = null
        downloadLabel = null
    }

    private fun formatBytes(bytes: Long): String = String.format(
        java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024.0 * 1024.0)
    )

    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density

    private fun downloadToFile(source: String, destination: File, onProgress: (Long, Long) -> Unit) {
        val freshSource = source + (if (source.contains("?")) "&" else "?") + "update_download=" + System.currentTimeMillis()
        val connection = (URL(freshSource).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15000
            readTimeout = 30000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ZoeysPocketPet-Updater")
            setRequestProperty("Accept", APK_MIME_TYPE)
            setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
        }
        val partial = File(destination.parentFile, "$UPDATE_FILE_NAME.part")
        partial.delete()
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong
            var downloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        output.write(buffer, 0, count)
                        downloaded += count
                        onProgress(downloaded, total)
                    }
                }
            }
            onProgress(downloaded, total)
            if (partial.length() < 1024L * 1024L || !partial.renameTo(destination)) {
                throw IOException("Downloaded APK was incomplete")
            }
        } finally {
            connection.disconnect()
            if (partial.exists()) partial.delete()
        }
    }

    fun resumePendingInstall() {
        val pendingPermission = updatePrefs.getBoolean(PENDING_PERMISSION, false)
        if (pendingPermission) {
            updatePrefs.edit().remove(PENDING_PERMISSION).apply()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()) {
                install(updateFile())
            }
            return
        }

    }

    private fun install(file: File) {
        if (!file.exists()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            updatePrefs.edit().putBoolean(PENDING_PERMISSION, true).apply()
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Toast.makeText(context, "Allow installs, then the installer will open automatically.", Toast.LENGTH_LONG).show()
            return
        }
        updatePrefs.edit().remove(PENDING_PERMISSION).apply()
        val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent(Intent.ACTION_VIEW)
            .setDataAndType(apkUri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val UPDATE_FILE_NAME = "zoeys-pocket-pet-update.apk"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val PENDING_PERMISSION = "pending_install_permission"
        const val TAG = "ZoeyPetUpdater"
    }
}

private class PetGameView(context: Context, private val onPetCreated: () -> Unit) : View(context) {
    private val appContext = context
    private val updateManager = AppUpdateManager(appContext)
    private val prefs = context.getSharedPreferences("zoey_pet", Context.MODE_PRIVATE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = PaintTypeface.rounded() }
    private val pet = PetState(prefs)
    private val cloudSave = CloudSaveManager()
    private val petArtCache = HashMap<PetKind, Bitmap>()
    private val walkFrameCache = HashMap<String, Bitmap>()
    private val walkFrameBoundsCache = HashMap<String, PetSpriteLayout.Bounds>()
    private val walkEnvelopeCache = HashMap<PetKind, PetSpriteLayout.Envelope>()
    private val growthColorFilters = HashMap<String, android.graphics.ColorMatrixColorFilter>()
    private val eggGradients = HashMap<PetKind, RadialGradient>()
    private val eggBackStrands = Array(26) { strand ->
        val x = -91f + strand * 7f
        Path().apply {
            moveTo(x - 8f, -6f + (strand % 4) * 3f)
            quadTo(x + 12f, -27f + (strand % 5) * 4f, x + 29f, -6f + (strand % 3) * 3f)
        }
    }
    private val eggFrontStrands = Array(21) { strand ->
        val x = -91f + strand * 8f
        Path().apply {
            moveTo(x - 9f, 2f + (strand % 3) * 2f)
            quadTo(x + 7f, 16f + (strand % 4), x + 24f, -1f + (strand % 3) * 2f)
        }
    }
    private val eggCrack = Path().apply {
        moveTo(-6f, -123f);lineTo(1f, -111f);lineTo(-6f, -99f)
        lineTo(6f, -88f);lineTo(1f, -74f)
    }
    private var warmedKind: PetKind? = null
    private val petArtResources = mapOf(
        PetKind.CAT to R.drawable.companion_cat,
        PetKind.DOG to R.drawable.companion_dog,
        PetKind.BUNNY to R.drawable.companion_bunny,
        PetKind.HAMSTER to R.drawable.companion_hamster,
        PetKind.DRAGON to R.drawable.companion_dragon
    )
    private val buttons = ArrayList<CareButton>()
    private var message = "Hi ${pet.playerName}! I'm so happy to see you!"
    private var messageUntil = 0L
    private var messageColor = Color.WHITE
    private var animationStart = SystemClock.uptimeMillis()
    private var lastSaved = animationStart
    private var pressedAction: CareAction? = null
    private var pressedPetTouch = false
    private var activeAction: Action? = null
    private var actionStartedAt = 0L
    private var actionUntil = 0L
    private var touchReactionStartedAt = 0L
    private var touchReactionUntil = 0L
    private var lifecycleDialogShowing = false
    private var hatchPromptShown = false
    private var evolutionPromptShown = false
    private var deathPromptShown = false
    private var transitionKind: TransitionKind? = null
    private var transitionStartedAt = 0L
    private var transitionUntil = 0L
    private var petCenterX = 0f
    private var petGroundY = 0f
    private var motionX = .5f
    private var motionDirection = 1f
    private var motionMode = MotionMode.REST
    private var motionModeUntil = 0L
    private var motionLastAt = SystemClock.uptimeMillis()
    private var motionModeStartedAt = motionLastAt
    private var setupMode = !pet.created
    private var setupKind = pet.kind
    private var setupName = pet.name
    private var setupPlayerName = pet.playerName
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
    // The care categories are always available; the old giant CARE opener is
    // intentionally gone. Selecting a category temporarily shows its actions.
    private var careCategory: CareCategory? = null
    private var tutorialShowing = false
    @Volatile private var weather = WeatherState()
    private var weatherLoading = false
    private var weatherStarted = false
    private var activityResumed = true
    private var lastCloudUploadAt = 0L
    private var ambientPlayer: MediaPlayer? = null
    private var ambientMode: AmbientMode? = null
    private var interactionPlayer: MediaPlayer? = null
    private val weatherHandler = Handler(Looper.getMainLooper())
    private val weatherRefresh = object : Runnable {
        override fun run() {
            if (!activityResumed || windowVisibility != View.VISIBLE) return
            refreshWeather()
            scheduleWeatherRefresh()
        }
    }

    private fun petArtwork(kind: PetKind): Bitmap = petArtCache.getOrPut(kind) {
        val options = BitmapFactory.Options().apply {
            inSampleSize = 2
            inScaled = false
        }
        BitmapFactory.decodeResource(resources, petArtResources.getValue(kind), options)
            ?: error("Unable to load artwork for ${kind.label}")
    }

    private fun walkFrameArtwork(kind: PetKind, frame: Int): Bitmap {
        val index = PetSpriteLayout.assetFrame(kind.name.lowercase(), frame)
        val cacheKey = "${kind.name}_$index"
        return walkFrameCache.getOrPut(cacheKey) {
            val resourceId = resources.getIdentifier("walk_${kind.name.lowercase()}_$index", "drawable", context.packageName)
            check(resourceId != 0) { "Missing walk frame: $cacheKey" }
            BitmapFactory.decodeResource(resources, resourceId, BitmapFactory.Options().apply { inScaled = false })
                ?: error("Unable to decode walk frame: $cacheKey")
        }
    }

    private fun walkFrameBounds(kind: PetKind, frame: Int): PetSpriteLayout.Bounds {
        val index = PetSpriteLayout.assetFrame(kind.name.lowercase(), frame)
        val cacheKey = "${kind.name}_$index"
        return walkFrameBoundsCache.getOrPut(cacheKey) {
            val bitmap = walkFrameArtwork(kind, frame)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            PetSpriteLayout.scan(pixels, bitmap.width, bitmap.height)
        }
    }

    private fun preloadWalkArtwork(kind: PetKind) {
        if (warmedKind == kind) return
        // Decode and measure once, never during an individual walking step.
        // Only retain the active pet's cels to bound bitmap memory.
        walkFrameCache.clear()
        walkFrameBoundsCache.clear()
        val envelope = PetSpriteLayout.Envelope()
        for (frame in 0 until PetSpriteLayout.frameCount(kind.name.lowercase())) envelope.include(walkFrameBounds(kind, frame))
        walkEnvelopeCache[kind] = envelope
        warmedKind = kind
        motionLastAt = SystemClock.uptimeMillis()
    }

    init {
        isFocusable = true
        pet.updateFromClock()
        motionModeUntil = motionLastAt + 1800L
        if (pet.created && pet.hatched && !pet.dead) {
            // Decode before the first animated draw, not during the first step.
            preloadWalkArtwork(pet.kind)
        }
    }

    /**
     * Uses the phone's last known approximate location with Open-Meteo. If
     * location or network access is unavailable, the scene still follows the
     * local clock and uses calm, partly-cloudy fallback scenery.
     */
    fun refreshWeather() {
        weatherStarted = true
        scheduleWeatherRefresh()
        if (!activityResumed || windowVisibility != View.VISIBLE) return
        if (weatherLoading) return
        weatherLoading = true
        Thread {
            val snapshot = runCatching {
                val location = lastKnownLocation()
                if (location == null) fallbackWeather() else fetchWeather(location)
            }.getOrElse { fallbackWeather() }
            weatherHandler.post {
                weather = snapshot
                weatherLoading = false
                invalidate()
            }
        }.start()
    }

    private fun scheduleWeatherRefresh() {
        weatherHandler.removeCallbacks(weatherRefresh)
        if (activityResumed && windowVisibility == View.VISIBLE) {
            weatherHandler.postDelayed(weatherRefresh, WEATHER_REFRESH_MS)
        }
    }

    fun pauseForActivity() {
        activityResumed = false
        weatherHandler.removeCallbacks(weatherRefresh)
        stopAmbientSound()
        stopInteractionSound()
    }

    fun resumeForActivity() {
        activityResumed = true
        motionLastAt = SystemClock.uptimeMillis()
        if (weatherStarted) refreshWeather()
        invalidate()
    }

    private fun lastKnownLocation(): Location? {
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return null
        val candidates = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        return candidates.maxByOrNull { it.time }
    }

    private fun fetchWeather(location: Location): WeatherState {
        val request = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=${location.latitude}&longitude=${location.longitude}" +
                "&current=precipitation,rain,showers,snowfall,cloud_cover,weather_code" +
                "&hourly=precipitation_probability&daily=sunrise,sunset&forecast_days=1&timezone=auto"
        )
        val connection = (request.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mochigotchi-Weather")
        }
        return try {
            if (connection.responseCode !in 200..299) throw IOException("Weather HTTP ${connection.responseCode}")
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val current = root.optJSONObject("current") ?: throw IOException("Weather response missing current data")
            val code = current.optInt("weather_code", 0)
            val precipitation = current.optDouble("precipitation", 0.0)
            val rain = current.optDouble("rain", 0.0) + current.optDouble("showers", 0.0) + current.optDouble("snowfall", 0.0)
            val hourly = root.optJSONObject("hourly")
            val probabilities = hourly?.optJSONArray("precipitation_probability")
            val currentHour = current.optString("time").substringAfter('T').take(2).toIntOrNull()
                ?: LocalTime.now().hour
            val probabilityCount = probabilities?.length() ?: 0
            val firstHour = if (probabilityCount > 0) currentHour.coerceIn(0, probabilityCount - 1) else 0
            var rainSoon = 0f
            if (probabilities != null && probabilityCount > 0) {
                for (index in firstHour until min(probabilityCount, firstHour + 4)) {
                    rainSoon = max(rainSoon, probabilities.optDouble(index, 0.0).toFloat() / 100f)
                }
            }
            val daily = root.optJSONObject("daily")
            val sunrise = daily?.optJSONArray("sunrise")?.optString(0)?.let(::minutesFromIsoTime) ?: 360
            val sunset = daily?.optJSONArray("sunset")?.optString(0)?.let(::minutesFromIsoTime) ?: 1080
            WeatherState(
                available = true,
                cloudCover = (current.optDouble("cloud_cover", 28.0) / 100.0).toFloat().coerceIn(0f, 1f),
                raining = rain > .01 || precipitation > .01 || isWetWeatherCode(code),
                rainSoon = rainSoon,
                weatherCode = code,
                sunriseMinutes = sunrise,
                sunsetMinutes = sunset
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun minutesFromIsoTime(value: String): Int {
        val time = value.substringAfter('T').take(5)
        return time.substringBefore(':').toIntOrNull()?.times(60)?.plus(time.substringAfter(':').toIntOrNull() ?: 0) ?: 360
    }

    private fun isWetWeatherCode(code: Int): Boolean = code in 51..67 || code in 80..82 || code in 95..99

    private fun fallbackWeather(): WeatherState = WeatherState()

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        // Background time belongs to progress simulation, not missed walk poses.
        motionLastAt = SystemClock.uptimeMillis()
        if (visibility != View.VISIBLE) {
            stopAmbientSound()
            stopInteractionSound()
            weatherHandler.removeCallbacks(weatherRefresh)
        }
        else {
            if (weatherStarted && activityResumed) refreshWeather()
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        stopAmbientSound()
        stopInteractionSound()
        weatherHandler.removeCallbacks(weatherRefresh)
        super.onDetachedFromWindow()
    }

    private fun updateAmbientSound() {
        if (!activityResumed) return
        val minutes = LocalTime.now().hour * 60 + LocalTime.now().minute
        val daylight = daylightFactor(minutes, weather)
        val target = when {
            weather.raining -> AmbientMode.RAIN
            daylight < .2f -> AmbientMode.NIGHT
            else -> AmbientMode.DAY
        }
        if (target == ambientMode || windowVisibility != View.VISIBLE) return
        stopAmbientSound()
        val resource = when (target) {
            AmbientMode.DAY -> R.raw.ambient_birds
            AmbientMode.RAIN -> R.raw.ambient_rain
            AmbientMode.NIGHT -> R.raw.ambient_night
        }
        ambientPlayer = runCatching {
            MediaPlayer.create(appContext, resource)?.apply {
                isLooping = true
                setVolume(.18f, .18f)
                start()
            }
        }.getOrNull()
        ambientMode = target
    }

    private fun stopAmbientSound() {
        ambientPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
        ambientPlayer = null
        ambientMode = null
    }

    private fun playInteractionSound(action: Action) {
        // Use Android's already-available click effect. Creating and
        // releasing a ToneGenerator for every tap can block the main thread
        // on slower phones and cause an ANR after repeated care actions.
        playSoundEffect(android.view.SoundEffectConstants.CLICK)
    }

    private fun startSleepAmbient() {
        stopInteractionSound()
        interactionPlayer = runCatching {
            MediaPlayer.create(appContext, R.raw.sleep_ambient_snore)?.apply {
                isLooping = true
                setVolume(.42f, .42f)
                start()
            }
        }.getOrNull()
    }

    private fun stopInteractionSound() {
        interactionPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
                player.release()
            }
        }
        interactionPlayer = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        updateAmbientSound()
        pet.updateFromClock()
        finishLifecycleTransition(now)
        maybePromptLifecycle()
        drawBackground(canvas)
        if (setupMode) {
            drawSetup(canvas, now)
            scheduleNextFrame(now)
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
        if (now - lastSaved > 30_000L) savePet(uploadCloud = false)
        scheduleNextFrame(now)
    }

    private fun scheduleNextFrame(now: Long) {
        if (!activityResumed || windowVisibility != View.VISIBLE) return
        if (setupMode) {
            postInvalidateDelayed(100L)
            return
        }
        val highPriority = activeAction != null ||
            transitionKind != null ||
            menuAnimationStart != 0L ||
            touchReactionUntil > now ||
            (!pet.dead && motionMode == MotionMode.WALK)
        if (highPriority) postInvalidateOnAnimation()
        else postInvalidateDelayed(IDLE_FRAME_DELAY_MS)
    }

    private fun maybePromptLifecycle() {
        if (setupMode || pet.dead || lifecycleDialogShowing || transitionKind != null) return
        when {
            !pet.hatched && pet.hatchReady && !hatchPromptShown -> showHatchPrompt()
            pet.hatched && pet.evolutionReady && !evolutionPromptShown -> showEvolutionPrompt()
            pet.hatched && pet.deathReady && !deathPromptShown -> showDeathPrompt()
        }
    }

    private fun showHatchPrompt() {
        val activity = appContext as? Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        hatchPromptShown = true
        lifecycleDialogShowing = true
        val dialog = AlertDialog.Builder(activity)
            .setTitle("A little hatchling is ready!")
            .setMessage("${pet.name}'s egg is ready to crack open. Shall we welcome the baby now?")
            .setNegativeButton("NOT YET") { _, _ ->
                lifecycleDialogShowing = false
                remindLifecycleLater(TransitionKind.HATCH)
            }
            .setPositiveButton("HATCH NOW") { _, _ ->
                lifecycleDialogShowing = false
                beginLifecycleTransition(TransitionKind.HATCH)
            }
            .create()
        dialog.setOnCancelListener {
            lifecycleDialogShowing = false
            remindLifecycleLater(TransitionKind.HATCH)
        }
        dialog.show()
    }

    private fun showEvolutionPrompt() {
        val activity = appContext as? Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        evolutionPromptShown = true
        lifecycleDialogShowing = true
        val dialog = AlertDialog.Builder(activity)
            .setTitle("${pet.name} is ready to grow!")
            .setMessage("A new chapter is waiting. Would you like ${pet.name} to evolve now?")
            .setNegativeButton("NOT YET") { _, _ ->
                lifecycleDialogShowing = false
                remindLifecycleLater(TransitionKind.EVOLUTION)
            }
            .setPositiveButton("GROW NOW") { _, _ ->
                lifecycleDialogShowing = false
                beginLifecycleTransition(TransitionKind.EVOLUTION)
            }
            .create()
        dialog.setOnCancelListener {
            lifecycleDialogShowing = false
            remindLifecycleLater(TransitionKind.EVOLUTION)
        }
        dialog.show()
    }

    private fun showDeathPrompt() {
        val activity = appContext as? Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        deathPromptShown = true
        lifecycleDialogShowing = true
        val dialog = AlertDialog.Builder(activity)
            .setTitle("A beautiful life")
            .setMessage("${pet.name} has lived a happy life and is growing very old. Would you like to say goodbye, or keep caring for this pet forever?")
            .setNegativeButton("KEEP PLAYING") { _, _ ->
                lifecycleDialogShowing = false
                pet.keepOldPetForever()
                message = "${pet.name} will stay with you forever."
                messageColor = Color.rgb(73, 139, 112)
                messageUntil = SystemClock.uptimeMillis() + 5000L
                savePet()
            }
            .setPositiveButton("LET GO") { _, _ ->
                lifecycleDialogShowing = false
                pet.confirmDeath()
                activeAction = null
                message = "${pet.name} lived a happy life and will always be remembered."
                messageColor = Color.rgb(130, 82, 185)
                messageUntil = SystemClock.uptimeMillis() + 7000L
                savePet()
            }
            .create()
        dialog.setOnCancelListener {
            lifecycleDialogShowing = false
            remindLifecycleLater(TransitionKind.DEATH)
        }
        dialog.show()
    }

    private fun remindLifecycleLater(kind: TransitionKind) {
        postDelayed({
            when (kind) {
                TransitionKind.HATCH -> hatchPromptShown = false
                TransitionKind.EVOLUTION -> evolutionPromptShown = false
                TransitionKind.DEATH -> deathPromptShown = false
            }
            invalidate()
        }, LIFECYCLE_REPROMPT_MS)
    }

    private fun beginLifecycleTransition(kind: TransitionKind) {
        if (transitionKind != null) return
        transitionKind = kind
        transitionStartedAt = SystemClock.uptimeMillis()
        transitionUntil = transitionStartedAt + if (kind == TransitionKind.HATCH) 2800L else 1900L
        activeAction = null
        message = when (kind) {
            TransitionKind.HATCH -> "The shell is cracking open…"
            TransitionKind.EVOLUTION -> "${pet.name} is growing…"
            TransitionKind.DEATH -> message
        }
        messageColor = Color.rgb(130, 82, 185)
        messageUntil = transitionUntil + 3500L
        invalidate()
    }

    private fun finishLifecycleTransition(now: Long) {
        val kind = transitionKind ?: return
        if (now < transitionUntil) return
        when (kind) {
            TransitionKind.HATCH -> {
                pet.confirmHatch()
                message = "${pet.name} has hatched! Hello, little one!"
                activeAction = Action.PLAY
                actionStartedAt = now
                actionUntil = now + 1600L
            }
            TransitionKind.EVOLUTION -> {
                pet.confirmEvolution()
                message = "Amazing! ${pet.name} grew into a new stage!"
                activeAction = Action.PLAY
                actionStartedAt = now
                actionUntil = now + 1800L
            }
            TransitionKind.DEATH -> return
        }
        transitionKind = null
        savePet()
    }

    private fun transitionProgress(now: Long): Float {
        if (transitionKind == null || transitionUntil <= transitionStartedAt) return 0f
        return ((now - transitionStartedAt).toFloat() / (transitionUntil - transitionStartedAt).toFloat()).coerceIn(0f, 1f)
    }

    private fun smoothTransition(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun growthTransitionScale(now: Long): Float {
        if (transitionKind != TransitionKind.EVOLUTION) return 1f
        val current = pet.growthStage.size
        val next = when (pet.growthStage) {
            PetGrowth.Stage.BABY -> PetGrowth.Stage.YOUNG.size
            PetGrowth.Stage.YOUNG -> PetGrowth.Stage.ADULT.size
            else -> current
        }
        if (current <= 0.0) return 1f
        return (1f + ((next / current - 1.0) * smoothTransition(transitionProgress(now)))).toFloat()
    }

    private fun drawEmergingBaby(canvas: Canvas, now: Long, ground: Float, eggScale: Float) {
        val progress = transitionProgress(now)
        if (progress <= .2f) return
        val reveal = smoothTransition(((progress - .2f) / .8f).coerceIn(0f, 1f))
        val bitmap = petArtwork(pet.kind)
        val babyWidth = dp(148f) * eggScale * (.38f + .62f * reveal)
        val babyHeight = babyWidth * bitmap.height / bitmap.width.toFloat()
        val lift = dp(48f) * reveal
        val rect = RectF(
            width / 2f - babyWidth / 2f,
            ground - babyHeight * .48f - lift,
            width / 2f + babyWidth / 2f,
            ground + babyHeight * .52f - lift
        )
        paint.alpha = (255f * reveal).roundToInt().coerceIn(0, 255)
        paint.colorFilter = null
        paint.isFilterBitmap = true
        canvas.drawBitmap(bitmap, null, rect, paint)
        paint.alpha = 255
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
        textPaint.textSize = min(dp(22f), dp(22f) * titleWidth / textPaint.measureText("MOCHIGOTCHI"))
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("MOCHIGOTCHI", titleCenter, dp(38f), textPaint)
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
        canvas.drawText("Choose your name, egg, and pet", width / 2f, dp(66f), textPaint)

        val cx = width / 2f
        drawEgg(canvas, cx, dp(275f), setupKind, (now - animationStart) / 1000.0, 0.0, dp(1f))

        val nameRect = RectF(dp(28f), dp(305f), width - dp(28f), dp(383f))
        paint.color = Color.argb(70, 54, 30, 92)
        canvas.drawRoundRect(nameRect, dp(18f), dp(18f), paint)
        paint.color = Color.argb(75, 255, 255, 255)
        canvas.drawRect(nameRect.left + dp(16f), dp(343f), nameRect.right - dp(16f), dp(344f), paint)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = PaintTypeface.rounded()
        textPaint.textSize = dp(10f)
        textPaint.color = Color.argb(210, 255, 255, 255)
        canvas.drawText("YOUR NAME  •  TAP TO EDIT", nameRect.left + dp(18f), nameRect.top + dp(16f), textPaint)
        textPaint.textSize = dp(16f)
        textPaint.typeface = PaintTypeface.bold()
        textPaint.color = Color.WHITE
        canvas.drawText(setupPlayerName, nameRect.left + dp(18f), nameRect.top + dp(33f), textPaint)
        textPaint.textSize = dp(10f)
        textPaint.typeface = PaintTypeface.rounded()
        textPaint.color = Color.argb(210, 255, 255, 255)
        canvas.drawText("PET NAME  •  TAP TO EDIT", nameRect.left + dp(18f), dp(360f), textPaint)
        textPaint.textSize = dp(16f)
        textPaint.typeface = PaintTypeface.bold()
        textPaint.color = Color.WHITE
        canvas.drawText(setupName, nameRect.left + dp(18f), dp(377f), textPaint)

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
        val welcome = "WELCOME ${setupName.uppercase()}"
        textPaint.textSize = min(dp(17f), dp(17f) * (hatch.width() - dp(24f)) / textPaint.measureText(welcome))
        canvas.drawText(welcome, hatch.centerX(), hatch.centerY() + dp(6f), textPaint)
    }

    private fun drawEgg(
        canvas: Canvas,
        cx: Float,
        ground: Float,
        kind: PetKind,
        seconds: Double,
        progress: Double,
        scale: Float,
        reactionAction: Action? = null,
        reactionProgress: Float = 0f,
        touchProgress: Float = 0f
    ) {
        canvas.save()
        canvas.translate(cx, ground)
        canvas.scale(scale, scale)
        paint.colorFilter = null
        paint.style = Paint.Style.FILL
        val dragon = kind == PetKind.DRAGON
        paint.color = Color.argb(45, 67, 57, 82)
        canvas.drawOval(RectF(-98f, -4f, 98f, 22f), paint)
        paint.color = if (dragon) Color.rgb(137, 92, 57) else Color.rgb(222, 182, 101)
        canvas.drawOval(RectF(-94f, -19f, 94f, 15f), paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        for (strand in 0..25) {
            paint.color = if (dragon) {
                if (strand % 2 == 0) Color.rgb(173, 123, 72) else Color.rgb(107, 72, 48)
            } else {
                if (strand % 2 == 0) Color.rgb(250, 217, 144) else Color.rgb(195, 151, 76)
            }
            canvas.drawPath(eggBackStrands[strand], paint)
        }
        canvas.save()
        val touchWave = sin(touchProgress * Math.PI.toFloat())
        val sleepEase = ((1f - cos(reactionProgress * Math.PI.toFloat())) * .5f)
        // Eggs stay upright while sleeping. Once the initial settle-in is
        // complete, a slow whole-egg scale pulse reads as gentle breathing
        // without making the egg look like it has fallen over.
        val sleepBreath = if (reactionAction == Action.SLEEP) {
            sleepEase * sin(seconds * (Math.PI * 2.0 / 2.8)).toFloat()
        } else 0f
        val sleepScaleX = 1f + sleepBreath * .032f
        val sleepScaleY = 1f - sleepBreath * .045f
        val actionLift = when (reactionAction) {
            Action.FEED -> -abs(sin(reactionProgress * Math.PI.toFloat() * 2f)) * 7f
            Action.PLAY -> -abs(sin(reactionProgress * Math.PI.toFloat() * 3f)) * 12f
            Action.BATH -> -abs(sin(reactionProgress * Math.PI.toFloat() * 2f)) * 5f
            Action.SLEEP -> 0f
            null -> 0f
        }
        val touchLift = -abs(touchWave) * 8f
        val actionTilt = when (reactionAction) {
            Action.FEED -> sin(reactionProgress * Math.PI.toFloat() * 4f) * 4f
            Action.PLAY -> sin(reactionProgress * Math.PI.toFloat() * 6f) * 7f
            Action.BATH -> sin(reactionProgress * Math.PI.toFloat() * 8f) * 3f
            Action.SLEEP -> 0f
            null -> 0f
        }
        val touchTilt = sin(touchProgress * Math.PI.toFloat() * 5f) * 4f
        canvas.translate(
            sin(touchProgress * Math.PI.toFloat() * 3f) * 2f,
            PetGrowth.eggLift(seconds).toFloat() + actionLift + touchLift
        )
        if (reactionAction == Action.SLEEP) canvas.scale(sleepScaleX, sleepScaleY, 0f, 0f)
        canvas.rotate(
            PetGrowth.eggAngle(seconds, progress).toFloat() + actionTilt + touchTilt,
            0f,
            0f
        )
        val shell = when (kind) {
            PetKind.CAT -> Color.rgb(221, 199, 240)
            PetKind.DOG -> Color.rgb(255, 220, 163)
            PetKind.BUNNY -> Color.rgb(202, 238, 211)
            PetKind.HAMSTER -> Color.rgb(255, 227, 174)
            PetKind.DRAGON -> Color.rgb(171, 233, 234)
        }
        val spot = when (kind) {
            PetKind.CAT -> Color.rgb(173, 135, 207)
            PetKind.DOG -> Color.rgb(217, 156, 94)
            PetKind.BUNNY -> Color.rgb(141, 195, 158)
            PetKind.HAMSTER -> Color.rgb(226, 172, 91)
            PetKind.DRAGON -> Color.rgb(86, 177, 185)
        }
        paint.style = Paint.Style.FILL
        paint.shader = eggGradients.getOrPut(kind) {
            RadialGradient(-20f, -110f, 142f, Color.rgb(255, 248, 225), shell, Shader.TileMode.CLAMP)
        }
        canvas.drawOval(RectF(-48f, -146f, 48f, 2f), paint)
        paint.shader = null
        paint.color = spot
        canvas.drawOval(RectF(5f, -127f, 21f, -111f), paint)
        canvas.drawOval(RectF(-34f, -83f, -15f, -62f), paint)
        canvas.drawOval(RectF(15f, -52f, 34f, -33f), paint)
        paint.color = Color.argb(110, 255, 255, 255)
        canvas.drawOval(RectF(-31f, -119f, -17f, -82f), paint)
        if (progress > .85) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.8f
            paint.color = spot
            canvas.drawPath(eggCrack, paint)
        }
        canvas.restore()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (dragon) 3.5f else 2f
        for (strand in 0..20) {
            paint.color = if (dragon) {
                if (strand % 2 == 0) Color.rgb(178, 128, 76) else Color.rgb(114, 79, 50)
            } else {
                if (strand % 2 == 0) Color.rgb(247, 213, 127) else Color.rgb(208, 164, 88)
            }
            canvas.drawPath(eggFrontStrands[strand], paint)
        }
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    private fun drawPlayground(canvas: Canvas, now: Long) {
        val scene = playgroundScene()
        paint.color = Color.argb(35, 56, 44, 82)
        canvas.drawRoundRect(RectF(scene.left, scene.top + dp(5f), scene.right, scene.bottom + dp(5f)), dp(26f), dp(26f), paint)
        paint.color = Color.WHITE
        canvas.drawRoundRect(scene, dp(26f), dp(26f), paint)

        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(scene, dp(26f), dp(26f), Path.Direction.CW) })
        val weatherNow = weather
        val daylight = daylightFactor(LocalTime.now().hour * 60 + LocalTime.now().minute, weatherNow)
        paint.shader = LinearGradient(
            0f, scene.top, 0f, scene.bottom,
            blendColor(Color.rgb(28, 42, 86), Color.rgb(190, 229, 255), daylight),
            blendColor(Color.rgb(72, 64, 116), Color.rgb(250, 239, 249), daylight), Shader.TileMode.CLAMP
        )
        canvas.drawRect(scene, paint)
        paint.shader = null

        drawCelestial(canvas, scene, weatherNow)

        val cloudiness = max(weatherNow.cloudCover, weatherNow.rainSoon * .85f)
        val cloudCount = (cloudiness * 4f).roundToInt().coerceIn(0, 4)
        val cloudColor = if (daylight < .2f) Color.argb(110, 122, 130, 164)
        else if (weatherNow.raining || weatherNow.rainSoon > .5f) Color.argb(205, 148, 164, 185)
        else Color.argb(195, 255, 255, 255)
        paint.color = cloudColor
        for (index in 0 until cloudCount) {
            val cycle = ((now / (42_000f + index * 4_000f) + index * .29f) % 1f + 1f) % 1f
            val x = scene.left - dp(88f) + cycle * (scene.width() + dp(176f))
            val y = scene.top + dp(61f + index * 34f)
            drawCloud(canvas, x, y, .28f + (index % 2) * .07f)
        }

        val distantMountains = Path().apply {
            moveTo(scene.left, scene.bottom - dp(78f))
            lineTo(scene.left + dp(57f), scene.bottom - dp(132f))
            lineTo(scene.left + dp(104f), scene.bottom - dp(92f))
            lineTo(scene.left + dp(163f), scene.bottom - dp(146f))
            lineTo(scene.left + dp(231f), scene.bottom - dp(88f))
            lineTo(scene.right - dp(104f), scene.bottom - dp(139f))
            lineTo(scene.right - dp(44f), scene.bottom - dp(91f))
            lineTo(scene.right, scene.bottom - dp(124f))
            lineTo(scene.right, scene.bottom)
            lineTo(scene.left, scene.bottom)
            close()
        }
        paint.color = if (daylight < .2f) Color.rgb(67, 78, 112) else Color.rgb(174, 213, 216)
        canvas.drawPath(distantMountains, paint)

        // Trees sit in the distant layer, behind the hills and always behind
        // the pet, so a hatchling never appears to walk over them.
        drawTrees(canvas, scene, daylight)
        if (daylight > .2f && !weatherNow.raining) drawBirds(canvas, scene, now)

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

        drawGroundDetails(canvas, scene, daylight, now)
        if (daylight < .2f && !weatherNow.raining) drawNightCreatures(canvas, scene, now)
        if (weatherNow.raining) drawRain(canvas, scene, now, daylight)
        canvas.restore()
    }

    private fun playgroundScene(): RectF {
        val top = dp(77f)
        val bottom = statsTop()
        return RectF(dp(18f), top, width - dp(18f), bottom - dp(10f))
    }

    private fun drawCloud(canvas: Canvas, x: Float, y: Float, scale: Float) {
        canvas.drawCircle(x, y, dp(22f) * scale, paint)
        canvas.drawCircle(x + dp(22f) * scale, y - dp(8f) * scale, dp(28f) * scale, paint)
        canvas.drawCircle(x + dp(52f) * scale, y, dp(20f) * scale, paint)
        canvas.drawRoundRect(RectF(x - dp(5f) * scale, y, x + dp(60f) * scale, y + dp(18f) * scale), dp(10f), dp(10f), paint)
    }

    private fun drawCelestial(canvas: Canvas, scene: RectF, state: WeatherState) {
        val now = LocalTime.now().hour * 60 + LocalTime.now().minute + LocalTime.now().second / 60f
        val sunrise = state.sunriseMinutes.toFloat().coerceIn(0f, 1439f)
        val sunset = state.sunsetMinutes.toFloat().coerceIn(sunrise + 60f, 1439f)
        val day = now in sunrise..sunset
        val fraction = if (day) ((now - sunrise) / (sunset - sunrise)).coerceIn(0f, 1f)
        else ((if (now >= sunset) now - sunset else now + 1440f - sunset) /
            (1440f - sunset + sunrise)).coerceIn(0f, 1f)
        val x = if (day) scene.left + scene.width() * (.12f + .76f * fraction)
        else scene.right - scene.width() * (.12f + .76f * fraction)
        val y = scene.top + scene.height() * (.68f - sin(fraction * Math.PI.toFloat()) * .52f)

        if (day) {
            paint.color = Color.argb(42, 255, 224, 111)
            canvas.drawCircle(x, y, dp(27f), paint)
            paint.color = Color.rgb(255, 220, 109)
            canvas.drawCircle(x, y, dp(15f), paint)
        } else {
            paint.color = Color.argb(230, 255, 249, 207)
            canvas.drawCircle(x, y, dp(14f), paint)
            paint.color = Color.rgb(45, 56, 101)
            canvas.drawCircle(x + dp(6f), y - dp(4f), dp(13f), paint)
            paint.color = Color.argb(150, 255, 255, 255)
            val stars = arrayOf(floatArrayOf(.18f, .20f), floatArrayOf(.76f, .18f), floatArrayOf(.62f, .36f), floatArrayOf(.34f, .42f))
            for (star in stars) canvas.drawCircle(scene.left + scene.width() * star[0], scene.top + scene.height() * star[1], dp(1.5f), paint)
        }
    }

    private fun drawTrees(canvas: Canvas, scene: RectF, daylight: Float) {
        val treeColor = if (daylight < .2f) Color.rgb(51, 77, 77) else Color.rgb(101, 168, 127)
        paint.color = treeColor
        val positions = floatArrayOf(.12f, .31f, .70f, .88f)
        for (index in positions.indices) {
            val x = scene.left + scene.width() * positions[index]
            val base = scene.bottom - dp(31f + (index % 2) * 8f)
            val size = dp(29f + (index % 3) * 6f)
            canvas.drawRect(RectF(x - dp(2f), base - size * .55f, x + dp(2f), base), paint)
            canvas.drawCircle(x, base - size * .8f, size * .55f, paint)
            canvas.drawCircle(x - size * .34f, base - size * .58f, size * .42f, paint)
            canvas.drawCircle(x + size * .34f, base - size * .58f, size * .42f, paint)
        }
    }

    private fun drawGroundDetails(canvas: Canvas, scene: RectF, daylight: Float, now: Long) {
        // Build one continuous grass bank across the whole lower strip. The
        // uneven top edge keeps it organic without making it look like a row
        // of detached tufts.
        val grassTop = scene.bottom - dp(36f)
        val grassPatch = Path().apply {
            moveTo(scene.left, grassTop + dp(3f))
            cubicTo(
                scene.left + scene.width() * .18f,
                grassTop - dp(1f),
                scene.left + scene.width() * .34f,
                grassTop + dp(3f),
                scene.left + scene.width() * .50f,
                grassTop
            )
            cubicTo(
                scene.left + scene.width() * .67f,
                grassTop - dp(3f),
                scene.left + scene.width() * .84f,
                grassTop + dp(3f),
                scene.right,
                grassTop + dp(1f)
            )
            lineTo(scene.right, scene.bottom)
            lineTo(scene.left, scene.bottom)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = if (daylight < .2f) Color.rgb(55, 99, 82) else Color.rgb(136, 198, 151)
        canvas.drawPath(grassPatch, paint)

        // Use several tightly packed rows of tapered, pointed blades. Each
        // blade stays rooted and only its tip moves, creating a thick patch
        // instead of a few rounded line marks.
        paint.style = Paint.Style.FILL
        val bladePath = Path()
        val baseCount = (scene.width() / dp(3.1f)).roundToInt().coerceIn(70, 150)
        for (row in 0..2) {
            val rowCount = baseCount + row * 3
            for (index in 0 until rowCount) {
                val normalizedX = (index + .5f) / rowCount
                val baseX = scene.left + scene.width() * normalizedX + dp((row - 1) * .8f)
                // All rows share the true bottom edge, so there is no bare
                // strip beneath the blades. Layering comes from their varied
                // heights and dense spacing instead of raised root lines.
                val baseY = scene.bottom - dp(1f)
                val height = dp(17f + row * 2f + (index * 5 % 6) * 1.6f)
                val wind = sin(now / (760f + (index % 4) * 85f) + index * .67f + row * .9f).toFloat()
                val sway = wind * dp(2.4f + row * .35f)
                val halfWidth = dp(1.25f + row * .12f)
                val tipX = baseX + sway
                val tipY = baseY - height
                paint.color = when {
                    daylight < .2f -> Color.rgb(43 + row * 5, 83 + row * 8, 68 + row * 6)
                    row == 0 -> Color.rgb(79, 155, 101)
                    row == 1 -> Color.rgb(64, 143, 90)
                    else -> Color.rgb(53, 130, 80)
                }
                bladePath.reset()
                bladePath.moveTo(baseX - halfWidth, baseY)
                bladePath.cubicTo(
                    baseX - halfWidth * .7f,
                    baseY - height * .38f,
                    tipX - halfWidth * .24f,
                    tipY + height * .20f,
                    tipX,
                    tipY
                )
                bladePath.cubicTo(
                    tipX + halfWidth * .24f,
                    tipY + height * .20f,
                    baseX + halfWidth * .7f,
                    baseY - height * .38f,
                    baseX + halfWidth,
                    baseY
                )
                bladePath.close()
                canvas.drawPath(bladePath, paint)
            }
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawBirds(canvas: Canvas, scene: RectF, now: Long) {
        // Keep the original simple distant-bird mark, but give it a slightly
        // quicker, continuously sampled flight path and rounded strokes.
        paint.color = Color.argb(185, 71, 80, 94)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.6f)
        paint.strokeCap = Paint.Cap.ROUND
        for (index in 0 until 3) {
            val travelPhase = now / (1500f + index * 180f) + index * 1.7f
            val flapPhase = now / 270f + index * 1.4f
            val x = scene.left + scene.width() * (.18f + index * .29f) + sin(travelPhase) * dp(31f)
            val y = scene.top + dp(133f + index * 22f) + cos(travelPhase * .72f) * dp(12f)
            val wing = sin(flapPhase).toFloat() * dp(5f)
            canvas.drawArc(RectF(x - dp(9f), y - wing, x, y + dp(5f)), 205f, 135f, false, paint)
            canvas.drawArc(RectF(x, y + dp(5f), x + dp(9f), y + dp(10f) + wing), 205f, 135f, false, paint)
        }
        paint.strokeCap = Paint.Cap.BUTT
        paint.style = Paint.Style.FILL
    }

    private fun drawNightCreatures(canvas: Canvas, scene: RectF, now: Long) {
        val frogBody = Color.rgb(72, 123, 87)
        val frogDark = Color.rgb(49, 91, 67)
        val frogLight = Color.rgb(149, 190, 111)
        val positions = floatArrayOf(.14f, .86f)
        paint.style = Paint.Style.FILL
        for (index in positions.indices) {
            val cycleMs = 1750L + index * 190L
            val clock = now + index * 470L
            val cycle = clock / cycleMs
            val cyclePosition = (clock % cycleMs).toFloat()
            val direction = if (cycle % 2L == 0L) 1f else -1f
            val center = scene.left + scene.width() * positions[index]
            val span = dp(31f)
            val startX = center - direction * span
            val endX = center + direction * span
            val jumpStart = 390f
            val jumpDuration = 620f
            val rawProgress = ((cyclePosition - jumpStart) / jumpDuration).coerceIn(0f, 1f)
            val moving = cyclePosition >= jumpStart && cyclePosition <= jumpStart + jumpDuration
            val easedProgress = rawProgress * rawProgress * (3f - 2f * rawProgress)
            val rawX = if (moving) startX + (endX - startX) * easedProgress else if (cyclePosition < jumpStart) startX else endX
            // This is scenery, so the pet/egg is deliberately drawn over it
            // afterward. Do not skip or clamp around the pet: natural
            // occlusion is what prevents clipping and pop-in.
            val x = rawX.coerceIn(scene.left + dp(16f), scene.right - dp(16f))
            val hop = if (moving) sin(rawProgress * Math.PI).toFloat() else 0f
            val ground = scene.bottom - dp(27f)
            val y = ground - hop * dp(22f)
            paint.color = Color.argb((62f - hop * 24f).roundToInt(), 48, 73, 61)
            canvas.drawOval(RectF(x - dp(11f) - hop * dp(3f), ground + dp(1f), x + dp(11f) + hop * dp(3f), ground + dp(5f)), paint)

            canvas.save()
            canvas.translate(x, y)
            val squash = 1f + (1f - hop) * .08f
            canvas.scale(squash, 1f - (1f - hop) * .05f, 0f, dp(3f))
            paint.color = frogDark
            canvas.drawOval(RectF(dp(-13f), dp(-3f), dp(-5f), dp(7f)), paint)
            canvas.drawOval(RectF(dp(5f), dp(-3f), dp(13f), dp(7f)), paint)
            paint.color = frogBody
            canvas.drawOval(RectF(dp(-11f), dp(-13f), dp(11f), dp(5f)), paint)
            canvas.drawOval(RectF(dp(-10f), dp(-20f), dp(10f), dp(-5f)), paint)
            paint.color = frogLight
            canvas.drawCircle(dp(-6f), dp(-19f), dp(4f), paint)
            canvas.drawCircle(dp(6f), dp(-19f), dp(4f), paint)
            paint.color = Color.rgb(31, 47, 36)
            canvas.drawCircle(dp(-6f), dp(-19f), dp(1.5f), paint)
            canvas.drawCircle(dp(6f), dp(-19f), dp(1.5f), paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dp(1.2f)
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = frogDark
            val mouth = Path().apply {
                moveTo(dp(-5f), dp(-11f))
                quadTo(0f, dp(-8f), dp(5f), dp(-11f))
            }
            canvas.drawPath(mouth, paint)
            paint.style = Paint.Style.FILL
            paint.strokeCap = Paint.Cap.BUTT
            canvas.restore()
        }
    }

    private fun drawRain(canvas: Canvas, scene: RectF, now: Long, daylight: Float) {
        paint.color = if (daylight < .2f) Color.argb(120, 173, 199, 238) else Color.argb(145, 92, 157, 202)
        paint.strokeWidth = dp(1.2f)
        paint.style = Paint.Style.STROKE
        for (index in 0 until 42) {
            val x = scene.left + ((index * 43 + (now / 7L).toInt()) % scene.width().toInt()).toFloat()
            val y = scene.top + ((index * 67 + (now / 5L).toInt()) % scene.height().toInt()).toFloat()
            canvas.drawLine(x.toFloat(), y.toFloat(), x - dp(4f), y + dp(12f), paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun daylightFactor(minutes: Int, state: WeatherState): Float {
        val dawn = state.sunriseMinutes
        val dusk = state.sunsetMinutes
        return when {
            minutes < dawn - 35 -> 0f
            minutes < dawn -> (minutes - (dawn - 35)) / 35f
            minutes <= dusk -> 1f
            minutes < dusk + 35 -> 1f - (minutes - dusk) / 35f
            else -> 0f
        }.coerceIn(0f, 1f)
    }

    private fun blendColor(night: Int, day: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(night) + (Color.red(day) - Color.red(night)) * t).roundToInt(),
            (Color.green(night) + (Color.green(day) - Color.green(night)) * t).roundToInt(),
            (Color.blue(night) + (Color.blue(day) - Color.blue(night)) * t).roundToInt()
        )
    }

    private fun drawPet(canvas: Canvas, now: Long) {
        if (pet.dead) {
            drawMemorial(canvas)
            return
        }
        if (!pet.hatched) {
            val ground = statsTop() - dp(64f)
            val eggScale = min(dp(1f), min((width - dp(64f)) / 240f, ((ground - dp(125f)) / 166f).coerceAtLeast(0f)))
            val actionProgress = if (activeAction == Action.SLEEP) sleepPoseProgress(now) else interactionProgress(now)
            val touchProgress = touchProgress(now)
            drawEgg(
                canvas,
                width / 2f,
                ground,
                pet.kind,
                (now - animationStart) / 1000.0,
                pet.hatchProgress.toDouble(),
                eggScale,
                activeAction,
                actionProgress,
                touchProgress
            )
            if (transitionKind == TransitionKind.HATCH) drawEmergingBaby(canvas, now, ground, eggScale)
            motionLastAt = now
            motionX = .5f
            petCenterX = width / 2f
            petGroundY = ground
            drawPetName(canvas, width / 2f, ground + dp(12f))
            return
        }
        preloadWalkArtwork(pet.kind)
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

        val requestedWidth = min(width - dp(42f), dp(296f))
        val groundY = bottom - dp(42f)
        val walking = motionMode == MotionMode.WALK && activeAction == null
        val frameCount = PetSpriteLayout.frameCount(pet.kind.name.lowercase())
        val frame = if (walking || activeAction == Action.PLAY) (((now - animationStart) / WALK_FRAME_DURATION_MS) % frameCount).toInt() else 0
        // Bound the complete repaired silhouettes in both facing directions.
        // Reserve room for travel and the existing play bounce above the pet.
        val layout = PetSpriteLayout.fit(
            walkEnvelopeCache.getValue(pet.kind), requestedWidth.toDouble(),
            sceneLeft.toDouble(), sceneRight.toDouble(), dp(134f).toDouble(),
            groundY.toDouble(), dp(6f).toDouble(), dp(68f).toDouble()
        )
        // Fit the adult first, then scale every earlier stage relative to it.
        // Otherwise scenery fitting can make baby and adult the same size.
        val growthScale = growthTransitionScale(now)
        val artWidth = (layout.artWidth * pet.growthStage.size * growthScale).toFloat()
        val reach = walkEnvelopeCache.getValue(pet.kind).reach * artWidth
        val leftCenter = sceneLeft + dp(6f) + reach
        val rightCenter = sceneRight - dp(6f) - reach
        val centerX = (leftCenter + motionX * (rightCenter - leftCenter)).toFloat()
        petCenterX = centerX
        petGroundY = groundY
        val bitmap = walkFrameArtwork(pet.kind, frame)
        val artScale = artWidth / bitmap.width
        // Keep the feet planted while resting.  A whole-body vertical bob reads
        // as hovering, especially against the simple ground in this scene.
        val idleBob = 0f
        val reactionProgress = interactionProgress(now)
        val sleepProgress = sleepPoseProgress(now)
        val touchProgress = touchProgress(now)
        val reactionWave = sin((if (activeAction == Action.SLEEP) sleepProgress else reactionProgress) * Math.PI.toFloat())
        val sleepEase = if (activeAction == Action.SLEEP) {
            (1f - cos(sleepProgress * Math.PI.toFloat())) * .5f
        } else 0f
        val reactionBob = when (activeAction) {
            Action.FEED -> -abs(sin(reactionProgress * Math.PI.toFloat() * 2f)) * dp(3f)
            Action.PLAY -> -abs(sin(reactionProgress * Math.PI.toFloat() * 3f)) * dp(9f)
            // Lower the whole body into the ground rather than tipping it up
            // like a standing pose. The feet remain anchored while the body
            // settles into a clear resting position.
            Action.SLEEP -> dp(14f) * sleepEase - reactionWave * dp(1.5f)
            else -> 0f
        }
        val reactionShiftX = if (activeAction == Action.BATH) sin(reactionProgress * Math.PI.toFloat() * 8f) * dp(2f) else 0f
        val touchShiftX = sin(touchProgress * Math.PI.toFloat() * 5f) * dp(2f)
        val reactionScale = if (activeAction == Action.PLAY) 1f + reactionWave * .025f else 1f + sin(touchProgress * Math.PI.toFloat()) * .012f
        val reactionScaleY = reactionScale * (1f - sleepEase * .12f)
        val touchBob = -abs(sin(touchProgress * Math.PI.toFloat())) * dp(4f)
        val rootY = groundY + idleBob + reactionBob + touchBob

        // A tight, dark contact shadow anchors every paw to the grass.
        paint.color = Color.argb(58, 67, 57, 82)
        canvas.drawOval(
            RectF(centerX - artWidth * .25f, groundY - dp(3f), centerX + artWidth * .25f, groundY + dp(7f)),
            paint
        )
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.color = Color.WHITE
        val filterKey = "${pet.kind.name}_${pet.stage}"
        paint.colorFilter = growthColorFilters.getOrPut(filterKey) {
            android.graphics.ColorMatrixColorFilter(PetGrowth.colorMatrix(pet.kind.name.lowercase(), pet.growthStage))
        }
        // 10 fps gives the drawn cels time to read as a deliberate gait rather
        // than a frantic, glitchy run.
        val visibleBottom = walkFrameBounds(pet.kind, frame).bottom
        val artTop = rootY - visibleBottom * artScale
        val artBottom = artTop + bitmap.height * artScale
        val artRect = RectF(centerX - artWidth / 2f, artTop, centerX + artWidth / 2f, artBottom)
        canvas.save()
        canvas.translate(reactionShiftX + touchShiftX, 0f)
        if (reactionScale != 1f || reactionScaleY != 1f) canvas.scale(reactionScale, reactionScaleY, centerX, rootY)
        // Tilt the head toward the ground in either facing direction. A
        // fixed tilt raised the head when the dragon was facing right, making
        // the rear look like it was standing the pet up while asleep.
        if (sleepEase > 0f) {
            val sleepTilt = if (motionDirection > 0f) 10f else -10f
            canvas.rotate(sleepTilt * sleepEase, centerX, rootY)
        }
        // The artwork faces left by default.  Mirror it only while travelling
        // right; the old condition reversed that relationship.
        if (motionDirection > 0f) canvas.scale(-1f, 1f, centerX, rootY)
        canvas.drawBitmap(bitmap, null, artRect, paint)
        if (activeAction == Action.SLEEP) drawSleepEyes(canvas, artRect)
        canvas.restore()
        paint.colorFilter = null

        drawPetName(canvas, centerX, groundY)
    }

    private fun drawSleepEyes(canvas: Canvas, artRect: RectF) {
        val eyePosition = when (pet.kind) {
            PetKind.CAT -> .285f to .49f
            PetKind.DOG -> .31f to .455f
            PetKind.BUNNY -> .34f to .455f
            PetKind.HAMSTER -> .31f to .445f
            PetKind.DRAGON -> .24f to .505f
        }
        val eyeX = artRect.left + artRect.width() * eyePosition.first
        val eyeY = artRect.top + artRect.height() * eyePosition.second
        val eyeWidth = artRect.width() * when (pet.kind) {
            PetKind.DRAGON -> .19f
            PetKind.CAT -> .17f
            else -> .16f
        }
        val eyeHeight = artRect.height() * when (pet.kind) {
            PetKind.DRAGON -> .19f
            else -> .17f
        }
        paint.colorFilter = null
        // First cover the open eye with a small face-coloured almond. A line
        // alone leaves the original iris visible and reads like an eyebrow.
        val faceColor = when (pet.kind) {
            PetKind.CAT -> Color.rgb(181, 148, 229)
            PetKind.DOG -> Color.rgb(226, 151, 78)
            PetKind.BUNNY -> Color.rgb(159, 221, 181)
            PetKind.HAMSTER -> Color.rgb(235, 174, 75)
            PetKind.DRAGON -> Color.rgb(23, 194, 201)
        }
        val left = eyeX - eyeWidth / 2f
        val right = eyeX + eyeWidth / 2f
        val top = eyeY - eyeHeight / 2f
        val bottom = eyeY + eyeHeight / 2f
        val cover = Path().apply {
            moveTo(left, eyeY)
            quadTo(eyeX, top, right, eyeY)
            quadTo(eyeX, bottom, left, eyeY)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.color = faceColor
        canvas.drawPath(cover, paint)

        // Add a single relaxed, downward-curved eyelid inside the full cover.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(2.8f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.rgb(73, 48, 74)
        val lid = Path().apply {
            val lidY = eyeY + eyeHeight * .03f
            moveTo(left + eyeWidth * .13f, lidY)
            cubicTo(
                eyeX - eyeWidth * .25f,
                eyeY + eyeHeight * .42f,
                eyeX + eyeWidth * .25f,
                eyeY + eyeHeight * .42f,
                right - eyeWidth * .13f,
                lidY
            )
        }
        canvas.drawPath(lid, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawPetName(canvas: Canvas, centerX: Float, groundY: Float) {
        val bottom = statsTop() - dp(10f)
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

    private fun interactionProgress(now: Long): Float {
        return if (activeAction != null && actionUntil > actionStartedAt) {
            ((now - actionStartedAt).toFloat() / (actionUntil - actionStartedAt).toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    private fun sleepPoseProgress(now: Long): Float {
        return if (activeAction == Action.SLEEP && actionUntil > actionStartedAt) {
            ((now - actionStartedAt).toFloat() / 1200f).coerceIn(0f, 1f)
        } else 0f
    }

    private fun touchProgress(now: Long): Float {
        return if (touchReactionUntil > touchReactionStartedAt && now < touchReactionUntil) {
            ((now - touchReactionStartedAt).toFloat() / (touchReactionUntil - touchReactionStartedAt).toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

    private fun petHitRect(): RectF {
        val halfWidth = if (pet.hatched) dp(132f) else dp(86f)
        val top = if (pet.hatched) petGroundY - dp(190f) else petGroundY - dp(165f)
        return RectF(petCenterX - halfWidth, top, petCenterX + halfWidth, petGroundY + dp(12f))
    }

    private fun touchPet() {
        if (pet.dead) return
        val now = SystemClock.uptimeMillis()
        touchReactionStartedAt = now
        touchReactionUntil = now + 900L
        message = if (pet.hatched) "That tickles!" else "A gentle touch makes the egg wobble."
        messageColor = if (pet.hatched) pet.kind.dark else Color.rgb(66, 92, 126)
        messageUntil = now + 2200L
        playInteractionSound(Action.PLAY)
        invalidate()
    }

    private fun drawMemorial(canvas: Canvas) {
        val rect = RectF(dp(36f), dp(139f), width - dp(36f), statsTop() - dp(38f))
        paint.color = Color.argb(225, 255, 250, 252)
        canvas.drawRoundRect(rect, dp(24f), dp(24f), paint)
        val cy = rect.centerY()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.bold()
        textPaint.color = Color.rgb(91, 63, 112)
        textPaint.textSize = dp(20f)
        val title = "Remembering ${pet.name}"
        textPaint.textSize = min(dp(20f), dp(20f) * (rect.width() - dp(24f)) / textPaint.measureText(title))
        canvas.drawText(title, width / 2f, cy - dp(32f), textPaint)
        textPaint.textSize = dp(14f)
        textPaint.typeface = PaintTypeface.rounded()
        canvas.drawText("A little friend, always remembered", width / 2f, cy + dp(2f), textPaint)
        textPaint.textSize = dp(12f)
        canvas.drawText("Your memories are saved", width / 2f, cy + dp(33f), textPaint)
    }

    private fun memoriesRect(): RectF = RectF(dp(28f), height - dp(154f), width - dp(28f), height - dp(95f))

    private fun showMemories() {
        val memories = pet.memories()
        val dialog = AlertDialog.Builder(appContext).setTitle("Saved memories").setPositiveButton("CLOSE", null)
        if (memories.isEmpty()) dialog.setMessage("Your past pets will be remembered here.")
        else dialog.setItems(memories.toTypedArray(), null)
        dialog.show()
    }

    private fun drawActionEffects(canvas: Canvas, now: Long) {
        if (pet.dead) return
        val action = activeAction
        if (action == null) {
            val progress = touchProgress(now)
            if (progress <= 0f) return
            val cx = petCenterX
            val cy = petGroundY - dp(8f)
            val wave = sin(progress * Math.PI.toFloat())
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = PaintTypeface.bold()
            textPaint.textSize = dp(22f)
            textPaint.color = Color.rgb(255, 232, 165)
            canvas.drawText("✦", cx - dp(62f), cy - dp(72f) - wave * dp(9f), textPaint)
            canvas.drawText("✦", cx + dp(62f), cy - dp(92f) + wave * dp(9f), textPaint)
            drawReactionLabel(canvas, if (pet.hatched) "HI!" else "WOBBLE!", cx, cy - dp(126f), if (pet.hatched) pet.kind.dark else Color.rgb(66, 92, 126))
            return
        }
        if (now >= actionUntil) {
            if (action == Action.SLEEP) stopInteractionSound()
            activeAction = null
            return
        }
        val progress = if (action == Action.SLEEP) {
            // Keep the sleeping feedback alive without making the pose take a
            // full minute to settle. The next sleep cycle starts seamlessly.
            (((now - actionStartedAt).coerceAtLeast(0L) % 2400L).toFloat() / 2400f)
        } else {
            interactionProgress(now)
        }
        val seconds = progress * 1.6f
        val cx = petCenterX
        val cy = petGroundY - dp(8f)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.bold()
        val egg = !pet.hatched
        val labelColor = if (egg) Color.rgb(66, 92, 126) else pet.kind.dark
        when (action) {
            Action.FEED -> {
                textPaint.textSize = dp(28f)
                textPaint.color = if (egg) Color.rgb(255, 231, 159) else Color.rgb(255, 238, 190)
                canvas.drawText(if (egg) "♥" else "●", cx + sin(seconds * 5f) * dp(20f), cy - dp(104f) - seconds * dp(7f), textPaint)
                drawReactionLabel(canvas, if (egg) "TOASTY!" else "YUM!", cx, cy - dp(132f), labelColor)
            }
            Action.PLAY -> {
                textPaint.textSize = dp(24f)
                textPaint.color = Color.rgb(255, 244, 166)
                canvas.drawText("★", cx - dp(94f), cy - dp(25f) + sin(seconds * 6f) * dp(10f), textPaint)
                canvas.drawText("★", cx + dp(94f), cy - dp(43f) + cos(seconds * 5f) * dp(10f), textPaint)
                drawReactionLabel(canvas, if (egg) "WIGGLE!" else "WHEEE!", cx, cy - dp(132f), labelColor)
            }
            Action.BATH -> {
                textPaint.textSize = dp(24f)
                textPaint.color = Color.argb(220, 255, 255, 255)
                canvas.drawText("○  ○  ○", cx, cy - dp(118f) - sin(seconds * 4f) * dp(8f), textPaint)
                drawReactionLabel(canvas, if (egg) "NEST TIDY!" else "SPARKLY!", cx, cy - dp(142f), labelColor)
            }
            Action.SLEEP -> {
                val elapsed = (now - actionStartedAt).coerceAtLeast(0L)
                val cycleMs = 3600L
                val zDurationMs = 1550L
                val cyclePosition = elapsed % cycleMs
                val daylight = daylightFactor(LocalTime.now().hour * 60 + LocalTime.now().minute, weather)
                val zColor = blendColor(Color.rgb(235, 232, 255), Color.rgb(84, 67, 126), daylight)
                val zShadow = blendColor(Color.rgb(61, 48, 88), Color.WHITE, daylight)
                val xOffsets = floatArrayOf(54f, 78f, 98f)
                val baseHeights = floatArrayOf(76f, 103f, 125f)
                val sizes = floatArrayOf(23f, 17f, 13f)
                val offsets = longArrayOf(0L, 1150L, 2300L)
                for (index in offsets.indices) {
                    val age = cyclePosition - offsets[index]
                    if (age !in 0L until zDurationMs) continue
                    val raw = age.toFloat() / zDurationMs.toFloat()
                    val eased = raw * raw * (3f - 2f * raw)
                    val alpha = when {
                        raw < .18f -> raw / .18f
                        raw > .78f -> (1f - raw) / .22f
                        else -> 1f
                    }.coerceIn(0f, 1f)
                    val drift = sin(eased * Math.PI).toFloat() * dp(9f)
                    val x = cx + dp(xOffsets[index]) + drift
                    val y = cy - dp(baseHeights[index]) - dp(48f) * eased
                    textPaint.textSize = dp(sizes[index])
                    textPaint.color = Color.argb((alpha * 82f).roundToInt(), Color.red(zShadow), Color.green(zShadow), Color.blue(zShadow))
                    canvas.drawText(if (index == 2) "z" else "Z", x + dp(1f), y + dp(1f), textPaint)
                    textPaint.color = Color.argb((alpha * 235f).roundToInt(), Color.red(zColor), Color.green(zColor), Color.blue(zColor))
                    canvas.drawText(if (index == 2) "z" else "Z", x, y, textPaint)
                }
                drawReactionLabel(canvas, if (egg) "SAFE & SLEEPY" else "SWEET DREAMS", cx, cy - dp(132f), labelColor)
            }
        }
    }

    private fun drawReactionLabel(canvas: Canvas, label: String, centerX: Float, baseline: Float, color: Int) {
        textPaint.textSize = dp(14f)
        textPaint.typeface = PaintTypeface.bold()
        textPaint.color = Color.argb(125, 255, 255, 255)
        canvas.drawText(label, centerX + dp(1f), baseline + dp(2f), textPaint)
        textPaint.color = color
        canvas.drawText(label, centerX, baseline, textPaint)
    }

    private fun drawMessage(canvas: Canvas, now: Long) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.rounded()
        val displayedMessage = when {
            pet.dead -> "A little friend, always remembered"
            !pet.hatched && now >= messageUntil -> "A little friend is growing inside"
            pet.sick && now >= messageUntil -> "${pet.symptomLabel.lowercase().replace('_', ' ')} — medicine can help"
            pet.needsCritical && now >= messageUntil -> "Needs care — growth is slowed"
            else -> message
        }
        val messageMaxWidth = width - dp(78f)
        textPaint.textSize = min(dp(14f), dp(14f) * messageMaxWidth / textPaint.measureText(displayedMessage))
        val messageRect = RectF(dp(28f), dp(83f), width - dp(28f), dp(119f))
        paint.color = if (now < messageUntil) Color.argb(238, 255, 255, 255) else Color.argb(205, 255, 255, 255)
        canvas.drawRoundRect(messageRect, dp(18f), dp(18f), paint)
        paint.color = Color.argb(220, Color.red(messageColor), Color.green(messageColor), Color.blue(messageColor))
        canvas.drawCircle(messageRect.left + dp(13f), messageRect.centerY(), dp(4f), paint)
        textPaint.color = if (now < messageUntil) Color.rgb(54, 63, 55) else Color.rgb(81, 73, 88)
        canvas.drawText(displayedMessage, messageRect.centerX() + dp(4f), dp(106f), textPaint)
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
        canvas.drawText("${pet.stage} • AGE ${pet.ageLabel} • HEALTH ${pet.healthPercent.roundToInt()}%", dp(22f), top + dp(48f), textPaint)
        if (pet.sick) {
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = PaintTypeface.bold()
            textPaint.textSize = dp(10f)
            textPaint.color = Color.rgb(185, 96, 104)
            canvas.drawText(pet.symptomLabel, width - dp(22f), top + dp(48f), textPaint)
            textPaint.textAlign = Paint.Align.LEFT
        }
        textPaint.textSize = dp(11f)
        textPaint.typeface = PaintTypeface.bold()
        textPaint.color = Color.rgb(105, 78, 116)
        val progressLabel = if (pet.hatched) "EVOLUTION" else "HATCHING"
        // Do not display 100% while the readiness threshold is still just
        // below complete; that made the hatch prompt appear to be missing.
        val progress = pet.evolutionProgress
        val progressPercent = if (progress >= 100f) 100 else progress.toInt()
        val evolutionText = "$progressLabel $progressPercent% • ${pet.evolutionHint}"
        textPaint.textSize = min(dp(11f), dp(11f) * (width - dp(44f)) / textPaint.measureText(evolutionText))
        canvas.drawText(evolutionText, dp(22f), top + dp(61f), textPaint)

        paint.color = Color.rgb(244, 226, 238)
        canvas.drawRoundRect(reset, dp(15f), dp(15f), paint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = dp(11f)
        textPaint.color = Color.rgb(122, 69, 123)
        canvas.drawText("NEW PET", reset.centerX(), reset.centerY() + dp(4f), textPaint)

        if (pet.dead) return
        val values = if (!pet.hatched) listOf(pet.hunger to "WARMTH", pet.joy to "COMFORT", pet.energy to "REST", pet.clean to "NEST")
            else listOf(pet.hunger to "HUNGER", pet.joy to "JOY", pet.energy to "ENERGY", pet.clean to "CLEAN")
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
        if (pet.dead) {
            val rect = memoriesRect()
            paint.color = Color.rgb(228, 211, 240)
            canvas.drawRoundRect(rect, dp(20f), dp(20f), paint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = Color.rgb(68, 43, 90)
            textPaint.typeface = PaintTypeface.bold()
            textPaint.textSize = dp(15f)
            canvas.drawText("SAVED MEMORIES", rect.centerX(), rect.centerY() + dp(5f), textPaint)
            textPaint.textSize = dp(12f)
            textPaint.typeface = PaintTypeface.rounded()
            canvas.drawText("Choose NEW PET whenever you're ready", width / 2f, rect.bottom + dp(31f), textPaint)
            return
        }
        if (careCategory == null) {
            for (category in CareCategory.values()) drawCareCategory(canvas, category)
        } else {
            val category = careCategory ?: return
            val options = careOptions(category)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = PaintTypeface.bold()
            textPaint.textSize = dp(11f)
            textPaint.color = Color.rgb(111, 82, 123)
            canvas.drawText("CHOOSE ${category.label}", width / 2f, statsTop() + dp(165f), textPaint)
            for (index in options.indices) {
                val action = options[index]
                val rect = careOptionRect(action, index, options.size)
                buttons.add(CareButton(action, rect))
                drawCareOption(canvas, action, rect)
            }
            drawCareCenterButton(canvas)
        }
    }

    private fun drawCareCategory(canvas: Canvas, category: CareCategory) {
        val rect = careCategoryRect(category)
        drawOrganicButton(canvas, rect, category.fill, CareCategory.values().indexOf(category), pressed = false)
        val scale = min(rect.width() / dp(68f), rect.height() / dp(58f)).coerceIn(.78f, 1.45f)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = dp(16f) * scale
        textPaint.color = Color.rgb(76, 49, 94)
        canvas.drawText(category.glyph, rect.centerX(), rect.top + rect.height() * .43f, textPaint)
        textPaint.textSize = dp(8f) * scale
        canvas.drawText(category.label, rect.centerX(), rect.top + rect.height() * .75f, textPaint)
    }

    private fun drawCareOption(canvas: Canvas, action: CareAction, rect: RectF) {
        val scale = min(rect.width() / dp(76f), rect.height() / dp(58f)).coerceIn(.78f, 1.3f)
        drawOrganicButton(canvas, rect, if (pressedAction == action) Color.WHITE else action.fill, action.ordinal, pressedAction == action)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = dp(15f) * scale
        textPaint.color = Color.rgb(76, 49, 94)
        canvas.drawText(action.glyph, rect.centerX(), rect.top + rect.height() * .43f, textPaint)
        textPaint.textSize = dp(9f) * scale
        canvas.drawText(action.label, rect.centerX(), rect.top + rect.height() * .75f, textPaint)
    }

    private fun drawCareCenterButton(canvas: Canvas) {
        val rect = careCenterRect()
        val scale = (rect.width() / dp(132f)).coerceIn(.72f, 1f)
        drawOrganicButton(canvas, rect, Color.rgb(86, 58, 108), 9, pressed = false)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = PaintTypeface.bold()
        textPaint.textSize = dp(11f) * scale
        textPaint.color = Color.WHITE
        canvas.drawText(if (careCategory == null) "CLOSE" else "BACK", rect.centerX(), rect.centerY() + dp(4f) * scale, textPaint)
    }

    private fun drawOrganicButton(canvas: Canvas, rect: RectF, color: Int, variant: Int, pressed: Boolean) {
        val path = organicButtonPath(rect, variant)
        canvas.save()
        canvas.translate(0f, dp(6f))
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(if (pressed) 18 else 30, 67, 39, 95)
        canvas.drawPath(path, paint)
        canvas.restore()
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(3f)
        paint.color = Color.WHITE
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(52, 255, 255, 255)
        canvas.drawCircle(rect.left + rect.width() * .22f, rect.top + rect.height() * .2f, min(rect.width(), rect.height()) * .075f, paint)
    }

    private fun organicButtonPath(rect: RectF, variant: Int): Path {
        val w = rect.width()
        val h = rect.height()
        val wobble = ((variant % 5) - 2) * .018f
        val path = Path()
        path.moveTo(rect.left + w * (.5f + wobble), rect.top)
        path.cubicTo(
            rect.left + w * .78f, rect.top - h * .025f,
            rect.right + w * .025f, rect.top + h * .12f,
            rect.right, rect.top + h * .43f
        )
        path.cubicTo(
            rect.right + w * .02f, rect.top + h * .73f,
            rect.right - w * .16f, rect.bottom + h * .02f,
            rect.left + w * .52f, rect.bottom
        )
        path.cubicTo(
            rect.left + w * .2f, rect.bottom + h * .025f,
            rect.left - w * .025f, rect.bottom - h * .18f,
            rect.left, rect.top + h * .52f
        )
        path.cubicTo(
            rect.left - w * .01f, rect.top + h * .2f,
            rect.left + w * .2f, rect.top + h * .02f,
            rect.left + w * (.5f + wobble), rect.top
        )
        path.close()
        return path
    }

    private fun careCategoryRect(category: CareCategory): RectF {
        return careCategoryGeometry()[CareCategory.values().indexOf(category)]
    }

    private fun careCenterRect(): RectF = RectF(
        width / 2f - min(dp(66f), carePanelRect().width() * .36f),
        carePanelRect().bottom - dp(62f),
        width / 2f + min(dp(66f), carePanelRect().width() * .36f),
        carePanelRect().bottom
    )

    private fun careOptionRect(action: CareAction, index: Int, count: Int): RectF {
        return careOptionGeometry(action.category, count)[index]
    }

    private fun radialPoint(angleDegrees: Float, radius: Float): Pair<Float, Float> {
        val radians = Math.toRadians(angleDegrees.toDouble())
        val center = careCenterRect()
        return (center.centerX() + cos(radians).toFloat() * radius) to
            (center.centerY() + sin(radians).toFloat() * radius)
    }

    private fun carePanelRect(): RectF = RectF(
        dp(12f), statsTop() + dp(176f), width - dp(12f), height - dp(18f)
    )

    private fun careCategoryGeometry(): List<RectF> {
        val panel = carePanelRect()
        val gap = dp(3f)
        val top = panel.top + dp(2f)
        val bottom = panel.bottom - dp(2f)
        val totalHeight = bottom - top
        val centerWidth = (panel.width() * .34f).coerceAtLeast(dp(92f))
        val sideWidth = ((panel.width() - centerWidth - gap * 2f) / 2f).coerceAtLeast(dp(48f))
        val left = panel.left
        val centerLeft = left + sideWidth + gap
        val right = centerLeft + centerWidth + gap
        val topHeight = (totalHeight * .46f).coerceAtLeast(dp(72f))
        val bottomTop = top + topHeight + gap
        val sideRects = listOf(
            RectF(left, bottomTop, left + sideWidth, bottom),
            RectF(left, top, left + sideWidth, bottomTop),
            RectF(centerLeft, top, centerLeft + centerWidth, bottom),
            RectF(right, top, right + sideWidth, bottomTop),
            RectF(right, bottomTop, right + sideWidth, bottom)
        )
        return sideRects
    }

    private fun careOptionGeometry(category: CareCategory, count: Int): List<RectF> {
        val panel = carePanelRect()
        var widthCandidate = min(dp(126f), panel.width() - dp(24f))
        while (widthCandidate >= dp(48f)) {
            val heightCandidate = widthCandidate * .67f
            var radius = careRadialRadius(panel, widthCandidate, heightCandidate)
            while (radius >= dp(28f)) {
                val anchor = radialPoint(category.angleDegrees, radius)
                val tangentRadians = Math.toRadians((category.angleDegrees + 90f).toDouble())
                val gap = dp(4f)
                val spacing = widthCandidate + gap
                val rects = (0 until count).map { index ->
                    val centeredIndex = index - (count - 1) / 2f
                    val x = anchor.first + cos(tangentRadians).toFloat() * centeredIndex * spacing
                    val y = anchor.second + sin(tangentRadians).toFloat() * centeredIndex * spacing
                    RectF(
                        x - widthCandidate / 2f, y - heightCandidate / 2f,
                        x + widthCandidate / 2f, y + heightCandidate / 2f
                    )
                }
                if (careRectsFit(rects, panel, careCenterRect())) return rects
                radius -= dp(2f)
            }
            widthCandidate -= dp(2f)
        }
        return careFallbackRects(listOf(category.angleDegrees), panel, count)
    }

    private fun careRadialRadius(panel: RectF, buttonWidth: Float, buttonHeight: Float): Float {
        val center = careCenterRect()
        return min(
            dp(170f),
            min(
                panel.width() / 2f - buttonWidth / 2f - dp(8f),
                center.centerY() - panel.top - buttonHeight / 2f - dp(8f)
            )
        ).coerceAtLeast(dp(28f))
    }

    private fun careRectsFit(rects: List<RectF>, panel: RectF, center: RectF): Boolean {
        val gap = dp(4f)
        if (rects.any { it.left < panel.left || it.right > panel.right || it.top < panel.top || it.bottom > panel.bottom }) return false
        for (first in rects.indices) {
            for (second in first + 1 until rects.size) if (rectsOverlap(rects[first], rects[second], gap)) return false
            if (rectsOverlap(rects[first], center, gap)) return false
        }
        return true
    }

    private fun rectsOverlap(first: RectF, second: RectF, gap: Float): Boolean =
        first.left < second.right + gap && first.right + gap > second.left &&
            first.top < second.bottom + gap && first.bottom + gap > second.top

    private fun careFallbackRects(angles: List<Float>, panel: RectF, tangentCount: Int = 1): List<RectF> {
        val widthCandidate = min(dp(54f), panel.width() / 3f)
        val heightCandidate = widthCandidate * .67f
        val radius = careRadialRadius(panel, widthCandidate, heightCandidate)
        if (tangentCount > 1 && angles.size == 1) {
            val anchor = radialPoint(angles.single(), radius)
            val tangentRadians = Math.toRadians((angles.single() + 90f).toDouble())
            val spacing = widthCandidate + dp(6f)
            return (0 until tangentCount).map { index ->
                val centeredIndex = index - (tangentCount - 1) / 2f
                val x = anchor.first + cos(tangentRadians).toFloat() * centeredIndex * spacing
                val y = anchor.second + sin(tangentRadians).toFloat() * centeredIndex * spacing
                RectF(
                    x - widthCandidate / 2f, y - heightCandidate / 2f,
                    x + widthCandidate / 2f, y + heightCandidate / 2f
                )
            }
        }
        return angles.map { angle ->
            val point = radialPoint(angle, radius)
            RectF(point.first - widthCandidate / 2f, point.second - heightCandidate / 2f,
                point.first + widthCandidate / 2f, point.second + heightCandidate / 2f)
        }
    }

    private fun careOptions(category: CareCategory): List<CareAction> = when (category) {
        CareCategory.FOOD -> listOf(CareAction.MEAL, CareAction.TREAT)
        CareCategory.FUN -> listOf(CareAction.PLAY, CareAction.TOY, CareAction.CUDDLE)
        CareCategory.REST -> listOf(CareAction.NAP, CareAction.SLEEP)
        CareCategory.CLEAN -> listOf(CareAction.BATH, CareAction.TIDY)
        CareCategory.HEALTH -> if (pet.sick) listOf(CareAction.CHECKUP, CareAction.MEDICINE)
        else listOf(CareAction.CHECKUP, CareAction.VITAMIN)
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

        if (progress < 1f && activityResumed && windowVisibility == View.VISIBLE) {
            postInvalidateOnAnimation()
        }
        if (!menuOpen && progress >= 1f) menuAnimationStart = 0L
    }

    private fun setMenuOpen(open: Boolean) {
        menuOpen = open
        menuOpening = open
        menuAnimationStart = SystemClock.uptimeMillis()
        invalidate()
    }

    fun maybeShowTutorialIfNeeded() {
        if (!pet.created || tutorialShowing || prefs.getBoolean("tutorial_seen", false)) return
        val activity = appContext as? Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        if (!activity.hasWindowFocus()) {
            postDelayed({ maybeShowTutorialIfNeeded() }, 1000L)
            return
        }
        showTutorialPage(0, true)
    }

    private fun showTutorialPage(page: Int, firstRun: Boolean) {
        val activity = appContext as? Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        val pages = arrayOf(
            "Your little friend has four bars: HUNGER, JOY, ENERGY, and CLEAN. Keep them happy and your friend grows.",
            "Tap the big CARE button to choose FOOD, FUN, REST, CLEAN, or HEALTH. You can do more than one kind thing before closing the menu.",
            "CHECKUP tells you how your friend feels. VITAMIN gives a tiny boost. If your friend gets sick, MEDICINE appears. You can read this again in MENU > SETTINGS."
        )
        val lastPage = pages.lastIndex
        tutorialShowing = true
        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (page == 0) "Let's learn together!" else "How to care for me")
            .setMessage(pages[page])
            .setNegativeButton(if (page == 0) "SKIP" else "BACK") { _, _ ->
                tutorialShowing = false
                if (page == 0) {
                    if (firstRun) prefs.edit().putBoolean("tutorial_seen", true).apply()
                } else {
                    post { showTutorialPage(page - 1, firstRun) }
                }
            }
            .setPositiveButton(if (page == lastPage) "LET'S PLAY" else "NEXT") { _, _ ->
                tutorialShowing = false
                if (page == lastPage) {
                    if (firstRun) prefs.edit().putBoolean("tutorial_seen", true).apply()
                } else {
                    post { showTutorialPage(page + 1, firstRun) }
                }
            }
            .create()
        dialog.setOnCancelListener { tutorialShowing = false }
        dialog.show()
    }

    private fun showSettings() {
        val activity = appContext as? Activity ?: return
        val remindersOn = CareReminderScheduler.isEnabled(appContext)
        val choices = arrayOf(
            "CHANGE NAME",
            if (remindersOn) "TURN OFF CARE REMINDERS" else "TURN ON CARE REMINDERS",
            "HOW TO PLAY",
            "RESET DATA"
        )
        AlertDialog.Builder(activity)
            .setTitle("Settings")
            .setMessage(
                (if (cloudSave.isSignedIn()) "Google backup is connected." else "Google backup is not connected yet.") +
                    "\nCare reminders are ${if (remindersOn) "on" else "off"}."
            )
            .setItems(choices) { _, which ->
                when (which) {
                    0 -> editPlayerName()
                    1 -> toggleCareReminders()
                    2 -> post { showTutorialPage(0, false) }
                    else -> showResetChoices()
                }
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun toggleCareReminders() {
        if (CareReminderScheduler.isEnabled(appContext)) {
            CareReminderScheduler.disable(appContext)
            Toast.makeText(appContext, "Care reminders turned off.", Toast.LENGTH_SHORT).show()
        } else {
            CareReminderScheduler.enable(appContext)
            (appContext as? MainActivity)?.maybeRequestCareReminderPermission(force = true)
            Toast.makeText(appContext, "Care reminders turned on.", Toast.LENGTH_SHORT).show()
        }
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
        CareReminderScheduler.clearNotification(appContext)
        CareReminderScheduler.clearGeneralNotification(appContext)
        CareReminderScheduler.clearSickNotification(appContext)
        setupMode = true
        setupKind = pet.kind
        setupName = pet.name
        setupPlayerName = pet.playerName
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

    private fun statsTop(): Float = height * 0.5f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (menuOpen || menuAnimationStart != 0L) return handleMenuTouch(event)
        if (setupMode) return handleSetupTouch(event)
        if (event.y >= carePanelRect().top) return handleCareMenuTouch(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (menuButtonRect().contains(event.x, event.y)) {
                    setMenuOpen(true)
                    return true
                }
                pressedAction = buttons.firstOrNull { it.rect.contains(event.x, event.y) }?.action
                pressedPetTouch = pressedAction == null && petHitRect().contains(event.x, event.y)
                if (newPetRect().contains(event.x, event.y) || headerResetRect().contains(event.x, event.y) || headerUpdateRect().contains(event.x, event.y)) pressedAction = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (pet.dead && memoriesRect().contains(event.x, event.y)) {
                    showMemories()
                    return true
                }
                val action = buttons.firstOrNull { it.rect.contains(event.x, event.y) }?.action
                if (action != null && action == pressedAction) perform(action)
                if (pressedPetTouch && action == null && petHitRect().contains(event.x, event.y)) touchPet()
                if (newPetRect().contains(event.x, event.y) || headerResetRect().contains(event.x, event.y)) confirmNewPet()
                if (headerUpdateRect().contains(event.x, event.y)) checkForUpdates(showNoUpdate = true)
                pressedAction = null
                pressedPetTouch = false
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedAction = null
                pressedPetTouch = false
                invalidate()
                return true
            }
        }
        return true
    }

    private fun handleCareMenuTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedAction = buttons.firstOrNull { it.rect.contains(event.x, event.y) }?.action
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (careCategory != null && careCenterRect().contains(event.x, event.y)) {
                    careCategory = null
                } else if (careCategory == null) {
                    val category = CareCategory.values().firstOrNull {
                        careCategoryRect(it).contains(event.x, event.y)
                    }
                    if (category != null) careCategory = category
                } else {
                    val action = buttons.firstOrNull { it.rect.contains(event.x, event.y) }?.action
                    if (action != null && action == pressedAction) {
                        perform(action)
                    }
                }
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
        if (setupPlayerNameRect().contains(event.x, event.y)) {
            editPlayerName()
            return true
        }
        if (setupPetNameRect().contains(event.x, event.y)) {
            editPetName()
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
            pet.createEgg(setupName, setupKind, setupPlayerName)
            setupMode = false
            message = "${pet.name}'s egg is settling in!"
            messageUntil = SystemClock.uptimeMillis() + 5000L
            savePet()
            post { onPetCreated() }
            invalidate()
        }
        return true
    }

    private fun newPetRect(): RectF = RectF(width - dp(94f), statsTop() + dp(14f), width - dp(18f), statsTop() + dp(46f))

    private fun setupPlayerNameRect(): RectF = RectF(dp(28f), dp(305f), width - dp(28f), dp(343f))

    private fun setupPetNameRect(): RectF = RectF(dp(28f), dp(344f), width - dp(28f), dp(383f))

    private fun menuButtonRect(): RectF = RectF(dp(12f), dp(18f), dp(54f), dp(57f))

    private fun menuSettingsRect(): RectF = RectF(dp(24f), dp(112f), dp(258f), dp(164f))

    private fun headerResetRect(): RectF = RectF(width - dp(114f), dp(18f), width - dp(20f), dp(57f))

    private fun headerUpdateRect(): RectF = RectF(width - dp(214f), dp(21f), width - dp(121f), dp(54f))

    private fun editPlayerName() {
        val input = EditText(appContext).apply {
            setText(pet.playerName)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            hint = "Your name"
        }
        AlertDialog.Builder(appContext)
            .setTitle("What should I call you?")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val newName = input.text.toString().trim().take(20).ifBlank { "Zoey" }
                pet.playerName = newName
                setupPlayerName = newName
                message = "Hi $newName! I'm so happy to see you!"
                messageUntil = 0L
                savePet()
                invalidate()
            }
            .show()
    }

    private fun editPetName() {
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
            .setMessage("Your current pet stays until you choose a new egg. Its memories will be saved when you do.")
            .setNegativeButton("KEEP PET", null)
            .setPositiveButton("NEW PET") { _, _ ->
                // Keep the current pet/save until a replacement egg is chosen.
                setupKind = PetKind.BUNNY
                setupName = "Mochi"
                setupPlayerName = pet.playerName
                setupMode = true
                invalidate()
            }
            .show()
    }

    private fun perform(action: CareAction) {
        if (pet.dead) return
        val result = pet.applyCare(action)
        if (pet.dead) { savePet(); invalidate(); return }
        activeAction = action.motionAction
        actionStartedAt = SystemClock.uptimeMillis()
        actionUntil = actionStartedAt + action.durationMillis
        touchReactionStartedAt = 0L
        touchReactionUntil = 0L
        message = result.first
        messageColor = result.second
        messageUntil = SystemClock.uptimeMillis() + 3500L
        playInteractionSound(action.motionAction)
        if (action == CareAction.SLEEP) startSleepAmbient() else stopInteractionSound()
        savePet()
    }

    fun savePet(uploadCloud: Boolean = true) {
        pet.save()
        if (!pet.sick) CareReminderScheduler.clearSickNotification(appContext)
        CareReminderScheduler.scheduleNextThreshold(appContext)
        val now = SystemClock.uptimeMillis()
        if (uploadCloud || now - lastCloudUploadAt >= CLOUD_UPLOAD_INTERVAL_MS) {
            cloudSave.upload()
            lastCloudUploadAt = now
        }
        lastSaved = now
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
                setupPlayerName = pet.playerName
                // The cloud snapshot may have been saved before the phone
                // went offline. Apply elapsed time after restoring it, then
                // save that caught-up state back to both stores.
                pet.updateFromClock()
                savePet()
                invalidate()
                Toast.makeText(appContext, "Cloud progress restored.", Toast.LENGTH_LONG).show()
            }
            CloudRestoreResult.NO_CLOUD_BACKUP -> {
                preferCloudRestore = false
                cloudSyncReady = true
                pet.updateFromClock()
                savePet()
                if (hasCreatedPet()) {
                    Toast.makeText(appContext, "Google backup enabled.", Toast.LENGTH_SHORT).show()
                }
            }
            CloudRestoreResult.KEPT_LOCAL -> {
                preferCloudRestore = false
                cloudSyncReady = true
                pet.updateFromClock()
                savePet()
                invalidate()
                Toast.makeText(appContext, "Google backup synced.", Toast.LENGTH_SHORT).show()
            }
            CloudRestoreResult.FAILED -> {
                Toast.makeText(appContext, "Cloud backup could not sync yet; this phone still has its local progress.", Toast.LENGTH_LONG).show()
            }
        }
        postDelayed({
            (appContext as? MainActivity)?.maybeRequestCareReminderPermission()
        }, 900L)
    }

    fun hasCreatedPet(): Boolean = pet.created

    fun hasCloudAccount(): Boolean = cloudSave.isSignedIn()

    fun petName(): String = pet.name

    fun checkForUpdates(showNoUpdate: Boolean) {
        updateManager.check(showNoUpdate)
    }

    fun resumePendingInstall() {
        updateManager.resumePendingInstall()
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private data class CareButton(val action: CareAction, val rect: RectF)

    private data class WeatherState(
        val available: Boolean = false,
        val cloudCover: Float = .28f,
        val raining: Boolean = false,
        val rainSoon: Float = 0f,
        val weatherCode: Int = 0,
        val sunriseMinutes: Int = 360,
        val sunsetMinutes: Int = 1080
    )

    private enum class Action { FEED, PLAY, BATH, SLEEP }

    private enum class CareCategory(val label: String, val glyph: String, val fill: Int, val angleDegrees: Float) {
        FOOD("FOOD", "+", Color.rgb(255, 225, 170), -160f),
        FUN("FUN", "★", Color.rgb(255, 193, 216), -125f),
        REST("REST", "Z", Color.rgb(198, 205, 255), -90f),
        CLEAN("CLEAN", "✦", Color.rgb(190, 232, 220), -55f),
        HEALTH("HEALTH", "♥", Color.rgb(244, 208, 214), -20f)
    }

    private enum class CareAction(
        val category: CareCategory,
        val label: String,
        val glyph: String,
        val motionAction: Action,
        val durationMillis: Long,
        val fill: Int,
        val primaryNeed: Int = -1,
        val primaryBoost: Float = 0f,
        val secondaryNeed: Int = -1,
        val secondaryBoost: Float = 0f
    ) {
        MEAL(CareCategory.FOOD, "MEAL", "+", Action.FEED, 1600L, Color.rgb(255, 225, 170), 0, 18f),
        TREAT(CareCategory.FOOD, "TREAT", "♥", Action.FEED, 1600L, Color.rgb(255, 214, 157), 0, 10f, 1, 5f),
        PLAY(CareCategory.FUN, "PLAY", "★", Action.PLAY, 1600L, Color.rgb(255, 193, 216), 1, 16f),
        TOY(CareCategory.FUN, "TOY", "●", Action.PLAY, 1600L, Color.rgb(255, 211, 227), 1, 10f),
        CUDDLE(CareCategory.FUN, "CUDDLE", "♥", Action.PLAY, 1600L, Color.rgb(255, 202, 218), 1, 8f, 2, 4f),
        NAP(CareCategory.REST, "NAP", "z", Action.SLEEP, 12000L, Color.rgb(211, 216, 255), 2, 12f),
        SLEEP(CareCategory.REST, "SLEEP", "Zz", Action.SLEEP, 60000L, Color.rgb(198, 205, 255), 2, 22f),
        BATH(CareCategory.CLEAN, "BATH", "✦", Action.BATH, 1600L, Color.rgb(190, 232, 220), 3, 25f),
        TIDY(CareCategory.CLEAN, "TIDY", "✧", Action.BATH, 1600L, Color.rgb(207, 239, 226), 3, 12f),
        CHECKUP(CareCategory.HEALTH, "CHECKUP", "♡", Action.PLAY, 1600L, Color.rgb(244, 208, 214)),
        MEDICINE(CareCategory.HEALTH, "MEDICINE", "+", Action.FEED, 1600L, Color.rgb(239, 195, 207)),
        VITAMIN(CareCategory.HEALTH, "VITAMIN", "●", Action.FEED, 1600L, Color.rgb(247, 220, 177), 2, 5f, 0, 5f)
    }

    private enum class TransitionKind { HATCH, EVOLUTION, DEATH }

    private enum class CloudRestoreResult {
        RESTORED,
        NO_CLOUD_BACKUP,
        KEPT_LOCAL,
        FAILED
    }

    companion object {
        private const val WALK_FRAME_DURATION_MS = 105L
        private const val WEATHER_REFRESH_MS = 30 * 60 * 1000L
        private const val LIFECYCLE_REPROMPT_MS = 20_000L
        private const val IDLE_FRAME_DELAY_MS = 33L
        private const val CLOUD_UPLOAD_INTERVAL_MS = 5 * 60 * 1000L
    }

    private enum class MotionMode { REST, WALK, CURIOUS, STAND }

    private enum class AmbientMode { DAY, RAIN, NIGHT }

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
                        pet.mergeHistory(snapshot.data?.get("historyJson") as? String)
                        val cloudHasPet = snapshot.getBoolean("created") == true
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
        private val life = PetLife().apply {
            needs[0] = readMetric("hunger", 78f)
            needs[1] = readMetric("joy", 82f)
            needs[2] = readMetric("energy", 74f)
            needs[3] = readMetric("clean", 88f)
            generation = prefs.getInt("generation", 0).coerceIn(0, 2)
            ageMillis = prefs.getLong("age_millis", 0L).coerceAtLeast(0)
            goodCareMillis = prefs.getLong("good_care_millis", 0L).coerceAtLeast(0)
            totalCareMillis = prefs.getLong("total_care_millis", 0L).coerceAtLeast(goodCareMillis)
            created = prefs.getBoolean("created", false)
            hatched = prefs.getBoolean("hatched", false)
            dead = prefs.getBoolean("dead", false)
            diedAt = prefs.getLong("died_at", 0L)
            sick = prefs.getBoolean("sick", false)
            symptom = prefs.getInt("symptom", PetLife.NO_SYMPTOM)
            poorCareMillis = prefs.getLong("poor_care_millis", 0L).coerceAtLeast(0)
            lowNeedMillis = prefs.getLong("low_need_millis", 0L).coerceAtLeast(0)
            sickAt = prefs.getLong("sick_at", 0L).coerceAtLeast(0)
            oldAgeDeclined = prefs.getBoolean("old_age_declined", false)
            eggAgeMillis = prefs.getLong("egg_age_millis", 0L)
            eggProgressMillis = prefs.getLong("egg_progress_millis", 0L).toDouble()
            evolutionMillis = if (prefs.contains("evolution_millis")) prefs.getLong("evolution_millis", 0L).toDouble()
                else PetGrowth.migrateEvolution(generation, ageMillis, carePercent())
            adultAgeMillis = prefs.getLong("adult_age_millis", 0L)
            adultClockReady = prefs.getBoolean("adult_clock_ready", generation < 2 || prefs.contains("adult_age_millis"))
        }
        val hunger: Float get() = life.needs[0]
        val joy: Float get() = life.needs[1]
        val energy: Float get() = life.needs[2]
        val clean: Float get() = life.needs[3]
        var growth = prefs.getFloat("growth", 0f)
        val generation: Int get() = life.generation
        val created: Boolean get() = life.created
        val hatched: Boolean get() = life.hatched
        val dead: Boolean get() = life.dead
        val sick: Boolean get() = life.sick
        val symptomLabel: String get() = life.symptomLabel()
        val needsCritical: Boolean get() = life.needsCritical()
        var name = prefs.getString("name", "Mochi") ?: "Mochi"
        var playerName = prefs.getString("player_name", "Zoey") ?: "Zoey"
        var kind = prefs.getString("kind", PetKind.BUNNY.name)?.let { value ->
            PetKind.values().firstOrNull { it.name == value }
        } ?: PetKind.BUNNY
        private var lastUpdate = prefs.getLong("last_update", System.currentTimeMillis())
        private var lastSavedAt = prefs.getLong("saved_at", 0L)
        private var petId = prefs.getString("pet_id", null) ?: java.util.UUID.randomUUID().toString()
        private var createdAt = prefs.getLong("created_at", lastUpdate - life.ageMillis - life.eggAgeMillis)
        private var historyJson = prefs.getString("history_json", "[]") ?: "[]"

        val savedAt: Long get() = lastSavedAt
        val growthStage: PetGrowth.Stage get() = PetGrowth.stage(hatched, generation)
        val stage: String get() = if (dead) "REMEMBERED" else growthStage.name
        val level: Int get() = if (!hatched) 0 else generation + 1
        val ageHours: Float get() = life.ageMillis / 3_600_000f
        val ageLabel: String
            get() {
                val millis = if (hatched) life.ageMillis else life.eggAgeMillis
                return when {
                    millis < 3_600_000L -> "${millis / 60_000L}m"
                    millis < PetGrowth.DAY_MILLIS -> "${millis / 3_600_000L}h"
                    else -> "${millis / PetGrowth.DAY_MILLIS}d"
                }
            }
        val carePercent: Float get() = life.carePercent().toFloat().coerceIn(0f, 100f)
        val healthPercent: Float get() = life.healthPercent().toFloat().coerceIn(0f, 100f)
        val hatchProgress: Float get() = PetGrowth.hatchProgress(life.eggProgressMillis.toLong()).toFloat()
        val evolutionProgress: Float
            get() = when {
                !hatched -> hatchProgress * 100f
                generation >= 2 -> 100f
                else -> (life.evolutionMillis / PetGrowth.STAGE_MILLIS * 100).toFloat().coerceIn(0f, 100f)
            }
        val evolutionHint: String
            get() = when {
                dead -> "Memories saved"
                !hatched && hatchReady -> "Ready to hatch"
                !hatched -> "About 2 days with good care"
                evolutionReady -> "Ready to grow"
                generation >= 2 -> "Adult • enjoy your time together"
                deathReady -> "A happy life is nearly complete"
                else -> "About a week per stage • care helps"
            }

        val hatchReady: Boolean get() = life.hatchReady()
        val evolutionReady: Boolean get() = life.evolutionReady()
        val deathReady: Boolean get() = life.deathReady()

        fun updateFromClock() {
            val now = System.currentTimeMillis()
            life.advance((now - lastUpdate).coerceAtLeast(0), now)
            lastUpdate = now
        }

        fun apply(action: Action): Pair<String, Int> {
            updateFromClock()
            life.care(action.ordinal)
            return when (action) {
                Action.FEED -> (if (hatched) "Nom nom! Tasty treats!" else "Toasty and warm!") to Color.rgb(172, 86, 40)
                Action.PLAY -> (if (hatched) "Wheee! That was fun!" else "That made me wiggle!") to Color.rgb(191, 54, 112)
                Action.BATH -> (if (hatched) "Sparkly clean!" else "Nest is nice and tidy!") to Color.rgb(39, 135, 119)
                Action.SLEEP -> (if (hatched) "Sweet dreams, little one." else "Sleepy and safe.") to Color.rgb(75, 78, 173)
            }
        }

        fun applyCare(action: CareAction): Pair<String, Int> {
            updateFromClock()
            when (action) {
                CareAction.CHECKUP -> return if (sick) {
                    "${name} needs a little medicine." to Color.rgb(185, 96, 104)
                } else {
                    "${name} looks happy and healthy!" to Color.rgb(73, 139, 112)
                }
                CareAction.MEDICINE -> {
                    life.cure()
                    return "Medicine helped! Feeling better!" to Color.rgb(185, 96, 104)
                }
                else -> {
                    if (action.primaryNeed >= 0) life.careNeed(action.primaryNeed, action.primaryBoost)
                    if (action.secondaryNeed >= 0) life.careNeed(action.secondaryNeed, action.secondaryBoost)
                }
            }
            return when (action) {
                CareAction.MEAL -> (if (hatched) "Nom nom! A lovely meal!" else "Toasty and warm!") to Color.rgb(172, 86, 40)
                CareAction.TREAT -> (if (hatched) "A tiny tasty treat!" else "A cozy little treat!") to Color.rgb(172, 86, 40)
                CareAction.PLAY -> (if (hatched) "Wheee! That was fun!" else "That made me wiggle!") to Color.rgb(191, 54, 112)
                CareAction.TOY -> "A favorite toy!" to Color.rgb(191, 54, 112)
                CareAction.CUDDLE -> "A warm cuddle!" to Color.rgb(191, 54, 112)
                CareAction.NAP -> "A short, cozy nap." to Color.rgb(75, 78, 173)
                CareAction.SLEEP -> "Sweet dreams, little one." to Color.rgb(75, 78, 173)
                CareAction.BATH -> (if (hatched) "Sparkly clean!" else "Nest is nice and tidy!") to Color.rgb(39, 135, 119)
                CareAction.TIDY -> (if (hatched) "Everything is tidy!" else "A lovely tidy nest!") to Color.rgb(39, 135, 119)
                CareAction.VITAMIN -> "A little healthy boost!" to Color.rgb(185, 96, 104)
                CareAction.CHECKUP, CareAction.MEDICINE -> "" to Color.WHITE
            }
        }

        fun createEgg(newName: String, newKind: PetKind, newPlayerName: String = playerName) {
            if (created) archiveCurrent(if (dead) "old_age" else "retired")
            name = newName.ifBlank { "Mochi" }
            playerName = newPlayerName.trim().take(20).ifBlank { "Zoey" }
            kind = newKind
            life.createEgg()
            growth = 0f
            petId = java.util.UUID.randomUUID().toString()
            createdAt = System.currentTimeMillis()
            lastUpdate = createdAt
        }

        fun resetLocal() {
            prefs.edit().clear().apply()
            life.createEgg();life.created = false
            life.needs[0] = 78f;life.needs[1] = 82f;life.needs[2] = 74f;life.needs[3] = 88f
            name = "Mochi";playerName = "Zoey";kind = PetKind.BUNNY;growth = 0f
            historyJson = "[]";petId = java.util.UUID.randomUUID().toString()
            lastUpdate = System.currentTimeMillis();createdAt = lastUpdate;lastSavedAt = 0L
        }

        fun confirmHatch() = life.confirmHatch()
        fun confirmEvolution() = life.confirmEvolution()
        fun confirmDeath() {
            life.confirmDeath(System.currentTimeMillis())
            if (life.dead) archiveCurrent("old_age")
        }
        fun keepOldPetForever() = life.keepForever()

        private fun history(): org.json.JSONArray = try {
            org.json.JSONArray(historyJson)
        } catch (_: Exception) {
            // Keep a recoverable copy rather than deleting unreadable history.
            prefs.edit().putString("history_recovery", historyJson).apply()
            org.json.JSONArray()
        }

        private fun archiveCurrent(reason: String) {
            val items = history()
            for (index in 0 until items.length()) if (items.getJSONObject(index).optString("id") == petId) return
            items.put(JSONObject().apply {
                put("id", petId);put("name", name);put("kind", kind.name)
                put("ageMillis", life.ageMillis);put("carePercent", carePercent)
                put("createdAt", createdAt);put("endedAt", if (dead) life.diedAt else System.currentTimeMillis())
                put("reason", reason);put("generation", generation)
                put("snapshot", JSONObject(cloudData().filterKeys { it != "historyJson" }))
            })
            historyJson = items.toString()
        }

        fun memories(): List<String> {
            val items = history()
            return (items.length() - 1 downTo 0).map { index ->
                val item = items.getJSONObject(index)
                val days = item.optLong("ageMillis") / PetGrowth.DAY_MILLIS
                val ending = if (item.optString("reason") == "old_age") "Old age" else "Retired"
                "${item.optString("name")} • ${item.optString("kind").lowercase()} • ${days}d • $ending"
            }
        }

        fun mergeHistory(remote: String?) {
            if (remote == null) return
            val merged = history()
            try {
                val incoming = org.json.JSONArray(remote)
                for (index in 0 until incoming.length()) {
                    val item = incoming.optJSONObject(index) ?: continue
                    if ((0 until merged.length()).none { merged.getJSONObject(it).optString("id") == item.optString("id") }) merged.put(item)
                }
                historyJson = merged.toString()
                prefs.edit().putString("history_json", historyJson).apply()
            } catch (_: Exception) {
                prefs.edit().putString("history_remote_recovery", remote).apply()
            }
        }

        fun cloudData(): Map<String, Any> = mapOf(
            "hunger" to hunger.toDouble(), "joy" to joy.toDouble(), "energy" to energy.toDouble(), "clean" to clean.toDouble(),
            "growth" to growth.toDouble(), "generation" to generation.toLong(), "ageMillis" to life.ageMillis,
            "goodCareMillis" to life.goodCareMillis, "totalCareMillis" to life.totalCareMillis,
            "name" to name, "playerName" to playerName, "kind" to kind.name, "created" to created, "hatched" to hatched,
            "lastUpdate" to lastUpdate, "savedAt" to lastSavedAt,
            "eggAgeMillis" to life.eggAgeMillis, "eggProgressMillis" to life.eggProgressMillis.toLong(),
            "evolutionMillis" to life.evolutionMillis.toLong(), "adultAgeMillis" to life.adultAgeMillis,
            "adultClockReady" to life.adultClockReady,
            "oldAgeDeclined" to life.oldAgeDeclined,
            "sick" to life.sick, "symptom" to life.symptom.toLong(),
            "poorCareMillis" to life.poorCareMillis, "lowNeedMillis" to life.lowNeedMillis, "sickAt" to life.sickAt,
            "dead" to dead, "diedAt" to life.diedAt, "petId" to petId, "createdAt" to createdAt, "historyJson" to historyJson
        )

        fun hasCreatedPet(): Boolean = created

        fun loadCloud(data: Map<String, Any>) {
            fun number(key: String, fallback: Float): Float = (data[key] as? Number)?.toFloat() ?: fallback
            fun long(key: String, fallback: Long): Long = (data[key] as? Number)?.toLong() ?: fallback
            life.needs[0] = number("hunger", hunger).coerceIn(0f, 100f)
            life.needs[1] = number("joy", joy).coerceIn(0f, 100f)
            life.needs[2] = number("energy", energy).coerceIn(0f, 100f)
            life.needs[3] = number("clean", clean).coerceIn(0f, 100f)
            growth = number("growth", growth).coerceIn(0f, 100f)
            life.generation = long("generation", generation.toLong()).toInt().coerceIn(0, 2)
            life.ageMillis = long("ageMillis", life.ageMillis).coerceAtLeast(0)
            life.goodCareMillis = long("goodCareMillis", life.goodCareMillis).coerceAtLeast(0)
            life.totalCareMillis = long("totalCareMillis", life.totalCareMillis).coerceAtLeast(life.goodCareMillis)
            name = (data["name"] as? String)?.take(14)?.ifBlank { name } ?: name
            playerName = (data["playerName"] as? String)?.take(20)?.ifBlank { playerName } ?: playerName
            (data["kind"] as? String)?.let { value -> kind = PetKind.values().firstOrNull { it.name == value } ?: kind }
            life.created = data["created"] as? Boolean ?: created
            life.hatched = data["hatched"] as? Boolean ?: hatched
            life.dead = data["dead"] as? Boolean ?: false
            life.diedAt = long("diedAt", 0)
            life.eggAgeMillis = long("eggAgeMillis", 0)
            life.eggProgressMillis = long("eggProgressMillis", 0).toDouble()
            life.evolutionMillis = if (data.containsKey("evolutionMillis")) long("evolutionMillis", 0).toDouble()
                else PetGrowth.migrateEvolution(generation, life.ageMillis, life.carePercent())
            life.adultAgeMillis = long("adultAgeMillis", 0)
            life.adultClockReady = data["adultClockReady"] as? Boolean ?: (generation < 2 || data.containsKey("adultAgeMillis"))
            life.oldAgeDeclined = data["oldAgeDeclined"] as? Boolean ?: false
            life.sick = data["sick"] as? Boolean ?: false
            life.symptom = long("symptom", PetLife.NO_SYMPTOM.toLong()).toInt()
                .coerceIn(PetLife.NO_SYMPTOM, PetLife.ITCHY)
            life.poorCareMillis = long("poorCareMillis", 0L).coerceAtLeast(0)
            life.lowNeedMillis = long("lowNeedMillis", 0L).coerceAtLeast(0)
            life.sickAt = long("sickAt", 0L).coerceAtLeast(0)
            lastUpdate = long("lastUpdate", lastUpdate)
            lastSavedAt = long("savedAt", lastSavedAt)
            petId = data["petId"] as? String ?: petId
            createdAt = long("createdAt", lastUpdate - life.ageMillis - life.eggAgeMillis)
            // Merge memorials by pet id so restoring an older backup cannot
            // discard locally recorded history.
            mergeHistory(data["historyJson"] as? String)
            life.hatchEvent = false;life.evolutionEvent = false;life.deathEvent = false
        }

        fun save() {
            lastSavedAt = System.currentTimeMillis()
            prefs.edit()
                .putFloat("hunger", hunger).putFloat("joy", joy).putFloat("energy", energy).putFloat("clean", clean)
                .putFloat("growth", growth).putInt("generation", generation)
                .putLong("age_millis", life.ageMillis).putLong("good_care_millis", life.goodCareMillis).putLong("total_care_millis", life.totalCareMillis)
                .putString("name", name).putString("player_name", playerName).putString("kind", kind.name).putBoolean("created", created).putBoolean("hatched", hatched)
                .putLong("last_update", lastUpdate).putLong("saved_at", lastSavedAt)
                .putLong("egg_age_millis", life.eggAgeMillis).putLong("egg_progress_millis", life.eggProgressMillis.toLong())
                .putLong("evolution_millis", life.evolutionMillis.toLong()).putLong("adult_age_millis", life.adultAgeMillis)
                .putBoolean("adult_clock_ready", life.adultClockReady)
                .putBoolean("old_age_declined", life.oldAgeDeclined)
                .putBoolean("sick", life.sick).putInt("symptom", life.symptom)
                .putLong("poor_care_millis", life.poorCareMillis).putLong("low_need_millis", life.lowNeedMillis).putLong("sick_at", life.sickAt)
                .putBoolean("dead", dead).putLong("died_at", life.diedAt)
                .putString("pet_id", petId).putLong("created_at", createdAt).putString("history_json", historyJson)
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
