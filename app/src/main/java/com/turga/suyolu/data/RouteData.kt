package com.turga.suyolu.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class PipeProximityInfo(
    val distanceMeters: Double,
    val nearestLat: Double,
    val nearestLng: Double,
    val bearingDegrees: Double,
    val directionLabel: String
)

data class RouteData(
    val version: String = "SY1",
    val fountainName: String,
    val startLat: Double,
    val startLng: Double,
    val startPointType: PointType = PointType.START,
    val points: List<RoutePoint>
) {
    /**
     * Calculates the total route length in meters using Haversine formula.
     */
    fun calculateTotalDistanceMeters(): Double {
        if (points.isEmpty()) return 0.0
        var totalDist = 0.0
        var prevLat = startLat
        var prevLng = startLng

        for (p in points) {
            totalDist += haversineDistance(prevLat, prevLng, p.lat, p.lng)
            prevLat = p.lat
            prevLng = p.lng
        }
        return totalDist
    }

    /**
     * Finds the shortest perpendicular distance from the user's current GPS position
     * to any segment of the water pipe polyline, plus the compass direction to walk.
     */
    fun findNearestPipeProximity(userLat: Double, userLng: Double): PipeProximityInfo {
        val allPts = mutableListOf(RoutePoint(startLat, startLng, startPointType))
        allPts.addAll(points)

        if (allPts.size == 1) {
            val d = haversineDistance(userLat, userLng, startLat, startLng)
            val brg = bearingDegrees(userLat, userLng, startLat, startLng)
            return PipeProximityInfo(d, startLat, startLng, brg, compassDirectionTr(brg))
        }

        val cosLat = cos(Math.toRadians(userLat))
        var minDist = Double.MAX_VALUE
        var bestLat = startLat
        var bestLng = startLng

        for (i in 0 until allPts.size - 1) {
            val a = allPts[i]
            val b = allPts[i + 1]

            // Convert to local meter coordinates relative to A
            val bx = (b.lng - a.lng) * 111320.0 * cosLat
            val by = (b.lat - a.lat) * 111320.0
            val px = (userLng - a.lng) * 111320.0 * cosLat
            val py = (userLat - a.lat) * 111320.0

            val segLenSq = bx * bx + by * by
            val t = if (segLenSq > 0.0001) {
                ((px * bx + py * by) / segLenSq).coerceIn(0.0, 1.0)
            } else {
                0.0
            }

            val projLat = a.lat + t * (b.lat - a.lat)
            val projLng = a.lng + t * (b.lng - a.lng)
            val dist = haversineDistance(userLat, userLng, projLat, projLng)

            if (dist < minDist) {
                minDist = dist
                bestLat = projLat
                bestLng = projLng
            }
        }

        val brg = bearingDegrees(userLat, userLng, bestLat, bestLng)
        return PipeProximityInfo(
            distanceMeters = minDist,
            nearestLat = bestLat,
            nearestLng = bestLng,
            bearingDegrees = brg,
            directionLabel = compassDirectionTr(brg)
        )
    }

    companion object {
        fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0 // Earth radius in meters
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return r * c
        }

        fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val phi1 = Math.toRadians(lat1)
            val phi2 = Math.toRadians(lat2)
            val dLon = Math.toRadians(lon2 - lon1)
            val y = sin(dLon) * cos(phi2)
            val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
            return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        }

        fun compassDirectionTr(bearing: Double): String {
            return when (((bearing + 22.5) % 360.0 / 45.0).toInt()) {
                0 -> "Kuzey ⬆️"
                1 -> "Kuzeydoğu ↗️"
                2 -> "Doğu ➡️"
                3 -> "Güneydoğu ↘️"
                4 -> "Güney ⬇️"
                5 -> "Güneybatı ↙️"
                6 -> "Batı ⬅️"
                else -> "Kuzeybatı ↖️"
            }
        }
    }
}
