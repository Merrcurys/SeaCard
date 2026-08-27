package ru.merrcurys.seacard.core.design

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

object GradientUtils {
    fun loadGradientColorPref(context: Context): Color {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val colorValue = prefs.getInt("gradient_color", BerlinAzure.hashCode())
        return GradientColorOption.entries.find { it.color.hashCode() == colorValue }?.color ?: BerlinAzure
    }
}

class GradientState(initialColor: Color) {
    var gradientColor by mutableStateOf(initialColor)

    fun updateGradientColor(newColor: Color) {
        gradientColor = newColor
    }
}

@Composable
fun rememberGradientState(context: Context): GradientState {
    val initialColor = GradientUtils.loadGradientColorPref(context)
    return remember { GradientState(initialColor) }
}
