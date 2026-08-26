package ru.merrcurys.seacard.core.design

import android.graphics.Color
import android.view.WindowManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Edge-to-edge: контент рисуется под статус- и навигационную панель, полосы прозрачные —
 * виден фон из Compose (градиент), а не [android.R.color] / windowBackground (#111111).
 * Плюс снятие [FLAG_TRANSLUCENT_*] для старых OEM, где иначе остаётся серый дефолт.
 */
fun ComponentActivity.applySeaCardSystemBarColors() {
    @Suppress("DEPRECATION")
    window.clearFlags(
        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
    }
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
}
