package com.turga.suyolu.data

enum class PointType(val code: String, val displayName: String, val symbol: String) {
    START("S", "Hat Başlangıcı", "📍"),
    WAYPOINT("W", "Hat Noktası", "📍"),
    VALVE("V", "Vana / Maslak", "🚰"),
    AIR_VALVE("H", "Hava Alma Bacası", "🌬️"),
    SOURCE("K", "Su Gözü / Kaynak", "💧"),
    FOUNTAIN("C", "Çeşme", "⛲");

    companion object {
        fun fromCode(code: String): PointType {
            return entries.find { it.code == code || (code == "Ç" && it == FOUNTAIN) } ?: WAYPOINT
        }
    }
}
