package ru.merrcurys.seacard.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CardEntity::class, CardFolderEntity::class, CardFolderRefEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun cardFolderDao(): CardFolderDao
    abstract fun cardFolderRefDao(): CardFolderRefDao
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cards ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS card_folders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "sortOrder INTEGER NOT NULL, " +
                "createdAt INTEGER NOT NULL)"
        )
        db.execSQL("ALTER TABLE cards ADD COLUMN folderId INTEGER")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Одна карта теперь может лежать в нескольких папках: переносим текущую
        // привязку из cards.folderId в таблицу связей и убираем колонку.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS card_folder_refs (" +
                "cardId INTEGER NOT NULL, " +
                "folderId INTEGER NOT NULL, " +
                "PRIMARY KEY(cardId, folderId))"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_card_folder_refs_folderId " +
                "ON card_folder_refs(folderId)"
        )
        db.execSQL(
            "INSERT OR IGNORE INTO card_folder_refs(cardId, folderId) " +
                "SELECT id, folderId FROM cards WHERE folderId IS NOT NULL"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS cards_new (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "name TEXT NOT NULL, " +
                "code TEXT NOT NULL, " +
                "type TEXT NOT NULL, " +
                "addTime INTEGER NOT NULL, " +
                "usageCount INTEGER NOT NULL, " +
                "color INTEGER NOT NULL, " +
                "frontCoverPath TEXT, " +
                "backCoverPath TEXT, " +
                "note TEXT, " +
                "sortOrder INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "INSERT INTO cards_new (" +
                "id, name, code, type, addTime, usageCount, color, " +
                "frontCoverPath, backCoverPath, note, sortOrder) " +
                "SELECT id, name, code, type, addTime, usageCount, color, " +
                "frontCoverPath, backCoverPath, note, sortOrder FROM cards"
        )
        db.execSQL("DROP TABLE cards")
        db.execSQL("ALTER TABLE cards_new RENAME TO cards")
    }
}

object DatabaseProvider {

    @Volatile
    private var instance: AppDatabase? = null

    fun get(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "seacard.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}
