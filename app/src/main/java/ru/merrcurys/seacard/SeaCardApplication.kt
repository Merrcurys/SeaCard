package ru.merrcurys.seacard

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.runBlocking
import ru.merrcurys.seacard.core.db.PrefsToRoomMigration
import ru.merrcurys.seacard.core.nfc.NfcPauseManager

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
    }
}
