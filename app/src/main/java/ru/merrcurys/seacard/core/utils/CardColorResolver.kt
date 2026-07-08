package ru.merrcurys.seacard.core.utils

import android.content.Context
import ru.merrcurys.seacard.core.db.CardDao
import ru.merrcurys.seacard.core.db.CardEntity

/**
 * Цвет карты хранится в БД. Если там дефолтный белый, а обложка есть — берём акцент из обложки.
 */
object CardColorResolver {

    const val DEFAULT_CARD_COLOR = 0xFFFFFFFF.toInt()

    /**
     * Возвращает [storedColor], если он не дефолтный; иначе пробует извлечь акцент из [frontCoverPath].
     */
    suspend fun resolveColor(
        context: Context,
        storedColor: Int,
        frontCoverPath: String?,
    ): Int {
        if (storedColor != DEFAULT_CARD_COLOR) return storedColor
        return accentFromCover(context, frontCoverPath) ?: storedColor
    }

    suspend fun accentFromCover(context: Context, frontCoverPath: String?): Int? {
        val coverPath = frontCoverPath ?: return null
        val accent = if (coverPath.startsWith("cards/")) {
            DominantColorExtractor.fromAsset(context, coverPath)
        } else {
            DominantColorExtractor.fromFile(coverPath)
        } ?: return null
        return accent.takeIf { it != DEFAULT_CARD_COLOR }
    }

    /**
     * Исправляет карты с дефолтным белым цветом и обложкой (импорт, старые данные, пропущенная миграция).
     * Идемпотентно: уже исправленные карты пропускаются.
     */
    suspend fun repairDefaultColors(context: Context, dao: CardDao): Int {
        var updated = 0
        for (card in dao.getAll()) {
            val coverPath = card.frontCoverPath ?: continue
            if (card.color != DEFAULT_CARD_COLOR) continue

            val accentColor = accentFromCover(context, coverPath) ?: continue
            dao.update(card.copy(color = accentColor))
            updated++
        }
        return updated
    }
}
