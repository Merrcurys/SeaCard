package ru.merrcurys.seacard.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.core.graphics.scale

/** Lossy-WebP с учётом API: на Android 11+ используем WEBP_LOSSY вместо deprecated WEBP. */
internal val webpLossyFormat: Bitmap.CompressFormat
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Bitmap.CompressFormat.WEBP_LOSSY
    } else {
        @Suppress("DEPRECATION")
        Bitmap.CompressFormat.WEBP
    }

/**
 * Сохранение пользовательских обложек: тот же порядок размера, что у [ColorCoverGenerator] (600 px по длинной стороне).
 */
object CoverBitmapStorage {

    const val MAX_LONG_SIDE_PX = 600

    fun scaleDownForCoverIfNeeded(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longSide = max(w, h)
        if (longSide <= MAX_LONG_SIDE_PX) return bitmap
        val scale = MAX_LONG_SIDE_PX.toFloat() / longSide
        val nw = max(1, (w * scale).roundToInt())
        val nh = max(1, (h * scale).roundToInt())
        return bitmap.scale(nw, nh)
    }

    fun saveBitmapAsWebpToCovers(filesDir: File, bitmap: Bitmap, fileName: String, quality: Int = 90): String? {
        val scaled = scaleDownForCoverIfNeeded(bitmap)
        return try {
            val coversDir = File(filesDir, "covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val file = File(coversDir, fileName)
            FileOutputStream(file).use { out ->
                scaled.compress(webpLossyFormat, quality, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            if (scaled !== bitmap) bitmap.recycle()
            scaled.recycle()
        }
    }

    fun saveBitmapAsWebpToCovers(context: Context, bitmap: Bitmap, fileName: String, quality: Int = 90): String? =
        saveBitmapAsWebpToCovers(context.filesDir, bitmap, fileName, quality)

    /** Сохраняет в [dest] и освобождает [bitmap]. */
    fun saveBitmapAsWebpFile(dest: File, bitmap: Bitmap, quality: Int = 90): Boolean {
        val scaled = scaleDownForCoverIfNeeded(bitmap)
        return try {
            FileOutputStream(dest).use { out ->
                scaled.compress(webpLossyFormat, quality, out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            if (scaled !== bitmap) bitmap.recycle()
            scaled.recycle()
        }
    }
}
