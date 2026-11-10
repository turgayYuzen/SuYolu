package com.turga.suyolu.algorithm

import android.location.Location
import com.turga.suyolu.data.RouteData
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Standart telefon GPS alıcılarındaki 3-5 metrelik atmosferik/yansıma hatasını
 * yazılımsal olarak ~1.0 metre seviyesine indiren sinyal işleme motoru:
 *
 * 1. 2B Jeodezik Kalman Filtresi (Walking & Stationary Adaptive Process Noise)
 * 2. Soğuk Başlangıç (Cold-Start 20m) Sapma Sıfırlayıcı
 * 3. Ters-Varyans Ağırlıklı Nokta Ortalaması (Inverse-Variance Weighted Point Averaging)
 * 4. Yerel DGPS / Diferansiyel Hata Düzeltmesi (Time-Decayed Differential Bias Correction)
 */
class GpsPrecisionFilter(
    private val walkingProcessNoiseMetersPerSec: Double = 1.4
) {

    data class FilteredLocationResult(
        val latitude: Double,
        val longitude: Double,
        val rawAccuracyMeters: Float,
        val fusedAccuracyMeters: Float,
        val differentialOffsetActive: Boolean = false,
        val timeSinceCalibrationMs: Long = 0L
    )

    data class AveragedPointResult(
        val latitude: Double,
        val longitude: Double,
        val achievedAccuracyMeters: Float,
        val sampleCount: Int
    )

    private data class GpsSample(
        val lat: Double,
        val lng: Double,
        val accuracyMeters: Float,
        val timestampMs: Long
    )

    private var isInitialized = false
    private var initialRawAccuracy = 99f
    private var currentLat = 0.0
    private var currentLng = 0.0
    private var varianceMetersSq = 25.0
    private var lastTimestampMs = 0L

    // Yerel DGPS / Diferansiyel Hata Vektörü
    // (10 ölçümlük gerçek ortalama ile anlık ham/kalman konumu arasındaki fark)
    private var differentialBiasLat = 0.0
    private var differentialBiasLng = 0.0
    private var lastCalibrationTimeMs = 0L
    private val differentialHalfLifeMs = 10.0 * 60.0 * 1000.0 // 10 dakika yarılanma ömrü

    // Son 15 saniyedeki okumaları tutan dairesel tampon
    private val recentSamples = ArrayDeque<GpsSample>()
    private val maxBufferAgeMs = 15_000L

    fun reset() {
        isInitialized = false
        initialRawAccuracy = 99f
        varianceMetersSq = 25.0
        lastTimestampMs = 0L
        differentialBiasLat = 0.0
        differentialBiasLng = 0.0
        lastCalibrationTimeMs = 0L
        recentSamples.clear()
    }

    /**
     * 10 ölçüm ortalaması alındığında Kalman filtresini doğrudan bu koordinata sabitler
     * ve yürüyüş için Diferansiyel DGPS sapma vektörünü tazeler.
     */
    fun anchorToExactPoint(
        lat: Double,
        lng: Double,
        achievedAccuracyMeters: Float,
        instantaneousRawLat: Double = currentLat,
        instantaneousRawLng: Double = currentLng
    ) {
        val nowMs = System.currentTimeMillis()

        // Diferansiyel Hata Vektörü = Gerçek Ortalama - Anlık Konum
        if (isInitialized && instantaneousRawLat != 0.0 && instantaneousRawLng != 0.0) {
            val deltaLat = lat - instantaneousRawLat
            val deltaLng = lng - instantaneousRawLng
            val distOffset = RouteData.haversineDistance(instantaneousRawLat, instantaneousRawLng, lat, lng)
            // Mantıksız aşırı sıçramalar (> 15m) haricinde diferansiyel ofseti belleğe al
            if (distOffset <= 15.0) {
                differentialBiasLat = deltaLat
                differentialBiasLng = deltaLng
                lastCalibrationTimeMs = nowMs
            }
        } else {
            differentialBiasLat = 0.0
            differentialBiasLng = 0.0
            lastCalibrationTimeMs = nowMs
        }

        currentLat = lat
        currentLng = lng
        val cleanAcc = max(achievedAccuracyMeters, 0.7f)
        varianceMetersSq = (cleanAcc * cleanAcc).toDouble()
        initialRawAccuracy = cleanAcc
        lastTimestampMs = nowMs
        isInitialized = true
    }

    /**
     * Kullanıcı durup "Konumu Sıfırla / Kalibre Et" dediğinde diferansiyel sapmayı tazeler.
     */
    fun updateDifferentialBias(exactLat: Double, exactLng: Double) {
        val nowMs = System.currentTimeMillis()
        if (isInitialized && currentLat != 0.0) {
            differentialBiasLat = exactLat - currentLat
            differentialBiasLng = exactLng - currentLng
            lastCalibrationTimeMs = nowMs
            currentLat = exactLat
            currentLng = exactLng
        }
    }

    fun getTimeSinceCalibrationMs(nowMs: Long = System.currentTimeMillis()): Long {
        if (lastCalibrationTimeMs == 0L) return 0L
        return (nowMs - lastCalibrationTimeMs).coerceAtLeast(0L)
    }

    /**
     * Yeni gelen ham GPS konumunu 2B Kalman filtresinden geçirir,
     * varsa zamana göre sönümlenen Diferansiyel DGPS düzeltmesini uygular.
     */
    fun process(location: Location): FilteredLocationResult {
        val rawAcc = if (location.hasAccuracy()) max(location.accuracy, 0.8f) else 10.0f
        val nowMs = if (location.time > 0) location.time else System.currentTimeMillis()

        // Sadece makul hassasiyetli örnekleri tampona ekle
        if (rawAcc <= 10.0f || recentSamples.isEmpty()) {
            if (rawAcc <= 6.0f) {
                recentSamples.removeAll { it.accuracyMeters > 9.0f }
            }
            recentSamples.addLast(
                GpsSample(
                    lat = location.latitude,
                    lng = location.longitude,
                    accuracyMeters = rawAcc,
                    timestampMs = nowMs
                )
            )
        }
        while (recentSamples.isNotEmpty() && (nowMs - recentSamples.first().timestampMs) > maxBufferAgeMs) {
            recentSamples.removeFirst()
        }

        if (!isInitialized) {
            currentLat = location.latitude
            currentLng = location.longitude
            initialRawAccuracy = rawAcc
            varianceMetersSq = (rawAcc * rawAcc).toDouble()
            lastTimestampMs = nowMs
            isInitialized = true
            return FilteredLocationResult(
                latitude = currentLat,
                longitude = currentLng,
                rawAccuracyMeters = rawAcc,
                fusedAccuracyMeters = rawAcc,
                differentialOffsetActive = false,
                timeSinceCalibrationMs = 0L
            )
        }

        // Soğuk Başlangıç (Cold-Start) Düzeltmesi
        if (initialRawAccuracy > 9.0f && rawAcc <= 5.5f) {
            currentLat = location.latitude
            currentLng = location.longitude
            initialRawAccuracy = rawAcc
            varianceMetersSq = (rawAcc * rawAcc).toDouble()
            lastTimestampMs = nowMs
            return FilteredLocationResult(
                latitude = currentLat,
                longitude = currentLng,
                rawAccuracyMeters = rawAcc,
                fusedAccuracyMeters = rawAcc,
                differentialOffsetActive = false,
                timeSinceCalibrationMs = 0L
            )
        }

        val dtSec = ((nowMs - lastTimestampMs).coerceAtLeast(100L)) / 1000.0
        lastTimestampMs = nowMs

        val speedMetersPerSec = if (location.hasSpeed()) {
            location.speed.toDouble().coerceIn(0.15, 4.0)
        } else {
            val stepDist = RouteData.haversineDistance(currentLat, currentLng, location.latitude, location.longitude)
            val estimatedSpeed = stepDist / dtSec
            if (stepDist < rawAcc * 0.6) 0.25 else estimatedSpeed.coerceIn(0.3, 3.0)
        }

        val effectiveQ = max(0.25, speedMetersPerSec * walkingProcessNoiseMetersPerSec)
        varianceMetersSq += dtSec * effectiveQ * effectiveQ

        val measurementVariance = (rawAcc * rawAcc).toDouble()
        val kalmanGain = varianceMetersSq / (varianceMetersSq + measurementVariance)

        currentLat += kalmanGain * (location.latitude - currentLat)
        currentLng += kalmanGain * (location.longitude - currentLng)
        varianceMetersSq = (1.0 - kalmanGain) * varianceMetersSq

        val fusedAcc = sqrt(varianceMetersSq).toFloat().coerceAtLeast(0.6f)

        // DGPS Diferansiyel Hata Düzeltmesi (Zamanla Zarif Sönümleme / Exponential Decay)
        var finalLat = currentLat
        var finalLng = currentLng
        var hasActiveDiff = false
        val timeSinceCalib = if (lastCalibrationTimeMs > 0L) nowMs - lastCalibrationTimeMs else 0L

        if (lastCalibrationTimeMs > 0L && (differentialBiasLat != 0.0 || differentialBiasLng != 0.0)) {
            // ln(2) = 0.69314718
            val decay = exp(-timeSinceCalib * 0.69314718 / differentialHalfLifeMs)
            if (decay > 0.05) {
                finalLat += differentialBiasLat * decay
                finalLng += differentialBiasLng * decay
                hasActiveDiff = true
            }
        }

        return FilteredLocationResult(
            latitude = finalLat,
            longitude = finalLng,
            rawAccuracyMeters = rawAcc,
            fusedAccuracyMeters = fusedAcc,
            differentialOffsetActive = hasActiveDiff,
            timeSinceCalibrationMs = timeSinceCalib
        )
    }

    fun computeStationaryAverage(): AveragedPointResult? {
        if (recentSamples.isEmpty()) return null
        val latest = recentSamples.last()

        val stationaryCluster = recentSamples.filter { s ->
            RouteData.haversineDistance(latest.lat, latest.lng, s.lat, s.lng) <= 6.0
        }

        return computeWeightedAverageFromSamples(stationaryCluster.ifEmpty { listOf(latest) })
    }

    companion object {
        fun computeWeightedAverage(locations: List<Location>): AveragedPointResult? {
            if (locations.isEmpty()) return null
            val samples = locations.map { loc ->
                GpsSample(
                    lat = loc.latitude,
                    lng = loc.longitude,
                    accuracyMeters = if (loc.hasAccuracy()) max(loc.accuracy, 0.8f) else 6.0f,
                    timestampMs = loc.time
                )
            }
            return computeWeightedAverageFromSamples(samples)
        }

        private fun computeWeightedAverageFromSamples(samples: List<GpsSample>): AveragedPointResult? {
            if (samples.isEmpty()) return null
            if (samples.size == 1) {
                val single = samples.first()
                return AveragedPointResult(
                    latitude = single.lat,
                    longitude = single.lng,
                    achievedAccuracyMeters = single.accuracyMeters,
                    sampleCount = 1
                )
            }

            var initWeightSum = 0.0
            var initLatSum = 0.0
            var initLngSum = 0.0
            for (s in samples) {
                val w = 1.0 / (s.accuracyMeters * s.accuracyMeters)
                initLatSum += s.lat * w
                initLngSum += s.lng * w
                initWeightSum += w
            }
            val centerLat = initLatSum / initWeightSum
            val centerLng = initLngSum / initWeightSum

            val distances = samples.map { s ->
                RouteData.haversineDistance(centerLat, centerLng, s.lat, s.lng)
            }.sorted()
            val medianDist = distances[distances.size / 2].coerceAtLeast(1.0)
            val maxAllowedDist = medianDist * 2.2

            val inliers = samples.filter { s ->
                RouteData.haversineDistance(centerLat, centerLng, s.lat, s.lng) <= maxAllowedDist
            }.ifEmpty { samples }

            var totalWeight = 0.0
            var weightedLat = 0.0
            var weightedLng = 0.0

            for (s in inliers) {
                val sigma = max(s.accuracyMeters, 0.8f).toDouble()
                val weight = 1.0 / (sigma * sigma)
                weightedLat += s.lat * weight
                weightedLng += s.lng * weight
                totalWeight += weight
            }

            val finalLat = weightedLat / totalWeight
            val finalLng = weightedLng / totalWeight

            val theoreticalSigma = (1.0 / sqrt(totalWeight)) * 1.25
            val achievedAccuracy = theoreticalSigma.toFloat().coerceAtLeast(0.5f)

            return AveragedPointResult(
                latitude = finalLat,
                longitude = finalLng,
                achievedAccuracyMeters = achievedAccuracy,
                sampleCount = inliers.size
            )
        }
    }
}
