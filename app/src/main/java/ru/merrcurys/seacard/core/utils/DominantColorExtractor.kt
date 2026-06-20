package ru.merrcurys.seacard.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream

object DominantColorExtractor {

    fun fromBitmap(bitmap: Bitmap): Int? = try {
        val colorCount = mutableMapOf<Int, Int>()
        val width = bitmap.width
        val height = bitmap.height
        val step = (width * height / 10000).coerceAtLeast(1)
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val color = bitmap.getPixel(x, y)
                val alpha = color ushr 24
                if (alpha > 200) {
                    colorCount[color] = (colorCount[color] ?: 0) + 1
                }
            }
        }
        colorCount.maxByOrNull { it.value }?.key?.toOpaqueRgb()
    } catch (_: Exception) {
        null
    }

    suspend fun fromAsset(context: Context, assetPath: String): Int? = withContext(Dispatchers.IO) {
        try {
            context.assets.open(assetPath).use { input ->
                BitmapFactory.decodeStream(input)?.let(::fromBitmap)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fromUri(context: Context, uri: Uri): Int? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input)?.let(::fromBitmap)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fromFile(filePath: String): Int? = withContext(Dispatchers.IO) {
        try {
            FileInputStream(filePath).use { input ->
                BitmapFactory.decodeStream(input)?.let(::fromBitmap)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun Int.toOpaqueRgb(): Int = (this and 0xFFFFFF) or 0xFF000000.toInt()
}
