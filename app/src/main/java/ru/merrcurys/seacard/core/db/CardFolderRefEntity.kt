package ru.merrcurys.seacard.core.db

import androidx.room.Entity
import androidx.room.Index

/** Связь «карта ↔ папка»: одна карта может лежать в нескольких папках. */
@Entity(
    tableName = "card_folder_refs",
    primaryKeys = ["cardId", "folderId"],
    indices = [Index("folderId")]
)
data class CardFolderRefEntity(
    val cardId: Long,
    val folderId: Long
)
