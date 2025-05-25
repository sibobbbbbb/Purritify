package com.example.purrytify.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.purrytify.data.dao.AnalyticsDao
import com.example.purrytify.data.dao.SongDao
import com.example.purrytify.data.entity.Song
import com.example.purrytify.data.entity.ListeningSession
import com.example.purrytify.data.entity.MonthlyAnalytics
import com.example.purrytify.data.entity.SongStreak

@Database(
    entities = [
        Song::class,
        ListeningSession::class,
        MonthlyAnalytics::class,
        SongStreak::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun analyticsDao(): AnalyticsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE songs ADD COLUMN user_id INTEGER NOT NULL DEFAULT -1")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Tambah kolom is_online (Boolean)
                database.execSQL("ALTER TABLE songs ADD COLUMN is_online INTEGER NOT NULL DEFAULT 0")
                // Tambah kolom online_id (Integer nullable)
                database.execSQL("ALTER TABLE songs ADD COLUMN online_id INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create listening_sessions table
                database.execSQL("DROP TABLE IF EXISTS listening_sessions")
                database.execSQL("DROP TABLE IF EXISTS monthly_analytics")
                database.execSQL("DROP TABLE IF EXISTS song_streaks")

                database.execSQL("""
            CREATE TABLE IF NOT EXISTS listening_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                song_id INTEGER NOT NULL,
                song_title TEXT NOT NULL,
                artist_name TEXT NOT NULL,
                start_time INTEGER NOT NULL,
                end_time INTEGER,
                duration_listened INTEGER NOT NULL,
                total_duration INTEGER NOT NULL,
                date TEXT NOT NULL,
                month TEXT NOT NULL,
                user_id INTEGER NOT NULL,
                is_online INTEGER NOT NULL DEFAULT 0,
                online_id INTEGER
            )
        """.trimIndent())

                // Create monthly_analytics table
                database.execSQL("""
            CREATE TABLE IF NOT EXISTS monthly_analytics (
                id TEXT PRIMARY KEY NOT NULL,
                month TEXT NOT NULL,
                user_id INTEGER NOT NULL,
                total_listening_time INTEGER NOT NULL,
                total_songs_played INTEGER NOT NULL,
                unique_songs_count INTEGER NOT NULL,
                unique_artists_count INTEGER NOT NULL,
                last_updated INTEGER NOT NULL
            )
        """.trimIndent())

                // Create song_streaks table
                database.execSQL("""
            CREATE TABLE IF NOT EXISTS song_streaks (
                id TEXT PRIMARY KEY NOT NULL,
                song_id INTEGER NOT NULL,
                song_title TEXT NOT NULL,
                artist_name TEXT NOT NULL,
                current_streak INTEGER NOT NULL,
                last_played_date TEXT NOT NULL,
                user_id INTEGER NOT NULL,
                is_online INTEGER NOT NULL DEFAULT 0,
                online_id INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())

                // Create indices for better performance
                database.execSQL("CREATE INDEX IF NOT EXISTS index_listening_sessions_user_month ON listening_sessions(user_id, month)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_listening_sessions_date ON listening_sessions(date)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_song_streaks_user ON song_streaks(user_id)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_monthly_analytics_user ON monthly_analytics(user_id)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "purrytify_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}