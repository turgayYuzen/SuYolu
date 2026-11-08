package com.turga.suyolu.algorithm

import com.turga.suyolu.data.PointType
import com.turga.suyolu.data.RouteData
import com.turga.suyolu.data.RoutePoint

/**
 * İmece Modu: Farklı kişilerin veya farklı günlerde kaydedilen boru hattı parçalarını
 * (biri çeşmeden, biri kaynaktan ters yönde gelmiş olsa bile) otomatik olarak doğru sıraya dizip
 * uçtan uca tek bir boru hattı (RouteData) haline getiren akıllı algoritma.
 */
object RouteMerger {

    private data class Segment(
        val originalName: String,
        val points: List<RoutePoint>
    ) {
        val first: RoutePoint get() = points.first()
        val last: RoutePoint get() = points.last()

        fun reversed(): Segment = copy(points = points.reversed())
    }

    /**
     * Birden fazla RouteData parçasını alır, başlangıç noktası Çeşme (veya ilk seçilen parça) olacak şekilde
     * en yakın uçları birbirine bağlar ve ters yürünmüş parçaları otomatik düzeltir.
     */
    fun mergeSegments(
        fountainTitle: String,
        routes: List<RouteData>
    ): RouteData {
        require(routes.isNotEmpty()) { "Birleştirilecek en az 1 parça olmalıdır!" }
        if (routes.size == 1) {
            return routes.first().copy(fountainName = fountainTitle)
        }

        // Her RouteData'yı tam nokta listesine (Start + Points) dönüştür
        val rawSegments = routes.mapNotNull { r ->
            val allPts = mutableListOf<RoutePoint>()
            allPts.add(RoutePoint(r.startLat, r.startLng, r.startPointType, r.fountainName))
            allPts.addAll(r.points)
            if (allPts.isEmpty()) null else Segment(r.fountainName, allPts)
        }.toMutableList()

        if (rawSegments.isEmpty()) return routes.first()

        // 1. Çeşme (Başlangıç) parçasını bul:
        // İçinde FOUNTAIN veya START olup SOURCE (Kaynak) ile başlamayan ilk parçayı baz al
        var startSegIndex = rawSegments.indexOfFirst { seg ->
            seg.first.type == PointType.FOUNTAIN || seg.first.type == PointType.START
        }.coerceAtLeast(0)

        var currentChain = rawSegments.removeAt(startSegIndex).points.toMutableList()

        // Eğer ilk parçanın ilk noktası Su Gözü (SOURCE) ve son noktası Çeşme (FOUNTAIN) ise ters çevir
        if (currentChain.first().type == PointType.SOURCE && currentChain.last().type == PointType.FOUNTAIN) {
            currentChain = currentChain.reversed().toMutableList()
        }

        // 2. Kalan parçaları en yakın uç noktasına göre (düz veya ters çevirerek) zincire ekle
        while (rawSegments.isNotEmpty()) {
            val chainEnd = currentChain.last()
            val chainStart = currentChain.first()

            var bestSegIndex = 0
            var bestMode = 0 // 0: Append Normal, 1: Append Reversed, 2: Prepend Normal, 3: Prepend Reversed
            var minDistance = Double.MAX_VALUE

            for ((idx, seg) in rawSegments.withIndex()) {
                // Durum 0: Zincir Sonu -> Parça Başı (Normal devam)
                val d0 = RouteData.haversineDistance(chainEnd.lat, chainEnd.lng, seg.first.lat, seg.first.lng)
                if (d0 < minDistance) {
                    minDistance = d0
                    bestSegIndex = idx
                    bestMode = 0
                }

                // Durum 1: Zincir Sonu -> Parça Sonu (Karşıdan / Kaynaktan gelip ortada buluşma!)
                val d1 = RouteData.haversineDistance(chainEnd.lat, chainEnd.lng, seg.last.lat, seg.last.lng)
                if (d1 < minDistance) {
                    minDistance = d1
                    bestSegIndex = idx
                    bestMode = 1
                }

                // Durum 2: Parça Sonu -> Zincir Başı (Zincirin önüne normal ekleme)
                val d2 = RouteData.haversineDistance(seg.last.lat, seg.last.lng, chainStart.lat, chainStart.lng)
                if (d2 < minDistance) {
                    minDistance = d2
                    bestSegIndex = idx
                    bestMode = 2
                }

                // Durum 3: Parça Başı -> Zincir Başı (Zincirin önüne ters ekleme)
                val d3 = RouteData.haversineDistance(seg.first.lat, seg.first.lng, chainStart.lat, chainStart.lng)
                if (d3 < minDistance) {
                    minDistance = d3
                    bestSegIndex = idx
                    bestMode = 3
                }
            }

            val chosen = rawSegments.removeAt(bestSegIndex)
            when (bestMode) {
                0 -> appendCleaned(currentChain, chosen.points)
                1 -> appendCleaned(currentChain, chosen.reversed().points)
                2 -> {
                    val newChain = chosen.points.toMutableList()
                    appendCleaned(newChain, currentChain)
                    currentChain = newChain
                }
                3 -> {
                    val newChain = chosen.reversed().points.toMutableList()
                    appendCleaned(newChain, currentChain)
                    currentChain = newChain
                }
            }
        }

        // Eğer birleşmiş tüm hattın sonunda Çeşme, başında Kaynak kaldıysa tüm hattı Çeşme -> Kaynak yönüne çevir
        if (currentChain.first().type == PointType.SOURCE && currentChain.last().type != PointType.SOURCE) {
            currentChain.reverse()
        }

        // Ara bağlantı noktalarında kalan eski START etiketlerini normal WAYPOINT yap
        val finalStart = currentChain.first()
        val finalSubPoints = currentChain.drop(1).mapIndexed { index, pt ->
            if (pt.type == PointType.START && index < currentChain.size - 2) {
                pt.copy(type = PointType.WAYPOINT)
            } else {
                pt
            }
        }

        return RouteData(
            version = "SY1",
            fountainName = fountainTitle.ifBlank { "İmece Su Hattı" },
            startLat = finalStart.lat,
            startLng = finalStart.lng,
            startPointType = finalStart.type,
            points = finalSubPoints
        )
    }

    /**
     * İki parça ortada buluştuğunda (5 metreden daha yakın mükerrer buluşma noktası varsa)
     * üst üste binmeyi önler, kritik nokta etiketini korur.
     */
    private fun appendCleaned(target: MutableList<RoutePoint>, addition: List<RoutePoint>) {
        if (addition.isEmpty()) return
        if (target.isEmpty()) {
            target.addAll(addition)
            return
        }

        val lastPt = target.last()
        val firstAdd = addition.first()
        val gapMeters = RouteData.haversineDistance(lastPt.lat, lastPt.lng, firstAdd.lat, firstAdd.lng)

        if (gapMeters < 5.0) {
            // Buluşma noktası çakışıyor: eğer yeni gelen nokta kritik bir noktaysa (Vana vs.) onu koru
            if (lastPt.type == PointType.WAYPOINT && firstAdd.type != PointType.WAYPOINT && firstAdd.type != PointType.START) {
                target[target.lastIndex] = firstAdd
            }
            target.addAll(addition.drop(1))
        } else {
            target.addAll(addition)
        }
    }
}
