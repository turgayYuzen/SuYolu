package com.turga.suyolu.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.turga.suyolu.data.PointType
import com.turga.suyolu.data.RouteData
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object ExportManager {

    /**
     * Converts Turkish UTF-8 characters to safe ASCII for older CNC R12 DXF parsers (Mach3, ArtCAM, etc.).
     */
    private fun toSafeAscii(input: String): String {
        return input
            .replace('ç', 'c').replace('Ç', 'C')
            .replace('ğ', 'g').replace('Ğ', 'G')
            .replace('ı', 'i').replace('İ', 'I')
            .replace('ö', 'o').replace('Ö', 'O')
            .replace('ş', 's').replace('Ş', 'S')
            .replace('ü', 'u').replace('Ü', 'U')
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Generates a Dual-QR DXF R12 string for CNC milling:
     * Contains: Pipe Route QR (Left) + App Download QR (Right) + Guidelines.
     */
    fun generateDxf(
        routeData: RouteData,
        pipeQrMatrix: Array<BooleanArray>?,
        appQrMatrix: Array<BooleanArray>?
    ): String {
        val sb = StringBuilder()
        val safeFountainName = toSafeAscii(routeData.fountainName)

        // HEADER SECTION
        sb.append("0\nSECTION\n2\nHEADER\n")
        sb.append("9\n\$ACADVER\n1\nAC1009\n") // DXF R12 compatibility
        sb.append("0\nENDSEC\n")

        // TABLES SECTION (Layers)
        sb.append("0\nSECTION\n2\nTABLES\n0\nTABLE\n2\nLAYER\n70\n5\n")

        val layers = listOf(
            Pair("0", 7),
            Pair("FRAME", 7),          // White
            Pair("QR_PIPE", 1),        // Red
            Pair("QR_APP", 3),         // Green
            Pair("TEXT_INFO", 2)       // Yellow
        )

        for ((name, color) in layers) {
            sb.append("0\nLAYER\n2\n$name\n70\n0\n62\n$color\n6\nCONTINUOUS\n")
        }
        sb.append("0\nENDTAB\n0\nENDSEC\n")

        // ENTITIES SECTION
        sb.append("0\nSECTION\n2\nENTITIES\n")

        // 1. Tabela Başlığı
        sb.append("0\nTEXT\n8\nTEXT_INFO\n10\n10.0\n20\n115.0\n30\n0.0\n40\n5.0\n1\n")
            .append("SU YOLU REHBERI: ").append(safeFountainName).append("\n")

        sb.append("0\nTEXT\n8\nTEXT_INFO\n10\n10.0\n20\n108.0\n30\n0.0\n40\n2.8\n1\n")
            .append("1. BORU HATTI VERISI (UYGULAMA ILE OKUTUNUZ)       2. UYGULAMAYI INDIR (KAMERAYLA OKUTUNUZ)\n")

        // 2. Sol QR: Boru Hattı Matrisi
        if (pipeQrMatrix != null && pipeQrMatrix.isNotEmpty()) {
            appendDxfQrMatrix(sb, pipeQrMatrix, startX = 12.0, startY = 45.0, totalSizeMm = 45.0, layerName = "QR_PIPE")
        }

        // 3. Sağ QR: Uygulama İndir Matrisi
        if (appQrMatrix != null && appQrMatrix.isNotEmpty()) {
            appendDxfQrMatrix(sb, appQrMatrix, startX = 85.0, startY = 45.0, totalSizeMm = 45.0, layerName = "QR_APP")
        }

        // 4. Alt Açıklama & Talimatlar
        sb.append("0\nTEXT\n8\nTEXT_INFO\n10\n10.0\n20\n32.0\n30\n0.0\n40\n2.4\n1\n")
            .append("KULLANIM: 1. Sagdaki QR ile 'Su Yolu' uygulamasini telefonunuza kurunuz.\n")
        sb.append("0\nTEXT\n8\nTEXT_INFO\n10\n10.0\n20\n25.0\n30\n0.0\n40\n2.4\n1\n")
            .append("          2. Uygulama ile soldaki QR kodu tarayarak boru ve vana yerlerini bulunuz.\n")

        val totalLength = routeData.calculateTotalDistanceMeters()
        sb.append("0\nTEXT\n8\nTEXT_INFO\n10\n10.0\n20\n15.0\n30\n0.0\n40\n2.2\n1\n")
            .append(String.format(Locale.US, "KOORDINAT: %.5f, %.5f | MESAFE: %.1f M | NOKTA: %d",
                routeData.startLat, routeData.startLng, totalLength, routeData.points.size + 1)).append("\n")

        sb.append("0\nENDSEC\n0\nEOF\n")
        return sb.toString()
    }

    private fun appendDxfQrMatrix(
        sb: StringBuilder,
        matrix: Array<BooleanArray>,
        startX: Double,
        startY: Double,
        totalSizeMm: Double,
        layerName: String
    ) {
        val matrixSize = matrix.size
        val cellSize = totalSizeMm / matrixSize

        for (y in 0 until matrixSize) {
            for (x in 0 until matrixSize) {
                if (matrix[y][x]) {
                    val x0 = startX + (x * cellSize)
                    val y0 = startY + ((matrixSize - 1 - y) * cellSize)
                    val x1 = x0 + cellSize
                    val y1 = y0 + cellSize

                    sb.append("0\nSOLID\n8\n").append(layerName).append("\n")
                    sb.append("10\n").append(String.format(Locale.US, "%.2f", x0)).append("\n")
                    sb.append("20\n").append(String.format(Locale.US, "%.2f", y0)).append("\n")
                    sb.append("11\n").append(String.format(Locale.US, "%.2f", x1)).append("\n")
                    sb.append("21\n").append(String.format(Locale.US, "%.2f", y0)).append("\n")
                    sb.append("12\n").append(String.format(Locale.US, "%.2f", x0)).append("\n")
                    sb.append("22\n").append(String.format(Locale.US, "%.2f", y1)).append("\n")
                    sb.append("13\n").append(String.format(Locale.US, "%.2f", x1)).append("\n")
                    sb.append("23\n").append(String.format(Locale.US, "%.2f", y1)).append("\n")
                }
            }
        }
    }

    /**
     * Generates a Dual-QR Laser Engraving SVG string (Millimeter unit scale):
     * Includes Plate Frame + Pipe QR + App QR + Instructions + Coordinates.
     */
    fun generateSvg(
        routeData: RouteData,
        pipeQrMatrix: Array<BooleanArray>?,
        appQrMatrix: Array<BooleanArray>?
    ): String {
        val sb = StringBuilder()
        val totalLengthM = routeData.calculateTotalDistanceMeters()
        val escapedName = escapeXml(routeData.fountainName)

        // 170mm x 120mm tabela
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"170mm\" height=\"120mm\" viewBox=\"0 0 170 120\">\n")

        // Styles
        sb.append("  <style>\n")
        sb.append("    .title { font-family: sans-serif; font-weight: bold; font-size: 5px; fill: #111; text-anchor: middle; }\n")
        sb.append("    .subtitle { font-family: sans-serif; font-size: 3px; fill: #555; text-anchor: middle; }\n")
        sb.append("    .section-title { font-family: sans-serif; font-weight: bold; font-size: 2.8px; fill: #000; text-anchor: middle; }\n")
        sb.append("    .section-sub { font-family: sans-serif; font-size: 2px; fill: #666; text-anchor: middle; }\n")
        sb.append("    .inst-title { font-family: sans-serif; font-weight: bold; font-size: 2.5px; fill: #111; }\n")
        sb.append("    .inst-body { font-family: sans-serif; font-size: 2.2px; fill: #333; }\n")
        sb.append("    .meta { font-family: monospace; font-size: 2.1px; fill: #444; text-anchor: middle; }\n")
        sb.append("    .frame { fill: #ffffff; stroke: #222; stroke-width: 0.8; rx: 4; }\n")
        sb.append("    .box { fill: #f9f9f9; stroke: #ccc; stroke-width: 0.4; rx: 2; }\n")
        sb.append("  </style>\n")

        // Outer Plate Frame
        sb.append("  <rect x=\"2\" y=\"2\" width=\"166\" height=\"116\" class=\"frame\" />\n")

        // Header Title
        sb.append("  <text x=\"85\" y=\"11\" class=\"title\">SU YOLU ÇEŞME &amp; BORU HATTI REHBERİ</text>\n")
        sb.append("  <text x=\"85\" y=\"16\" class=\"subtitle\">").append(escapedName).append(" Tarihi Hattı</text>\n")
        sb.append("  <line x1=\"10\" y1=\"19\" x2=\"160\" y2=\"19\" stroke=\"#888\" stroke-width=\"0.4\" />\n")

        // Left Box: Pipe QR
        val qrSize = 44.0
        val leftQrX = 22.0
        val leftQrY = 28.0

        sb.append("  <text x=\"").append(leftQrX + qrSize / 2).append("\" y=\"25\" class=\"section-title\">1. BORU HATTI BİLGİSİ</text>\n")
        if (pipeQrMatrix != null && pipeQrMatrix.isNotEmpty()) {
            sb.append(generateSvgQrPath(pipeQrMatrix, leftQrX, leftQrY, qrSize))
        }
        sb.append("  <text x=\"").append(leftQrX + qrSize / 2).append("\" y=\"76\" class=\"section-sub\">[Su Yolu Uygulaması ile Okutunuz]</text>\n")

        // Right Box: App Download QR
        val rightQrX = 104.0
        val rightQrY = 28.0

        sb.append("  <text x=\"").append(rightQrX + qrSize / 2).append("\" y=\"25\" class=\"section-title\">2. UYGULAMAYI İNDİR</text>\n")
        if (appQrMatrix != null && appQrMatrix.isNotEmpty()) {
            sb.append(generateSvgQrPath(appQrMatrix, rightQrX, rightQrY, qrSize))
        }
        sb.append("  <text x=\"").append(rightQrX + qrSize / 2).append("\" y=\"76\" class=\"section-sub\">[Telefon Kameranızla Okutunuz]</text>\n")

        // Middle Divider
        sb.append("  <line x1=\"85\" y1=\"24\" x2=\"85\" y2=\"77\" stroke=\"#ddd\" stroke-width=\"0.4\" stroke-dasharray=\"1,1\" />\n")

        // Bottom Instruction Card
        sb.append("  <rect x=\"10\" y=\"82\" width=\"150\" height=\"24\" class=\"box\" />\n")
        sb.append("  <text x=\"14\" y=\"87\" class=\"inst-title\">KULLANIM TALİMATI:</text>\n")
        sb.append("  <text x=\"14\" y=\"92\" class=\"inst-body\">1. Adım: Önce sağdaki QR kodu telefonunuzun kamerasıyla okutup ücretsiz &apos;Su Yolu&apos; uygulamasını yükleyin.</text>\n")
        sb.append("  <text x=\"14\" y=\"96\" class=\"inst-body\">2. Adım: Uygulamayı açıp &apos;Çeşmedeki QR Kodu Tara&apos; butonuna basarak soldaki Boru Hattı QR kodunu okutun.</text>\n")
        sb.append("  <text x=\"14\" y=\"100\" class=\"inst-body\">3. Adım: Tüm boru hattı, vana ve kaynaklar canlı radarla haritada açılacak ve tamir kolayca yapılabilecektir.</text>\n")

        // Meta Footer
        sb.append("  <text x=\"85\" y=\"112\" class=\"meta\">Konum: ")
            .append(String.format(Locale.US, "%.5f, %.5f", routeData.startLat, routeData.startLng))
            .append("  |  Hat Uzunluğu: ").append(String.format(Locale.US, "%.1f m", totalLengthM))
            .append("  |  Kayıtlı Nokta: ").append(routeData.points.size + 1)
            .append("</text>\n")

        sb.append("</svg>")
        return sb.toString()
    }

    private fun generateSvgQrPath(matrix: Array<BooleanArray>, startX: Double, startY: Double, size: Double): String {
        val matrixSize = matrix.size
        val moduleSize = size / matrixSize
        val pathData = StringBuilder()

        for (y in 0 until matrixSize) {
            for (x in 0 until matrixSize) {
                if (matrix[y][x]) {
                    val mx = startX + (x * moduleSize)
                    val my = startY + (y * moduleSize)
                    pathData.append(String.format(Locale.US, "M%.2f,%.2f h%.2f v%.2f h-%.2f z ", mx, my, moduleSize, moduleSize, moduleSize))
                }
            }
        }
        return "  <path d=\"${pathData.toString()}\" fill=\"#000000\" />\n"
    }

    /**
     * Generates a high-resolution printable Plate Image (PNG) containing both QR codes and full instructions.
     */
    fun generatePlateBitmap(
        routeData: RouteData,
        pipeQrBitmap: Bitmap?,
        appQrBitmap: Bitmap?
    ): Bitmap {
        val width = 1200
        val height = 850
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Zemin (Pirinç/Mermer Plaka Rengi)
        val bgPaint = Paint().apply {
            color = Color.parseColor("#FDFDFD")
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Çerçeve
        val framePaint = Paint().apply {
            color = Color.parseColor("#1B3B6F")
            style = Paint.Style.STROKE
            strokeWidth = 14f
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(24f, 24f, width - 24f, height - 24f), 28f, 28f, framePaint)

        // İnce iç çerçeve
        val innerFramePaint = Paint().apply {
            color = Color.parseColor("#B0BEC5")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(40f, 40f, width - 40f, height - 40f), 20f, 20f, innerFramePaint)

        // 3. Başlık
        val titlePaint = Paint().apply {
            color = Color.parseColor("#0D47A1")
            textSize = 42f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("SU YOLU ÇEŞME & BORU HATTI REHBERİ", width / 2f, 105f, titlePaint)

        val subTitlePaint = Paint().apply {
            color = Color.parseColor("#37474F")
            textSize = 28f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("${routeData.fountainName} Su Hattı", width / 2f, 150f, subTitlePaint)

        // Çizgi
        val linePaint = Paint().apply {
            color = Color.parseColor("#CFD8DC")
            strokeWidth = 3f
        }
        canvas.drawLine(80f, 175f, width - 80f, 175f, linePaint)

        // 4. Sol QR: Boru Hattı QR'ı
        val qrBoxSize = 310
        val leftX = 140
        val qrY = 230

        val sectionHeaderPaint = Paint().apply {
            color = Color.parseColor("#0D47A1")
            textSize = 24f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val sectionSubPaint = Paint().apply {
            color = Color.parseColor("#546E7A")
            textSize = 19f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        canvas.drawText("1. BORU HATTI VERİSİ", leftX + qrBoxSize / 2f, qrY - 15f, sectionHeaderPaint)
        if (pipeQrBitmap != null) {
            canvas.drawBitmap(pipeQrBitmap, null, Rect(leftX, qrY, leftX + qrBoxSize, qrY + qrBoxSize), null)
        }
        canvas.drawText("[Su Yolu Uygulaması ile Okutunuz]", leftX + qrBoxSize / 2f, qrY + qrBoxSize + 30f, sectionSubPaint)

        // 5. Sağ QR: Uygulama İndir QR'ı
        val rightX = width - leftX - qrBoxSize
        canvas.drawText("2. UYGULAMAYI İNDİR", rightX + qrBoxSize / 2f, qrY - 15f, sectionHeaderPaint)
        if (appQrBitmap != null) {
            canvas.drawBitmap(appQrBitmap, null, Rect(rightX, qrY, rightX + qrBoxSize, qrY + qrBoxSize), null)
        }
        canvas.drawText("[Telefon Kameranızla Okutunuz]", rightX + qrBoxSize / 2f, qrY + qrBoxSize + 30f, sectionSubPaint)

        // Ortadaki dikey ayraç
        canvas.drawLine(width / 2f, 210f, width / 2f, 580f, linePaint)

        // 6. Alt Açıklama Kartı
        val boxPaint = Paint().apply {
            color = Color.parseColor("#F1F5F9")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(70f, 620f, width - 70f, 765f), 16f, 16f, boxPaint)

        val instHeaderPaint = Paint().apply {
            color = Color.parseColor("#B71C1C")
            textSize = 22f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val instBodyPaint = Paint().apply {
            color = Color.parseColor("#263238")
            textSize = 20f
            isAntiAlias = true
        }

        canvas.drawText("📌 NASIL KULLANILIR?", 95f, 655f, instHeaderPaint)
        canvas.drawText("1. Adım: Sağdaki QR kodu normal telefon kameranızla okutarak ücretsiz 'Su Yolu' uygulamasını indirin.", 95f, 690f, instBodyPaint)
        canvas.drawText("2. Adım: Uygulamayı açıp 'Çeşmedeki QR Kodu Tara' butonuna basarak soldaki Boru Hattı kodunu okutun.", 95f, 722f, instBodyPaint)
        canvas.drawText("3. Adım: Tüm boru hattı ve vanalar haritada canlı açılacak, arıza noktası elinizle koymuş gibi bulunacaktır.", 95f, 752f, instBodyPaint)

        // 7. Alt Bilgi
        val metaPaint = Paint().apply {
            color = Color.parseColor("#78909C")
            textSize = 19f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val distM = routeData.calculateTotalDistanceMeters()
        canvas.drawText(
            String.format(Locale.getDefault(), "Başlangıç: %.5f, %.5f  •  Hat: %.1f m  •  Nokta: %d",
                routeData.startLat, routeData.startLng, distM, routeData.points.size + 1),
            width / 2f, 805f, metaPaint
        )

        return bitmap
    }

    /**
     * Generates a 3D Google Earth KML string.
     */
    fun generateKml(routeData: RouteData): String {
        val sb = StringBuilder()
        val escapedName = escapeXml(routeData.fountainName)

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("<Document>\n")
        sb.append("  <name>Su Yolu - ").append(escapedName).append("</name>\n")
        sb.append("  <description>Su Yolu Uygulaması Boru Hattı GPS Verisi</description>\n")

        // Polyline Style
        sb.append("  <Style id=\"waterPipeStyle\">\n")
        sb.append("    <LineStyle><color>ff0000ff</color><width>4</width></LineStyle>\n")
        sb.append("  </Style>\n")

        // Start Point Placemark
        sb.append("  <Placemark>\n")
        sb.append("    <name>").append(escapedName).append(" (").append(escapeXml(routeData.startPointType.displayName)).append(")</name>\n")
        sb.append("    <Point><coordinates>").append(routeData.startLng).append(",").append(routeData.startLat).append(",0</coordinates></Point>\n")
        sb.append("  </Placemark>\n")

        // Critical Points
        for (pt in routeData.points) {
            if (pt.type != PointType.WAYPOINT) {
                sb.append("  <Placemark>\n")
                sb.append("    <name>").append(escapeXml(pt.type.displayName)).append("</name>\n")
                sb.append("    <Point><coordinates>").append(pt.lng).append(",").append(pt.lat).append(",0</coordinates></Point>\n")
                sb.append("  </Placemark>\n")
            }
        }

        // Pipe Route Polyline
        sb.append("  <Placemark>\n")
        sb.append("    <name>Boru Hattı</name>\n")
        sb.append("    <styleUrl>#waterPipeStyle</styleUrl>\n")
        sb.append("    <LineString>\n")
        sb.append("      <coordinates>\n")
        sb.append("        ").append(routeData.startLng).append(",").append(routeData.startLat).append(",0\n")

        for (pt in routeData.points) {
            sb.append("        ").append(pt.lng).append(",").append(pt.lat).append(",0\n")
        }

        sb.append("      </coordinates>\n")
        sb.append("    </LineString>\n")
        sb.append("  </Placemark>\n")

        sb.append("</Document>\n")
        sb.append("</kml>")
        return sb.toString()
    }

    /**
     * Generates standard GPX 1.1 XML string for handheld GPS devices (Garmin, Magellan, etc.).
     */
    fun generateGpx(routeData: RouteData): String {
        val sb = StringBuilder()
        val escapedName = escapeXml(routeData.fountainName)

        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Su Yolu App\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <metadata><name>").append(escapedName).append("</name></metadata>\n")

        // Waypoints
        sb.append("  <wpt lat=\"").append(routeData.startLat).append("\" lon=\"").append(routeData.startLng).append("\">\n")
        sb.append("    <name>").append(escapedName).append(" (").append(escapeXml(routeData.startPointType.displayName)).append(")</name>\n")
        sb.append("  </wpt>\n")

        for (pt in routeData.points) {
            if (pt.type != PointType.WAYPOINT) {
                sb.append("  <wpt lat=\"").append(pt.lat).append("\" lon=\"").append(pt.lng).append("\">\n")
                sb.append("    <name>").append(escapeXml(pt.type.displayName)).append("</name>\n")
                sb.append("  </wpt>\n")
            }
        }

        // Track
        sb.append("  <trk>\n")
        sb.append("    <name>").append(escapedName).append(" Boru Hatti</name>\n")
        sb.append("    <trkseg>\n")
        sb.append("      <trkpt lat=\"").append(routeData.startLat).append("\" lon=\"").append(routeData.startLng).append("\" />\n")
        for (pt in routeData.points) {
            sb.append("      <trkpt lat=\"").append(pt.lat).append("\" lon=\"").append(pt.lng).append("\" />\n")
        }
        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>")
        return sb.toString()
    }

    /**
     * Saves string content (DXF, SVG, KML, GPX) to Downloads directory.
     */
    fun saveFileToDownloads(
        context: Context,
        fileName: String,
        mimeType: String,
        content: String
    ): Uri? {
        val sanitizedFileName = fileName.replace(Regex("[^a-zA-Z0-9._\\-ğüşıöçĞÜŞİÖÇ ]"), "_")
        val resolver = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SuYolu")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { os ->
                    os.write(content.toByteArray(Charsets.UTF_8))
                }
            }
            uri
        } else {
            val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "SuYolu")
            if (!targetDir.exists()) targetDir.mkdirs()
            val file = File(targetDir, sanitizedFileName)
            FileOutputStream(file).use { os ->
                os.write(content.toByteArray(Charsets.UTF_8))
            }
            Uri.fromFile(file)
        }
    }

    /**
     * Saves Plate or QR Bitmap image to Gallery / Pictures folder.
     */
    fun saveImage(context: Context, fileName: String, bitmap: Bitmap): Uri? {
        val sanitizedFileName = fileName.replace(Regex("[^a-zA-Z0-9._\\-ğüşıöçĞÜŞİÖÇ ]"), "_")
        val resolver = context.contentResolver
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, sanitizedFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SuYolu")
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it)?.use { os ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                }
            }
            uri
        } else {
            val targetDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SuYolu")
            if (!targetDir.exists()) targetDir.mkdirs()
            val file = File(targetDir, sanitizedFileName)
            FileOutputStream(file).use { os ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
            }
            Uri.fromFile(file)
        }
    }

    /**
     * Creates a shareable cache file and opens the Android share intent.
     */
    fun shareContent(context: Context, fileName: String, content: String, mimeType: String) {
        try {
            val sanitizedFileName = fileName.replace(Regex("[^a-zA-Z0-9._\\-ğüşıöçĞÜŞİÖÇ ]"), "_")
            val cacheDir = File(context.cacheDir, "shared_files")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, sanitizedFileName)
            file.writeText(content, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Su Yolu Dosyasını Paylaş"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Shares a generated Bitmap image.
     */
    fun shareImage(context: Context, fileName: String, bitmap: Bitmap) {
        try {
            val sanitizedFileName = fileName.replace(Regex("[^a-zA-Z0-9._\\-ğüşıöçĞÜŞİÖÇ ]"), "_")
            val cacheDir = File(context.cacheDir, "shared_files")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = File(cacheDir, sanitizedFileName)
            FileOutputStream(file).use { os ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Plaka Görselini Paylaş"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
