package ru.merrcurys.seacard.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import kotlinx.coroutines.runBlocking
import ru.merrcurys.seacard.domain.entity.Card

/**
 * Ленивая фабрика виджета для API ≤ 30: RemoteViews и обложка строятся только для
 * той ячейки, которую запрашивает лончер (`getViewAt`).
 *
 * На API 31+ этот путь не используется — провайдер отправляет RemoteCollectionItems
 * напрямую (см. [SeaCardWidgetData]).
 */
class SeaCardRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var cards: List<Card> = emptyList()

    override fun onCreate() = loadCards()

    override fun onDataSetChanged() = loadCards()

    /** Данные грузим с защитой: сбой не должен ронять процесс приложения при обновлении виджета. */
    private fun loadCards() {
        cards = try {
            runBlocking { SeaCardWidgetData.loadCards(context) }
        } catch (t: Throwable) {
            Log.e(TAG, "loadCards failed", t)
            // Не превращаем временный сбой БД в пустой виджет: оставляем предыдущий список.
            cards
        }
    }

    override fun onDestroy() {
        cards = emptyList()
    }

    override fun getCount(): Int = cards.size

    // null здесь недопустим: на Android 16+ один null-элемент ломает всю коллекцию.
    override fun getViewAt(position: Int): RemoteViews {
        val card = cards.getOrNull(position) ?: return SeaCardWidgetData.buildPlaceholderItem(context)
        return SeaCardWidgetData.buildItem(context, card)
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = cards.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private companion object {
        private const val TAG = "SeaCardWidget"
    }
}
