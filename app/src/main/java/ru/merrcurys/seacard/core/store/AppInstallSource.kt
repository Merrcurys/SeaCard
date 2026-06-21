package ru.merrcurys.seacard.core.store

import android.content.Context
import android.os.Build

enum class AppInstallSource {
    GOOGLE_PLAY,
    RU_STORE,
    UNKNOWN,
    ;

    companion object {
        private const val INSTALLER_GOOGLE_PLAY = "com.android.vending"
        private const val INSTALLER_RU_STORE_VK = "ru.vk.store"
        private const val INSTALLER_RU_STORE_LEGACY = "ru.store"

        fun detect(context: Context): AppInstallSource {
            val installer = installingPackageName(context) ?: return UNKNOWN
            return when (installer) {
                INSTALLER_GOOGLE_PLAY -> GOOGLE_PLAY
                INSTALLER_RU_STORE_VK, INSTALLER_RU_STORE_LEGACY -> RU_STORE
                else -> UNKNOWN
            }
        }

        private fun installingPackageName(context: Context): String? {
            val packageManager = context.packageManager
            val packageName = context.packageName
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
        }
    }
}
