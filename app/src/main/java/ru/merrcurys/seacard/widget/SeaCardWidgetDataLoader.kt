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
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.widget.RemoteViewsCompat
import kotlinx.coroutines.runBlocking
import ru.merrcurys.seacard.R
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.core.utils.CardSortUtil
import ru.merrcurys.seacard.domain.entity.Card
import java.io.File
import androidx.core.graphics.createBitmap

/** Собирает данные и RemoteViews для виджета без RemoteViewsService. */
object SeaCardWidgetDataLoader {

    private const val WIDGET_COVER_ASPECT_RATIO = 1.574f

    /** Макс. сторона превью в px: мало для ячейки ~100dp, зато укладываемся в лимит Binder на элемент RemoteViews. */
    private const val THUMBNAIL_MAX_DP = 160f

    private const val CORNER_RADIUS_DP = 8f

    fun load(context: Context): List<Card> = runBlocking {
        val dao = DatabaseProvider.get(context).cardDao()
        val raw = dao.getAll().map { it.toCard() }
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val sortTypeName = prefs.getString("sort_type", null)
        CardSortUtil.sorted(raw, sortTypeName)
    }

    fun buildItems(context: Context, cards: List<Card>): RemoteViewsCompat.RemoteCollectionItems {
        val cornerRadiusPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            CORNER_RADIUS_DP,
            context.resources.displayMetrics
        )
        val thumbnailMaxSidePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            THUMBNAIL_MAX_DP,
            context.resources.displayMetrics
        ).toInt().coerceAtLeast(128)

        val builder = RemoteViewsCompat.RemoteCollectionItems.Builder()
            .setViewTypeCount(1)
            .setHasStableIds(true)

        for (card in cards) {
            val views = RemoteViews(context.packageName, R.layout.widget_seacard_item)
            val bitmap = decodeCoverThumbnail(context, card, thumbnailMaxSidePx)
            if (bitmap != null) {
                val normalized = centerCropToAspectRatio(bitmap, WIDGET_COVER_ASPECT_RATIO)
                views.setImageViewBitmap(R.id.widget_card_cover, roundCorners(normalized, cornerRadiusPx))
            } else {
                views.setImageViewResource(R.id.widget_card_cover, R.drawable.widget_card_placeholder)
            }
            val fillInIntent = Intent().putExtra("card_id", card.id)
            views.setOnClickFillInIntent(R.id.widget_card_cover, fillInIntent)
            builder.addItem(card.id, views)
        }

        return builder.build()
    }

    private fun decodeCoverThumbnail(context: Context, card: Card, maxSidePx: Int): Bitmap? {
        val path = card.frontCoverPath ?: return null
        return try {
            if (path.startsWith("cards/")) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computeInSampleSize(bounds, maxSidePx)
                    inJustDecodeBounds = false
                }
                context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
            } else {
                val file = File(path)
                if (!file.exists()) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computeInSampleSize(bounds, maxSidePx)
                    inJustDecodeBounds = false
                }
                BitmapFactory.decodeFile(file.absolutePath, opts)
            }
        } catch (_: Exception) {
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
        val output = createBitmap(source.width, source.height)
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
