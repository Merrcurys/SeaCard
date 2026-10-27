package ru.merrcurys.seacard.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import ru.merrcurys.seacard.R
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.core.utils.CardSortUtil
import ru.merrcurys.seacard.domain.entity.Card
import java.io.File

/**
 * Общая сборка элементов виджета.
 *
 * Одна и та же логика используется двумя путями:
 * - API ≤ 30: `SeaCardRemoteViewsFactory` отдаёт элементы лениво через RemoteViewsService;
 * - API 31+: провайдер сам собирает `RemoteCollectionItems` и отправляет их в одном
 *   `updateAppWidget`. Это важно: на Android 16+ legacy-адаптер сначала асинхронно
 *   конвертируется в коллекцию, и если процесс приложения успевает умереть после
 *   `goAsync().finish()`, виджет остаётся пустым до следующего обновления.
 */
internal object SeaCardWidgetData {

    private const val TAG = "SeaCardWidget"

    /** Макс. сторона превью: ячейка ~90–100dp, меньший запас снижает риск OOM и размер Parcel. */
    private const val THUMBNAIL_MAX_DP = 120f
    private const val CORNER_RADIUS_DP = 8f
    private const val WIDGET_COVER_ASPECT_RATIO = 1.574f

    suspend fun loadCards(context: Context): List<Card> {
        val raw = DatabaseProvider.get(context).cardDao().getAll().map { it.toCard() }
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return CardSortUtil.sorted(raw, prefs.getString("sort_type", null))
    }

    /**
     * Собирает один элемент сетки. Никогда не бросает исключение и никогда не возвращает
     * null: на Android 16+ один null-элемент делает невалидной всю коллекцию, и виджет
     * целиком перестаёт обновляться (остаётся пустым).
     */
    fun buildItem(context: Context, card: Card): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_seacard_item)
        try {
            val bitmap = decodeCoverThumbnail(context, card)
            if (bitmap != null) {
                val cropped = centerCropToAspectRatio(bitmap, WIDGET_COVER_ASPECT_RATIO)
                if (cropped !== bitmap) bitmap.recycle()
                val rounded = roundCorners(cropped, cornerRadiusPx(context))
                if (rounded !== cropped) cropped.recycle()
                views.setImageViewBitmap(R.id.widget_card_cover, rounded)
            } else {
                views.setImageViewResource(R.id.widget_card_cover, R.drawable.widget_card_placeholder)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "buildItem failed for ${card.id}", t)
            views.setImageViewResource(R.id.widget_card_cover, R.drawable.widget_card_placeholder)
        }
        views.setOnClickFillInIntent(R.id.widget_card_cover, Intent().putExtra("card_id", card.id))
        return views
    }

    /** Элемент-заглушка для позиции, которой нет в списке (адаптер не должен получать null). */
    fun buildPlaceholderItem(context: Context): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_seacard_item).apply {
            setImageViewResource(R.id.widget_card_cover, R.drawable.widget_card_placeholder)
        }

    private fun cornerRadiusPx(context: Context): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            CORNER_RADIUS_DP,
            context.resources.displayMetrics
        )

    private fun thumbnailMaxSidePx(context: Context): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            THUMBNAIL_MAX_DP,
            context.resources.displayMetrics
        ).toInt().coerceAtLeast(96)

    private fun decodeCoverThumbnail(context: Context, card: Card): Bitmap? {
        val path = card.frontCoverPath ?: return null
        return try {
            val maxSide = thumbnailMaxSidePx(context)
            if (path.startsWith("cards/")) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computeInSampleSize(bounds, maxSide)
                    inJustDecodeBounds = false
                }
                context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
            } else {
                val file = File(path)
                if (!file.exists()) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computeInSampleSize(bounds, maxSide)
                    inJustDecodeBounds = false
                }
                BitmapFactory.decodeFile(file.absolutePath, opts)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "decodeCoverThumbnail failed for ${card.id}", t)
            null
        }
    }

    private fun computeInSampleSize(bounds: BitmapFactory.Options, maxSidePx: Int): Int {
        var h = bounds.outHeight
        var w = bounds.outWidth
        if (h <= 0 || w <= 0) return 1
        var inSampleSize = 1
        while (h > maxSidePx || w > maxSidePx) {
            inSampleSize *= 2
            h /= 2
            w /= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun roundCorners(source: Bitmap, cornerRadius: Float): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(
            RectF(0f, 0f, source.width.toFloat(), source.height.toFloat()),
            cornerRadius,
            cornerRadius,
            paint
        )
        return output
    }

    private fun centerCropToAspectRatio(source: Bitmap, targetAspectRatio: Float): Bitmap {
        if (targetAspectRatio <= 0f) return source
        val sourceWidth = source.width
        val sourceHeight = source.height
        if (sourceWidth <= 0 || sourceHeight <= 0) return source

        val currentAspectRatio = sourceWidth.toFloat() / sourceHeight.toFloat()
        if (kotlin.math.abs(currentAspectRatio - targetAspectRatio) < 0.001f) return source

        return if (currentAspectRatio > targetAspectRatio) {
            val targetWidth = (sourceHeight * targetAspectRatio).toInt().coerceAtLeast(1)
            val left = ((sourceWidth - targetWidth) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(source, left, 0, targetWidth.coerceAtMost(sourceWidth - left), sourceHeight)
        } else {
            val targetHeight = (sourceWidth / targetAspectRatio).toInt().coerceAtLeast(1)
            val top = ((sourceHeight - targetHeight) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(source, 0, top, sourceWidth, targetHeight.coerceAtMost(sourceHeight - top))
        }
    }
}
