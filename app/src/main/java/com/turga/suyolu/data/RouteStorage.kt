package com.turga.suyolu.data

import android.content.Context
import com.turga.suyolu.algorithm.DeltaCompressor
import org.json.JSONArray
import org.json.JSONObject

data class SavedRouteRecord(
    val id: String,
    val timestampMs: Long,
    val routeData: RouteData,
    val encodedCompact: String
)

object RouteStorage {
    private const val PREF_NAME = "su_yolu_routes_prefs"
    private const val KEY_SAVED_ROUTES = "saved_routes_json"
    private const val KEY_ACTIVE_ROUTE = "active_route_compact"

    fun saveRoute(context: Context, routeData: RouteData): SavedRouteRecord {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val encoded = DeltaCompressor.encode(routeData)

        // Also store as active route so screen rotation / app restart never loses it
        prefs.edit().putString(KEY_ACTIVE_ROUTE, encoded).apply()

        val existing = getSavedRoutes(context).toMutableList()
        // Remove duplicate with same fountain name if updated
        existing.removeAll { it.routeData.fountainName.equals(routeData.fountainName, ignoreCase = true) }

        val record = SavedRouteRecord(
            id = "route_${System.currentTimeMillis()}",
            timestampMs = System.currentTimeMillis(),
            routeData = routeData,
            encodedCompact = encoded
        )
        existing.add(0, record)

        val arr = JSONArray()
        for (item in existing.take(30)) { // Keep up to 30 routes locally
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestampMs", item.timestampMs)
                put("encoded", item.encodedCompact)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_ROUTES, arr.toString()).apply()
        return record
    }

    fun getSavedRoutes(context: Context): List<SavedRouteRecord> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SAVED_ROUTES, null) ?: return emptyList()
        val list = mutableListOf<SavedRouteRecord>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", "route_$i")
                val ts = obj.optLong("timestampMs", 0L)
                val encoded = obj.getString("encoded")
                val decoded = DeltaCompressor.decode(encoded)
                list.add(SavedRouteRecord(id, ts, decoded, encoded))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun deleteRoute(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val remaining = getSavedRoutes(context).filterNot { it.id == id }
        val arr = JSONArray()
        for (item in remaining) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("timestampMs", item.timestampMs)
                put("encoded", item.encodedCompact)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_SAVED_ROUTES, arr.toString()).apply()
    }

    fun getLastActiveRoute(context: Context): RouteData? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val compact = prefs.getString(KEY_ACTIVE_ROUTE, null) ?: return null
        return try {
            DeltaCompressor.decode(compact)
        } catch (_: Exception) {
            null
        }
    }

    fun setActiveRoute(context: Context, routeData: RouteData) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val encoded = DeltaCompressor.encode(routeData)
        prefs.edit().putString(KEY_ACTIVE_ROUTE, encoded).apply()
    }

    /**
     * Generates a realistic sample route ("Dedeler Pınarı - Örnek Hat") so users and Play Store
     * reviewers can immediately test QR generation, DXF/SVG export, and map navigation.
     */
    fun createSampleRoute(): RouteData {
        val startLat = 39.92077
        val startLng = 32.85411
        val samplePoints = listOf(
            RoutePoint(39.92086, 32.85420, PointType.WAYPOINT),
            RoutePoint(39.92095, 32.85431, PointType.WAYPOINT),
            RoutePoint(39.92105, 32.85440, PointType.VALVE, "Ana Maslak / Vana 1"),
            RoutePoint(39.92116, 32.85452, PointType.WAYPOINT),
            RoutePoint(39.92128, 32.85465, PointType.AIR_VALVE, "Yamaç Hava Bacası"),
            RoutePoint(39.92140, 32.85478, PointType.WAYPOINT),
            RoutePoint(39.92152, 32.85492, PointType.WAYPOINT),
            RoutePoint(39.92165, 32.85505, PointType.VALVE, "Dağıtım Vanası 2"),
            RoutePoint(39.92178, 32.85520, PointType.WAYPOINT),
            RoutePoint(39.92192, 32.85535, PointType.SOURCE, "Yukarı Çamlık Su Gözü (Kaynak)")
        )
        return RouteData(
            version = "SY1",
            fountainName = "Dedeler Pınarı (Örnek)",
            startLat = startLat,
            startLng = startLng,
            startPointType = PointType.FOUNTAIN,
            points = samplePoints
        )
    }
}
