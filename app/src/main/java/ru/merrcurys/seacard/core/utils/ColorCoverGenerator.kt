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
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get

/**
 * Генерирует и сохраняет обложку карты из цвета и названия.
 */
object ColorCoverGenerator {

    private const val MAX_TEXT_SIZE = 150f
    private const val MIN_TEXT_SIZE = 24f
    private const val PADDING_RATIO = 0.1f
    private const val LINE_SPACING = 1.15f

    private fun isColorDark(color: Int): Boolean {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        val brightness = (red * 299 + green * 587 + blue * 114) / 1000
        return brightness < 128
    }

    /**
     * Генерирует Bitmap обложки из цвета фона и названия карты.
     * Шрифт подбирается под длину названия: короткое — крупно, длинное — мельче
     * и с переносом на несколько строк, чтобы текст целиком поместился в обложку.
     */
    fun generateColorCoverBitmap(name: String, color: Int): Bitmap? = try {
        val aspectRatio = 1.574f
        val width = 600
        val height = (width / aspectRatio).toInt()
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val textColor = if (isColorDark(color)) GColor.WHITE else GColor.BLACK
        val paint = Paint().apply {
            setColor(textColor)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = MAX_TEXT_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val displayName = name.trim().ifBlank { "Карта" }
        val maxWidth = width - 2f * width * PADDING_RATIO
        val maxHeight = height - 2f * height * PADDING_RATIO

        var textSize = MAX_TEXT_SIZE
        var lines: List<String>
        while (true) {
            paint.textSize = textSize
            lines = wrapText(displayName, paint, maxWidth)
            val fits = lines.all { paint.measureText(it) <= maxWidth } &&
                lines.size * lineHeight(paint) <= maxHeight
            if (fits || textSize <= MIN_TEXT_SIZE) break
            textSize = (textSize - 4f).coerceAtLeast(MIN_TEXT_SIZE)
        }

        // Совсем длинное название на минимальном кегле обрезаем по высоте.
        val lineStep = lineHeight(paint)
        val maxLines = (maxHeight / lineStep).toInt().coerceAtLeast(1)
        if (lines.size > maxLines) {
            lines = lines.take(maxLines).toMutableList().also { visible ->
                visible[visible.lastIndex] = ellipsize(visible.last(), paint, maxWidth)
            }
        }

        val x = width / 2f
        // Высота именно глифов: межстрочный интервал добавляется только между строками,
        // иначе одна строка уезжает выше середины на величину leading.
        val glyphHeight = paint.descent() - paint.ascent()
        val totalHeight = (lines.size - 1) * lineStep + glyphHeight
        var y = (height - totalHeight) / 2f - paint.ascent()
        for (line in lines) {
            canvas.drawText(line, x, y, paint)
            y += lineStep
        }
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    private fun lineHeight(paint: Paint): Float =
        (paint.descent() - paint.ascent()) * LINE_SPACING

    /** Разбивает текст на строки по словам (и по символам для слишком длинных слов). */
    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            val words = paragraph.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) continue
            var current = ""
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    current = candidate
                    continue
                }
                if (current.isNotEmpty()) lines.add(current)
                var rest = word
                while (paint.measureText(rest) > maxWidth && rest.length > 1) {
                    var cut = rest.length - 1
                    while (cut > 1 && paint.measureText(rest.substring(0, cut)) > maxWidth) cut--
                    lines.add(rest.substring(0, cut))
                    rest = rest.substring(cut)
                }
                current = rest
            }
            if (current.isNotEmpty()) lines.add(current)
        }
        if (lines.isEmpty()) lines.add(text.ifBlank { "Карта" })
        return lines
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        var result = text
        while (result.isNotEmpty() && paint.measureText("$result…") > maxWidth) {
            result = result.dropLast(1)
        }
        return "$result…"
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
            // Текст занимает всю область внутри отступов (может быть в несколько строк),
            // поэтому пропускаем её целиком и сэмплируем только фон по краям.
            val textLeft = (width * PADDING_RATIO).toInt()
            val textRight = width - textLeft
            val textTop = (height * PADDING_RATIO).toInt()
            val textBottom = height - textTop
            for (y in 0 until height step stepY) {
                for (x in 0 until width step stepX) {
                    if (x in textLeft..textRight && y in textTop..textBottom) continue
                    total++
                    val pixelRgb = bitmap[x, y] and 0xFFFFFF
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
                bitmap.compress(webpLossyFormat, 90, out)
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
