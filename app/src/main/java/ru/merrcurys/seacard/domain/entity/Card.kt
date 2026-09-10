package ru.merrcurys.seacard.domain.entity

/**
 * Модель карточки для UI. Единый вид для всех карточек (все поля всегда присутствуют).
 */
data class Card(
    val id: Long,
    val name: String,
    val code: String,
    val type: String,
    val addTime: Long,
    val usageCount: Int,
    val color: Int,
    /** Лицевая обложка: путь к файлу или asset (например "cards/xxx.webp"). */
    val frontCoverPath: String?,
    /** Оборотная обложка: путь к файлу. */
    val backCoverPath: String?,
    /** Заметка пользователя. */
    val note: String?,
    /** Позиция в пользовательской сортировке (меньше — раньше). */
    val sortOrder: Long = 0L
) {
    /** Для совместимости с местами, где раньше использовался coverAsset (лицевая обложка). */
    val coverAsset: String? get() = frontCoverPath
}
