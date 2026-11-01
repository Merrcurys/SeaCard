package ru.merrcurys.seacard.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Прогрев RemoteViewsService для API ≤ 30.
 *
 * После `updateAppWidget` лончер сам привязывается к сервису, но процесс приложения
 * может быть убит сразу после `goAsync().finish()`. Тогда лончер поднимает сервис
 * «на холодную», и на Android 10/11 это иногда заканчивается надписью
 * «Не удалось загрузить виджет». Собственный bind держит процесс живым и создаёт
 * сервис заранее, пока broadcast ещё не завершён.
 */
internal object SeaCardWidgetServicePrewarm {

    private const val TAG = "SeaCardWidget"
    private const val KEEP_ALIVE_MS = 20_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connection: ServiceConnection? = null
    private var releaseJob: Job? = null

    @Synchronized
    fun prewarm(context: Context) {
        val appContext = context.applicationContext
        if (connection == null) {
            val newConnection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) = Unit
                override fun onServiceDisconnected(name: ComponentName?) = Unit
            }
            val bound = runCatching {
                appContext.bindService(
                    Intent(appContext, SeaCardWidgetService::class.java),
                    newConnection,
                    Context.BIND_AUTO_CREATE
                )
            }.getOrElse { t ->
                Log.w(TAG, "widget service prewarm bind failed", t)
                false
            }
            if (!bound) return
            connection = newConnection
        }
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(KEEP_ALIVE_MS)
            release(appContext)
        }
    }

    @Synchronized
    private fun release(context: Context) {
        val current = connection ?: return
        connection = null
        releaseJob = null
        runCatching { context.unbindService(current) }
            .onFailure { Log.w(TAG, "widget service prewarm unbind failed", it) }
    }
}
