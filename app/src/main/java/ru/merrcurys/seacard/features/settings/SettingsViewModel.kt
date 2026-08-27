package ru.merrcurys.seacard.features.settings

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.merrcurys.seacard.core.design.BerlinAzure
import ru.merrcurys.seacard.core.design.GradientColorOption
import androidx.compose.ui.graphics.Color

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

    private val _gradientColor = MutableStateFlow(loadGradientColor())
    val gradientColor: StateFlow<Color> = _gradientColor.asStateFlow()

    private val _gridColumns = MutableStateFlow(loadGridColumns())
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private fun loadGradientColor(): Color {
        val colorValue = prefs.getInt("gradient_color", BerlinAzure.hashCode())
        return GradientColorOption.entries.find { it.color.hashCode() == colorValue }?.color ?: BerlinAzure
    }

    private fun loadGridColumns(): Int =
        prefs.getInt("grid_columns", 2).coerceIn(1, 4)

    fun setGradientColor(color: Color) {
        prefs.edit { putInt("gradient_color", color.hashCode()) }
        _gradientColor.value = color
    }

    fun setGridColumns(columns: Int) {
        val fixed = columns.coerceIn(1, 4)
        prefs.edit { putInt("grid_columns", fixed) }
        _gridColumns.value = fixed
    }
}
