package ru.merrcurys.seacard

import android.app.Application
import kotlinx.coroutines.runBlocking
import ru.merrcurys.seacard.core.db.PrefsToRoomMigration
import ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider

class SeaCardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runBlocking {
            PrefsToRoomMigration.migrateIfNeeded(applicationContext)
        }
        // Система не гарантирует APPWIDGET_UPDATE при обновлении приложения/данных,
        // поэтому виджеты, сломанные старой версией, сами пересоберутся новым кодом
        // после первого запуска приложения (пользователю не нужно удалять/добавлять).
        SeaCardAppWidgetProvider.notifyDataChanged(applicationContext)
    }
}
