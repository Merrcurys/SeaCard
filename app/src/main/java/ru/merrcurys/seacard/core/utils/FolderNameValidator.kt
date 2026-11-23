package ru.merrcurys.seacard.core.utils

import ru.merrcurys.seacard.domain.entity.Folder
import java.util.Locale

enum class FolderNameError(val message: String) {
    EMPTY("Введите название"),
    TOO_LONG("Максимум ${FolderNameValidator.MAX_LENGTH} символов"),
    DUPLICATE("Вкладка с таким названием уже есть")
}

sealed interface FolderCreationResult {
    data class Success(val folder: Folder) : FolderCreationResult
    data class Failure(val message: String) : FolderCreationResult
}

/** Чистая логика нормализации и проверки имён папок (без Android-зависимостей). */
object FolderNameValidator {

    const val MAX_LENGTH = 40

    fun normalize(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    /**
     * @param existing пары (id папки, её имя) — для проверки дублей.
     * @param selfId id переименовываемой папки: совпадение с собственным именем разрешено.
     */
    fun validate(
        raw: String,
        existing: List<Pair<Long?, String>>,
        selfId: Long? = null
    ): FolderNameError? {
        val name = normalize(raw)
        if (name.isEmpty()) return FolderNameError.EMPTY
        if (name.length > MAX_LENGTH) return FolderNameError.TOO_LONG
        val normalizedName = name.lowercase(Locale.ROOT)
        val duplicate = existing.any { (id, other) ->
            id != selfId && normalize(other).lowercase(Locale.ROOT) == normalizedName
        }
        return if (duplicate) FolderNameError.DUPLICATE else null
    }
}
