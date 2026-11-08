package com.turga.suyolu.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.turga.suyolu.MainActivity
import com.turga.suyolu.data.PointType
import com.turga.suyolu.data.RouteData
import com.turga.suyolu.data.RoutePoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class GpsTrackingService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): GpsTrackingService = this@GpsTrackingService
    }

    private val binder = LocalBinder()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val name = intent.getStringExtra(EXTRA_FOUNTAIN_NAME) ?: "Tarihi Çeşme"
                val isResume = intent.getBooleanExtra(EXTRA_IS_RESUME, false)
                val typeCode = intent.getStringExtra(EXTRA_INITIAL_POINT_TYPE) ?: PointType.FOUNTAIN.code
                val initialType = PointType.fromCode(typeCode)
                startForegroundTracking(name, isResume, initialType)
            }
            ACTION_PAUSE -> {
                pauseForegroundTracking()
            }
            ACTION_STOP -> {
                stopForegroundTracking()
            }
        }
        return START_STICKY
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                _currentLocation.value = location
                _gpsAccuracyMeters.value = if (location.hasAccuracy()) location.accuracy else null

                if (!_isTracking.value) return

                // Ignore extreme GPS jumps (> 35 meters inaccuracy) if we already have a start point
                val currentPoints = _recordedPoints.value.toMutableList()
                if (currentPoints.isNotEmpty() && location.hasAccuracy() && location.accuracy > 35f) {
                    return
                }

                if (currentPoints.isEmpty()) {
                    // İlk nokta kullanıcının seçtiği başlangıç konumuyla (Çeşme, Su Gözü, Vana vs.) kaydedilir
                    startLat = location.latitude
                    startLng = location.longitude
                    val initType = _initialPointType.value
                    currentPoints.add(
                        RoutePoint(
                            lat = location.latitude,
                            lng = location.longitude,
                            type = initType,
                            label = initType.displayName
                        )
                    )
                    _recordedPoints.value = currentPoints
                    updateNotification()
                } else {
                    // Check distance from last recorded point
                    val lastPoint = currentPoints.last()
                    val dist = RouteData.haversineDistance(
                        lastPoint.lat, lastPoint.lng,
                        location.latitude, location.longitude
                    )

                    // Auto-record if distance traveled >= 10 meters
                    if (dist >= MIN_DISTANCE_THRESHOLD_METERS) {
                        currentPoints.add(
                            RoutePoint(
                                lat = location.latitude,
                                lng = location.longitude,
                                type = PointType.WAYPOINT
                            )
                        )
                        _recordedPoints.value = currentPoints
                        _totalDistanceMeters.value += dist
                        updateNotification()
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startForegroundTracking(
        fountainName: String,
        isResume: Boolean = false,
        initialPointType: PointType = PointType.FOUNTAIN
    ) {
        _fountainName.value = fountainName
        _initialPointType.value = initialPointType
        _isTracking.value = true
        _isPaused.value = false
        if (!isResume && _recordedPoints.value.isEmpty()) {
            _recordedPoints.value = emptyList()
            _totalDistanceMeters.value = 0.0
        }

        createNotificationChannel()

        // Android 14+ (API 34+) mandatory foregroundServiceType specification
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), serviceType)

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            setMinUpdateDistanceMeters(2f)
        }.build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun pauseForegroundTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _isTracking.value = false
        _isPaused.value = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopForegroundTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _isTracking.value = false
        _isPaused.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Su Yolu GPS Takip Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Boru hattı GPS koordinatlarını arka planda kaydeder."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ptCount = _recordedPoints.value.size
        val dist = _totalDistanceMeters.value

        val statusPrefix = if (_isPaused.value) "⏸️ Beklemede" else "🟢 Kaydediliyor"
        val contentText = String.format(
            Locale.getDefault(),
            "%s | Nokta: %d | Mesafe: %.1f m",
            statusPrefix, ptCount, dist
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Su Yolu GPS: ${_fountainName.value}")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    companion object {
        const val ACTION_START = "ACTION_START_GPS_TRACKING"
        const val ACTION_PAUSE = "ACTION_PAUSE_GPS_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_GPS_TRACKING"
        const val EXTRA_FOUNTAIN_NAME = "EXTRA_FOUNTAIN_NAME"
        const val EXTRA_IS_RESUME = "EXTRA_IS_RESUME"
        const val EXTRA_INITIAL_POINT_TYPE = "EXTRA_INITIAL_POINT_TYPE"

        private const val CHANNEL_ID = "su_yolu_gps_channel"
        private const val NOTIFICATION_ID = 1001
        private const val UPDATE_INTERVAL_MS = 3000L
        private const val FASTEST_INTERVAL_MS = 1500L
        private const val MIN_DISTANCE_THRESHOLD_METERS = 10.0

        private var startLat: Double = 0.0
        private var startLng: Double = 0.0

        private val _isTracking = MutableStateFlow(false)
        val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

        private val _isPaused = MutableStateFlow(false)
        val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

        private val _initialPointType = MutableStateFlow<PointType>(PointType.FOUNTAIN)
        val initialPointType: StateFlow<PointType> = _initialPointType.asStateFlow()

        private val _fountainName = MutableStateFlow("Tarihi Çeşme")
        val fountainName: StateFlow<String> = _fountainName.asStateFlow()

        private val _recordedPoints = MutableStateFlow<List<RoutePoint>>(emptyList())
        val recordedPoints: StateFlow<List<RoutePoint>> = _recordedPoints.asStateFlow()

        private val _currentLocation = MutableStateFlow<Location?>(null)
        val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()

        private val _gpsAccuracyMeters = MutableStateFlow<Float?>(null)
        val gpsAccuracyMeters: StateFlow<Float?> = _gpsAccuracyMeters.asStateFlow()

        private val _totalDistanceMeters = MutableStateFlow(0.0)
        val totalDistanceMeters: StateFlow<Double> = _totalDistanceMeters.asStateFlow()

        fun startService(
            context: Context,
            fountainName: String,
            initialPointType: PointType = PointType.FOUNTAIN
        ) {
            val intent = Intent(context, GpsTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FOUNTAIN_NAME, fountainName)
                putExtra(EXTRA_INITIAL_POINT_TYPE, initialPointType.code)
                putExtra(EXTRA_IS_RESUME, false)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Yarım kalan bir boru hattı kaydını (örn. ertesi gün) kaldığı noktadan devam ettirir.
         */
        fun resumeRouteService(context: Context, existingRoute: RouteData) {
            val pts = mutableListOf(
                RoutePoint(
                    lat = existingRoute.startLat,
                    lng = existingRoute.startLng,
                    type = PointType.WAYPOINT
                )
            )
            pts.addAll(existingRoute.points)

            startLat = existingRoute.startLat
            startLng = existingRoute.startLng
            _fountainName.value = existingRoute.fountainName
            _recordedPoints.value = pts
            _totalDistanceMeters.value = existingRoute.calculateTotalDistanceMeters()

            val intent = Intent(context, GpsTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FOUNTAIN_NAME, existingRoute.fountainName)
                putExtra(EXTRA_IS_RESUME, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseService(context: Context) {
            val intent = Intent(context, GpsTrackingService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun continueFromPause(context: Context, fountainName: String) {
            val intent = Intent(context, GpsTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FOUNTAIN_NAME, fountainName)
                putExtra(EXTRA_IS_RESUME, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, GpsTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun clearSessionPoints() {
            _recordedPoints.value = emptyList()
            _totalDistanceMeters.value = 0.0
            _isPaused.value = false
        }

        fun addCriticalPoint(type: PointType, label: String? = null): Boolean {
            val currLoc = _currentLocation.value ?: return false
            val currentPoints = _recordedPoints.value.toMutableList()

            if (currentPoints.isNotEmpty()) {
                val lastPt = currentPoints.last()
                val dist = RouteData.haversineDistance(
                    lastPt.lat, lastPt.lng,
                    currLoc.latitude, currLoc.longitude
                )
                _totalDistanceMeters.value += dist
            } else {
                startLat = currLoc.latitude
                startLng = currLoc.longitude
            }

            currentPoints.add(
                RoutePoint(
                    lat = currLoc.latitude,
                    lng = currLoc.longitude,
                    type = type,
                    label = label ?: type.displayName
                )
            )
            _recordedPoints.value = currentPoints
            return true
        }

        fun undoLastPoint(): Boolean {
            val currentPoints = _recordedPoints.value.toMutableList()
            if (currentPoints.size <= 1) return false
            val removed = currentPoints.removeAt(currentPoints.lastIndex)
            val prev = currentPoints.last()
            val dist = RouteData.haversineDistance(prev.lat, prev.lng, removed.lat, removed.lng)
            _totalDistanceMeters.value = (_totalDistanceMeters.value - dist).coerceAtLeast(0.0)
            _recordedPoints.value = currentPoints
            return true
        }
    }
}
