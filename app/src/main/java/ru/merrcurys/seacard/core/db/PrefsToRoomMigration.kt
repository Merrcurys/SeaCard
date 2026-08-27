package ru.merrcurys.seacard.core.db

import android.content.Context
import ru.merrcurys.seacard.core.utils.CardColorResolver
import ru.merrcurys.seacard.core.utils.ColorCoverGenerator
import androidx.core.content.edit

/**
 * Однократная миграция данных из SharedPreferences в Room.
 * После обновления приложения карточки пользователя переносятся в БД и не теряются.
 */
object PrefsToRoomMigration {

    private const val PREFS_NAME = "cards"
    private const val KEY_CARD_LIST = "card_list"
    private const val KEY_MIGRATED = "migrated_to_room"
    private const val KEY_COLOR_COVERS_MIGRATED = "color_covers_migrated_to_webp"
    private const val PREFIX_COVER_FRONT = "cover_front_"
    private const val PREFIX_COVER_BACK = "cover_back_"
    private const val PREFIX_NOTE = "note_"

    /**
     * Выполняет миграцию, если она ещё не выполнялась.
     * Вызывать при первом обращении к БД (например, в DatabaseProvider.get).
     */
    suspend fun migrateIfNeeded(context: Context) {
        migrateColorCoversToWebp(context)
        repairCoverAccentColors(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        val cardSet = prefs.getStringSet(KEY_CARD_LIST, null) ?: emptySet()
        if (cardSet.isEmpty()) {
            prefs.edit { putBoolean(KEY_MIGRATED, true) }
            return
        }

        val db = DatabaseProvider.get(context)
        val dao = db.cardDao()

        for (cardString in cardSet) {
            val parts = cardString.split("|")
            val entity = when (parts.size) {
                2 -> CardEntity(
                    name = parts[0],
                    code = parts[1],
                    type = "barcode",
                    addTime = System.currentTimeMillis(),
                    usageCount = 0,
                    color = 0xFFFFFFFF.toInt(),
                    frontCoverPath = null,
                    backCoverPath = null,
                    note = null
                )
                3 -> CardEntity(
                    name = parts[0],
                    code = parts[1],
                    type = parts[2],
                    addTime = System.currentTimeMillis(),
                    usageCount = 0,
                    color = 0xFFFFFFFF.toInt(),
                    frontCoverPath = null,
                    backCoverPath = null,
                    note = null
                )
                5 -> CardEntity(
                    name = parts[0],
                    code = parts[1],
                    type = parts[2],
                    addTime = parts[3].toLongOrNull() ?: System.currentTimeMillis(),
                    usageCount = parts[4].toIntOrNull() ?: 0,
                    color = 0xFFFFFFFF.toInt(),
                    frontCoverPath = null,
                    backCoverPath = null,
                    note = null
                )
                6 -> CardEntity(
                    name = parts[0],
                    code = parts[1],
                    type = parts[2],
                    addTime = parts[3].toLongOrNull() ?: System.currentTimeMillis(),
                    usageCount = parts[4].toIntOrNull() ?: 0,
                    color = parts[5].toIntOrNull() ?: 0xFFFFFFFF.toInt(),
                    frontCoverPath = null,
                    backCoverPath = null,
                    note = null
                )
                7 -> CardEntity(
                    name = parts[0],
                    code = parts[1],
                    type = parts[2],
                    addTime = parts[3].toLongOrNull() ?: System.currentTimeMillis(),
                    usageCount = parts[4].toIntOrNull() ?: 0,
                    color = parts[5].toIntOrNull() ?: 0xFFFFFFFF.toInt(),
                    frontCoverPath = parts[6].takeIf { it.isNotBlank() },
                    backCoverPath = null,
                    note = null
                )
                else -> continue
            }

            val id = dao.insert(entity)

            val frontCover = prefs.getString("${PREFIX_COVER_FRONT}${entity.name}_${entity.code}", null)
            val backCover = prefs.getString("${PREFIX_COVER_BACK}${entity.name}_${entity.code}", null)
            val note = prefs.getString("${PREFIX_NOTE}${entity.name}_${entity.code}_${entity.type}", null)

            if (frontCover != null || backCover != null || !note.isNullOrBlank()) {
                val updated = entity.copy(
                    id = id,
                    frontCoverPath = frontCover ?: entity.frontCoverPath,
                    backCoverPath = backCover ?: entity.backCoverPath,
                    note = note ?: entity.note
                )
                dao.update(updated)
            }
        }

        prefs.edit { putBoolean(KEY_MIGRATED, true) }
    }

    /**
     * Однократная миграция: для старых кастомных карт (frontCoverPath == null, но есть color)
     * генерирует обложку из цвета и названия и сохраняет в WebP.
     */
    private suspend fun migrateColorCoversToWebp(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_COLOR_COVERS_MIGRATED, false)) return

        val db = DatabaseProvider.get(context)
        val dao = db.cardDao()
        val allCards = dao.getAll()

        for (card in allCards) {
            if (card.frontCoverPath != null) continue
            val path = ColorCoverGenerator.generateAndSaveAsWebp(
                context = context,
                name = card.name,
                color = card.color,
                fileName = "front_${card.name.replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9\\-_]"), "_").take(50).ifBlank { "card" }}_migrated_${card.id}.webp"
            )
            path?.let { dao.updateFrontCover(card.id, it) }
        }

        prefs.edit { putBoolean(KEY_COLOR_COVERS_MIGRATED, true) }
        ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider.notifyDataChanged(context)
    }

    /**
     * Карты с дефолтным белым и обложкой: записывает акцентный цвет из обложки.
     * Идемпотентно, вызывается при каждом запуске (в т.ч. после импорта старых бэкапов).
     */
    private suspend fun repairCoverAccentColors(context: Context) {
        val updated = CardColorResolver.repairDefaultColors(context, DatabaseProvider.get(context).cardDao())
        if (updated > 0) {
            ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider.notifyDataChanged(context)
        }
    }
}
