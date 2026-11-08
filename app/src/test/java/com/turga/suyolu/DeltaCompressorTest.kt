package com.turga.suyolu

import com.turga.suyolu.algorithm.DeltaCompressor
import com.turga.suyolu.data.PointType
import com.turga.suyolu.data.RouteData
import com.turga.suyolu.data.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeltaCompressorTest {

    @Test
    fun testEncodeAndDecodeCycle() {
        val originalRoute = RouteData(
            version = "SY1",
            fountainName = "Dedeler Pınarı",
            startLat = 39.85123,
            startLng = 32.78123,
            points = listOf(
                RoutePoint(lat = 39.85133, lng = 32.78115, type = PointType.WAYPOINT),
                RoutePoint(lat = 39.85145, lng = 32.78110, type = PointType.VALVE),
                RoutePoint(lat = 39.85153, lng = 32.78107, type = PointType.SOURCE)
            )
        )

        // Encode
        val compressed = DeltaCompressor.encode(originalRoute)
        println("Sıkıştırılmış String: $compressed")

        // Format check
        assertTrue(compressed.startsWith("SY1;Dedeler Pınarı;39.85123,32.78123;"))
        assertTrue(compressed.contains("V"))
        assertTrue(compressed.contains("K"))

        // Decode
        val decodedRoute = DeltaCompressor.decode(compressed)

        // Verify
        assertEquals("SY1", decodedRoute.version)
        assertEquals("Dedeler Pınarı", decodedRoute.fountainName)
        assertEquals(39.85123, decodedRoute.startLat, 0.00001)
        assertEquals(32.78123, decodedRoute.startLng, 0.00001)

        assertEquals(3, decodedRoute.points.size)
        assertEquals(PointType.WAYPOINT, decodedRoute.points[0].type)
        assertEquals(PointType.VALVE, decodedRoute.points[1].type)
        assertEquals(PointType.SOURCE, decodedRoute.points[2].type)

        assertEquals(39.85153, decodedRoute.points[2].lat, 0.0001)
        assertEquals(32.78107, decodedRoute.points[2].lng, 0.0001)

        // Compression stats
        val stats = DeltaCompressor.calculateStats(originalRoute, compressed)
        println("Orijinal Bayt: ${stats.originalSizeBytes}, Sıkıştırılmış Bayt: ${stats.compressedSizeBytes}, Tasarruf: %${stats.compressionRatioPercentage}")
        assertTrue(stats.compressionRatioPercentage > 0f)
    }
}
