package ru.merrcurys.seacard.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.merrcurys.seacard.domain.entity.Card

/**
 * Единая сущность карточки в Room.
 * Все карточки имеют один и тот же набор полей (нет «разных полей» между картами).
 */
@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val code: String,
    val type: String,
    val addTime: Long,
    val usageCount: Int,
    val color: Int,
    /** Путь к лицевой обложке: файл на диске или asset (например "cards/xxx.webp"). */
    val frontCoverPath: String?,
    /** Путь к оборотной обложке (файл на диске). */
    val backCoverPath: String?,
    /** Заметка пользователя (до 100 символов). */
    val note: String?,
    /** Позиция в пользовательской сортировке (меньше — раньше). */
    val sortOrder: Long = 0L
) {
    fun toCard(): Card = Card(
        id = id,
        name = name,
        code = code,
        type = type,
        addTime = addTime,
        usageCount = usageCount,
        color = color,
        frontCoverPath = frontCoverPath,
        backCoverPath = backCoverPath,
        note = note,
        sortOrder = sortOrder
    )
}
