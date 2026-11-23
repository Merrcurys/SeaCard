package ru.merrcurys.seacard.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CardFolderRefDao {

    @Query("SELECT * FROM card_folder_refs")
    fun getAllFlow(): Flow<List<CardFolderRefEntity>>

    @Query("SELECT * FROM card_folder_refs")
    suspend fun getAll(): List<CardFolderRefEntity>

    @Query("SELECT folderId FROM card_folder_refs WHERE cardId = :cardId")
    suspend fun getFolderIds(cardId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(refs: List<CardFolderRefEntity>)

    @Query("DELETE FROM card_folder_refs WHERE cardId = :cardId")
    suspend fun deleteByCard(cardId: Long)

    @Query("DELETE FROM card_folder_refs WHERE cardId = :cardId AND folderId = :folderId")
    suspend fun deleteRef(cardId: Long, folderId: Long)

    @Query("DELETE FROM card_folder_refs WHERE folderId = :folderId")
    suspend fun deleteByFolder(folderId: Long)

    @Query("DELETE FROM card_folder_refs")
    suspend fun deleteAll()
}
