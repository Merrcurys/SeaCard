package ru.merrcurys.seacard.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.merrcurys.seacard.domain.entity.Folder

@Entity(tableName = "card_folders")
data class CardFolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortOrder: Long = 0L,
    val createdAt: Long = 0L
) {
    fun toFolder(): Folder = Folder(
        id = id,
        name = name,
        sortOrder = sortOrder,
        createdAt = createdAt
    )
}
