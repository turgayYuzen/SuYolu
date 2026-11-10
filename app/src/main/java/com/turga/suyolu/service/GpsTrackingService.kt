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
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.turga.suyolu.MainActivity
import com.turga.suyolu.algorithm.GpsPrecisionFilter
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
                for (rawLoc in result.locations) {
                    handleIncomingLocation(rawLoc)
                }
            }
        }
    }

    private fun handleIncomingLocation(rawLocation: Location) {
        val rawAcc = if (rawLocation.hasAccuracy()) rawLocation.accuracy else 25f
        _rawGpsAccuracyMeters.value = rawAcc

        // Aşırı gürültülü / baz istasyonu kaynaklı (> 25m) sapmaları Kalman filtresine sokmadan ele
        if (rawAcc > MAX_ACCEPTABLE_RAW_ACCURACY_METERS && _currentLocation.value != null) {
            return
        }

        // 1. 2B Jeodezik Kalman Filtresinden geçir (~1m - 1.5m birleşik hassasiyet)
        val filtered = precisionFilter.process(rawLocation)
        val fusedLoc = Location(rawLocation).apply {
            latitude = filtered.latitude
            longitude = filtered.longitude
            accuracy = filtered.fusedAccuracyMeters
        }

        _currentLocation.value = fusedLoc
        _gpsAccuracyMeters.value = filtered.fusedAccuracyMeters

        // 2. Eğer "Hassas Nokta Sabitleme (1m Modu)" açıksa örnek topla
        if (_isAveragingPoint.value) {
            averagingBuffer.add(rawLocation)
            val avg = GpsPrecisionFilter.computeWeightedAverage(averagingBuffer)
            if (avg != null) {
                _averagingSampleCount.value = avg.sampleCount
                _averagingAchievedAccuracy.value = avg.achievedAccuracyMeters
            }
        }

        if (!_isTracking.value) return

        val currentPoints = _recordedPoints.value.toMutableList()

        if (currentPoints.isEmpty()) {
            // İlk başlangıç noktası için Hassasiyet Kapısı (Accuracy Gate):
            // Uydu kilidi henüz çok kaba ise (> 12m) ve henüz 4 okuma toplanmadıysa birkaç saniye bekle
            initialLockBuffer.add(rawLocation)
            if (rawAcc > INITIAL_LOCK_REQUIRED_ACCURACY_METERS && initialLockBuffer.size < 4) {
                return
            }

            val bestStart = GpsPrecisionFilter.computeWeightedAverage(initialLockBuffer)
            val startLatitude = bestStart?.latitude ?: filtered.latitude
            val startLongitude = bestStart?.longitude ?: filtered.longitude
            initialLockBuffer.clear()

            startLat = startLatitude
            startLng = startLongitude
            val initType = _initialPointType.value
            currentPoints.add(
                RoutePoint(
                    lat = startLatitude,
                    lng = startLongitude,
                    type = initType,
                    label = initType.displayName
                )
            )
            _recordedPoints.value = currentPoints
            updateNotification()
        } else {
            // Son kaydedilen noktadan itibaren Kalman-filtrelenmiş mesafeyi kontrol et
            val lastPoint = currentPoints.last()
            val dist = RouteData.haversineDistance(
                lastPoint.lat, lastPoint.lng,
                filtered.latitude, filtered.longitude
            )

            // 10 metre yürüdükçe otomatik ara nokta (Waypoint) kaydet
            if (dist >= MIN_DISTANCE_THRESHOLD_METERS) {
                currentPoints.add(
                    RoutePoint(
                        lat = filtered.latitude,
                        lng = filtered.longitude,
                        type = PointType.WAYPOINT
                    )
                )
                _recordedPoints.value = currentPoints
                _totalDistanceMeters.value += dist
                updateNotification()
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
        initialLockBuffer.clear()

        if (!isResume && _recordedPoints.value.isEmpty()) {
            precisionFilter.reset()
            _recordedPoints.value = emptyList()
            _totalDistanceMeters.value = 0.0
        }

        createNotificationChannel()

        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), serviceType)

        // 1 Hz (1000ms) yüksek frekanslı ve 0m mesafe eşikli GNSS isteği:
        // Kullanıcı vana/çeşme başında sabit dursa bile saniyede 1 kez uydu okuması gelmeye devam eder,
        // böylece Kalman Filtresi ve Nokta Ortalaması (Point Averaging) hatayı ~1 metreye indirir.
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            setMinUpdateDistanceMeters(0f)
            setWaitForAccurateLocation(true)
            setGranularity(Granularity.GRANULARITY_FINE)
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
        cancelPrecisionAveraging()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopForegroundTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _isTracking.value = false
        _isPaused.value = false
        cancelPrecisionAveraging()
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
                description = "Boru hattı GPS koordinatlarını arka planda yüksek hassasiyetle kaydeder."
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
        val acc = _gpsAccuracyMeters.value

        val statusPrefix = if (_isPaused.value) "⏸️ Beklemede" else "🟢 Kaydediliyor"
        val accText = if (acc != null) String.format(Locale.getDefault(), " | ±%.1fm", acc) else ""
        val contentText = String.format(
            Locale.getDefault(),
            "%s | Nokta: %d | %.1f m%s",
            statusPrefix, ptCount, dist, accText
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

        // 1 Hz Yüksek Frekanslı Örnekleme
        private const val UPDATE_INTERVAL_MS = 1000L
        private const val FASTEST_INTERVAL_MS = 500L
        private const val MIN_DISTANCE_THRESHOLD_METERS = 10.0
        private const val MAX_ACCEPTABLE_RAW_ACCURACY_METERS = 25.0f
        private const val INITIAL_LOCK_REQUIRED_ACCURACY_METERS = 12.0f

        private val precisionFilter = GpsPrecisionFilter()
        private val initialLockBuffer = mutableListOf<Location>()
        private val averagingBuffer = mutableListOf<Location>()

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

        // Kalman Filtresi sonrası birleşik (fused) hassasiyet (~1.0m - 1.5m)
        private val _gpsAccuracyMeters = MutableStateFlow<Float?>(null)
        val gpsAccuracyMeters: StateFlow<Float?> = _gpsAccuracyMeters.asStateFlow()

        // Ham sensör hassasiyeti (karşılaştırma için)
        private val _rawGpsAccuracyMeters = MutableStateFlow<Float?>(null)
        val rawGpsAccuracyMeters: StateFlow<Float?> = _rawGpsAccuracyMeters.asStateFlow()

        private val _totalDistanceMeters = MutableStateFlow(0.0)
        val totalDistanceMeters: StateFlow<Double> = _totalDistanceMeters.asStateFlow()

        // Hassas Nokta Sabitleme (Point Averaging / ~1m Modu) State'leri
        private val _isAveragingPoint = MutableStateFlow(false)
        val isAveragingPoint: StateFlow<Boolean> = _isAveragingPoint.asStateFlow()

        private val _averagingSampleCount = MutableStateFlow(0)
        val averagingSampleCount: StateFlow<Int> = _averagingSampleCount.asStateFlow()

        private val _averagingAchievedAccuracy = MutableStateFlow<Float?>(null)
        val averagingAchievedAccuracy: StateFlow<Float?> = _averagingAchievedAccuracy.asStateFlow()

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
                    type = existingRoute.startPointType,
                    label = existingRoute.startPointType.displayName
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
            precisionFilter.reset()
            initialLockBuffer.clear()
            cancelPrecisionAveraging()
            _recordedPoints.value = emptyList()
            _totalDistanceMeters.value = 0.0
            _isPaused.value = false
        }

        /**
         * Kritik nokta için ~1m Hassas Sabitleme (Point Averaging) oturumunu başlatır.
         */
        fun startPrecisionAveraging() {
            averagingBuffer.clear()
            _currentLocation.value?.let { curr ->
                averagingBuffer.add(curr)
                _averagingSampleCount.value = 1
                _averagingAchievedAccuracy.value = curr.accuracy
            } ?: run {
                _averagingSampleCount.value = 0
                _averagingAchievedAccuracy.value = null
            }
            _isAveragingPoint.value = true
        }

        fun cancelPrecisionAveraging() {
            _isAveragingPoint.value = false
            averagingBuffer.clear()
            _averagingSampleCount.value = 0
            _averagingAchievedAccuracy.value = null
        }

        /**
         * Hassas Sabitleme oturumunda toplanan örneklerin ağırlıklı ortalamasını alarak
         * kritik noktayı (~1m doğrulukla) kaydeder.
         */
        fun commitPrecisionAveragedPoint(
            type: PointType,
            label: String? = null
        ): GpsPrecisionFilter.AveragedPointResult? {
            val averaged = GpsPrecisionFilter.computeWeightedAverage(averagingBuffer)
                ?: precisionFilter.computeStationaryAverage()
            cancelPrecisionAveraging()

            if (averaged == null) return null
            savePointInternal(averaged.latitude, averaged.longitude, type, label)
            return averaged
        }

        /**
         * Hızlı elle işaretleme: Son birkaç saniyedeki sabit duruş tamponunun ağırlıklı ortalamasını
         * veya Kalman filtrelenmiş anlık konumu kaydeder.
         */
        fun addCriticalPoint(type: PointType, label: String? = null): Boolean {
            val stationaryAvg = precisionFilter.computeStationaryAverage()
            val currLoc = _currentLocation.value
            val targetLat = stationaryAvg?.latitude ?: currLoc?.latitude ?: return false
            val targetLng = stationaryAvg?.longitude ?: currLoc?.longitude ?: return false

            savePointInternal(targetLat, targetLng, type, label)
            return true
        }

        private fun savePointInternal(lat: Double, lng: Double, type: PointType, label: String?) {
            val currentPoints = _recordedPoints.value.toMutableList()
            if (currentPoints.isNotEmpty()) {
                val lastPt = currentPoints.last()
                val dist = RouteData.haversineDistance(lastPt.lat, lastPt.lng, lat, lng)
                _totalDistanceMeters.value += dist
            } else {
                startLat = lat
                startLng = lng
            }

            currentPoints.add(
                RoutePoint(
                    lat = lat,
                    lng = lng,
                    type = type,
                    label = label ?: type.displayName
                )
            )
            _recordedPoints.value = currentPoints
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
