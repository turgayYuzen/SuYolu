package com.turga.suyolu.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.turga.suyolu.data.PointType

object MapIconHelper {

    fun createUserLocationDrawable(context: Context): Drawable {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cx = size / 2f
        val cy = size / 2f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Outer Translucent Pulsing Blue Halo Ring
        paint.color = Color.argb(70, 2, 136, 209)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, 44f, paint)

        // 2. White Ring Border
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, 26f, paint)

        // 3. Inner Solid Blue Circle
        paint.color = Color.parseColor("#0288D1")
        canvas.drawCircle(cx, cy, 20f, paint)

        // 4. White Center Dot
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, 7f, paint)

        return BitmapDrawable(context.resources, bitmap)
    }

    fun createPointTypeDrawable(context: Context, type: PointType): Drawable {
        val width = 96
        val height = 116
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val cx = width / 2f
        val cy = 42f
        val radius = 38f

        val (bgColor, emoji) = when (type) {
            PointType.FOUNTAIN -> Pair("#0D47A1", "⛲")                  // Çeşme
            PointType.SOURCE -> Pair("#00838F", "💧")                    // Su Gözü / Kaynak
            PointType.VALVE -> Pair("#FF8F00", "🚰")                     // Vana
            PointType.AIR_VALVE -> Pair("#00897B", "🌬️")                // Hava Bacası
            PointType.START, PointType.WAYPOINT -> Pair("#D84315", "📍") // Boru Hattı / Başlangıç Pini
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. Pin Ground Shadow
        paint.color = Color.argb(70, 0, 0, 0)
        paint.style = Paint.Style.FILL
        canvas.drawOval(RectF(cx - 16f, 102f, cx + 16f, 114f), paint)

        // 2. Pin Teardrop Path
        val path = Path().apply {
            moveTo(cx - radius, cy)
            arcTo(RectF(cx - radius, cy - radius, cx + radius, cy + radius), 180f, 180f)
            lineTo(cx, 104f)
            close()
        }

        // Fill Pin Background Color
        paint.color = Color.parseColor(bgColor)
        canvas.drawPath(path, paint)

        // White Outer Stroke Border
        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        canvas.drawPath(path, paint)

        // Inner Circle White Background Badge for Symbol Emoji
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius - 8f, paint)

        // Draw Symbol Emoji
        paint.textSize = 36f
        paint.textAlign = Paint.Align.CENTER

        val fontMetrics = paint.fontMetrics
        val baseline = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
        canvas.drawText(emoji, cx, baseline, paint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
