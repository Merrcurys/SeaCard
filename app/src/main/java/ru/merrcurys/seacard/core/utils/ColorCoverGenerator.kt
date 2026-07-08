package ru.merrcurys.seacard.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as GColor
import android.graphics.Paint
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream

/**
 * Генерирует и сохраняет обложку карты из цвета и названия.
 */
object ColorCoverGenerator {

    private fun isColorDark(color: Int): Boolean {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        val brightness = (red * 299 + green * 587 + blue * 114) / 1000
        return brightness < 128
    }

    /**
     * Генерирует Bitmap обложки из цвета фона и названия карты.
     */
    fun generateColorCoverBitmap(name: String, color: Int): Bitmap? = try {
        val aspectRatio = 1.574f
        val width = 600
        val height = (width / aspectRatio).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val textColor = if (isColorDark(color)) GColor.WHITE else GColor.BLACK
        val paint = Paint().apply {
            setColor(textColor)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 58f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val x = width / 2f
        val y = height / 2f - (paint.descent() + paint.ascent()) / 2f
        val displayName = if (name.isBlank()) "Карта" else name
        canvas.drawText(displayName, x, y, paint)
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    /**
     * Обложка, сгенерированная из цвета ([generateColorCoverBitmap]): однотонный фон + текст.
     * Нужно, чтобы не перегенерировать пользовательские фото при смене только акцентного цвета.
     */
    fun isGeneratedColorCover(coverPath: String, cardColor: Int): Boolean {
        if (coverPath.startsWith("cards/")) return false
        val bitmap = BitmapFactory.decodeFile(coverPath) ?: return false
        return try {
            val expectedRgb = cardColor and 0xFFFFFF
            val tolerance = 10
            val width = bitmap.width
            val height = bitmap.height
            if (width < 4 || height < 4) return false

            var matching = 0
            var total = 0
            val stepX = (width / 10).coerceAtLeast(1)
            val stepY = (height / 10).coerceAtLeast(1)
            for (y in 0 until height step stepY) {
                for (x in 0 until width step stepX) {
                    // Центр с названием — пропускаем, там может быть текст другого цвета.
                    if (x in width / 4..3 * width / 4 && y in height / 3..2 * height / 3) continue
                    total++
                    val pixelRgb = bitmap.getPixel(x, y) and 0xFFFFFF
                    if (rgbDistance(pixelRgb, expectedRgb) <= tolerance) matching++
                }
            }
            total > 0 && matching * 100 / total >= 85
        } catch (_: Exception) {
            false
        } finally {
            bitmap.recycle()
        }
    }

    private fun rgbDistance(a: Int, b: Int): Int {
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        return maxOf(kotlin.math.abs(ar - br), kotlin.math.abs(ag - bg), kotlin.math.abs(ab - bb))
    }

    /**
     * Генерирует обложку и сохраняет в WebP. Возвращает путь к файлу или null.
     */
    fun generateAndSaveAsWebp(context: Context, name: String, color: Int, fileName: String): String? {
        val bitmap = generateColorCoverBitmap(name, color) ?: return null
        try {
            val coversDir = File(context.filesDir, "covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val file = File(coversDir, fileName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.WEBP, 90, out)
            }
            return file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            bitmap.recycle()
        }
    }
}
