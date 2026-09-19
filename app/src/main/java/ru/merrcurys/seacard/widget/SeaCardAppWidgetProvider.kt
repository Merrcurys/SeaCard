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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ru.merrcurys.seacard.R
import ru.merrcurys.seacard.core.db.DatabaseProvider
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
        // «Не удалось загрузить виджет». goAsync() удерживает процесс до pendingResult.finish().
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

    // Осознанно используем классический API коллекций: он единственный, что работает
    // и на API ≤ 31 (через сервис), и на новых версиях. Платформенный setRemoteAdapter
    // с RemoteCollectionItems доступен только на API 32+.
    @Suppress("DEPRECATION")
    private suspend fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val hasCards = DatabaseProvider.get(context).cardDao().getAll().isNotEmpty()
        val views = RemoteViews(context.packageName, R.layout.widget_seacard)
        if (!hasCards) {
            views.setViewVisibility(R.id.widget_cards_grid, View.GONE)
            views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_cards_grid, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty_text, View.GONE)
            // Классический RemoteViewsService: элементы строятся лениво в фабрике,
            // Bitmap не сериализуются в SharedPreferences и не теряются на API ≤ 31.
            val serviceIntent = Intent(context, SeaCardWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            // Число колонок из настроек нужно задать до установки адаптера.
            val widgetColumns = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getInt("widget_columns", 2)
                .coerceIn(1, 4)
            views.setInt(R.id.widget_cards_grid, "setNumColumns", widgetColumns)
            views.setRemoteAdapter(R.id.widget_cards_grid, serviceIntent)
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
        if (hasCards) {
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_cards_grid)
        }
    }

    /** Вызвать при изменении списка карт (добавление/удаление), чтобы виджет обновился. */
    companion object {
        private const val TAG = "SeaCardWidget"

        fun notifyDataChanged(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SeaCardAppWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            if (ids.isEmpty()) return
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                // Явно адресуем трансляцию своему приложению: иначе на Android 8+
                // неявные трансляции манифестным приёмникам не доставляются.
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }
}
