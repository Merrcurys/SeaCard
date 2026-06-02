package ru.merrcurys.seacard.widget

import android.app.PendingIntent
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.graphics.Color
import android.view.View
import android.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import android.widget.RemoteViews
import kotlinx.coroutines.runBlocking
import ru.merrcurys.seacard.R
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.features.detail.CardDetailActivity

class SeaCardAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun getSystemAccentColor(context: Context): Int {
        return try {
            // Android 12+ (Monet): берем системный акцент из палитры обоев.
            val baseAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getColor(android.R.color.system_accent1_600)
            } else {
                // До Android 12 берем базовый цвет из текущих обоев.
                val wallpaperColor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    val wm = context.getSystemService(WallpaperManager::class.java)
                    wm?.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
                } else {
                    null
                }
                wallpaperColor ?: run {
                    val systemTheme = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault_DayNight)
                    val attrs = intArrayOf(
                        android.R.attr.colorPrimary,
                        android.R.attr.colorAccent
                    )
                    val ta = systemTheme.theme.obtainStyledAttributes(attrs)
                    val primary = ta.getColor(0, Color.GRAY).takeIf { it != 0 } ?: ta.getColor(1, Color.GRAY)
                    ta.recycle()
                    primary
                }
            }

            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(baseAccent, hsl)
            // Ограничиваем в допустимый диапазон (защита от «битых» тем)
            hsl[0] = hsl[0].coerceIn(0f, 360f)
            hsl[1] = hsl[1].coerceIn(0f, 1f)
            hsl[2] = hsl[2].coerceIn(0f, 1f)
            // Делаем фон умеренно насыщенным, чтобы оттенок был мягче.
            hsl[1] = (hsl[1] * 0.75f).coerceIn(0f, 0.45f)
            hsl[2] = 0.20f
            ColorUtils.HSLToColor(hsl)
        } catch (e: Exception) {
            // Запасной цвет при любой ошибке (светло-серый)
            ColorUtils.blendARGB(Color.GRAY, Color.BLACK, 0.6f)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_seacard)
        views.setInt(R.id.widget_root, "setBackgroundColor", getSystemAccentColor(context))

        val cardCount = runBlocking {
            DatabaseProvider.get(context).cardDao().getAll().size
        }
        if (cardCount == 0) {
            views.setViewVisibility(R.id.widget_cards_grid, View.GONE)
            views.setViewVisibility(R.id.widget_empty_text, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_cards_grid, View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty_text, View.GONE)
            val serviceIntent = Intent(context, SeaCardWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            views.setRemoteAdapter(R.id.widget_cards_grid, serviceIntent)
            val templateIntent = Intent(context, CardDetailActivity::class.java)
            views.setPendingIntentTemplate(
                R.id.widget_cards_grid,
                PendingIntent.getActivity(context, 0, templateIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            )
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
        if (cardCount > 0) {
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_cards_grid)
        }
    }

    /** Вызвать при изменении списка карт (добавление/удаление), чтобы виджет обновился. */
    companion object {
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
