package ru.merrcurys.seacard.core.play

import android.app.Activity
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * Отложенное обновление через Google Play In-app updates (FLEXIBLE).
 * После скачивания сразу запускается установка ([completeUpdate]), как в RuStore-контроллере.
 */
class PlayInAppUpdateController(
    private val activity: ComponentActivity,
) {
    private val appUpdateManager = AppUpdateManagerFactory.create(activity)
    private val updateLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            unregisterListenerIfNeeded()
        }
    }

    private var listenerRegistered = false
    private var installUiStarted = false

    private val installListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> activity.runOnUiThread { startInstallUiIfNeeded() }
            InstallStatus.FAILED -> Log.e(TAG, "Play: ошибка скачивания обновления")
            InstallStatus.CANCELED -> Log.d(TAG, "Play: загрузка прервана пользователем")
            else -> Unit
        }
    }

    fun checkOnLaunch() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                when (info.updateAvailability()) {
                    UpdateAvailability.UPDATE_AVAILABLE -> {
                        when (info.installStatus()) {
                            InstallStatus.DOWNLOADED ->
                                activity.runOnUiThread { startInstallUiIfNeeded() }
                            else -> startFlexibleUpdateFlow(info)
                        }
                    }
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                        ensureListenerRegistered()
                        if (info.installStatus() == InstallStatus.DOWNLOADED) {
                            activity.runOnUiThread { startInstallUiIfNeeded() }
                        }
                    }
                    else -> Unit
                }
            }
            .addOnFailureListener { t -> Log.e(TAG, "appUpdateInfo error", t) }
    }

    private fun startFlexibleUpdateFlow(
        info: com.google.android.play.core.appupdate.AppUpdateInfo,
    ) {
        if (!info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) return

        ensureListenerRegistered()
        appUpdateManager.startUpdateFlowForResult(
            info,
            updateLauncher,
            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
        )
    }

    private fun startInstallUiIfNeeded() {
        if (installUiStarted) return
        installUiStarted = true
        unregisterListenerIfNeeded()
        appUpdateManager.completeUpdate()
            .addOnFailureListener { t ->
                Log.e(TAG, "completeUpdate error", t)
                installUiStarted = false
            }
    }

    private fun ensureListenerRegistered() {
        if (!listenerRegistered) {
            appUpdateManager.registerListener(installListener)
            listenerRegistered = true
        }
    }

    private fun unregisterListenerIfNeeded() {
        if (listenerRegistered) {
            appUpdateManager.unregisterListener(installListener)
            listenerRegistered = false
        }
    }

    fun dispose() {
        unregisterListenerIfNeeded()
    }

    companion object {
        private const val TAG = "PlayInAppUpdate"
    }
}
