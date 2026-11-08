package com.turga.suyolu.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.EnumMap

object QrManager {

    /**
     * Google Play Store resmi uygulama indirme linki.
     * Gelecekte köylü veya usta standart telefon kamerasıyla okuttuğunda doğrudan uygulamayı indirir.
     */
    const val APP_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.turga.suyolu"

    /**
     * Generates a Bitmap image of the QR Code from the text payload.
     */
    fun generateQrBitmap(payload: String, sizePx: Int = 600): Bitmap? {
        return try {
            val bitMatrix = generateBitMatrix(payload, sizePx) ?: return null
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Generates a 2D Boolean Array representation of the QR Code matrix (for DXF & SVG vector export).
     */
    fun generateBooleanMatrix(payload: String): Array<BooleanArray>? {
        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
                put(EncodeHintType.MARGIN, 1) // Minimal margin for clear CNC/SVG boundaries
            }

            val bitMatrix = MultiFormatWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                0,
                0,
                hints
            )

            val width = bitMatrix.width
            val height = bitMatrix.height
            val matrix = Array(height) { BooleanArray(width) }

            for (y in 0 until height) {
                for (x in 0 until width) {
                    matrix[y][x] = bitMatrix.get(x, y)
                }
            }
            matrix
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun generateBitMatrix(payload: String, sizePx: Int): BitMatrix? {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M)
            put(EncodeHintType.MARGIN, 2)
        }
        return MultiFormatWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            hints
        )
    }
}
