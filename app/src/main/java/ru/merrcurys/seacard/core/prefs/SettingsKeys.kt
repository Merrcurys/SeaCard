package ru.merrcurys.seacard.core.prefs

/** Общие имена SharedPreferences("settings") и ключей настроек. */
object SettingsKeys {
    const val PREFS_NAME = "settings"

    /** Пауза NFC-платежей, пока приложение открыто. */
    const val KEY_PAUSE_NFC = "pause_nfc_when_open"

    /** Не давать экрану гаснуть/блокироваться, пока открыта карта. */
    const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

    /** Выкручивать яркость на максимум при открытии карты (нужно некоторым сканерам). */
    const val KEY_MAX_BRIGHTNESS = "max_brightness"
}
