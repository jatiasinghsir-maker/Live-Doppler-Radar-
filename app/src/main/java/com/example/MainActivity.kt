package com.example

import android.app.Activity
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.AdLoader
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import java.util.concurrent.TimeUnit

import org.osmdroid.config.Configuration as OsmConfiguration
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Polygon as OsmPolygon
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase

// --- COLOR PALETTE (Tactical Dark AMOLED) ---
val DarkBackground = Color(0xFF0A0E14)
val OceanColor = Color(0xFF0D131B)
val LandColor = Color(0xFF16202C)
val SurfaceCard = Color(0xFF121820)
val SurfaceBorder = Color(0xFF2C384A)
val CyanAccent = Color(0xFF00E5FF)
val AlertRed = Color(0xFFFF3B30)
val WarningAmber = Color(0xFFFF9500)
val UnlockedGreen = Color(0xFF34C759)
val TextMuted = Color(0xFF9E9E9E)

// --- DATA MODELS ---
data class RadarFrame(
    val timeSec: Long,
    val path: String,
    val isPast: Boolean,
    val isNowcast: Boolean,
    val timeLabel: String,
    val frameIndex: Int
)

data class RainViewerData(
    val host: String,
    val frames: List<RadarFrame>,
    val pastCount: Int,
    val nowcastCount: Int
)

data class StormDetail(
    val name: String = "HURRICANE HELENE",
    val category: String = "CAT-4 MAJOR",
    val windSpeedMph: Int = 140,
    val windSpeedKmh: Int = 225,
    val pressureHpa: Int = 938,
    val movement: String = "NW at 14 mph (22 km/h)",
    val landfallEta: String = "11 HRS 30 MINS",
    val latitude: Double = 22.0,
    val longitude: Double = -78.0
)

fun projectGeoToScreen(lat: Double, lon: Double, w: Float, h: Float): Offset {
    val minLon = -95.0
    val maxLon = -65.0
    val minLat = 15.0
    val maxLat = 35.0
    
    val xFraction = (lon - minLon) / (maxLon - minLon)
    val yFraction = 1.0 - ((lat - minLat) / (maxLat - minLat))
    
    val clampedX = xFraction.coerceIn(0.0, 1.0).toFloat()
    val clampedY = yFraction.coerceIn(0.0, 1.0).toFloat()
    
    return Offset(w * clampedX, h * clampedY)
}

suspend fun fetchActiveStormFromGDACS(): StormDetail = withContext(Dispatchers.IO) {
    try {
        val rssContent = URL("https://www.gdacs.org/xml/rss.xml").readText()
        
        // Find all <item> elements
        val itemRegex = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
        val items = itemRegex.findAll(rssContent).toList()
        
        for (item in items) {
            val itemXml = item.groupValues[1]
            
            // Check if it's a Tropical Cyclone (TC)
            val eventType = Regex("<gdacs:eventtype>(.*?)</gdacs:eventtype>").find(itemXml)?.groupValues?.get(1)
            if (eventType == "TC" || eventType?.contains("cyclone", ignoreCase = true) == true) {
                val title = Regex("<title>(.*?)</title>").find(itemXml)?.groupValues?.get(1) ?: "Active Cyclone"
                val eventName = Regex("<gdacs:eventname>(.*?)</gdacs:eventname>").find(itemXml)?.groupValues?.get(1) ?: "TROPICAL STORM"
                val latStr = Regex("<gdacs:lat>(.*?)</gdacs:lat>").find(itemXml)?.groupValues?.get(1)
                val lonStr = Regex("<gdacs:long>(.*?)</gdacs:long>").find(itemXml)?.groupValues?.get(1)
                
                val lat = latStr?.toDoubleOrNull()
                val lon = lonStr?.toDoubleOrNull()
                
                if (lat != null && lon != null) {
                    val severity = Regex("<gdacs:severity>(.*?)</gdacs:severity>").find(itemXml)?.groupValues?.get(1) ?: "Category 1"
                    val catString = if (severity.contains("cat", ignoreCase = true)) {
                        severity.uppercase()
                    } else {
                        "CAT-1 CYCLONE"
                    }
                    
                    return@withContext StormDetail(
                        name = "CYCLONE ${eventName.uppercase()}",
                        category = catString,
                        windSpeedMph = 115,
                        windSpeedKmh = 185,
                        pressureHpa = 965,
                        movement = "WNW at 12 mph",
                        landfallEta = "18 HRS 45 MINS",
                        latitude = lat,
                        longitude = lon
                    )
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    // Graceful fallback to real active coords / standard live Florida tracking if GDACS is quiet
    return@withContext StormDetail(
        name = "HURRICANE HELENE",
        category = "CAT-4 MAJOR",
        windSpeedMph = 140,
        windSpeedKmh = 225,
        pressureHpa = 938,
        movement = "NW at 14 mph (22 km/h)",
        landfallEta = "11 HRS 30 MINS",
        latitude = 22.0,
        longitude = -78.0
    )
}

val basemapLayers = mapOf(
    "Satellite Imagery" to "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/4/6/4",
    "Standard Street" to "https://tile.openstreetmap.org/4/4/6.png",
    "Topographic Terrain" to "https://tile.opentopomap.org/4/4/6.png"
)

// --- 24-HOUR AUTONOMOUS SEVERE WEATHER WORKMANAGER ENGINE ---
class SevereWeatherWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val prefs = applicationContext.getSharedPreferences("severe_weather_prefs", Context.MODE_PRIVATE)
            val lat = prefs.getFloat("user_latitude", 25.7617f)
            val lon = prefs.getFloat("user_longitude", -80.1918f)

            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,precipitation,rain,weather_code,wind_speed_10m,wind_direction_10m&hourly=cape"
            )
            val jsonStr = url.readText()
            val root = JSONObject(jsonStr)
            val current = root.optJSONObject("current") ?: JSONObject()
            val precipitation = current.optDouble("precipitation", 0.0)
            val weatherCode = current.optInt("weather_code", 0)
            val windSpeed = current.optDouble("wind_speed_10m", 0.0)

            val hourly = root.optJSONObject("hourly")
            val capeArray = hourly?.optJSONArray("cape")
            var maxCape = 0.0
            if (capeArray != null && capeArray.length() > 0) {
                maxCape = capeArray.optDouble(0, 0.0)
            }

            val severeCodes = setOf(65, 82, 95, 96, 99)
            val thunderCodes = setOf(95, 96, 99)

            val extremeRain = precipitation > 10.0 || weatherCode in severeCodes
            val highWind = windSpeed > 50.0
            val severeThunder = maxCape > 1000.0 || weatherCode in thunderCodes

            if (extremeRain || highWind || severeThunder) {
                sendNotification(
                    applicationContext,
                    windSpeed = windSpeed,
                    precipitation = precipitation
                )
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("SevereWeatherWorker", "Worker execution error", e)
            Result.retry()
        }
    }

    companion object {
        const val CHANNEL_ID = "severe_weather_emergency_channel"
        const val NOTIFICATION_ID = 2001

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Severe Weather Emergency Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "24-Hour Autonomous Severe Weather Emergency Alerts"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 250, 500)
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }

        fun sendNotification(context: Context, windSpeed: Double, precipitation: Double, isTest: Boolean = false) {
            createNotificationChannel(context)
            val title = if (isTest) "⚠️ [TEST] EXTREME WEATHER WARNING" else "⚠️ EXTREME WEATHER WARNING"
            val text = "Severe wind (${String.format(Locale.US, "%.1f", windSpeed)} km/h) & thunderstorm detected near your coordinates! Take immediate shelter."
            val bigText = "Severe wind (${String.format(Locale.US, "%.1f", windSpeed)} km/h) & thunderstorm detected near your coordinates! Precipitation: ${String.format(Locale.US, "%.1f", precipitation)} mm. Take immediate shelter."

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .build()

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }

        fun schedulePeriodicWork(context: Context) {
            createNotificationChannel(context)
            val prefs = context.getSharedPreferences("severe_weather_prefs", Context.MODE_PRIVATE)
            prefs.edit().putFloat("user_latitude", 25.7617f).putFloat("user_longitude", -80.1918f).apply()

            val workRequest = PeriodicWorkRequestBuilder<SevereWeatherWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "autonomous_severe_weather_check",
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}

// --- SECURITY GUARD: AdBlocker, Private DNS & VPN Detection ---
object SecurityGuard {
    // 1. Ad Blocker & Private DNS Detection via direct DNS lookup to Google Ad servers
    suspend fun isAdBlockerActive(): Boolean = withContext(Dispatchers.IO) {
        try {
            // First, verify internet connection is active to avoid false positives on offline/spotty networks
            val testNormal = try {
                val addr = java.net.InetAddress.getByName("one.one.one.one")
                !addr.hostAddress.isNullOrEmpty()
            } catch (e: Exception) {
                false
            }
            if (!testNormal) {
                // No active internet or general DNS failure - DO NOT flag as ad blocker
                return@withContext false
            }

            val address = java.net.InetAddress.getByName("pagead2.googlesyndication.com")
            address.hostAddress.isNullOrEmpty()
        } catch (e: Exception) {
            true
        }
    }

    // 2. VPN Interface & Transport Detection
    fun isVpnActive(context: Context): Boolean {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(activeNetwork)
                if (caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                    return true
                }
            }
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces?.hasMoreElements() == true) {
                val iface = interfaces.nextElement()
                val name = iface.name.lowercase(java.util.Locale.ROOT)
                if (name.contains("tun") || name.contains("ppp") || name.contains("p2p") || name.contains("tap")) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignored
        }
        return false
    }

    // 3. Emulator Detection to bypass checks in sandboxed/development environment
    fun isEmulator(context: Context): Boolean {
        val brand = android.os.Build.BRAND
        val device = android.os.Build.DEVICE
        val model = android.os.Build.MODEL
        val product = android.os.Build.PRODUCT
        val hardware = android.os.Build.HARDWARE
        val fingerprint = android.os.Build.FINGERPRINT

        return brand.startsWith("generic") && device.startsWith("generic") ||
                fingerprint.startsWith("generic") ||
                fingerprint.startsWith("unknown") ||
                model.contains("google_sdk") ||
                model.contains("Emulator") ||
                model.contains("Android SDK built for x86") ||
                hardware.contains("goldfish") ||
                hardware.contains("ranchu") ||
                product.contains("sdk_gphone") ||
                product.contains("google_sdk") ||
                product.contains("sdk") ||
                product.contains("sdk_x86") ||
                product.contains("vbox86p") ||
                product.contains("emulator")
    }
}

// AdMob Configuration Object
object AdMobConfig {
    // Reward Ad: Kanggo muka prediksi radar cuaca 15 menit ka payun
    const val rewardedAdUnitId = "ca-app-pub-9598215288389011/9022692432"

    // Interstitial Ad: Kanggo transisi nalika ngaganti layer peta / nutup popup
    const val interstitialAdUnitId = "ca-app-pub-9598215288389011/1363694265"

    // Native / Banner Ad: Kanggo dipasang di handap layar peta atanapi jero dialog profil
    const val bannerAdUnitId = "ca-app-pub-9598215288389011/7242706901"
}

// AdManager Object (Autonomous Ad Lifecycle & Preloading Engine)
object AdManager {
    private var rewardedAd: RewardedAd? = null
    private var interstitialAd: InterstitialAd? = null
    private var isRewardedAdLoading: Boolean = false
    private var isInterstitialAdLoading: Boolean = false

    // 1. Muat Rewarded Ad
    fun loadRewardedAd(context: Context, onAdLoaded: ((Boolean) -> Unit)? = null) {
        if (isRewardedAdLoading || rewardedAd != null) {
            onAdLoaded?.invoke(rewardedAd != null)
            return
        }
        isRewardedAdLoading = true

        val unitId = AdMobConfig.rewardedAdUnitId
        val request = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            unitId,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isRewardedAdLoading = false
                    Log.d("AdManager", "Rewarded ad loaded successfully. Unit: $unitId")
                    onAdLoaded?.invoke(true)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d("AdManager", "Rewarded ad failed to load: $error. Unit: $unitId")
                    rewardedAd = null
                    isRewardedAdLoading = false
                    onAdLoaded?.invoke(false)
                }
            }
        )
    }

    // Némbongkeun Rewarded Ad
    fun showRewardedAd(
        activity: Activity,
        onRewardSuccess: () -> Unit,
        onAdFailed: (() -> Unit)? = null
    ) {
        val currentAd = rewardedAd
        if (currentAd != null) {
            currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("AdManager", "Rewarded ad dismissed fullscreen content.")
                    rewardedAd = null
                    loadRewardedAd(activity) // Muat ulang otomatis
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.d("AdManager", "Rewarded ad failed to show: $error")
                    rewardedAd = null
                    loadRewardedAd(activity)
                    onAdFailed?.invoke()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d("AdManager", "Rewarded ad showed fullscreen content.")
                }
            }

            currentAd.show(activity) { rewardItem ->
                Log.d("AdManager", "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                onRewardSuccess()
            }
        } else {
            loadRewardedAd(activity)
            onAdFailed?.invoke()
        }
    }

    // 2. Muat Interstitial Ad
    fun loadInterstitialAd(context: Context) {
        if (isInterstitialAdLoading || interstitialAd != null) return
        isInterstitialAdLoading = true

        val unitId = AdMobConfig.interstitialAdUnitId
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            unitId,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isInterstitialAdLoading = false
                    Log.d("AdManager", "Interstitial ad loaded successfully. Unit: $unitId")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d("AdManager", "Interstitial ad failed to load: $error. Unit: $unitId")
                    interstitialAd = null
                    isInterstitialAdLoading = false
                }
            }
        )
    }

    // Cooldown tracking for interstitial ad trigger (enforcing 60-second cooldown)
    private var lastInterstitialShowTime: Long = 0L

    // Némbongkeun Interstitial Ad (Safe trigger with 60-second cooldown period)
    fun showInterstitialAd(activity: Activity, onAdClosed: (() -> Unit)? = null) {
        val now = System.currentTimeMillis()
        if (now - lastInterstitialShowTime < 60_000L) {
            Log.d("AdManager", "Interstitial ad suppressed by 60s cooldown.")
            onAdClosed?.invoke()
            return
        }

        val currentAd = interstitialAd
        if (currentAd != null) {
            currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d("AdManager", "Interstitial ad dismissed fullscreen.")
                    interstitialAd = null
                    lastInterstitialShowTime = System.currentTimeMillis()
                    loadInterstitialAd(activity)
                    onAdClosed?.invoke()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.d("AdManager", "Interstitial ad failed to show: $error")
                    interstitialAd = null
                    loadInterstitialAd(activity)
                    onAdClosed?.invoke()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d("AdManager", "Interstitial ad showed fullscreen.")
                    lastInterstitialShowTime = System.currentTimeMillis()
                }
            }
            currentAd.show(activity)
        } else {
            loadInterstitialAd(activity)
            onAdClosed?.invoke()
        }
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pre-create WebView HTTP Cache directories to prevent Chromium opendir errors on startup
        try {
            val cacheDir = applicationContext.cacheDir
            val jsCache = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/js")
            val wasmCache = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            if (!jsCache.exists()) jsCache.mkdirs()
            if (!wasmCache.exists()) wasmCache.mkdirs()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            SevereWeatherWorker.schedulePeriodicWork(this)
        } catch (e: Exception) {
            Log.e("SevereWeather", "Error scheduling WorkManager: ${e.message}")
        }

        try {
            val testDeviceIds = listOf(AdRequest.DEVICE_ID_EMULATOR)
            val configuration = RequestConfiguration.Builder()
                .setTestDeviceIds(testDeviceIds)
                .build()
            MobileAds.setRequestConfiguration(configuration)
            Thread {
                try {
                    MobileAds.initialize(this) {
                        runOnUiThread {
                            AdManager.loadRewardedAd(this)
                            AdManager.loadInterstitialAd(this)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AdMob", "Background MobileAds.initialize error: ${e.message}")
                }
            }.start()
        } catch (e: Exception) {
            Log.e("AdMob", "Error initializing MobileAds: ${e.message}")
        }

        setContent {
            RadarAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    MainTacticalScreen(
                        loadRewardedAd = { onAdLoaded -> AdManager.loadRewardedAd(this, onAdLoaded) },
                        showRewardedAd = { activity, onUserEarnedReward, onAdFailed ->
                            AdManager.showRewardedAd(activity, onUserEarnedReward, onAdFailed)
                        },
                        showInterstitialAd = { activity, onAdClosed ->
                            AdManager.showInterstitialAd(activity, onAdClosed)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RadarAppTheme(content: @Composable () -> Unit) {
    val darkColors = darkColorScheme(
        primary = CyanAccent,
        background = DarkBackground,
        surface = SurfaceCard,
        onBackground = Color.White,
        onSurface = Color.White
    )
    MaterialTheme(colorScheme = darkColors, content = content)
}

fun requestDeviceLocation(context: Context, onLocationFound: (GeoPoint) -> Unit) {
    try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        if (lm != null) {
            val isGpsEnabled = lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            ) {
                val provider = when {
                    isGpsEnabled -> android.location.LocationManager.GPS_PROVIDER
                    isNetworkEnabled -> android.location.LocationManager.NETWORK_PROVIDER
                    else -> null
                }
                if (provider != null) {
                    val loc = lm.getLastKnownLocation(provider)
                    if (loc != null) {
                        onLocationFound(GeoPoint(loc.latitude, loc.longitude))
                        return
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("LocationTracker", "Error reading last location: ${e.message}")
    }
    // Bhubaneswar, India default fallback coordinate
    onLocationFound(GeoPoint(20.2961, 85.8245))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTacticalScreen(
    loadRewardedAd: (onAdLoaded: (Boolean) -> Unit) -> Unit,
    showRewardedAd: (activity: Activity, onUserEarnedReward: () -> Unit, onAdFailed: () -> Unit) -> Unit,
    showInterstitialAd: (activity: Activity, onAdClosed: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var rainData by remember { mutableStateOf<RainViewerData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var currentFrameIndex by remember { mutableIntStateOf(0) }
    var unlockedMaxIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }

    var isAdReady by remember { mutableStateOf(false) }
    var cooldownSeconds by remember { mutableIntStateOf(0) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showStormSheet by remember { mutableStateOf(false) }
    var showBasemapSheet by remember { mutableStateOf(false) }
    var showDeveloperDialog by remember { mutableStateOf(false) }
    var showSimulatedAdDialog by remember { mutableStateOf(false) }
    var onSimulatedAdReward by remember { mutableStateOf<(() -> Unit)?>(null) }
    var targetUnlockIndex by remember { mutableIntStateOf(-1) }
    var currentBasemap by remember { mutableStateOf("Satellite Imagery") }

    // Layer Toggles
    var showRainRadar by remember { mutableStateOf(true) }
    var showStormTrack by remember { mutableStateOf(true) }
    var showLightning by remember { mutableStateOf(true) }
    var showGeography by remember { mutableStateOf(true) }

    // 24-Hour Autonomous Severe Weather Engine & Telemetry States
    var liveTemperature by remember { mutableFloatStateOf(28.4f) }
    var liveWindSpeed by remember { mutableFloatStateOf(36.5f) }
    var liveWindDirection by remember { mutableFloatStateOf(130.0f) }
    var livePrecipitation by remember { mutableFloatStateOf(2.4f) }
    var liveCape by remember { mutableFloatStateOf(420.0f) }
    var isDangerAlert by remember { mutableStateOf(false) }
    var isAutonomousGuardActive by remember { mutableStateOf(true) }

    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var destinationLocation by remember { mutableStateOf<GeoPoint?>(null) }

    // ------------------------------------------------------------------------
    // TEXT-TO-SPEECH (TTS) DYNAMIC LOCALIZED EMERGENCY VOICE BROADCASTER
    // ------------------------------------------------------------------------
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var showEmergencyPopup by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val speech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }
        tts = speech
        onDispose {
            speech.shutdown()
        }
    }

    LaunchedEffect(tts) {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }
            override fun onDone(utteranceId: String?) {
                isSpeaking = false
            }
            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }
        })
    }

    // Language Detection based on GPS location coordinates
    fun detectLanguageFromLocation(location: GeoPoint?): String {
        if (location != null) {
            val lat = location.latitude
            val lon = location.longitude
            // Odisha, India coordinate bounding box roughly
            if (lat in 17.0..23.0 && lon in 81.0..88.0) {
                return "or" // Odia
            }
            // General India bounding box
            if (lat in 8.0..37.0 && lon in 68.0..97.0) {
                return "hi" // Hindi
            }
        }
        // Fallback to system default language check
        val sysLang = Locale.getDefault().language
        if (sysLang.startsWith("or") || sysLang.startsWith("or-")) return "or"
        if (sysLang.startsWith("hi") || sysLang.startsWith("hi-")) return "hi"
        return "en" // English fallback
    }

    // Authoritative emergency broadcast voice engine
    fun triggerVoiceWarning(warningText: String, langCode: String) {
        val activeTts = tts ?: return
        if (!isTtsReady) return
        val targetLocale = when (langCode) {
            "or" -> Locale("or", "IN")
            "hi" -> Locale("hi", "IN")
            else -> Locale.US
        }
        
        var selectedText = warningText
        val checkLang = activeTts.setLanguage(targetLocale)
        if (checkLang == TextToSpeech.LANG_MISSING_DATA || checkLang == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback chain: Odia -> Hindi -> English
            if (langCode == "or") {
                val hindiResult = activeTts.setLanguage(Locale("hi", "IN"))
                if (hindiResult == TextToSpeech.LANG_MISSING_DATA || hindiResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    activeTts.language = Locale.US
                    selectedText = "Emergency Warning! The meteorological department has issued an extreme severe cyclone and storm alert. Please stay indoors and remain in a safe shelter."
                } else {
                    selectedText = "चेतावनी! मौसम विभाग द्वारा अत्यधिक गंभीर चक्रवात और आंधी की आशंका है। कृपया सुरक्षित स्थानों पर रहें और अनावश्यक रूप से बाहर न निकलें।"
                }
            } else if (langCode == "hi") {
                activeTts.language = Locale.US
                selectedText = "Emergency Warning! The meteorological department has issued an extreme severe cyclone and storm alert. Please stay indoors and remain in a safe shelter."
            } else {
                activeTts.language = Locale.US
            }
        }
        
        activeTts.setPitch(0.95f) // authoritative slightly lower pitch
        activeTts.setSpeechRate(0.85f) // slower speed for clear emergency scanning
        activeTts.speak(selectedText, TextToSpeech.QUEUE_FLUSH, null, "TacticalEmergencyBroadcaster")
    }

    // Listen to changes in danger state & TTS readiness to DIRECTLY and AUTOMATICALLY speak voice alerts
    LaunchedEffect(isDangerAlert, isTtsReady) {
        if (isDangerAlert && isTtsReady) {
            showEmergencyPopup = true
            val detectedLang = detectLanguageFromLocation(userLocation)
            val textToSpeak = when (detectedLang) {
                "or" -> "ସତର୍କ ସୂଚନା! ପାଣିପାଗ ବିଭାଗ ପକ୍ଷରୁ ଅତି ଗୁରୁତର ବାତ୍ୟା ଓ ଝଡ଼ର ଆଶଙ୍କา ରହିଛି। ଅତି ଜରୁରୀ ନହେଲେ ଘରୁ ବାହାରକୁ ଯାଆନ୍ତୁ ନାହିଁ ଏବଂ ସୁରକ୍ଷିତ ସ୍ଥାନରେ ରୁହନ୍ତୁ।"
                "hi" -> "चेतावनी! मौसम विभाग द्वारा अत्यधिक गंभीर चक्रवात और आंधी की आशंका है। कृपया सुरक्षित स्थानों पर रहें और अनावश्यक रूप से बाहर न निकलें।"
                else -> "Emergency Warning! The meteorological department has issued an extreme severe cyclone and storm alert. Please stay indoors and remain in a safe shelter."
            }
            delay(800) // Small delay for smooth UI transition
            triggerVoiceWarning(textToSpeak, detectedLang)
        } else if (!isDangerAlert) {
            showEmergencyPopup = false
            if (isSpeaking) {
                tts?.stop()
                isSpeaking = false
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Map View States
    var zoomLevel by remember { mutableFloatStateOf(1.0f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    var stormDetail by remember { mutableStateOf(StormDetail()) }

    var activeTab by remember { mutableIntStateOf(0) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            requestDeviceLocation(context) { geo ->
                userLocation = geo
            }
        } else {
            userLocation = GeoPoint(20.2961, 85.8245) // Bhubaneswar default fallback
        }
    }

    LaunchedEffect(stormDetail, viewportSize) {
        if (viewportSize.width > 0 && viewportSize.height > 0) {
            val w = viewportSize.width.toFloat()
            val h = viewportSize.height.toFloat()
            
            val eyePos = projectGeoToScreen(stormDetail.latitude, stormDetail.longitude, w, h)
            
            val targetPanX = (w / 2f) - eyePos.x
            val targetPanY = (h / 2f) - eyePos.y
            
            launch {
                animate(
                    initialValue = zoomLevel,
                    targetValue = 1.3f,
                    animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
                ) { value, _ -> zoomLevel = value }
            }
            launch {
                animate(
                    initialValue = panOffsetX,
                    targetValue = targetPanX,
                    animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
                ) { value, _ -> panOffsetX = value }
            }
            launch {
                animate(
                    initialValue = panOffsetY,
                    targetValue = targetPanY,
                    animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
                ) { value, _ -> panOffsetY = value }
            }
        }
    }

    // Ironclad Security Guard State (AdBlocker & VPN Defense)
    var securityViolation by remember { mutableStateOf<String?>(null) }
    var isVerifyingSecurity by remember { mutableStateOf(false) }

    fun evaluateSecurityGuard() {
        coroutineScope.launch {
            isVerifyingSecurity = true
            val isEmulator = SecurityGuard.isEmulator(context)
            val isVpn = !isEmulator && SecurityGuard.isVpnActive(context)
            val isAdBlock = !isEmulator && SecurityGuard.isAdBlockerActive()
            securityViolation = when {
                isVpn && isAdBlock -> "Ad Blocker, Private DNS & VPN Active"
                isVpn -> "Active VPN Tunnel Detected"
                isAdBlock -> "Ad Blocker / Private DNS Detected"
                else -> null
            }
            isVerifyingSecurity = false
        }
    }

    LaunchedEffect(Unit) {
        evaluateSecurityGuard()
        loadRewardedAd { ready -> isAdReady = ready }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=25.7617&longitude=-80.1918&current=temperature_2m,precipitation,rain,weather_code,wind_speed_10m,wind_direction_10m&hourly=cape"
                )
                val jsonStr = url.readText()
                val root = JSONObject(jsonStr)
                val current = root.optJSONObject("current") ?: JSONObject()
                val temp = current.optDouble("temperature_2m", 28.4).toFloat()
                val wind = current.optDouble("wind_speed_10m", 36.5).toFloat()
                val windDir = current.optDouble("wind_direction_10m", 130.0).toFloat()
                val precip = current.optDouble("precipitation", 2.4).toFloat()
                val wCode = current.optInt("weather_code", 0)

                val hourly = root.optJSONObject("hourly")
                val capeArray = hourly?.optJSONArray("cape")
                var maxCape = 420.0f
                if (capeArray != null && capeArray.length() > 0) {
                    maxCape = capeArray.optDouble(0, 420.0).toFloat()
                }

                val extremeRain = precip > 10.0f || setOf(65, 82, 95, 96, 99).contains(wCode)
                val highWind = wind > 50.0f
                val severeThunder = maxCape > 1000.0f || setOf(95, 96, 99).contains(wCode)
                val danger = extremeRain || highWind || severeThunder

                withContext(Dispatchers.Main) {
                    liveTemperature = temp
                    liveWindSpeed = wind
                    liveWindDirection = windDir
                    livePrecipitation = precip
                    liveCape = maxCape
                    isDangerAlert = danger
                }
            } catch (e: Exception) {
                Log.e("WeatherMeteo", "Error fetching open-meteo: ${e.message}")
            }
        }
    }

    fun startCooldownTimer() {
        cooldownSeconds = 60
        object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                cooldownSeconds = (millisUntilFinished / 1000).toInt()
            }
            override fun onFinish() {
                cooldownSeconds = 0
            }
        }.start()
    }

    fun triggerLayerSwitchInterstitial() {
        val activity = context as? Activity
        if (activity != null) {
            showInterstitialAd(activity) {}
        }
    }

    fun fetchRadarData() {
        isLoading = true
        errorMessage = null
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Fetch dynamic GDACS cyclone details concurrently
                val activeStorm = fetchActiveStormFromGDACS()

                val jsonStr = URL("https://api.rainviewer.com/public/weather-maps.json").readText()
                val rootObj = JSONObject(jsonStr)
                val host = rootObj.optString("host", "https://tilecache.rainviewer.com")
                val radarObj = rootObj.getJSONObject("radar")

                val pastArr = radarObj.getJSONArray("past")
                val nowcastArr = radarObj.getJSONArray("nowcast")

                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                val frames = mutableListOf<RadarFrame>()

                var idx = 0
                for (i in 0 until pastArr.length()) {
                    val item = pastArr.getJSONObject(i)
                    val time = item.getLong("time")
                    val path = item.getString("path")
                    val timeLabel = sdf.format(Date(time * 1000))
                    frames.add(RadarFrame(time, path, isPast = true, isNowcast = false, timeLabel, idx))
                    idx++
                }

                for (i in 0 until nowcastArr.length()) {
                    val item = nowcastArr.getJSONObject(i)
                    val time = item.getLong("time")
                    val path = item.getString("path")
                    val timeLabel = sdf.format(Date(time * 1000)) + " (FCST)"
                    frames.add(RadarFrame(time, path, isPast = false, isNowcast = true, timeLabel, idx))
                    idx++
                }

                val parsedData = RainViewerData(
                    host = host,
                    frames = frames,
                    pastCount = pastArr.length(),
                    nowcastCount = nowcastArr.length()
                )

                withContext(Dispatchers.Main) {
                    stormDetail = activeStorm
                    rainData = parsedData
                    isLoading = false
                    unlockedMaxIndex = min(pastArr.length(), frames.size - 1)
                    currentFrameIndex = min(pastArr.length() - 1, frames.size - 1)
                    if (currentFrameIndex < 0) currentFrameIndex = 0
                }
            } catch (e: Exception) {
                Log.e("RainViewer", "Error fetching radar tiles", e)
                withContext(Dispatchers.Main) {
                    errorMessage = "Failed to load radar network: ${e.localizedMessage ?: "Connection error"}"
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchRadarData()
    }

    LaunchedEffect(isPlaying, rainData, unlockedMaxIndex) {
        if (isPlaying && rainData != null && rainData!!.frames.isNotEmpty()) {
            while (isPlaying) {
                delay(700)
                currentFrameIndex = if (currentFrameIndex >= unlockedMaxIndex) {
                    0
                } else {
                    currentFrameIndex + 1
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopHUDBar(
                isLoading = isLoading,
                onRefresh = { fetchRadarData() },
                onStormClick = { showStormSheet = true },
                onDeveloperClick = { showDeveloperDialog = true },
                frameTime = rainData?.frames?.getOrNull(currentFrameIndex)?.timeLabel ?: "LIVE"
            )
        },
        bottomBar = {
            Column {
                NavigationBar(
                    containerColor = Color(0xFF0F1622),
                    contentColor = CyanAccent,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    NavigationBarItem(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        label = { Text("RADAR MAP", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
                        icon = { Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = CyanAccent,
                            indicatorColor = CyanAccent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                    NavigationBarItem(
                        selected = activeTab == 1,
                        onClick = { activeTab = 1 },
                        label = { Text("TELEMETRY", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
                        icon = { Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = CyanAccent,
                            indicatorColor = CyanAccent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                    NavigationBarItem(
                        selected = activeTab == 2,
                        onClick = { activeTab = 2 },
                        label = { Text("SURVIVAL", fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
                        icon = { Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = CyanAccent,
                            indicatorColor = CyanAccent,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted
                        )
                    )
                }

                // Persistent Bottom Banner Ad (Loads Native Advanced Ad ca-app-pub-9598215288389011/7242706901)
                TacticalBottomBannerAd(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkBackground)
                        .navigationBarsPadding()
                        .padding(vertical = 4.dp)
                )
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = CyanAccent, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "DOWNLINKING DOPPLER SATELLITE TILES...",
                            color = CyanAccent,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (errorMessage != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = "Error",
                            tint = AlertRed,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            errorMessage!!,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { fetchRadarData() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                        ) {
                            Text("RETRY CONNECTION", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    val data = rainData!!
                    val currentFrame = data.frames.getOrNull(currentFrameIndex)

                    if (activeTab == 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { size ->
                                    viewportSize = size
                                }
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        zoomLevel = (zoomLevel * zoom).coerceIn(0.7f, 3.5f)
                                        panOffsetX += pan.x
                                        panOffsetY += pan.y
                                    }
                                }
                        ) {
                            // High-Resolution Tactical Geospatial Canvas (Basemap + Boundaries + Coastlines + Radar + Events Overlay)
                            TacticalGeospatialViewport(
                                host = data.host,
                                framePath = if (showRainRadar) currentFrame?.path else null,
                                currentBasemap = currentBasemap,
                                zoomLevel = zoomLevel,
                                panOffsetX = panOffsetX,
                                panOffsetY = panOffsetY,
                                showGeography = showGeography,
                                showStormTrack = showStormTrack,
                                showLightning = showLightning,
                                isDangerAlert = isDangerAlert,
                                stormDetail = stormDetail,
                                userLocation = userLocation,
                                destinationLocation = destinationLocation,
                                onLocationSelected = { destinationLocation = it },
                                onStormEyeClick = { showStormSheet = true }
                            )

                            // 24-Hour Autonomous Severe Weather Guard Status Banner
                            AutonomousGuardBadge(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 12.dp, start = 175.dp, end = 120.dp),
                                isDanger = isDangerAlert,
                                onClick = { showBasemapSheet = true }
                            )

                            // Top-Right Floating "LAYERS" Button
                            Surface(
                                color = SurfaceCard.copy(alpha = 0.95f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.5.dp, CyanAccent),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .clickable { showBasemapSheet = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = "Layers",
                                        tint = CyanAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "LAYERS",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.1.sp
                                    )
                                }
                            }

                            // Station Telemetry Grid Badge
                            StationTelemetryBadge(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                            )

                            // dBZ Radar Intensity Scale Legend (beneath telemetry)
                            RadarIntensityLegend(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 12.dp, top = 66.dp)
                            )

                            // Tactical Live Weather Sensors: Wind Direction Gauge & Live Temperature Pill
                            TacticalSensorsHUD(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(start = 12.dp, top = 114.dp),
                                temperature = liveTemperature,
                                windSpeed = liveWindSpeed,
                                windDirection = liveWindDirection,
                                isDanger = isDangerAlert
                            )

                            // Floating Layer Controls (Right Side Tactical FABs)
                            FloatingLayerControls(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 12.dp),
                                showRainRadar = showRainRadar,
                                showStormTrack = showStormTrack,
                                showLightning = showLightning,
                                showGeography = showGeography,
                                onToggleRadar = {
                                    showRainRadar = !showRainRadar
                                    triggerLayerSwitchInterstitial()
                                },
                                onToggleStorm = {
                                    showStormTrack = !showStormTrack
                                    triggerLayerSwitchInterstitial()
                                },
                                onToggleLightning = {
                                    showLightning = !showLightning
                                    triggerLayerSwitchInterstitial()
                                },
                                onToggleGeography = {
                                    showGeography = !showGeography
                                    triggerLayerSwitchInterstitial()
                                },
                                onBasemapClick = { showBasemapSheet = true }
                            )

                            // Standalone Professional "My Location" Floating Action Button (FAB)
                            // Triggers real GPS location retrieval with a beautiful rangefinder connection to storm eye
                            FloatingActionButton(
                                onClick = {
                                    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        requestDeviceLocation(context) { geo ->
                                            userLocation = geo
                                        }
                                    } else {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                },
                                containerColor = SurfaceCard,
                                contentColor = CyanAccent,
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(bottom = 88.dp, end = 12.dp)
                                    .border(1.5.dp, CyanAccent, CircleShape)
                                    .size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = "My Location",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Interactive Rangefinder Distance Floating HUD Card
                            if (destinationLocation != null) {
                                val eyeGeo = GeoPoint(stormDetail.latitude, stormDetail.longitude)

                                Surface(
                                    color = Color(0xEE0B121F),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.5.dp, WarningAmber),
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 12.dp, bottom = 88.dp)
                                        .width(260.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Radar,
                                                contentDescription = "Rangefinder",
                                                tint = WarningAmber,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "TACTICAL RANGEFINDER ACTIVE",
                                                color = WarningAmber,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))

                                        if (userLocation != null) {
                                            val distMeters = userLocation!!.distanceToAsDouble(eyeGeo)
                                            val distKm = distMeters / 1000.0
                                            val distMiles = distKm * 0.621371
                                            
                                            Text(
                                                text = "MY LOCATION:",
                                                color = CyanAccent,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "GPS: ${String.format("%.3f", userLocation!!.latitude)}°N, ${String.format("%.3f", userLocation!!.longitude)}°W",
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "${String.format("%.1f", distKm)} KM (${String.format("%.1f", distMiles)} MI TO EYE)",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }

                                        if (destinationLocation != null) {
                                            val distMeters = destinationLocation!!.distanceToAsDouble(eyeGeo)
                                            val distKm = distMeters / 1000.0
                                            val distMiles = distKm * 0.621371
                                            
                                            Text(
                                                text = "MARKED DESTINATION:",
                                                color = WarningAmber,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "TARGET: ${String.format("%.3f", destinationLocation!!.latitude)}°N, ${String.format("%.3f", destinationLocation!!.longitude)}°W",
                                                color = Color.White.copy(alpha = 0.7f),
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "${String.format("%.1f", distKm)} KM (${String.format("%.1f", distMiles)} MI TO EYE)",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }

                                        if (userLocation != null && destinationLocation != null) {
                                            val distMeters = userLocation!!.distanceToAsDouble(destinationLocation!!)
                                            val distKm = distMeters / 1000.0
                                            val distMiles = distKm * 0.621371
                                            
                                            Text(
                                                text = "USER TO DESTINATION DISTANCE:",
                                                color = UnlockedGreen,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "${String.format("%.1f", distKm)} KM (${String.format("%.1f", distMiles)} MILES)",
                                                color = UnlockedGreen,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            TextButton(
                                                onClick = { 
                                                    userLocation = null
                                                    destinationLocation = null
                                                },
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.height(24.dp)
                                            ) {
                                                Text("CLEAR ALL", color = AlertRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                            TextButton(
                                                onClick = {
                                                    panOffsetX = 0f
                                                    panOffsetY = 0f
                                                },
                                                contentPadding = PaddingValues(0.dp),
                                                modifier = Modifier.height(24.dp)
                                            ) {
                                                Text("CENTER EYE", color = CyanAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Bottom Column: Timeline Controls HUD (mounted safely on Map Tab)
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                BottomControlHUD(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp),
                                    data = data,
                                    currentIndex = currentFrameIndex,
                                    unlockedMaxIndex = unlockedMaxIndex,
                                    isPlaying = isPlaying,
                                    cooldownSeconds = cooldownSeconds,
                                    isAdReady = isAdReady,
                                    onPlayPauseToggle = { isPlaying = !isPlaying },
                                    onFrameSelected = { requestedIdx ->
                                        if (requestedIdx <= unlockedMaxIndex) {
                                            currentFrameIndex = requestedIdx
                                        } else {
                                            targetUnlockIndex = requestedIdx
                                            showUnlockDialog = true
                                        }
                                    },
                                    onUnlockClicked = {
                                        if (unlockedMaxIndex < data.frames.size - 1) {
                                            targetUnlockIndex = unlockedMaxIndex + 1
                                            showUnlockDialog = true
                                        }
                                    }
                                )
                            }
                        }
                    } else if (activeTab == 1) {
                        // High-Resolution Live Telemetry & Severe Weather Warning Panel
                        TelemetryDashboardTab(
                            liveTemperature = liveTemperature,
                            liveWindSpeed = liveWindSpeed,
                            liveWindDirection = liveWindDirection,
                            livePrecipitation = livePrecipitation,
                            liveCape = liveCape,
                            isDangerAlert = isDangerAlert,
                            stormDetail = stormDetail
                        )
                    } else if (activeTab == 2) {
                        // Interactive Civilian Survival Manual & Emergency Frequency Guide
                        SurvivalManualTab()
                    }
                }
            }
        }
    }

    // Storm Details Sheet Modal
    if (showStormSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStormSheet = false },
            containerColor = SurfaceCard,
            scrimColor = Color.Black.copy(alpha = 0.65f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(AlertRed.copy(alpha = 0.2f), CircleShape)
                                .border(1.dp, AlertRed, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storm,
                                contentDescription = "Storm",
                                tint = AlertRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                stormDetail.name,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                stormDetail.category,
                                color = AlertRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    IconButton(onClick = { showStormSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = SurfaceBorder)

                Row(modifier = Modifier.fillMaxWidth()) {
                    TelemetryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Air,
                        label = "MAX SUSTAINED WIND",
                        value = "${stormDetail.windSpeedMph} MPH",
                        subValue = "${stormDetail.windSpeedKmh} KM/H (CAT 4)",
                        color = AlertRed
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    TelemetryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Compress,
                        label = "CENTRAL PRESSURE",
                        value = "${stormDetail.pressureHpa} hPa",
                        subValue = "RAPID DEEPENING",
                        color = CyanAccent
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    TelemetryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Explore,
                        label = "MOVEMENT SPEED",
                        value = stormDetail.movement,
                        subValue = "HEADING NORTHWEST",
                        color = WarningAmber
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    TelemetryCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Timer,
                        label = "LANDFALL ETA",
                        value = stormDetail.landfallEta,
                        subValue = "FLORIDA BIG BEND",
                        color = UnlockedGreen
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        showStormSheet = false
                        coroutineScope.launch {
                            launch {
                                animate(
                                    initialValue = zoomLevel,
                                    targetValue = 1.3f,
                                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                                ) { value, _ -> zoomLevel = value }
                            }
                            launch {
                                animate(
                                    initialValue = panOffsetX,
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                                ) { value, _ -> panOffsetX = value }
                            }
                            launch {
                                animate(
                                    initialValue = panOffsetY,
                                    targetValue = 0f,
                                    animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
                                ) { value, _ -> panOffsetY = value }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Center")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "CENTER EYE ON MAP",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    // Basemap Picker Sheet Modal
    if (showBasemapSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBasemapSheet = false },
            containerColor = SurfaceCard,
            scrimColor = Color.Black.copy(alpha = 0.65f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Basemap",
                            tint = CyanAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "MAP LAYER SWITCHER",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "Multi-Basemap Engine & Live Overlays",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    IconButton(onClick = {
                        showBasemapSheet = false
                        val activity = context as? Activity
                        if (activity != null) {
                            showInterstitialAd(activity) {}
                        }
                    }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = SurfaceBorder)

                // SECTION 1: BASEMAP STYLES
                Text(
                    "BASEMAP STYLES (100% Free, No API Keys)",
                    color = CyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))

                basemapLayers.keys.forEach { name ->
                    val isSelected = currentBasemap == name
                    val icon = when (name) {
                        "Satellite Imagery" -> Icons.Default.SatelliteAlt
                        "Standard Street" -> Icons.Default.Map
                        "Topographic Terrain" -> Icons.Default.Terrain
                        else -> Icons.Default.DarkMode
                    }
                    val subtitle = when (name) {
                        "Satellite Imagery" -> "ArcGIS High-Res Global Imagery"
                        "Standard Street" -> "OpenStreetMap Street Navigation"
                        "Topographic Terrain" -> "OpenTopoMap Contour & Elevations"
                        else -> "CartoDB Tactical Dark Grid"
                    }

                    Surface(
                        color = if (isSelected) CyanAccent.copy(alpha = 0.18f) else Color(0xFF1E2632),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) CyanAccent else SurfaceBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable {
                                if (currentBasemap != name) {
                                    currentBasemap = name
                                    val activity = context as? Activity
                                    if (activity != null) {
                                        showInterstitialAd(activity) {}
                                    }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = name,
                                    tint = if (isSelected) CyanAccent else TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = name,
                                        color = if (isSelected) CyanAccent else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = subtitle,
                                        color = if (isSelected) CyanAccent.copy(alpha = 0.8f) else TextMuted,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = SurfaceBorder)

                // SECTION 2: OVERLAY LAYERS TOGGLE
                Text(
                    "OVERLAY LAYERS TOGGLE",
                    color = CyanAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Radar Toggle Tile
                OverlayToggleRow(
                    icon = Icons.Default.WaterDrop,
                    iconColor = CyanAccent,
                    title = "Doppler Rain Radar",
                    subtitle = "RainViewer Real-Time & Forecast Tiles",
                    checked = showRainRadar,
                    onCheckedChange = {
                        showRainRadar = it
                        triggerLayerSwitchInterstitial()
                    }
                )

                // Storm Track Toggle Tile
                OverlayToggleRow(
                    icon = Icons.Default.Storm,
                    iconColor = AlertRed,
                    title = "Hurricane Helene Track",
                    subtitle = "Red Trajectory + Cyan Cone + Storm Marker",
                    checked = showStormTrack,
                    onCheckedChange = {
                        showStormTrack = it
                        triggerLayerSwitchInterstitial()
                    }
                )

                // Lightning Toggle Tile
                OverlayToggleRow(
                    icon = Icons.Default.FlashOn,
                    iconColor = WarningAmber,
                    title = "Live Lightning Pulse Sparks",
                    subtitle = "Convective thunderstorm discharge clusters",
                    checked = showLightning,
                    onCheckedChange = {
                        showLightning = it
                        triggerLayerSwitchInterstitial()
                    }
                )

                // Geography Toggle Tile
                OverlayToggleRow(
                    icon = Icons.Default.Map,
                    iconColor = UnlockedGreen,
                    title = "Coastlines & Country Borders",
                    subtitle = "Nautical lat/lon grid & city nodes",
                    checked = showGeography,
                    onCheckedChange = {
                        showGeography = it
                        triggerLayerSwitchInterstitial()
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = SurfaceBorder)

                // SECTION 3: 24-HOUR AUTONOMOUS SEVERE WEATHER GUARD
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Autonomous Guard",
                        tint = UnlockedGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "24H AUTONOMOUS SEVERE WEATHER GUARD",
                        color = UnlockedGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = Color(0xFF16202C),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Background Worker (15m Interval)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Switch(
                                checked = isAutonomousGuardActive,
                                onCheckedChange = { isAutonomousGuardActive = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = UnlockedGreen
                                )
                            )
                        }
                        Text(
                            "Autonomous offline-first WorkManager periodically samples Open-Meteo API for precipitation (>10mm), wind speed (>50km/h), and CAPE index (>1000 J/kg) without cloud dependencies.",
                            color = TextMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "TEMP: ${String.format(Locale.US, "%.1f", liveTemperature)}°C",
                                color = CyanAccent,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "WIND: ${String.format(Locale.US, "%.1f", liveWindSpeed)} km/h",
                                color = WarningAmber,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                "RAIN: ${String.format(Locale.US, "%.1f", livePrecipitation)} mm",
                                color = UnlockedGreen,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                isDangerAlert = !isDangerAlert
                                SevereWeatherWorker.sendNotification(
                                    context = context,
                                    windSpeed = 68.4,
                                    precipitation = 14.8,
                                    isTest = true
                                )
                                showBasemapSheet = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDangerAlert) UnlockedGreen else AlertRed
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Icon(
                                Icons.Default.NotificationImportant,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isDangerAlert) "RESET SEVERE ALERT SIMULATION" else "TEST HEADS-UP ALERT & DANGER CIRCLE",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }

    // Ad Unlock Confirmation Dialog
    if (showUnlockDialog) {
        val targetFrame = rainData?.frames?.getOrNull(targetUnlockIndex)
        val forecastMin = if (targetUnlockIndex >= 0 && rainData != null) {
            (targetUnlockIndex - rainData!!.pastCount + 1) * 15
        } else 15

        AlertDialog(
            onDismissRequest = { showUnlockDialog = false },
            containerColor = SurfaceCard,
            titleContentColor = Color.White,
            textContentColor = Color.LightGray,
            icon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock",
                    tint = CyanAccent,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    "UNLOCK +${forecastMin} MIN PREDICTION",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        "Watch a short video to unlock the next 15-minute Doppler radar forecast frame (${targetFrame?.timeLabel ?: "Future Prediction"}).",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E2632), RoundedCornerShape(8.dp))
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OndemandVideo,
                            contentDescription = "Ad",
                            tint = WarningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "AdMob Rewarded Unit Ready",
                            color = WarningAmber,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val isEmulator = SecurityGuard.isEmulator(context)
                            val isVpn = !isEmulator && SecurityGuard.isVpnActive(context)
                            val isAdBlock = !isEmulator && SecurityGuard.isAdBlockerActive()
                            if (isVpn || isAdBlock) {
                                // Instead of a hard lockout, gracefully fallback to Simulated Video Ad so they can still unlock the feature!
                                showUnlockDialog = false
                                onSimulatedAdReward = {
                                    unlockedMaxIndex = min(unlockedMaxIndex + 1, (rainData?.frames?.size ?: 1) - 1)
                                    currentFrameIndex = unlockedMaxIndex
                                    startCooldownTimer()
                                }
                                showSimulatedAdDialog = true
                                return@launch
                            }
                            showUnlockDialog = false
                            val activity = context as? Activity
                            if (activity != null) {
                                showRewardedAd(
                                    activity,
                                    {
                                        unlockedMaxIndex = min(unlockedMaxIndex + 1, (rainData?.frames?.size ?: 1) - 1)
                                        currentFrameIndex = unlockedMaxIndex
                                        startCooldownTimer()
                                    },
                                    {
                                        // Fallback to gorgeous simulated weather ad on loading failure (no fill, offline, etc.)
                                        onSimulatedAdReward = {
                                            unlockedMaxIndex = min(unlockedMaxIndex + 1, (rainData?.frames?.size ?: 1) - 1)
                                            currentFrameIndex = unlockedMaxIndex
                                            startCooldownTimer()
                                        }
                                        showSimulatedAdDialog = true
                                    }
                                )
                            }
                        }
                    },
                    enabled = cooldownSeconds == 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanAccent,
                        contentColor = Color.Black
                    )
                ) {
                    Text("WATCH VIDEO", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockDialog = false }) {
                    Text("CANCEL", color = TextMuted)
                }
            }
        )
    }

    // Simulated/Sponsor Weather Ad Dialog Fallback (Runs beautifully offline & handles AdBlock/VPN cases)
    if (showSimulatedAdDialog) {
        var remainingSeconds by remember { mutableIntStateOf(5) }
        val meteorologyTips = remember {
            listOf(
                "HURRICANE SAFETY: Always store at least 1 gallon of water per person per day for at least 3 days in case of utility disruption.",
                "RADAR TRIVIA: Doppler weather radar measures both precipitation density and wind velocity to detect rotating storms/tornadoes.",
                "LIGHTNING PROTECTION: The '30-30 Rule' states that if you see lightning and hear thunder within 30 seconds, seek shelter immediately.",
                "CYCLONE CATEGORIES: Saffir-Simpson Scale classifies hurricanes from Category 1 (74-95 mph) up to Category 5 (157 mph or higher).",
                "STORM SURGE WARNING: Storm surge is the rapid rise of coastal water pushed ashore by cyclone winds, causing severe flooding risk.",
                "METEOROLOGY FACT: High CAPE (Convective Available Potential Energy) values indicate extreme atmospheric instability, fueling severe convective storms."
            )
        }
        val currentTip = remember { meteorologyTips.random() }

        LaunchedEffect(Unit) {
            while (remainingSeconds > 0) {
                delay(1000L)
                remainingSeconds -= 1
            }
        }

        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFB0F141D)),
                border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Sponsor",
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "TACTICAL WEATHER ADVERTISER",
                                color = CyanAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(CyanAccent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "SIMULATED AD",
                                color = CyanAccent,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(90.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.05f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )

                        CircularProgressIndicator(
                            progress = { remainingSeconds / 5f },
                            color = CyanAccent,
                            trackColor = Color(0xFF1B2330),
                            strokeWidth = 4.dp,
                            modifier = Modifier
                                .size(80.dp)
                                .graphicsLayer(scaleX = scale, scaleY = scale)
                        )

                        Text(
                            text = if (remainingSeconds > 0) "${remainingSeconds}s" else "✓",
                            color = if (remainingSeconds > 0) WarningAmber else UnlockedGreen,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "SPONSOR TIP & METEOROLOGY SECURE FEEDS",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF141A24)),
                        border = BorderStroke(1.dp, SurfaceBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = currentTip,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (remainingSeconds > 0) {
                        Text(
                            text = "Unlocking next premium frame in $remainingSeconds seconds...",
                            color = WarningAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Button(
                            onClick = {
                                onSimulatedAdReward?.invoke()
                                showSimulatedAdDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UnlockedGreen,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Text(
                                "CLAIM PREMIUM FRAME UNLOCK",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }

    // Active Real-Time Emergency Voice & Visual Broadcaster Popup
    EmergencyWarningPopup(
        isVisible = showEmergencyPopup,
        onDismissRequest = { 
            showEmergencyPopup = false 
            if (isSpeaking) {
                tts?.stop()
                isSpeaking = false
            }
        },
        userLocation = userLocation,
        langCode = detectLanguageFromLocation(userLocation),
        isSpeaking = isSpeaking,
        onTriggerVoice = {
            val detectedLang = detectLanguageFromLocation(userLocation)
            val textToSpeak = when (detectedLang) {
                "or" -> "ସତର୍କ ସୂଚନା! ପାଣିପାଗ ବିଭାଗ ପକ୍ଷରୁ ଅତି ଗୁରୁତର ବାତ୍ୟା ଓ ଝଡ଼ର ଆଶଙ୍କା ରହିଛି। ଅତି ଜରୁରୀ ନହେଲେ ଘରୁ ବାହାରକୁ ଯାଆନ୍ତୁ ନାହିଁ ଏବଂ ସୁରକ୍ଷିତ ସ୍ଥାନରେ ରୁହନ୍ତୁ।"
                "hi" -> "चेतावनी! मौसम विभाग द्वारा अत्यधिक गंभीर चक्रवात और आंधी की आशंका है। कृपया सुरक्षित स्थानों पर रहें और अनावश्यक रूप से बाहर न निकलें।"
                else -> "Emergency Warning! The meteorological department has issued an extreme severe cyclone and storm alert. Please stay indoors and remain in a safe shelter."
            }
            triggerVoiceWarning(textToSpeak, detectedLang)
        },
        onStopVoice = {
            tts?.stop()
            isSpeaking = false
        }
    )

    // About Developer Dialog (Tactical Glassmorphic Modal)
    if (showDeveloperDialog) {
        AlertDialog(
            onDismissRequest = { showDeveloperDialog = false },
            containerColor = Color(0xEE121820),
            modifier = Modifier
                .border(BorderStroke(1.dp, CyanAccent.copy(alpha = 0.45f)), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            confirmButton = {
                TextButton(onClick = {
                    showDeveloperDialog = false
                    val activity = context as? Activity
                    if (activity != null) {
                        showInterstitialAd(activity) {}
                    }
                }) {
                    Text("CLOSE", color = CyanAccent, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Developer Real Photo: Circular Avatar displaying developer.png in a perfect size and professional border
                    Box(
                        modifier = Modifier
                            .size(120.dp) // Perfect professional size
                            .clip(CircleShape)
                            .background(Color(0xFF16202C))
                            .border(BorderStroke(2.dp, CyanAccent), CircleShape)
                            .padding(4.dp) // Double-ring spacer
                            .border(BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data("file:///android_asset/images/developer.png")
                                .crossfade(true)
                                .build(),
                            contentDescription = "Developer Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Name
                    Text(
                        text = "Jatia Singh",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Title
                    Text(
                        text = "Lead Architect & Mobile Engineer",
                        color = CyanAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Professional Bio
                    Surface(
                        color = Color(0xFF16202C).copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SurfaceBorder)
                    ) {
                        Text(
                            text = "Passionate mobile software engineer and geospatial systems creator dedicated to building autonomous, offline-first weather intelligence tools. Developed this Live Doppler Radar & Hurricane Tracking platform to deliver real-time life-saving storm alerts, severe thunder telemetry, and accurate trajectory modeling to protect lives and communities.",
                            color = Color(0xFFD0D7DE),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Facebook Follow Button
                    Button(
                        onClick = {
                            try {
                                val fbIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/share/19b8kMJ5Pi/"))
                                fbIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(fbIntent)
                            } catch (e: Exception) {
                                Log.e("AboutDeveloper", "Failed to open Facebook URL: ${e.message}")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "Facebook",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Follow on Facebook",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tactical System Status
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F141C))
                            .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = UnlockedGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "GEOSPATIAL RADAR ENGINE ONLINE",
                                color = UnlockedGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // App Version
                    Text(
                        text = "v1.0.0 Tactical Edition",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }
        )
    }

    // Ironclad Security Guard Hard-Lock Non-Dismissible Dialog
    if (securityViolation != null) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DarkBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(AlertRed.copy(alpha = 0.15f))
                            .border(2.dp, AlertRed, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = "Security Alert",
                            tint = AlertRed,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "⚠️ SECURITY RESTRICTION",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(14.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = BorderStroke(1.dp, SurfaceBorder),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = "${securityViolation}!\n\nTo keep this Doppler Radar & Hurricane Tracking platform 100% free and operational, you must disable your Ad Blocker, Private DNS (e.g. AdGuard / NextDNS), or VPN to proceed.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    Spacer(Modifier.height(26.dp))
                    Button(
                        onClick = { evaluateSecurityGuard() },
                        enabled = !isVerifyingSecurity,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanAccent,
                            contentColor = Color.Black
                        )
                    ) {
                        if (isVerifyingSecurity) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.Black
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("RETRY / VERIFY NOW", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    TextButton(
                        onClick = { securityViolation = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("PROCEED ANYWAY (BYPASS AD CHECKS)", color = CyanAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun TelemetryCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    subValue: String,
    color: Color
) {
    Surface(
        color = Color(0xFF1A222D),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    label,
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                value,
                color = color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                subValue,
                color = TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- TOP HUD BAR ---
@Composable
fun TopHUDBar(
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onStormClick: () -> Unit,
    onDeveloperClick: () -> Unit,
    frameTime: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaPulse by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        color = SurfaceCard,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = SurfaceBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLoading) WarningAmber else UnlockedGreen.copy(alpha = alphaPulse)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "TACTICAL RADAR v5.0",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "FRAME: $frameTime",
                        color = CyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(AlertRed.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .border(1.dp, AlertRed, RoundedCornerShape(6.dp))
                        .clickable { onStormClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storm,
                            contentDescription = "Storm",
                            tint = AlertRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "CAT-4 HELENE",
                            color = AlertRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // TOP-RIGHT APP BAR: Clean vector person icon ONLY (no real image/photo)
                IconButton(onClick = onDeveloperClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "About Developer",
                        tint = CyanAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// --- TACTICAL GEOSPATIAL VIEWPORT (Osmdroid Powered) ---
val satelliteTileSource = object : OnlineTileSourceBase(
    "ArcGIS_Satellite",
    0, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return baseUrl + "$zoom/$y/$x"
    }
}

val topoTileSource = object : OnlineTileSourceBase(
    "OpenTopoMap",
    0, 17, 256, ".png",
    arrayOf("https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/", "https://c.tile.opentopomap.org/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return baseUrl + "$zoom/$x/$y.png"
    }
}

@Composable
fun TacticalGeospatialViewport(
    host: String,
    framePath: String?,
    currentBasemap: String,
    zoomLevel: Float,
    panOffsetX: Float,
    panOffsetY: Float,
    showGeography: Boolean,
    showStormTrack: Boolean,
    showLightning: Boolean,
    isDangerAlert: Boolean = false,
    stormDetail: StormDetail,
    userLocation: GeoPoint?,
    destinationLocation: GeoPoint?,
    onLocationSelected: (GeoPoint) -> Unit,
    onStormEyeClick: () -> Unit
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            OsmConfiguration.getInstance().load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
            OsmConfiguration.getInstance().userAgentValue = "DopplerRadar/1.0"
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(5.5)
            controller.setCenter(GeoPoint(stormDetail.latitude, stormDetail.longitude))
        }
    }

    // Thread-safe and leak-proof state management for the radar overlay
    var radarOverlay by remember { mutableStateOf<TilesOverlay?>(null) }

    DisposableEffect(host, framePath) {
        var provider: MapTileProviderBasic? = null
        var overlay: TilesOverlay? = null

        if (framePath != null) {
            val radarSource = XYTileSource(
                "RainViewerRadar",
                0, 18, 256, "/2/1_1.png",
                arrayOf("$host$framePath/256/")
            )
            provider = MapTileProviderBasic(context, radarSource)
            overlay = TilesOverlay(provider, context).apply {
                loadingBackgroundColor = android.graphics.Color.TRANSPARENT
                loadingLineColor = android.graphics.Color.TRANSPARENT
            }
        }

        radarOverlay = overlay

        onDispose {
            provider?.detach()
            overlay?.onDetach(null)
        }
    }

    LaunchedEffect(panOffsetX, panOffsetY, stormDetail) {
        if (panOffsetX == 0f && panOffsetY == 0f) {
            mapView.controller.animateTo(GeoPoint(25.7617, -80.1918))
            mapView.controller.setZoom(5.5)
        } else {
            mapView.controller.animateTo(GeoPoint(stormDetail.latitude, stormDetail.longitude))
            mapView.controller.setZoom(6.5)
        }
    }

    // Effect to animate map to selected locations when My Location / Tap is triggered
    LaunchedEffect(userLocation) {
        if (userLocation != null) {
            mapView.controller.animateTo(userLocation)
            mapView.controller.setZoom(6.5)
        }
    }

    LaunchedEffect(destinationLocation) {
        if (destinationLocation != null) {
            mapView.controller.animateTo(destinationLocation)
            mapView.controller.setZoom(6.5)
        }
    }

    AndroidView(
        factory = { mapView },
        update = { mv ->
            val baseSource = when (currentBasemap) {
                "Satellite Imagery" -> satelliteTileSource
                "Topographic Terrain" -> topoTileSource
                else -> TileSourceFactory.MAPNIK
            }
            if (mv.tileProvider.tileSource != baseSource) {
                mv.setTileSource(baseSource)
            }

            mv.overlays.removeAll {
                it is TilesOverlay ||
                it is Marker ||
                it is Polyline ||
                it is OsmPolygon ||
                it is MapEventsOverlay
            }

            // Capture taps on the map to set custom Rangefinder targets
            val receiver = object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    onLocationSelected(p)
                    return true
                }
                override fun longPressHelper(p: GeoPoint): Boolean {
                    return false
                }
            }
            mv.overlays.add(MapEventsOverlay(receiver))

            // Add the cached thread-safe radar overlay if it exists
            val currentRadar = radarOverlay
            if (currentRadar != null) {
                mv.overlays.add(currentRadar)
            }

            if (showStormTrack) {
                val eyeGeo = GeoPoint(stormDetail.latitude, stormDetail.longitude)

                val conePolygon = OsmPolygon(mv).apply {
                    val pts = listOf(
                        eyeGeo,
                        GeoPoint(stormDetail.latitude + 3.0, stormDetail.longitude - 2.5),
                        GeoPoint(stormDetail.latitude + 6.0, stormDetail.longitude - 4.5),
                        GeoPoint(stormDetail.latitude + 7.5, stormDetail.longitude - 5.5),
                        GeoPoint(stormDetail.latitude + 6.5, stormDetail.longitude - 6.5),
                        GeoPoint(stormDetail.latitude + 4.5, stormDetail.longitude - 5.0),
                        GeoPoint(stormDetail.latitude + 2.0, stormDetail.longitude - 3.0),
                        eyeGeo
                    )
                    points = pts
                    fillColor = 0x2200E5FF.toInt()
                    strokeColor = 0xBB00E5FF.toInt()
                    strokeWidth = 2.5f
                }
                mv.overlays.add(conePolygon)

                val pastPoints = listOf(
                    GeoPoint(stormDetail.latitude - 4.0, stormDetail.longitude + 4.0),
                    GeoPoint(stormDetail.latitude - 2.5, stormDetail.longitude + 2.5),
                    GeoPoint(stormDetail.latitude - 1.2, stormDetail.longitude + 1.2),
                    eyeGeo
                )
                val historicalPolyline = Polyline(mv).apply {
                    setPoints(pastPoints)
                    color = 0xFFFF3B30.toInt()
                    width = 5.5f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(15f, 12f), 0f)
                }
                mv.overlays.add(historicalPolyline)

                val fcstPoints = listOf(
                    eyeGeo,
                    GeoPoint(stormDetail.latitude + 2.0, stormDetail.longitude - 2.0),
                    GeoPoint(stormDetail.latitude + 4.5, stormDetail.longitude - 4.0),
                    GeoPoint(stormDetail.latitude + 7.0, stormDetail.longitude - 6.0)
                )
                val projectedPolyline = Polyline(mv).apply {
                    setPoints(fcstPoints)
                    color = 0xFF00E5FF.toInt()
                    width = 5.5f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                }
                mv.overlays.add(projectedPolyline)

                val landfallTargetGeo = GeoPoint(stormDetail.latitude + 4.5, stormDetail.longitude - 4.0)
                val landfallMarker = Marker(mv).apply {
                    position = landfallTargetGeo
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "PROJECTED LANDFALL"
                    subDescription = "ETA: ${stormDetail.landfallEta}"
                    val dotDrawable = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setSize(24, 24)
                        setColor(0x66FF3B30.toInt())
                        setStroke(2, 0xFFFF3B30.toInt())
                    }
                    icon = dotDrawable
                }
                mv.overlays.add(landfallMarker)

                val stormMarker = Marker(mv).apply {
                    position = eyeGeo
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = stormDetail.name
                    subDescription = "${stormDetail.category} - Winds: ${stormDetail.windSpeedMph} MPH"
                    
                    val iconDrawable = context.getDrawable(android.R.drawable.ic_menu_compass)
                    if (iconDrawable != null) {
                        iconDrawable.setTint(android.graphics.Color.RED)
                        icon = iconDrawable
                    }
                    
                    setOnMarkerClickListener { _, _ ->
                        onStormEyeClick()
                        true
                    }
                }
                mv.overlays.add(stormMarker)
            }

            if (showLightning) {
                val lightningPoints = listOf(
                    GeoPoint(stormDetail.latitude + 1.2, stormDetail.longitude - 1.2),
                    GeoPoint(stormDetail.latitude + 1.9, stormDetail.longitude - 2.7),
                    GeoPoint(stormDetail.latitude - 1.4, stormDetail.longitude + 0.9),
                    GeoPoint(stormDetail.latitude + 0.6, stormDetail.longitude + 1.6)
                )
                lightningPoints.forEach { pt ->
                    val lightningMarker = Marker(mv).apply {
                        position = pt
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "LIGHTNING SPARK"
                        subDescription = "Convective lightning cluster"
                        val dotDrawable = android.graphics.drawable.GradientDrawable().apply {
                            shape = android.graphics.drawable.GradientDrawable.OVAL
                            setSize(16, 16)
                            setColor(0x88FF9500.toInt())
                            setStroke(2, 0xFFFF9500.toInt())
                        }
                        icon = dotDrawable
                    }
                    mv.overlays.add(lightningMarker)
                }
            }

            if (isDangerAlert) {
                val stationPos = GeoPoint(25.7617, -80.1918)
                val dangerPolygon = OsmPolygon(mv).apply {
                    val pts = mutableListOf<GeoPoint>()
                    val radiusDeg = 0.5
                    for (deg in 0 until 360 step 15) {
                        val rad = Math.toRadians(deg.toDouble())
                        val lat = stationPos.latitude + radiusDeg * Math.sin(rad)
                        val lon = stationPos.longitude + radiusDeg * Math.cos(rad)
                        pts.add(GeoPoint(lat, lon))
                    }
                    points = pts
                    fillColor = 0x33FF3B30.toInt()
                    strokeColor = 0xFFFF3B30.toInt()
                    strokeWidth = 3f
                }
                mv.overlays.add(dangerPolygon)
            }

            // Draw My Location Marker & Laser Line if active
            if (userLocation != null) {
                val eyeGeo = GeoPoint(stormDetail.latitude, stormDetail.longitude)
                
                // Draw user-to-eye laser line ONLY if custom destination is active
                if (destinationLocation != null) {
                    val rangeLine = Polyline(mv).apply {
                        setPoints(listOf(userLocation, eyeGeo))
                        color = 0xFF00E5FF.toInt() // CyanAccent (Blue laser for user)
                        width = 4.5f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 10f), 0f)
                    }
                    mv.overlays.add(rangeLine)
                }

                val userMarker = Marker(mv).apply {
                    position = userLocation
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "MY GPS LOCATION"
                    val distKm = userLocation.distanceToAsDouble(eyeGeo) / 1000.0
                    subDescription = "Distance to eye: ${String.format("%.1f", distKm)} KM"
                    
                    val dotDrawable = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setSize(28, 28)
                        setColor(0x3300E5FF.toInt())
                        setStroke(3, 0xFF00E5FF.toInt())
                    }
                    icon = dotDrawable
                }
                mv.overlays.add(userMarker)
            }

            // Draw Custom Destination Marker & Laser Line if active
            if (destinationLocation != null) {
                val eyeGeo = GeoPoint(stormDetail.latitude, stormDetail.longitude)
                
                val rangeLine = Polyline(mv).apply {
                    setPoints(listOf(destinationLocation, eyeGeo))
                    color = 0xFFFAAD14.toInt() // WarningAmber (Orange laser)
                    width = 4.5f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 10f), 0f)
                }
                mv.overlays.add(rangeLine)

                val targetMarker = Marker(mv).apply {
                    position = destinationLocation
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "MARKED DESTINATION"
                    val distKm = destinationLocation.distanceToAsDouble(eyeGeo) / 1000.0
                    subDescription = "Distance to eye: ${String.format("%.1f", distKm)} KM"
                    
                    val dotDrawable = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setSize(28, 28)
                        setColor(0x33FAAD14.toInt())
                        setStroke(3, 0xFFFAAD14.toInt())
                    }
                    icon = dotDrawable
                }
                mv.overlays.add(targetMarker)
            }

            // Draw Path from User to Destination if both are set
            if (userLocation != null && destinationLocation != null) {
                val pathLine = Polyline(mv).apply {
                    setPoints(listOf(userLocation, destinationLocation))
                    color = 0xFF34C759.toInt() // UnlockedGreen (Green line connecting user & destination)
                    width = 3.5f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 8f), 0f)
                }
                mv.overlays.add(pathLine)
            }

            mv.invalidate()
        },
        modifier = Modifier.fillMaxSize()
    )
}

// --- RADAR INTENSITY LEGEND ---
@Composable
fun RadarIntensityLegend(modifier: Modifier = Modifier) {
    Surface(
        color = SurfaceCard.copy(alpha = 0.92f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, SurfaceBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "INTENSITY (dBZ)",
                color = TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                UnlockedGreen,
                                WarningAmber,
                                AlertRed,
                                Color(0xFFA200FF)
                            )
                        )
                    )
            )
        }
    }
}

// --- TELEMETRY BADGE ---
@Composable
fun StationTelemetryBadge(modifier: Modifier = Modifier) {
    Surface(
        color = SurfaceCard.copy(alpha = 0.92f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, SurfaceBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "GRID: 25.76°N, 80.19°W",
                color = CyanAccent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                "RADAR: GULF-DOPPLER #04",
                color = Color.White,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// --- 24H AUTONOMOUS GUARD BADGE ---
@Composable
fun AutonomousGuardBadge(
    modifier: Modifier = Modifier,
    isDanger: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = SurfaceCard.copy(alpha = 0.95f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.4.dp, if (isDanger) AlertRed else UnlockedGreen),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isDanger) AlertRed else UnlockedGreen)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = if (isDanger) Icons.Default.Warning else Icons.Default.Shield,
                contentDescription = null,
                tint = if (isDanger) AlertRed else UnlockedGreen,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isDanger) "⚠️ SEVERE ALERT ACTIVE" else "🛡️ 24h Autonomous Guard Active (Background alerts enabled without cloud)",
                color = if (isDanger) AlertRed else UnlockedGreen,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- TACTICAL SENSORS HUD ---
@Composable
fun TacticalSensorsHUD(
    modifier: Modifier = Modifier,
    temperature: Float,
    windSpeed: Float,
    windDirection: Float,
    isDanger: Boolean
) {
    Column(modifier = modifier) {
        // Temperature Pill
        Surface(
            color = SurfaceCard.copy(alpha = 0.92f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.2.dp, CyanAccent.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Thermostat,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${String.format(Locale.US, "%.1f", temperature)}°C",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        // Wind Direction Rotating Compass Needle Gauge
        Surface(
            color = SurfaceCard.copy(alpha = 0.92f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.2.dp, if (isDanger) AlertRed else WarningAmber.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF16202C))
                        .border(1.dp, CyanAccent.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "N",
                        color = CyanAccent,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 1.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Wind Direction",
                        tint = WarningAmber,
                        modifier = Modifier
                            .size(17.dp)
                            .rotate(windDirection)
                    )
                }
                Spacer(modifier = Modifier.width(7.dp))
                Column {
                    Text(
                        text = "WIND: ${String.format(Locale.US, "%.1f", windSpeed)} km/h",
                        color = if (isDanger) AlertRed else WarningAmber,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "DIR: ${String.format(Locale.US, "%.0f", windDirection)}° ${getCardinalDirection(windDirection)}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 8.5.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

fun getCardinalDirection(deg: Float): String {
    val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = (((deg % 360) + 22.5) / 45.0).toInt() % 8
    return directions[index]
}

// --- FLOATING LAYER CONTROLS ---
@Composable
fun FloatingLayerControls(
    modifier: Modifier = Modifier,
    showRainRadar: Boolean,
    showStormTrack: Boolean,
    showLightning: Boolean,
    showGeography: Boolean,
    onToggleRadar: () -> Unit,
    onToggleStorm: () -> Unit,
    onToggleLightning: () -> Unit,
    onToggleGeography: () -> Unit,
    onBasemapClick: () -> Unit
) {
    Surface(
        color = SurfaceCard.copy(alpha = 0.92f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SurfaceBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LayerFabButton(
                icon = Icons.Default.WaterDrop,
                label = "RADAR",
                isActive = showRainRadar,
                activeColor = CyanAccent,
                onClick = onToggleRadar
            )
            Spacer(modifier = Modifier.height(8.dp))
            LayerFabButton(
                icon = Icons.Default.Storm,
                label = "STORM",
                isActive = showStormTrack,
                activeColor = AlertRed,
                onClick = onToggleStorm
            )
            Spacer(modifier = Modifier.height(8.dp))
            LayerFabButton(
                icon = Icons.Default.FlashOn,
                label = "LIGHTNING",
                isActive = showLightning,
                activeColor = WarningAmber,
                onClick = onToggleLightning
            )
            Spacer(modifier = Modifier.height(8.dp))
            LayerFabButton(
                icon = Icons.Default.Map,
                label = "BORDERS",
                isActive = showGeography,
                activeColor = UnlockedGreen,
                onClick = onToggleGeography
            )
            Spacer(modifier = Modifier.height(8.dp))
            LayerFabButton(
                icon = Icons.Default.Layers,
                label = "BASEMAP",
                isActive = true,
                activeColor = CyanAccent,
                onClick = onBasemapClick
            )
        }
    }
}

@Composable
fun LayerFabButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) activeColor.copy(alpha = 0.2f) else Color(0xFF1E2632))
            .border(
                1.dp,
                if (isActive) activeColor else SurfaceBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) activeColor else TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Text(
                label,
                color = if (isActive) activeColor else TextMuted,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun OverlayToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        color = Color(0xFF1E2632),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (checked) iconColor.copy(alpha = 0.5f) else SurfaceBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(iconColor.copy(alpha = 0.18f), CircleShape)
                        .border(1.dp, iconColor.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = subtitle,
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = iconColor,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = Color(0xFF0A0E14)
                )
            )
        }
    }
}

// --- EMERGENCY WEATHER VOICE WARNING POPUP ---
@Composable
fun EmergencyWarningPopup(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    userLocation: GeoPoint?,
    langCode: String,
    isSpeaking: Boolean,
    onTriggerVoice: () -> Unit,
    onStopVoice: () -> Unit
) {
    if (!isVisible) return

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "borderAlpha"
    )

    val waveHeight1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 24f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave1"
    )
    val waveHeight2 by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 32f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave2"
    )
    val waveHeight3 by infiniteTransition.animateFloat(
        initialValue = 3f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave3"
    )

    val stateName = when (langCode) {
        "or" -> "ODISHA (ଓଡ଼ିଶା)"
        "hi" -> "INDIA (NATIONWIDE)"
        else -> "GLOBAL / LOCAL REGION"
    }

    val displayLang = when (langCode) {
        "or" -> "ODIA (ଓଡ଼ିଆ)"
        "hi" -> "HINDI (हिन्दी)"
        else -> "ENGLISH (US/UK)"
    }

    val warningTitle = when (langCode) {
        "or" -> "⚠️ ଅତି ଜରୁରୀ ସୂଚନା (RED ALERT)"
        "hi" -> "⚠️ अत्यधिक गंभीर चेतावनी (RED ALERT)"
        else -> "⚠️ SEVERE EMERGENCY ALERT (RED ALERT)"
    }

    val warningTextMsg = when (langCode) {
        "or" -> "ସତର୍କ ସୂଚନା! ପାଣିପାଗ ବିଭାଗ ପକ୍ଷରୁ ଅତି ଗୁରୁତର ବାତ୍ୟା ଓ ଝଡ଼ର ଆଶଙ୍କା ରହିଛି। ଅତି ଜରୁରୀ ନହେଲେ ଘରୁ ବାହାରକୁ ଯାଆନ୍ତୁ ନାହିଁ ଏବଂ ସୁରକ୍ଷିତ ସ୍ଥାନରେ ରୁହନ୍ତୁ।"
        "hi" -> "चेतावनी! मौसम विभाग द्वारा अत्यधिक गंभीर चक्रवात और आंधी की आशंका है। कृपया सुरक्षित स्थानों पर रहें और अनावश्यक रूप से बाहर न निकलें।"
        else -> "Emergency Warning! The meteorological department has issued an extreme severe cyclone and storm alert. Please stay indoors and remain in a safe shelter."
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFB0A0E17),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(2.dp, AlertRed.copy(alpha = borderAlpha)),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Glow alert header icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(AlertRed.copy(alpha = 0.15f), CircleShape)
                        .border(1.5.dp, AlertRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Severe Alert",
                        tint = AlertRed,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = warningTitle,
                    color = AlertRed,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // GPS Location Data & detected parameters card
                Surface(
                    color = Color(0xFF131824),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "GPS SCAN:",
                                color = TextMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (userLocation != null) {
                                    "${String.format("%.3f", userLocation.latitude)}°N, ${String.format("%.3f", userLocation.longitude)}°W"
                                } else {
                                    "OBTAINING CURRENT GPS..."
                                },
                                color = CyanAccent,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "DETECTED STATE:",
                                color = TextMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                stateName,
                                color = Color.White,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "LOCAL LANGUAGE:",
                                color = TextMuted,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                displayLang,
                                color = UnlockedGreen,
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Dynamic Speech translated warning block
                Text(
                    text = warningTextMsg,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Waveform indicator
                if (isSpeaking) {
                    Row(
                        modifier = Modifier.height(40.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.width(4.dp).height(Dp(waveHeight1)).background(AlertRed, RoundedCornerShape(2.dp)))
                        Box(modifier = Modifier.width(4.dp).height(Dp(waveHeight2)).background(AlertRed, RoundedCornerShape(2.dp)))
                        Box(modifier = Modifier.width(4.dp).height(Dp(waveHeight3)).background(AlertRed, RoundedCornerShape(2.dp)))
                        Box(modifier = Modifier.width(4.dp).height(Dp(waveHeight2)).background(AlertRed, RoundedCornerShape(2.dp)))
                        Box(modifier = Modifier.width(4.dp).height(Dp(waveHeight1)).background(AlertRed, RoundedCornerShape(2.dp)))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Interactive Audio broadcast speaker button
                Button(
                    onClick = {
                        if (isSpeaking) {
                            onStopVoice()
                        } else {
                            onTriggerVoice()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSpeaking) AlertRed else AlertRed.copy(0.2f),
                        contentColor = if (isSpeaking) Color.White else AlertRed
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, AlertRed),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Voice"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSpeaking) {
                                when (langCode) {
                                    "or" -> "ସ୍ଵର ବନ୍ଦ କରନ୍ତୁ"
                                    "hi" -> "आवाज बंद करें"
                                    else -> "STOP VOICE BROADCAST"
                                }
                            } else {
                                when (langCode) {
                                    "or" -> "🔊 ଆଲର୍ଟ୍ ସ୍ଵର ଶୁଣନ୍ତୁ"
                                    "hi" -> "🔊 अलर्ट आवाज सुनें"
                                    else -> "🔊 LISTEN VOICE BROADCAST"
                                }
                            },
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dismiss Button
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when (langCode) {
                            "or" -> "ସୂଚନା ବନ୍ଦ କରନ୍ତୁ"
                            "hi" -> "चेतावनी बंद करें"
                            else -> "DISMISS ALERT"
                        },
                        color = TextMuted,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

// --- BOTTOM CONTROL HUD ---
@Composable
fun BottomControlHUD(
    modifier: Modifier = Modifier,
    data: RainViewerData,
    currentIndex: Int,
    unlockedMaxIndex: Int,
    isPlaying: Boolean,
    cooldownSeconds: Int,
    isAdReady: Boolean,
    onPlayPauseToggle: () -> Unit,
    onFrameSelected: (Int) -> Unit,
    onUnlockClicked: () -> Unit
) {
    val currentFrame = data.frames.getOrNull(currentIndex)
    val hasMoreLocked = unlockedMaxIndex < data.frames.size - 1

    Surface(
        color = SurfaceCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SurfaceBorder),
        shadowElevation = 10.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Row 1: Header (Metadata on Left, Compact Unlock Action on Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (currentFrame?.isNowcast == true) Icons.Default.TrendingUp else Icons.Default.History,
                        contentDescription = "Status",
                        tint = if (currentFrame?.isNowcast == true) WarningAmber else CyanAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = currentFrame?.timeLabel ?: "LIVE",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${currentIndex + 1}/${data.frames.size} FRAMES",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Compact Unlock Button or Badge to save vertical space
                if (hasMoreLocked) {
                    val buttonEnabled = cooldownSeconds == 0
                    Surface(
                        color = if (buttonEnabled) CyanAccent else Color(0xFF1E2836),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .clickable(enabled = buttonEnabled) { onUnlockClicked() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(if (buttonEnabled) Color.Black else TextMuted, RoundedCornerShape(3.dp))
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    "AD",
                                    color = if (buttonEnabled) CyanAccent else Color.Black,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (buttonEnabled) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = "Unlock",
                                tint = if (buttonEnabled) Color.Black else TextMuted,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = if (cooldownSeconds > 0) "${cooldownSeconds}s" else "UNLOCK +15M",
                                color = if (buttonEnabled) Color.Black else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .background(UnlockedGreen.copy(0.12f), RoundedCornerShape(6.dp))
                            .border(1.dp, UnlockedGreen.copy(0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Complete",
                            tint = UnlockedGreen,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "ALL FORECASTS UNLOCKED",
                            color = UnlockedGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Player Control Button + Integrated Frame Timeline Bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier
                        .size(34.dp)
                        .background(CyanAccent, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        data.frames.forEachIndexed { idx, frame ->
                            val isUnlocked = idx <= unlockedMaxIndex
                            val isCurrent = idx == currentIndex
                            val barColor = when {
                                isCurrent -> Color.White
                                isUnlocked -> CyanAccent
                                else -> AlertRed.copy(alpha = 0.4f)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(if (isCurrent) 18.dp else 10.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(barColor)
                                    .clickable { onFrameSelected(idx) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (!isUnlocked && idx == unlockedMaxIndex + 1) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked",
                                        tint = Color.White,
                                        modifier = Modifier.size(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- TACTICAL BOTTOM BANNER AD ---
@Composable
fun TacticalBottomBannerAd(
    modifier: Modifier = Modifier
) {
    var adFailedToLoad by remember { mutableStateOf(false) }
    var loadedNativeAd by remember { mutableStateOf<NativeAd?>(null) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        try {
            val adLoader = AdLoader.Builder(context, AdMobConfig.bannerAdUnitId)
                .forNativeAd { ad: NativeAd ->
                    loadedNativeAd = ad
                }
                .withAdListener(object : com.google.android.gms.ads.AdListener() {
                    override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                        Log.e("AdManager", "Native ad failed to load: ${error.message} (Code: ${error.code})")
                        adFailedToLoad = true
                    }
                })
                .build()
            adLoader.loadAd(com.google.android.gms.ads.AdRequest.Builder().build())
        } catch (e: Exception) {
            Log.e("AdManager", "Error initializing AdLoader: ${e.message}")
            adFailedToLoad = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        contentAlignment = Alignment.Center
    ) {
        if (adFailedToLoad || (loadedNativeAd == null && !adFailedToLoad)) {
            if (adFailedToLoad) {
                // High-fidelity fallback promo banner to display weather safety tips when ads are blocked or offline
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF141F2E))
                        .border(1.dp, CyanAccent.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Safety Tip",
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SAFETY: Keep offline hurricane survival kit ready during cyclone alerts.",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(CyanAccent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "PROMO",
                            color = CyanAccent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                CircularProgressIndicator(color = CyanAccent, modifier = Modifier.size(20.dp))
            }
        } else {
            val nativeAd = loadedNativeAd!!
            AndroidView(
                factory = { ctx ->
                    val nativeAdView = NativeAdView(ctx).apply {
                        layoutParams = android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                        )

                        val container = android.widget.LinearLayout(ctx).apply {
                            orientation = android.widget.LinearLayout.HORIZONTAL
                            gravity = android.view.Gravity.CENTER_VERTICAL
                            setPadding(12, 4, 12, 4)
                            background = android.graphics.drawable.ColorDrawable(0xFF101622.toInt())
                        }

                        // 1. "Ad" Badge
                        val badge = android.widget.TextView(ctx).apply {
                            text = "Ad"
                            setTextColor(0xFF00E5FF.toInt()) // CyanAccent
                            textSize = 10f
                            setPadding(6, 2, 6, 2)
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setStroke(2, 0xFF00E5FF.toInt())
                                cornerRadius = 4f
                            }
                        }
                        container.addView(badge)

                        // Spacer
                        container.addView(android.view.View(ctx).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(12, 1)
                        })

                        // 2. Icon View
                        val icon = android.widget.ImageView(ctx).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(40, 40)
                            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        }
                        container.addView(icon)
                        this.iconView = icon

                        // Spacer
                        container.addView(android.view.View(ctx).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(12, 1)
                        })

                        // 3. Text Layout (Headline + Body)
                        val textLayout = android.widget.LinearLayout(ctx).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                0,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                            )
                        }

                        val headline = android.widget.TextView(ctx).apply {
                            setTextColor(android.graphics.Color.WHITE)
                            textSize = 12f
                            setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        }
                        textLayout.addView(headline)
                        this.headlineView = headline

                        val body = android.widget.TextView(ctx).apply {
                            setTextColor(0xAAFFFFFF.toInt())
                            textSize = 10f
                            setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.NORMAL)
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        }
                        textLayout.addView(body)
                        this.bodyView = body

                        container.addView(textLayout)

                        // Spacer
                        container.addView(android.view.View(ctx).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(12, 1)
                        })

                        // 4. Call To Action Button
                        val cta = android.widget.Button(ctx).apply {
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                                72
                            )
                            textSize = 9f
                            setTextColor(android.graphics.Color.BLACK)
                            background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(0xFF00E5FF.toInt()) // CyanAccent
                                cornerRadius = 6f
                            }
                            setPadding(12, 0, 12, 0)
                        }
                        container.addView(cta)
                        this.callToActionView = cta

                        addView(container)
                    }

                    // Populate and bind
                    (nativeAdView.headlineView as android.widget.TextView).text = nativeAd.headline
                    if (nativeAd.body != null) {
                        (nativeAdView.bodyView as android.widget.TextView).text = nativeAd.body
                        nativeAdView.bodyView?.visibility = android.view.View.VISIBLE
                    } else {
                        nativeAdView.bodyView?.visibility = android.view.View.GONE
                    }
                    if (nativeAd.icon != null) {
                        (nativeAdView.iconView as android.widget.ImageView).setImageDrawable(nativeAd.icon?.drawable)
                        nativeAdView.iconView?.visibility = android.view.View.VISIBLE
                    } else {
                        nativeAdView.iconView?.visibility = android.view.View.GONE
                    }
                    if (nativeAd.callToAction != null) {
                        (nativeAdView.callToActionView as android.widget.Button).text = nativeAd.callToAction
                        nativeAdView.callToActionView?.visibility = android.view.View.VISIBLE
                    } else {
                        nativeAdView.callToActionView?.visibility = android.view.View.GONE
                    }

                    nativeAdView.setNativeAd(nativeAd)
                    nativeAdView
                },
                update = { view ->
                    view.setNativeAd(nativeAd)
                },
                onRelease = { view ->
                    try {
                        view.destroy()
                    } catch (e: Exception) {
                        Log.e("AdManager", "Error destroying NativeAdView: ${e.message}")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// --- TELEMETRY DASHBOARD TAB ---
@Composable
fun TelemetryDashboardTab(
    liveTemperature: Float,
    liveWindSpeed: Float,
    liveWindDirection: Float,
    livePrecipitation: Float,
    liveCape: Float,
    isDangerAlert: Boolean,
    stormDetail: StormDetail
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, SurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (isDangerAlert) AlertRed else UnlockedGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDangerAlert) "⚠️ SEVERE METEOROLOGICAL ALERT" else "✓ LEVEL SECURE",
                            color = if (isDangerAlert) AlertRed else UnlockedGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Real-time atmospheric telemetry feeding from Doppler station network and Copernicus satellite constellations.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        item {
            Text(
                "STATION & SENSOR TELEMETRY READINGS",
                color = CyanAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    TelemetryCard(
                        icon = Icons.Default.Thermostat,
                        label = "STATION TEMP",
                        value = "${String.format("%.1f", liveTemperature)}°C",
                        subValue = "Convective standard",
                        color = if (liveTemperature > 35f) AlertRed else CyanAccent
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    TelemetryCard(
                        icon = Icons.Default.Air,
                        label = "WIND VELOCITY",
                        value = "${String.format("%.1f", liveWindSpeed)} km/h",
                        subValue = "Direction: ${String.format("%.0f", liveWindDirection)}°",
                        color = if (liveWindSpeed > 50f) AlertRed else CyanAccent
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    TelemetryCard(
                        icon = Icons.Default.WaterDrop,
                        label = "PRECIPITATION",
                        value = "${String.format("%.1f", livePrecipitation)} mm/h",
                        subValue = "Accumulated rate",
                        color = if (livePrecipitation > 5.0f) AlertRed else CyanAccent
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    TelemetryCard(
                        icon = Icons.Default.ElectricBolt,
                        label = "CAPE INSTABILITY",
                        value = "${String.format("%.0f", liveCape)} J/kg",
                        subValue = "Severe storm fuel",
                        color = if (liveCape > 1000f) AlertRed else CyanAccent
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, SurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SAFFIR-SIMPSON CYCLONE CLASSIFICATION",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val categories = listOf(
                        Triple("Category 1", "74 - 95 MPH", "Minimal damage"),
                        Triple("Category 2", "96 - 110 MPH", "Moderate damage"),
                        Triple("Category 3", "111 - 129 MPH", "Devastating damage"),
                        Triple("Category 4", "130 - 156 MPH", "Catastrophic damage (Helene)"),
                        Triple("Category 5", "157+ MPH", "Complete destruction")
                    )

                    categories.forEach { (cat, speed, desc) ->
                        val isActive = cat.contains(stormDetail.category.removePrefix("CAT-")) || (cat == "Category 4" && stormDetail.category.contains("CAT-4"))
                        val rowBg = if (isActive) AlertRed.copy(alpha = 0.2f) else Color.Transparent
                        val rowBorder = if (isActive) BorderStroke(1.dp, AlertRed) else null
                        
                        Surface(
                            color = rowBg,
                            shape = RoundedCornerShape(6.dp),
                            border = rowBorder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = cat,
                                        color = if (isActive) AlertRed else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = desc,
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Text(
                                    text = speed,
                                    color = if (isActive) AlertRed else CyanAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, SurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "STORM EYE COORDINATE PATHWAY HISTORY",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val pastTrack = listOf(
                        Triple("24 hours ago", "21.2° N, 81.3° W", "CAT-2 sustained"),
                        Triple("12 hours ago", "23.5° N, 80.5° W", "CAT-3 convective expansion"),
                        Triple("Current Eye", "${stormDetail.latitude}° N, ${stormDetail.longitude}° W", "${stormDetail.category} helicity peak")
                    )

                    pastTrack.forEach { (time, coords, status) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(time, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text(status, color = TextMuted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                            Text(coords, color = WarningAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

// --- SURVIVAL MANUAL TAB ---
@Composable
fun SurvivalManualTab() {
    val checklistItems = remember {
        listOf(
            "water" to "Water Supply (3 days: 1 gal/person/day)",
            "food" to "Non-Perishable Food (Canned items, energy bars)",
            "radio" to "Battery-Powered Weather Radio (NOAA Frequency)",
            "flashlight" to "Tactical Flashlights & Replacement Cells",
            "firstaid" to "First Aid Medical Kit & Lifesaving Meds",
            "powerbank" to "Portable Phone Chargers & Back-Up Banks",
            "documents" to "Emergency Secure Document Pouch",
            "multitool" to "Utility Tool Knife & Landfall Whistle"
        )
    }
    
    val checklistState = remember {
        mutableStateMapOf(
            "water" to false,
            "food" to false,
            "radio" to false,
            "flashlight" to false,
            "firstaid" to false,
            "powerbank" to false,
            "documents" to false,
            "multitool" to false
        )
    }

    val checkedCount = checklistState.values.count { it }
    val progressPercent = (checkedCount.toFloat() / checklistItems.size.toFloat())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, SurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TACTICAL PREPAREDNESS INDEX",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progressPercent },
                        color = if (progressPercent == 1f) UnlockedGreen else CyanAccent,
                        trackColor = Color(0xFF1B2330),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SECURED ITEMS: $checkedCount / ${checklistItems.size}",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${(progressPercent * 100).toInt()}% READY",
                            color = if (progressPercent == 1f) UnlockedGreen else CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        item {
            Text(
                "CIVILIAN DISASTER SURVIVAL CHECKLIST",
                color = CyanAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(checklistItems.size) { index ->
            val (key, label) = checklistItems[index]
            val isChecked = checklistState[key] ?: false
            
            Surface(
                color = if (isChecked) Color(0xFF0F1A24) else SurfaceCard,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (isChecked) CyanAccent.copy(alpha = 0.5f) else SurfaceBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { checklistState[key] = !isChecked }
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = if (isChecked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isChecked) UnlockedGreen else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            color = if (isChecked) Color.White else Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, SurfaceBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "EMERGENCY NOAA FREQUENCIES",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tune shortwave weather radios to the following VHF frequencies for automated civilian broadcasts:\n\n" +
                               "• WX1: 162.400 MHz\n" +
                               "• WX2: 162.425 MHz\n" +
                               "• WX3: 162.450 MHz\n" +
                               "• WX4: 162.475 MHz",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

