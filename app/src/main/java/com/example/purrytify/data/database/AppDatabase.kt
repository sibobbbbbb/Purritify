package com.example.purrytify.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.purrytify.data.dao.SongDao
import com.example.purrytify.data.entity.Song

// Update versi database
@Database(entities = [Song::class], version = 3) // Ubah dari versi 2 ke versi 3
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migrasi dari versi 1 ke 2 (yang sudah ada)
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE songs ADD COLUMN user_id INTEGER NOT NULL DEFAULT -1")
            }
        }

        // Tambahkan migrasi dari versi 2 ke 3
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Tambah kolom is_online (Boolean)
                database.execSQL("ALTER TABLE songs ADD COLUMN is_online INTEGER NOT NULL DEFAULT 0")
                // Tambah kolom online_id (Integer nullable)
                database.execSQL("ALTER TABLE songs ADD COLUMN online_id INTEGER DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "purrytify_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // Tambahkan migrasi baru
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}