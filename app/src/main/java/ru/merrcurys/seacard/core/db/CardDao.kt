package ru.merrcurys.seacard.core.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Query("SELECT * FROM cards ORDER BY addTime DESC")
    fun getAllFlow(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards ORDER BY addTime DESC")
    suspend fun getAll(): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun getById(id: Long): CardEntity?

    @Query("SELECT * FROM cards WHERE name = :name AND code = :code AND type = :type LIMIT 1")
    suspend fun getByNameCodeType(name: String, code: String, type: String): CardEntity?

    @Insert
    suspend fun insert(entity: CardEntity): Long

    @Update
    suspend fun update(entity: CardEntity)

    @Delete
    suspend fun delete(entity: CardEntity)

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM cards")
    suspend fun deleteAll()

    @Query("UPDATE cards SET usageCount = usageCount + 1 WHERE id = :id")
    suspend fun incrementUsage(id: Long)

    @Query("UPDATE cards SET frontCoverPath = :path WHERE id = :id")
    suspend fun updateFrontCover(id: Long, path: String?)

    @Query("UPDATE cards SET backCoverPath = :path WHERE id = :id")
    suspend fun updateBackCover(id: Long, path: String?)

    @Query("UPDATE cards SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?)

    @Query("UPDATE cards SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Long, sortOrder: Long)
}
