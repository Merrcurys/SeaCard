package ru.merrcurys.seacard.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Досрочное обновление виджета после перезагрузки устройства и обновления приложения.
 * В этих случаях система не гарантирует APPWIDGET_UPDATE, и без явного запроса
 * виджет остаётся пустым до первого запуска приложения.
 */
class SeaCardWidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SeaCardAppWidgetProvider.notifyDataChanged(context)
    }
}
