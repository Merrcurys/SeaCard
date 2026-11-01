package ru.merrcurys.seacard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.RemoteViews
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

    // Legacy-адаптер осознанно оставлен для API ≤ 30: там он работает штатно.
    @Suppress("DEPRECATION")
    private suspend fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // Сбой чтения БД не должен превращать виджет в «Карт нет»: просто оставляем
        // предыдущее состояние до следующего обновления.
        val cards = try {
            SeaCardWidgetData.loadCards(context)
        } catch (t: Throwable) {
            Log.e(TAG, "loadCards failed for widget $appWidgetId", t)
            return
        }
        // На API ≤ 30 GridView.setNumColumns() НЕ помечен @RemotableViewMethod, поэтому
        // RemoteViews.setInt(..., "setNumColumns", n) на Android 10/11 бросает ActionException
        // и лончер показывает «Не удалось загрузить виджет». Колонки задаём статически
        // отдельными layout'ами.
        val widgetColumns = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getInt("widget_columns", 2)
            .coerceIn(1, 4)
        val views = RemoteViews(context.packageName, layoutForColumns(widgetColumns))
        if (cards.isEmpty()) {
            views.setViewVisibility(R.id.widget_cards_grid, View.GONE)
            views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        // На API 31+ отправляем RemoteCollectionItems в одном вызове. Legacy-адаптер
        // на Android 16+ конвертируется асинхронно уже после возврата из updateAppWidget,
        // и если процесс умрёт до завершения конвертации, виджет останется пустым.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val collectionViews = buildCollectionViews(context, widgetColumns)
                collectionViews.setRemoteAdapter(
                    R.id.widget_cards_grid,
                    SeaCardWidgetCollection.build(context, cards)
                )
                appWidgetManager.updateAppWidget(appWidgetId, collectionViews)
                return
            } catch (t: Throwable) {
                // Коллекция могла не влезть в лимит RemoteViews (много карт/крупные превью).
                // Тогда ниже отправим тот же список через сервис: он асинхронный, но рабочий.
                Log.e(TAG, "explicit collection update failed, falling back to service", t)
            }
        }

        // API ≤ 30 (и запасной путь): классический RemoteViewsService. Элементы
        // строятся лениво в фабрике, bitmap не сериализуются целиком.
        val legacyViews = buildCollectionViews(context, widgetColumns)
        val serviceIntent = Intent(context, SeaCardWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            // Уникальный data: без него FilterComparison у всех виджетов одинаковый
            // и лончер может переиспользовать не тот адаптер/не перепривязать сервис.
            data = Uri.parse("seacard-widget://${context.packageName}/$appWidgetId")
        }
        legacyViews.setRemoteAdapter(R.id.widget_cards_grid, serviceIntent)
        appWidgetManager.updateAppWidget(appWidgetId, legacyViews)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_cards_grid)
        // Держим процесс и RemoteViewsService живыми, пока лончер привязывается к сервису.
        // Иначе процесс может умереть сразу после onUpdate (goAsync завершён), лончер
        // стартует его заново и на Android 10/11 показывает «Не удалось загрузить виджет».
        SeaCardWidgetServicePrewarm.prewarm(context)
    }

    /** Базовая разметка списка: видимость и шаблон клика по карте. */
    private fun buildCollectionViews(context: Context, widgetColumns: Int): RemoteViews =
        RemoteViews(context.packageName, layoutForColumns(widgetColumns)).apply {
            setViewVisibility(R.id.widget_cards_grid, View.VISIBLE)
            setViewVisibility(R.id.widget_empty_text, View.GONE)
            val templateIntent = Intent(context, CardDetailActivity::class.java).apply {
                // NEW_TASK — запуск из виджета; SINGLE_TOP — повторный тап приходит в onNewIntent,
                // иначе показывалась бы ранее открытая карточка.
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            setPendingIntentTemplate(
                R.id.widget_cards_grid,
                PendingIntent.getActivity(
                    context,
                    0,
                    templateIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )
        }

    companion object {
        private const val TAG = "SeaCardWidget"

        /**
         * Колонки задаются статически в layout, а не через `setNumColumns`: на API ≤ 30
         * этот метод не помечен @RemotableViewMethod, и вызов через RemoteViews падает.
         */
        private fun layoutForColumns(columns: Int): Int = when (columns) {
            1 -> R.layout.widget_seacard_1col
            3 -> R.layout.widget_seacard_3col
            4 -> R.layout.widget_seacard_4col
            else -> R.layout.widget_seacard
        }

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
