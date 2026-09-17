package ru.merrcurys.seacard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.widget.RemoteViewsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.merrcurys.seacard.R
import ru.merrcurys.seacard.features.detail.CardDetailActivity

class SeaCardAppWidgetProvider : AppWidgetProvider() {

    private val widgetScope = CoroutineScope(Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // onUpdate() завершается сразу, а RemoteViews собираются в корутине. Без goAsync()
        // система считает приёмник отработавшим и может убить процесс до вызова
        // updateAppWidget() — на «холодную» (первое добавление виджета сразу после
        // установки) лончер так и не получает RemoteViews и показывает
        // «Не удалось загрузить виджет». Повторное добавление работает только потому,
        // что процесс уже прогрет. goAsync() удерживает процесс до pendingResult.finish().
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        widgetScope.launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    try {
                        updateAppWidget(appContext, appWidgetManager, appWidgetId)
                    } catch (t: Throwable) {
                        // Один сбойный виджет не должен ломать обновление остальных.
                        Log.e(TAG, "updateAppWidget($appWidgetId) failed", t)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val cards = SeaCardWidgetDataLoader.load(context)
        val views = RemoteViews(context.packageName, R.layout.widget_seacard)
        if (cards.isEmpty()) {
            views.setViewVisibility(R.id.widget_cards_grid, View.GONE)
            views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_cards_grid, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty_text, View.GONE)
            RemoteViewsCompat.setRemoteAdapter(
                context,
                views,
                appWidgetId,
                R.id.widget_cards_grid,
                SeaCardWidgetDataLoader.buildItems(context, cards)
            )
            val templateIntent = Intent(context, CardDetailActivity::class.java).apply {
                // NEW_TASK — запуск из виджета; SINGLE_TOP — повторный тап приходит в onNewIntent,
                // иначе показывалась бы ранее открытая карточка.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            views.setPendingIntentTemplate(
                R.id.widget_cards_grid,
                PendingIntent.getActivity(
                    context,
                    0,
                    templateIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /** Вызвать при изменении списка карт (добавление/удаление), чтобы виджет обновился. */
    companion object {
        private const val TAG = "SeaCardWidget"

        fun notifyDataChanged(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SeaCardAppWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            if (ids.isNotEmpty()) {
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                context.sendBroadcast(intent)
            }
        }
    }
}
