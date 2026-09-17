package ru.merrcurys.seacard.widget

import android.content.Intent
import android.widget.RemoteViewsService

/**
 * Классический сервис для коллекции виджета. В отличие от `RemoteViewsCompat`
 * (через который items сериализуются в SharedPreferences), здесь RemoteViews
 * строятся лениво для видимых ячеек — это работает на всех версиях Android,
 * не тратит память на превью всех карт и не теряет Bitmap при сериализации
 * Parcel'а на API ≤ 31.
 */
class SeaCardWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        SeaCardRemoteViewsFactory(applicationContext)
}
