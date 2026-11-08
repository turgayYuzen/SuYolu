package com.turga.suyolu.data

data class RoutePoint(
    val lat: Double,
    val lng: Double,
    val type: PointType = PointType.WAYPOINT,
    val label: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
