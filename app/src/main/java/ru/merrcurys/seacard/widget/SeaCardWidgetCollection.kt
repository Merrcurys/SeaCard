package ru.merrcurys.seacard.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import ru.merrcurys.seacard.domain.entity.Card

/**
 * Явная коллекция для виджета (API 31+).
 *
 * Вынесено в отдельный класс, чтобы тип `RemoteCollectionItems` не попадал в сигнатуры
 * кода, который загружается на API ≤ 30 (там такого класса нет).
 */
@RequiresApi(31)
internal object SeaCardWidgetCollection {

    fun build(context: Context, cards: List<Card>): RemoteViews.RemoteCollectionItems {
        val builder = RemoteViews.RemoteCollectionItems.Builder()
            .setHasStableIds(true)
            .setViewTypeCount(1)
        for (card in cards) {
            builder.addItem(card.id, SeaCardWidgetData.buildItem(context, card))
        }
        return builder.build()
    }
}
