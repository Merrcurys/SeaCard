package ru.merrcurys.seacard.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardFolderDao {

    @Query("SELECT * FROM card_folders ORDER BY sortOrder, id")
    fun getAllFlow(): Flow<List<CardFolderEntity>>

    @Query("SELECT * FROM card_folders ORDER BY sortOrder, id")
    suspend fun getAll(): List<CardFolderEntity>

    @Query("SELECT * FROM card_folders WHERE id = :id")
    suspend fun getById(id: Long): CardFolderEntity?

    @Insert
    suspend fun insert(entity: CardFolderEntity): Long

    @Update
    suspend fun update(entity: CardFolderEntity)

    @Query("DELETE FROM card_folders WHERE id = :id")
    suspend fun deleteById(id: Long)
}
