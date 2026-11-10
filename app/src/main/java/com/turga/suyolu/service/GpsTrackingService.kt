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

        // Aşırı gürültülü (> 25m) okumaları mevcut kilit varken yoksay
        if (rawAcc > MAX_ACCEPTABLE_RAW_ACCURACY_METERS && _currentLocation.value != null) {
            return
        }

        // 1. 2B Jeodezik Kalman Filtresi + Yerel DGPS Diferansiyel Düzeltmesi
        val filtered = precisionFilter.process(rawLocation)
        val fusedLoc = Location(rawLocation).apply {
            latitude = filtered.latitude
            longitude = filtered.longitude
            accuracy = filtered.fusedAccuracyMeters
        }

        _currentLocation.value = fusedLoc
        _gpsAccuracyMeters.value = filtered.fusedAccuracyMeters
        _isDgpsActive.value = filtered.differentialOffsetActive

        val minutesSinceCalib = (filtered.timeSinceCalibrationMs / (60 * 1000L)).toInt()
        _timeSinceCalibrationMinutes.value = minutesSinceCalib

        // 5 dakikadan uzun süredir sıfırlama yapılmadıysa ve kullanıcı yürüyüşteyse uyarı tetikle
        if (
            _isTracking.value &&
            _recordedPoints.value.isNotEmpty() &&
            !_isCalibratingStartPoint.value &&
            !_isAveragingPoint.value &&
            !_isRecalibratingDgps.value &&
            !_hasSnoozedReminder.value &&
            minutesSinceCalib >= RECALIBRATION_INTERVAL_MINUTES
        ) {
            _needsRecalibrationPrompt.value = true
        }

        // 2. Yürüyüş Sırasında Ara Sıfırlama (DGPS Recalibration) Örneği Toplama
        if (_isRecalibratingDgps.value && rawAcc <= 8.0f) {
            recalibrationBuffer.add(rawLocation)
            val avg = GpsPrecisionFilter.computeWeightedAverage(recalibrationBuffer)
            if (avg != null) {
                _recalibrationSampleCount.value = avg.sampleCount
                _recalibrationAchievedAccuracy.value = avg.achievedAccuracyMeters
            }
        }

        // 3. Kritik Nokta 10sn Sabitleme (Vana / Su Gözü / Baca eklerken) örneği toplama
        if (_isAveragingPoint.value && rawAcc <= 8.0f) {
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
            // =========================================================================
            // 2 AŞAMALI BAŞLANGIÇ NOKTASI KALİBRASYONU (20m Soğuk Başlangıç Koruması)
            // =========================================================================
            _isCalibratingStartPoint.value = true

            val isHighPrecisionReached =
                (filtered.fusedAccuracyMeters <= TARGET_START_FUSED_ACCURACY_METERS && rawAcc <= 6.5f) ||
                (initialLockBuffer.isNotEmpty() && rawAcc <= 6.5f)

            if (!isHighPrecisionReached) {
                initialLockBuffer.clear()
                _startCalibrationSamples.value = 0
                _startCalibrationAccuracy.value = filtered.fusedAccuracyMeters
                updateNotification()
                return
            }

            initialLockBuffer.add(rawLocation)
            val avg = GpsPrecisionFilter.computeWeightedAverage(initialLockBuffer)
            _startCalibrationSamples.value = initialLockBuffer.size
            _startCalibrationAccuracy.value = avg?.achievedAccuracyMeters ?: filtered.fusedAccuracyMeters

            if (initialLockBuffer.size >= TARGET_START_SAMPLE_COUNT && avg != null) {
                lockAndSaveStartPoint(avg.latitude, avg.longitude, avg.achievedAccuracyMeters, rawLocation)
            } else {
                updateNotification()
            }
        } else {
            // Başlangıç noktası sabitlendi, şimdi yürüyüş boyunca her 10 metrede bir ara nokta kaydet
            val lastPoint = currentPoints.last()
            val dist = RouteData.haversineDistance(
                lastPoint.lat, lastPoint.lng,
                filtered.latitude, filtered.longitude
            )

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

    private fun lockAndSaveStartPoint(lat: Double, lng: Double, achievedAcc: Float, rawRef: Location? = null) {
        val rawLat = rawRef?.latitude ?: _currentLocation.value?.latitude ?: lat
        val rawLng = rawRef?.longitude ?: _currentLocation.value?.longitude ?: lng

        precisionFilter.anchorToExactPoint(
            lat = lat,
            lng = lng,
            achievedAccuracyMeters = achievedAcc,
            instantaneousRawLat = rawLat,
            instantaneousRawLng = rawLng
        )
        initialLockBuffer.clear()
        _isCalibratingStartPoint.value = false
        _startCalibrationSamples.value = TARGET_START_SAMPLE_COUNT
        _startCalibrationAccuracy.value = achievedAcc
        _needsRecalibrationPrompt.value = false
        _hasSnoozedReminder.value = false

        startLat = lat
        startLng = lng
        val initType = _initialPointType.value
        val currentPoints = mutableListOf(
            RoutePoint(
                lat = lat,
                lng = lng,
                type = initType,
                label = initType.displayName
            )
        )
        _recordedPoints.value = currentPoints
        _currentLocation.value = _currentLocation.value?.apply {
            latitude = lat
            longitude = lng
            accuracy = achievedAcc
        }
        _gpsAccuracyMeters.value = achievedAcc
        updateNotification()
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
        _needsRecalibrationPrompt.value = false
        _hasSnoozedReminder.value = false

        if (!isResume && _recordedPoints.value.isEmpty()) {
            precisionFilter.reset()
            _recordedPoints.value = emptyList()
            _totalDistanceMeters.value = 0.0
            _isCalibratingStartPoint.value = true
            _startCalibrationSamples.value = 0
            _startCalibrationAccuracy.value = _gpsAccuracyMeters.value
        } else {
            _isCalibratingStartPoint.value = false
        }

        createNotificationChannel()

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
        _isCalibratingStartPoint.value = false
        cancelPrecisionAveraging()
        cancelDgpsRecalibration()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopForegroundTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _isTracking.value = false
        _isPaused.value = false
        _isCalibratingStartPoint.value = false
        cancelPrecisionAveraging()
        cancelDgpsRecalibration()
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

        val contentText = if (_isCalibratingStartPoint.value) {
            val samples = _startCalibrationSamples.value
            val accStr = if (acc != null) String.format(Locale.getDefault(), "±%.1fm", acc) else "Bekleniyor"
            if (samples == 0) {
                "🛰️ Başlangıç için 1m uydu kilidi bekleniyor ($accStr)..."
            } else {
                "🎯 Başlangıç sabitleniyor: $samples/$TARGET_START_SAMPLE_COUNT ($accStr)"
            }
        } else {
            val statusPrefix = if (_isPaused.value) "⏸️ Beklemede" else "🟢 Kaydediliyor"
            val dgpsTag = if (_isDgpsActive.value) " (DGPS)" else ""
            val accText = if (acc != null) String.format(Locale.getDefault(), " | ±%.1fm%s", acc, dgpsTag) else ""
            String.format(
                Locale.getDefault(),
                "%s | Nokta: %d | %.1f m%s",
                statusPrefix, ptCount, dist, accText
            )
        }

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

        // 1 Hz Ana Döngü + 200ms (5Hz) Hızlı Yakalama
        private const val UPDATE_INTERVAL_MS = 1000L
        private const val FASTEST_INTERVAL_MS = 200L
        private const val MIN_DISTANCE_THRESHOLD_METERS = 10.0
        private const val MAX_ACCEPTABLE_RAW_ACCURACY_METERS = 25.0f

        // Başlangıç Noktası 1m Kalibrasyon Hedefleri
        const val TARGET_START_FUSED_ACCURACY_METERS = 2.0f
        const val TARGET_START_SAMPLE_COUNT = 10
        const val RECALIBRATION_INTERVAL_MINUTES = 5

        private val precisionFilter = GpsPrecisionFilter()
        private val initialLockBuffer = mutableListOf<Location>()
        private val averagingBuffer = mutableListOf<Location>()
        private val recalibrationBuffer = mutableListOf<Location>()

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

        private val _rawGpsAccuracyMeters = MutableStateFlow<Float?>(null)
        val rawGpsAccuracyMeters: StateFlow<Float?> = _rawGpsAccuracyMeters.asStateFlow()

        private val _totalDistanceMeters = MutableStateFlow(0.0)
        val totalDistanceMeters: StateFlow<Double> = _totalDistanceMeters.asStateFlow()

        // Başlangıç Noktası 2 Aşamalı Kalibrasyon State'leri
        private val _isCalibratingStartPoint = MutableStateFlow(false)
        val isCalibratingStartPoint: StateFlow<Boolean> = _isCalibratingStartPoint.asStateFlow()

        private val _startCalibrationSamples = MutableStateFlow(0)
        val startCalibrationSamples: StateFlow<Int> = _startCalibrationSamples.asStateFlow()

        private val _startCalibrationAccuracy = MutableStateFlow<Float?>(null)
        val startCalibrationAccuracy: StateFlow<Float?> = _startCalibrationAccuracy.asStateFlow()

        // Yerel DGPS ve Periyodik Sıfırlama Hatırlatıcı State'leri
        private val _isDgpsActive = MutableStateFlow(false)
        val isDgpsActive: StateFlow<Boolean> = _isDgpsActive.asStateFlow()

        private val _timeSinceCalibrationMinutes = MutableStateFlow(0)
        val timeSinceCalibrationMinutes: StateFlow<Int> = _timeSinceCalibrationMinutes.asStateFlow()

        private val _needsRecalibrationPrompt = MutableStateFlow(false)
        val needsRecalibrationPrompt: StateFlow<Boolean> = _needsRecalibrationPrompt.asStateFlow()

        private val _hasSnoozedReminder = MutableStateFlow(false)

        // Yürüyüş Sırasında DGPS Sıfırlama (10sn Durarak Konum Tazeleme)
        private val _isRecalibratingDgps = MutableStateFlow(false)
        val isRecalibratingDgps: StateFlow<Boolean> = _isRecalibratingDgps.asStateFlow()

        private val _recalibrationSampleCount = MutableStateFlow(0)
        val recalibrationSampleCount: StateFlow<Int> = _recalibrationSampleCount.asStateFlow()

        private val _recalibrationAchievedAccuracy = MutableStateFlow<Float?>(null)
        val recalibrationAchievedAccuracy: StateFlow<Float?> = _recalibrationAchievedAccuracy.asStateFlow()

        // Ara Kritik Nokta Hassas Sabitleme (Point Averaging) State'leri
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

        fun forceLockStartPointNow(): Boolean {
            val avg = GpsPrecisionFilter.computeWeightedAverage(initialLockBuffer)
                ?: precisionFilter.computeStationaryAverage()
            val curr = _currentLocation.value
            val finalLat = avg?.latitude ?: curr?.latitude ?: return false
            val finalLng = avg?.longitude ?: curr?.longitude ?: return false
            val finalAcc = avg?.achievedAccuracyMeters ?: curr?.accuracy ?: 3.0f

            precisionFilter.anchorToExactPoint(finalLat, finalLng, finalAcc, finalLat, finalLng)
            initialLockBuffer.clear()
            _isCalibratingStartPoint.value = false
            _startCalibrationSamples.value = TARGET_START_SAMPLE_COUNT
            _startCalibrationAccuracy.value = finalAcc

            startLat = finalLat
            startLng = finalLng
            val initType = _initialPointType.value
            _recordedPoints.value = listOf(
                RoutePoint(
                    lat = finalLat,
                    lng = finalLng,
                    type = initType,
                    label = initType.displayName
                )
            )
            return true
        }

        /**
         * 5 dakika dolduğunda kullanıcı durup "10sn Konumu Sıfırla" butonuna bastığında çağrılır.
         */
        fun startDgpsRecalibration() {
            recalibrationBuffer.clear()
            _currentLocation.value?.let { curr ->
                recalibrationBuffer.add(curr)
                _recalibrationSampleCount.value = 1
                _recalibrationAchievedAccuracy.value = curr.accuracy
            } ?: run {
                _recalibrationSampleCount.value = 0
                _recalibrationAchievedAccuracy.value = null
            }
            _isRecalibratingDgps.value = true
            _needsRecalibrationPrompt.value = false
        }

        fun cancelDgpsRecalibration() {
            _isRecalibratingDgps.value = false
            recalibrationBuffer.clear()
            _recalibrationSampleCount.value = 0
            _recalibrationAchievedAccuracy.value = null
        }

        fun commitDgpsRecalibration(): GpsPrecisionFilter.AveragedPointResult? {
            val avg = GpsPrecisionFilter.computeWeightedAverage(recalibrationBuffer)
                ?: precisionFilter.computeStationaryAverage()
            cancelDgpsRecalibration()
            if (avg == null) return null

            precisionFilter.updateDifferentialBias(avg.latitude, avg.longitude)
            _hasSnoozedReminder.value = false
            _needsRecalibrationPrompt.value = false
            _timeSinceCalibrationMinutes.value = 0
            return avg
        }

        fun snoozeRecalibrationReminder() {
            _needsRecalibrationPrompt.value = false
            _hasSnoozedReminder.value = true
        }

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
            _isCalibratingStartPoint.value = false

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
            _isCalibratingStartPoint.value = false
            _startCalibrationSamples.value = 0
            _startCalibrationAccuracy.value = null
            cancelPrecisionAveraging()
            cancelDgpsRecalibration()
            _needsRecalibrationPrompt.value = false
            _hasSnoozedReminder.value = false
            _recordedPoints.value = emptyList()
            _totalDistanceMeters.value = 0.0
            _isPaused.value = false
        }

        fun startPrecisionAveraging() {
            averagingBuffer.clear()
            _currentLocation.value?.let { curr ->
                if ((_rawGpsAccuracyMeters.value ?: 99f) <= 8.0f) {
                    averagingBuffer.add(curr)
                    _averagingSampleCount.value = 1
                    _averagingAchievedAccuracy.value = curr.accuracy
                }
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

        fun commitPrecisionAveragedPoint(
            type: PointType,
            label: String? = null
        ): GpsPrecisionFilter.AveragedPointResult? {
            val averaged = GpsPrecisionFilter.computeWeightedAverage(averagingBuffer)
                ?: precisionFilter.computeStationaryAverage()
            cancelPrecisionAveraging()

            if (averaged == null) return null
            precisionFilter.anchorToExactPoint(averaged.latitude, averaged.longitude, averaged.achievedAccuracyMeters)
            _needsRecalibrationPrompt.value = false
            _hasSnoozedReminder.value = false
            _timeSinceCalibrationMinutes.value = 0
            savePointInternal(averaged.latitude, averaged.longitude, type, label)
            return averaged
        }

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
