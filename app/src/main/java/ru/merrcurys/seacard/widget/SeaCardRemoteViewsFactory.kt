package ru.merrcurys.seacard.widget

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
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking
import ru.merrcurys.seacard.R
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.core.utils.CardSortUtil
import ru.merrcurys.seacard.domain.entity.Card
import java.io.File

class SeaCardRemoteViewsFactory(
    private val context: android.content.Context,
    private val intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private var cards: List<Card> = emptyList()
    private val widgetCoverAspectRatio = 1.574f
    private val cornerRadiusPx: Float by lazy {
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            12f,
            context.resources.displayMetrics
        )
    }

    /** Макс. сторона превью в px: мало для ячейки ~100dp, зато укладываемся в лимит Binder на элемент RemoteViews. */
    private val thumbnailMaxSidePx: Int by lazy {
        val dm = context.resources.displayMetrics
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 160f, dm).toInt().coerceAtLeast(128)
    }

    override fun onCreate() {}

    override fun onDataSetChanged() {
        runBlocking {
            val dao = DatabaseProvider.get(context).cardDao()
            val raw = dao.getAll().map { it.toCard() }
            val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
            val sortTypeName = prefs.getString("sort_type", null)
            cards = CardSortUtil.sorted(raw, sortTypeName)
        }
    }

    private fun decodeCoverThumbnail(card: Card): Bitmap? {
        val path = card.frontCoverPath ?: return null
        return try {
            if (path.startsWith("cards/")) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computeInSampleSize(bounds, thumbnailMaxSidePx)
                    inJustDecodeBounds = false
                }
                context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
            } else {
                val file = File(path)
                if (!file.exists()) return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = computeInSampleSize(bounds, thumbnailMaxSidePx)
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

    override fun onDestroy() {
        cards = emptyList()
    }

    override fun getCount(): Int = cards.size

    override fun getViewAt(position: Int): RemoteViews? {
        if (position !in cards.indices) return null
        val card = cards[position]
        val views = RemoteViews(context.packageName, R.layout.widget_seacard_item)
        val bitmap = decodeCoverThumbnail(card)
        if (bitmap != null) {
            val normalized = centerCropToAspectRatio(bitmap, widgetCoverAspectRatio)
            views.setImageViewBitmap(R.id.widget_card_cover, roundCorners(normalized, cornerRadiusPx))
        } else {
            views.setImageViewResource(R.id.widget_card_cover, R.drawable.widget_card_placeholder)
        }
        val fillInIntent = Intent().putExtra("card_id", card.id)
        views.setOnClickFillInIntent(R.id.widget_card_cover, fillInIntent)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = cards.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
