package com.turga.suyolu.algorithm

import com.turga.suyolu.data.PointType
import com.turga.suyolu.data.RouteData
import com.turga.suyolu.data.RoutePoint
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class CompressionStats(
    val originalSizeBytes: Int,
    val compressedSizeBytes: Int,
    val compressionRatioPercentage: Float,
    val originalPointCount: Int = 0,
    val simplifiedPointCount: Int = 0
)

object DeltaCompressor {
    private const val PROTOCOL_VERSION = "SY1"
    private const val FACTOR = 100000.0 // 10^5 -> 0.00001 deg precision (~1.1 meter)
    private const val MAX_QR_SAFE_BYTES = 2100 // Safe byte limit for QR ErrorCorrectionLevel.M

    /**
     * Simplifies a route using Ramer-Douglas-Peucker while strictly preserving all critical points
     * (Valves, Air Valves, Sources, Fountains) so that even 10 km routes fit in a single QR code.
     */
    fun simplifyForQr(route: RouteData, initialToleranceMeters: Double = 1.5): RouteData {
        if (route.points.size <= 150) return route

        var tolerance = initialToleranceMeters
        var currentPoints = route.points

        while (tolerance <= 12.0) {
            val allWithStart = mutableListOf(
                RoutePoint(route.startLat, route.startLng, route.startPointType, route.fountainName)
            ).apply { addAll(currentPoints) }

            val simplifiedAll = rdpPreservingCritical(allWithStart, tolerance)
            val candidateRoute = route.copy(points = simplifiedAll.drop(1))
            val encodedBytes = encodeRaw(candidateRoute).toByteArray(Charsets.UTF_8).size

            if (encodedBytes <= MAX_QR_SAFE_BYTES) {
                return candidateRoute
            }
            tolerance += 1.5
        }

        val allWithStart = mutableListOf(
            RoutePoint(route.startLat, route.startLng, route.startPointType, route.fountainName)
        ).apply { addAll(currentPoints) }
        val finalSimplified = rdpPreservingCritical(allWithStart, tolerance)
        return route.copy(points = finalSimplified.drop(1))
    }

    private fun rdpPreservingCritical(points: List<RoutePoint>, epsilonMeters: Double): List<RoutePoint> {
        if (points.size < 3) return points

        // Split at critical points so they are NEVER removed
        for (i in 1 until points.size - 1) {
            if (points[i].type != PointType.WAYPOINT) {
                val left = rdpPreservingCritical(points.subList(0, i + 1), epsilonMeters)
                val right = rdpPreservingCritical(points.subList(i, points.size), epsilonMeters)
                return left.dropLast(1) + right
            }
        }

        // Standard Ramer-Douglas-Peucker on waypoint segment
        var dmax = 0.0
        var index = 0
        val start = points.first()
        val end = points.last()

        for (i in 1 until points.size - 1) {
            val d = perpendicularDistanceMeters(points[i], start, end)
            if (d > dmax) {
                index = i
                dmax = d
            }
        }

        return if (dmax > epsilonMeters) {
            val rec1 = rdpPreservingCritical(points.subList(0, index + 1), epsilonMeters)
            val rec2 = rdpPreservingCritical(points.subList(index, points.size), epsilonMeters)
            rec1.dropLast(1) + rec2
        } else {
            listOf(start, end)
        }
    }

    private fun perpendicularDistanceMeters(pt: RoutePoint, lineStart: RoutePoint, lineEnd: RoutePoint): Double {
        val cosLat = cos(Math.toRadians(lineStart.lat))
        val x0 = (pt.lng - lineStart.lng) * 111320.0 * cosLat
        val y0 = (pt.lat - lineStart.lat) * 111320.0

        val x1 = 0.0
        val y1 = 0.0
        val x2 = (lineEnd.lng - lineStart.lng) * 111320.0 * cosLat
        val y2 = (lineEnd.lat - lineStart.lat) * 111320.0

        val dx = x2 - x1
        val dy = y2 - y1
        val mag = sqrt(dx * dx + dy * dy)
        if (mag < 0.0001) {
            return sqrt(x0 * x0 + y0 * y0)
        }
        return abs(dy * x0 - dx * y0) / mag
    }

    /**
     * Encodes a RouteData object into a highly compressed QR-friendly string.
     * Automatically applies smart RDP simplification if route exceeds QR capacity.
     * Format: "SY1;Çeşme Adı;39.85123,32.78123;10,-8|12,-5V|8,-3K"
     */
    fun encode(route: RouteData): String {
        val raw = encodeRaw(route)
        if (raw.toByteArray(Charsets.UTF_8).size <= MAX_QR_SAFE_BYTES) {
            return raw
        }
        val simplified = simplifyForQr(route)
        return encodeRaw(simplified)
    }

    private fun encodeRaw(route: RouteData): String {
        val sanitizedName = route.fountainName
            .replace(";", " ")
            .replace("|", " ")
            .replace(",", " ")
            .trim()

        val startLatStr = String.format(java.util.Locale.US, "%.5f", route.startLat)
        val startLngStr = String.format(java.util.Locale.US, "%.5f", route.startLng)
        val startTypeCode = if (route.startPointType != PointType.START) {
            route.startPointType.code
        } else ""

        val sb = StringBuilder()
        sb.append(PROTOCOL_VERSION)
            .append(";")
            .append(sanitizedName)
            .append(";")
            .append(startLatStr)
            .append(",")
            .append(startLngStr)
            .append(startTypeCode)
            .append(";")

        var prevLat = route.startLat
        var prevLng = route.startLng

        val deltaTokens = mutableListOf<String>()

        for (point in route.points) {
            val dLatInt = ((point.lat - prevLat) * FACTOR).roundToInt()
            val dLngInt = ((point.lng - prevLng) * FACTOR).roundToInt()

            val token = StringBuilder()
            token.append(dLatInt).append(",").append(dLngInt)

            // Only append code if it's a special critical point (V, H, K, S)
            if (point.type != PointType.WAYPOINT) {
                token.append(point.type.code)
            }

            deltaTokens.add(token.toString())

            // Update accumulated lat/lng using rounded step to avoid drift
            prevLat += dLatInt / FACTOR
            prevLng += dLngInt / FACTOR
        }

        sb.append(deltaTokens.joinToString("|"))
        return sb.toString()
    }

    /**
     * Decodes a compressed QR string back into a RouteData object.
     */
    fun decode(compressed: String): RouteData {
        val parts = compressed.trim().split(";")
        require(parts.size >= 4) { "Geçersiz Su Yolu QR Verisi!" }

        val version = parts[0]
        require(version.startsWith("SY")) { "Desteklenmeyen Su Yolu protokol versiyonu: $version" }
        val fountainName = parts[1]

        val startCoords = parts[2].split(",")
        require(startCoords.size == 2) { "Geçersiz Başlangıç Koordinatı!" }

        val startLat = startCoords[0].toDouble()
        val rawLngStr = startCoords[1]
        val startTypeCodeChar = rawLngStr.lastOrNull { !it.isDigit() && it != '.' && it != '-' && it != '+' }
        val startLng: Double
        val startPointType: PointType

        if (startTypeCodeChar != null) {
            startLng = rawLngStr.dropLast(1).toDouble()
            startPointType = PointType.fromCode(startTypeCodeChar.toString())
        } else {
            startLng = rawLngStr.toDouble()
            startPointType = PointType.START
        }

        val pointsList = mutableListOf<RoutePoint>()

        if (parts[3].isNotBlank()) {
            val deltaTokens = parts[3].split("|")
            var currLat = startLat
            var currLng = startLng

            for (token in deltaTokens) {
                if (token.isBlank()) continue

                // Check for trailing point type character (V, K, H, S)
                val typeCodeChar = token.lastOrNull { !it.isDigit() && it != '-' && it != '+' && it != ',' }
                val numPart: String
                val pointType: PointType

                if (typeCodeChar != null) {
                    numPart = token.substring(0, token.lastIndexOf(typeCodeChar))
                    pointType = PointType.fromCode(typeCodeChar.toString())
                } else {
                    numPart = token
                    pointType = PointType.WAYPOINT
                }

                val dCoords = numPart.split(",")
                if (dCoords.size == 2) {
                    val dLatInt = dCoords[0].toIntOrNull() ?: 0
                    val dLngInt = dCoords[1].toIntOrNull() ?: 0

                    currLat += dLatInt / FACTOR
                    currLng += dLngInt / FACTOR

                    pointsList.add(
                        RoutePoint(
                            lat = currLat,
                            lng = currLng,
                            type = pointType,
                            label = pointType.displayName
                        )
                    )
                }
            }
        }

        return RouteData(
            version = version,
            fountainName = fountainName,
            startLat = startLat,
            startLng = startLng,
            startPointType = startPointType,
            points = pointsList
        )
    }

    /**
     * Calculates compression efficiency metrics compared to standard uncompressed JSON.
     */
    fun calculateStats(route: RouteData, compressedString: String): CompressionStats {
        val rawJsonSb = StringBuilder()
        rawJsonSb.append("{\"fountain\":\"").append(route.fountainName).append("\",\"start\":[")
            .append(route.startLat).append(",").append(route.startLng).append("],\"points\":[")

        route.points.forEachIndexed { idx, p ->
            if (idx > 0) rawJsonSb.append(",")
            rawJsonSb.append("{\"lat\":").append(p.lat).append(",\"lng\":").append(p.lng)
                .append(",\"type\":\"").append(p.type.name).append("\"}")
        }
        rawJsonSb.append("]}")

        val originalBytes = rawJsonSb.toString().toByteArray(Charsets.UTF_8).size
        val compressedBytes = compressedString.toByteArray(Charsets.UTF_8).size

        val ratio = if (originalBytes > 0) {
            ((originalBytes - compressedBytes).toFloat() / originalBytes.toFloat()) * 100f
        } else 0f

        val decodedCount = try {
            decode(compressedString).points.size + 1
        } catch (_: Exception) {
            route.points.size + 1
        }

        return CompressionStats(
            originalSizeBytes = originalBytes,
            compressedSizeBytes = compressedBytes,
            compressionRatioPercentage = ratio.coerceAtLeast(0f),
            originalPointCount = route.points.size + 1,
            simplifiedPointCount = decodedCount
        )
    }
}
