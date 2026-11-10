package com.turga.suyolu.algorithm

import com.turga.suyolu.data.PointType
import com.turga.suyolu.data.RouteData
import com.turga.suyolu.data.RoutePoint

/**
 * İmece & Hassas Hat Birleştirme Motoru:
 * 1. Uçtan Uca Birleştirme (Stitching): Farklı kişilerin/günlerin kaydettiği ardışık parçaları
 *    (ters yönde yürünmüş olsa bile) doğru sıraya dizip tek bir hat yapar.
 * 2. Çoklu Yürüyüş Ortalaması (Multi-Pass Averaging): Aynı boru hattı gidiş-dönüş veya
 *    birden fazla telefonla kaydedildiğinde, üst üste binen hatların ortalamasını alarak
 *    GPS sapmasını ~1 metre seviyesine indirir.
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

    fun mergeSegments(
        fountainTitle: String,
        routes: List<RouteData>
    ): RouteData {
        require(routes.isNotEmpty()) { "Birleştirilecek en az 1 parça olmalıdır!" }
        if (routes.size == 1) {
            return routes.first().copy(fountainName = fountainTitle)
        }

        val rawSegments = routes.mapNotNull { r ->
            val allPts = mutableListOf<RoutePoint>()
            allPts.add(RoutePoint(r.startLat, r.startLng, r.startPointType, r.fountainName))
            allPts.addAll(r.points)
            if (allPts.isEmpty()) null else Segment(r.fountainName, allPts)
        }.toMutableList()

        if (rawSegments.isEmpty()) return routes.first()

        // 1. Çeşme (Başlangıç) parçasını bul
        val startSegIndex = rawSegments.indexOfFirst { seg ->
            seg.first.type == PointType.FOUNTAIN || seg.first.type == PointType.START
        }.coerceAtLeast(0)

        var currentChain = rawSegments.removeAt(startSegIndex).points.toMutableList()

        if (currentChain.first().type == PointType.SOURCE && currentChain.last().type == PointType.FOUNTAIN) {
            currentChain = currentChain.reversed().toMutableList()
        }

        // 2. Kalan parçaları incele: Aynı hattın tekrarı mı (Multi-Pass Overlap) yoksa uç uca devamı mı?
        while (rawSegments.isNotEmpty()) {
            val chainStart = currentChain.first()
            val chainEnd = currentChain.last()

            // Önce paralel örtüşen (aynı hattın gidiş-dönüş veya 2. yürüyüşü olan) parça var mı kontrol et
            val overlapIndex = rawSegments.indexOfFirst { seg ->
                isParallelOverlappingPass(chainStart, chainEnd, seg)
            }

            if (overlapIndex >= 0) {
                val overlappingSeg = rawSegments.removeAt(overlapIndex)
                val forwardDist = RouteData.haversineDistance(chainStart.lat, chainStart.lng, overlappingSeg.first.lat, overlappingSeg.first.lng) +
                    RouteData.haversineDistance(chainEnd.lat, chainEnd.lng, overlappingSeg.last.lat, overlappingSeg.last.lng)
                val reverseDist = RouteData.haversineDistance(chainStart.lat, chainStart.lng, overlappingSeg.last.lat, overlappingSeg.last.lng) +
                    RouteData.haversineDistance(chainEnd.lat, chainEnd.lng, overlappingSeg.first.lat, overlappingSeg.first.lng)

                val alignedPass = if (reverseDist < forwardDist) overlappingSeg.reversed().points else overlappingSeg.points
                currentChain = averageTwoParallelPasses(currentChain, alignedPass).toMutableList()
                continue
            }

            // Paralel örtüşme yoksa en yakın uç noktasına göre uç uca ekle
            var bestSegIndex = 0
            var bestMode = 0 // 0: Append Normal, 1: Append Reversed, 2: Prepend Normal, 3: Prepend Reversed
            var minDistance = Double.MAX_VALUE

            for ((idx, seg) in rawSegments.withIndex()) {
                val d0 = RouteData.haversineDistance(chainEnd.lat, chainEnd.lng, seg.first.lat, seg.first.lng)
                if (d0 < minDistance) {
                    minDistance = d0
                    bestSegIndex = idx
                    bestMode = 0
                }

                val d1 = RouteData.haversineDistance(chainEnd.lat, chainEnd.lng, seg.last.lat, seg.last.lng)
                if (d1 < minDistance) {
                    minDistance = d1
                    bestSegIndex = idx
                    bestMode = 1
                }

                val d2 = RouteData.haversineDistance(seg.last.lat, seg.last.lng, chainStart.lat, chainStart.lng)
                if (d2 < minDistance) {
                    minDistance = d2
                    bestSegIndex = idx
                    bestMode = 2
                }

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

        if (currentChain.first().type == PointType.SOURCE && currentChain.last().type != PointType.SOURCE) {
            currentChain.reverse()
        }

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
     * İki parçanın başlangıç ve bitiş uçlarının (düz veya ters yönde) 30 metre içinde çakışıp çakışmadığını kontrol eder.
     */
    private fun isParallelOverlappingPass(
        chainStart: RoutePoint,
        chainEnd: RoutePoint,
        candidate: Segment
    ): Boolean {
        if (candidate.points.size < 2) return false
        val chainSpan = RouteData.haversineDistance(chainStart.lat, chainStart.lng, chainEnd.lat, chainEnd.lng)
        if (chainSpan < 15.0) return false

        val threshold = 30.0
        val sameDirMatch = RouteData.haversineDistance(chainStart.lat, chainStart.lng, candidate.first.lat, candidate.first.lng) <= threshold &&
            RouteData.haversineDistance(chainEnd.lat, chainEnd.lng, candidate.last.lat, candidate.last.lng) <= threshold

        val revDirMatch = RouteData.haversineDistance(chainStart.lat, chainStart.lng, candidate.last.lat, candidate.last.lng) <= threshold &&
            RouteData.haversineDistance(chainEnd.lat, chainEnd.lng, candidate.first.lat, candidate.first.lng) <= threshold

        return sameDirMatch || revDirMatch
    }

    /**
     * Aynı yöne hizalanmış iki paralel yürüyüş hattının karşılıklı noktalarını enterpole ederek
     * ortalamasını alır; vana/baca gibi kritik noktaları korur ve birleştirir.
     */
    private fun averageTwoParallelPasses(
        passA: List<RoutePoint>,
        passB: List<RoutePoint>
    ): List<RoutePoint> {
        if (passA.isEmpty()) return passB
        if (passB.isEmpty()) return passA

        val result = mutableListOf<RoutePoint>()
        for (ptA in passA) {
            val nearestB = passB.minByOrNull { ptB ->
                RouteData.haversineDistance(ptA.lat, ptA.lng, ptB.lat, ptB.lng)
            }
            if (nearestB != null && RouteData.haversineDistance(ptA.lat, ptA.lng, nearestB.lat, nearestB.lng) <= 25.0) {
                val mergedType = if (ptA.type != PointType.WAYPOINT) ptA.type else nearestB.type
                val mergedLabel = ptA.label ?: nearestB.label
                result.add(
                    RoutePoint(
                        lat = (ptA.lat + nearestB.lat) / 2.0,
                        lng = (ptA.lng + nearestB.lng) / 2.0,
                        type = mergedType,
                        label = mergedLabel
                    )
                )
            } else {
                result.add(ptA)
            }
        }
        return result
    }

    /**
     * İki parça ortada buluştuğunda (8 metreden daha yakın mükerrer buluşma noktası varsa)
     * koordinatlarının ortalamasını alarak birleştirir ve kritik nokta etiketini korur.
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

        if (gapMeters < 8.0) {
            val keepType = if (lastPt.type != PointType.WAYPOINT && lastPt.type != PointType.START) {
                lastPt.type
            } else {
                firstAdd.type
            }
            val keepLabel = lastPt.label ?: firstAdd.label
            target[target.lastIndex] = RoutePoint(
                lat = (lastPt.lat + firstAdd.lat) / 2.0,
                lng = (lastPt.lng + firstAdd.lng) / 2.0,
                type = keepType,
                label = keepLabel
            )
            target.addAll(addition.drop(1))
        } else {
            target.addAll(addition)
        }
    }
}
