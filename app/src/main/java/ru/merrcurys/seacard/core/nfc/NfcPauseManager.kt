package ru.merrcurys.seacard.core.nfc

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.util.Log

/**
 * «Пауза NFC» на время, пока приложение открыто.
 *
 * Обычные приложения Android не могут программно выключить системный NFC
 * (для этого нужен WRITE_SECURE_SETTINGS / права device-owner). Зато можно
 * перевести NFC-контроллер в режим чтения ([NfcAdapter.enableReaderMode]).
 * В reader mode отключается эмуляция карты (HCE), поэтому бесконтактная
 * оплата — Google Pay и т.п. — не срабатывает, пока приложение на переднем плане.
 *
 * Reader mode привязан к Activity: как только активность уходит в фон или
 * приложение закрывается, система снимает режим, и NFC снова работает штатно.
 * Это и обеспечивает требование «только пока открыто приложение».
 */
object NfcPauseManager {

    const val PREFS_NAME = "settings"
    const val KEY_PAUSE_NFC = "pause_nfc_when_open"

    private const val TAG = "NfcPauseManager"

    /** Пустой колбэк: теги читать не нужно, важен сам факт reader mode. */
    private val readerCallback = NfcAdapter.ReaderCallback { }

    private val readerFlags =
        NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

    /** Активность, для которой сейчас удерживается reader mode (или null). */
    private var readerActivity: Activity? = null

    /** Есть ли на устройстве NFC-адаптер. */
    fun isSupported(context: Context): Boolean = adapter(context) != null

    /** Включена ли пользователем пауза NFC (значение настройки). */
    fun isPauseEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PAUSE_NFC, false)

    /** Применяет настройку к конкретной активности: включить или снять reader mode. */
    fun apply(activity: Activity, pause: Boolean) {
        if (pause) enable(activity) else disable(activity)
    }

    /** Включает reader mode для активности (если это возможно). */
    fun enable(activity: Activity) {
        val adapter = adapter(activity) ?: return
        if (!adapter.isEnabled) return
        if (readerActivity === activity) return
        disableCurrent()
        try {
            adapter.enableReaderMode(activity, readerCallback, readerFlags, null)
            readerActivity = activity
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось включить режим чтения NFC", e)
        }
    }

    /** Снимает reader mode с активности, если он был включён именно для неё. */
    fun disable(activity: Activity) {
        if (readerActivity === activity) {
            disableCurrent()
        }
    }

    private fun disableCurrent() {
        val activity = readerActivity ?: return
        readerActivity = null
        try {
            adapter(activity)?.disableReaderMode(activity)
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось выключить режим чтения NFC", e)
        }
    }

    private fun adapter(context: Context): NfcAdapter? =
        context.getSystemService(NfcManager::class.java)?.defaultAdapter
}
