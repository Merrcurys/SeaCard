package ru.merrcurys.seacard.core.db

import ru.merrcurys.seacard.core.utils.FolderCreationResult
import ru.merrcurys.seacard.core.utils.FolderNameValidator

/** Создаёт папку с нормализацией имени, проверкой дублей и следующим порядком. */
suspend fun CardFolderDao.createWithValidation(rawName: String): FolderCreationResult {
    val existing = getAll()
    val normalized = FolderNameValidator.normalize(rawName)
    FolderNameValidator.validate(
        raw = normalized,
        existing = existing.map { it.id to it.name }
    )?.let { return FolderCreationResult.Failure(it.message) }

    val entity = CardFolderEntity(
        name = normalized,
        sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1L) + 1L,
        createdAt = System.currentTimeMillis()
    )
    val id = insert(entity)
    return FolderCreationResult.Success(entity.copy(id = id).toFolder())
}
