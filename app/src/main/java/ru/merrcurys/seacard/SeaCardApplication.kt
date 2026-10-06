package ru.merrcurys.seacard

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.runBlocking
import ru.merrcurys.seacard.core.db.PrefsToRoomMigration
import ru.merrcurys.seacard.core.nfc.NfcPauseManager
import ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider

class SeaCardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runBlocking {
            PrefsToRoomMigration.migrateIfNeeded(applicationContext)
        }

        // «Пауза NFC» действует ровно пока приложение на переднем плане:
        // reader mode включается на возобновлённой активности и снимается при уходе в фон.
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (NfcPauseManager.isPauseEnabled(activity)) {
                    NfcPauseManager.enable(activity)
                } else {
                    NfcPauseManager.disable(activity)
                }
            }

            override fun onActivityPaused(activity: Activity) {
                NfcPauseManager.disable(activity)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        // Система не гарантирует APPWIDGET_UPDATE при обновлении приложения/данных,
        // поэтому виджеты, сломанные старой версией, сами пересоберутся новым кодом
        // после первого запуска приложения (пользователю не нужно удалять/добавлять).
        SeaCardAppWidgetProvider.notifyDataChanged(applicationContext)
    }
}
