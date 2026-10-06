package ru.merrcurys.seacard.features.settings

import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.merrcurys.seacard.core.design.BerlinAzure
import ru.merrcurys.seacard.core.design.GradientColorOption
import ru.merrcurys.seacard.core.prefs.SettingsKeys
import ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider
import androidx.compose.ui.graphics.Color

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(
        SettingsKeys.PREFS_NAME,
        android.content.Context.MODE_PRIVATE
    )

    private val _gradientColor = MutableStateFlow(loadGradientColor())
    val gradientColor: StateFlow<Color> = _gradientColor.asStateFlow()

    private val _gridColumns = MutableStateFlow(loadGridColumns())
    val gridColumns: StateFlow<Int> = _gridColumns.asStateFlow()

    private val _widgetColumns = MutableStateFlow(loadWidgetColumns())
    val widgetColumns: StateFlow<Int> = _widgetColumns.asStateFlow()

    private val _pauseNfc = MutableStateFlow(loadPauseNfc())
    val pauseNfc: StateFlow<Boolean> = _pauseNfc.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(loadKeepScreenOn())
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _maxBrightness = MutableStateFlow(loadMaxBrightness())
    val maxBrightness: StateFlow<Boolean> = _maxBrightness.asStateFlow()

    private fun loadGradientColor(): Color {
        val colorValue = prefs.getInt("gradient_color", BerlinAzure.hashCode())
        return GradientColorOption.entries.find { it.color.hashCode() == colorValue }?.color ?: BerlinAzure
    }

    private fun loadGridColumns(): Int =
        prefs.getInt("grid_columns", 2).coerceIn(1, 4)

    private fun loadWidgetColumns(): Int =
        prefs.getInt("widget_columns", 2).coerceIn(1, 4)

    private fun loadPauseNfc(): Boolean =
        prefs.getBoolean(SettingsKeys.KEY_PAUSE_NFC, false)

    private fun loadKeepScreenOn(): Boolean =
        prefs.getBoolean(SettingsKeys.KEY_KEEP_SCREEN_ON, false)

    private fun loadMaxBrightness(): Boolean =
        prefs.getBoolean(SettingsKeys.KEY_MAX_BRIGHTNESS, true)

    fun setGradientColor(color: Color) {
        prefs.edit { putInt("gradient_color", color.hashCode()) }
        _gradientColor.value = color
    }

    fun setGridColumns(columns: Int) {
        val fixed = columns.coerceIn(1, 4)
        prefs.edit { putInt("grid_columns", fixed) }
        _gridColumns.value = fixed
    }

    fun setWidgetColumns(columns: Int) {
        val fixed = columns.coerceIn(1, 4)
        prefs.edit { putInt("widget_columns", fixed) }
        _widgetColumns.value = fixed
        // Виджет читает это значение при сборке RemoteViews — просим его пересобраться.
        SeaCardAppWidgetProvider.notifyDataChanged(getApplication())
    }

    fun setPauseNfc(enabled: Boolean) {
        prefs.edit { putBoolean(SettingsKeys.KEY_PAUSE_NFC, enabled) }
        _pauseNfc.value = enabled
    }

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit { putBoolean(SettingsKeys.KEY_KEEP_SCREEN_ON, enabled) }
        _keepScreenOn.value = enabled
    }

    fun setMaxBrightness(enabled: Boolean) {
        prefs.edit { putBoolean(SettingsKeys.KEY_MAX_BRIGHTNESS, enabled) }
        _maxBrightness.value = enabled
    }
}
