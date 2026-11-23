package ru.merrcurys.seacard.domain.entity

/**
 * Пользовательская папка (вкладка) карт.
 * Связь с картами — many-to-many через таблицу card_folder_refs.
 */
data class Folder(
    val id: Long,
    val name: String,
    val sortOrder: Long = 0L,
    val createdAt: Long = 0L
)

const val NO_FOLDER_LABEL = "Без вкладки"
