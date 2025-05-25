package com.example.purrytify.data.dao

import androidx.room.*
import com.example.purrytify.data.entity.ListeningSession
import com.example.purrytify.data.entity.MonthlyAnalytics
import com.example.purrytify.data.entity.SongStreak
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalyticsDao {

    // ==================== LISTENING SESSIONS ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertListeningSession(session: ListeningSession): Long

    @Update
    fun updateListeningSession(session: ListeningSession): Int

    @Query("SELECT * FROM listening_sessions WHERE user_id = :userId AND month = :month ORDER BY start_time DESC")
    fun getListeningSessionsByMonth(userId: Long, month: String): List<ListeningSession>

    @Query("SELECT * FROM listening_sessions WHERE user_id = :userId AND date = :date ORDER BY start_time DESC")
    fun getListeningSessionsByDate(userId: Long, date: String): List<ListeningSession>

    @Query("UPDATE listening_sessions SET end_time = :endTime, duration_listened = :durationListened WHERE id = :sessionId")
    fun updateSessionEndTime(sessionId: Long, endTime: Long, durationListened: Long): Int

    @Query("SELECT * FROM listening_sessions WHERE id = :sessionId")
    fun getSessionById(sessionId: Long): ListeningSession?

    // ==================== MONTHLY ANALYTICS ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateMonthlyAnalytics(analytics: MonthlyAnalytics): Long

    @Query("SELECT * FROM monthly_analytics WHERE user_id = :userId AND month = :month")
    fun getMonthlyAnalytics(userId: Long, month: String): MonthlyAnalytics?

    @Query("SELECT * FROM monthly_analytics WHERE user_id = :userId ORDER BY month DESC")
    fun getAllMonthlyAnalytics(userId: Long): List<MonthlyAnalytics>

    // ==================== SONG STREAKS ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateSongStreak(streak: SongStreak): Long

    @Query("SELECT * FROM song_streaks WHERE id = :streakId")
    fun getSongStreakById(streakId: String): SongStreak?

    @Query("SELECT * FROM song_streaks WHERE user_id = :userId AND current_streak >= 2 ORDER BY current_streak DESC")
    fun getActiveStreaks(userId: Long): List<SongStreak>

    @Query("SELECT * FROM song_streaks WHERE user_id = :userId ORDER BY current_streak DESC LIMIT 10")
    fun getTopStreaks(userId: Long): List<SongStreak>

    @Query("DELETE FROM song_streaks WHERE user_id = :userId AND current_streak < 2 AND updated_at < :cutoffTime")
    fun cleanupOldStreaks(userId: Long, cutoffTime: Long): Int

    // ==================== ANALYTICS QUERIES ====================

    // Get total listening time for a month
    @Query("""
        SELECT COALESCE(SUM(duration_listened), 0) 
        FROM listening_sessions 
        WHERE user_id = :userId AND month = :month
    """)
    fun getTotalListeningTimeForMonth(userId: Long, month: String): Long

    // Get top artists for a month
    @Query("""
        SELECT artist_name, 
               SUM(duration_listened) as total_duration,
               COUNT(*) as play_count
        FROM listening_sessions 
        WHERE user_id = :userId AND month = :month 
        GROUP BY artist_name 
        ORDER BY total_duration DESC 
        LIMIT :limit
    """)
    fun getTopArtistsByDuration(userId: Long, month: String, limit: Int): List<ArtistStats>

    // Get top songs for a month
    @Query("""
        SELECT song_id, song_title, artist_name,
               SUM(duration_listened) as total_duration,
               COUNT(*) as play_count
        FROM listening_sessions 
        WHERE user_id = :userId AND month = :month 
        GROUP BY song_id, song_title, artist_name 
        ORDER BY play_count DESC, total_duration DESC 
        LIMIT :limit
    """)
    fun getTopSongsByPlayCount(userId: Long, month: String, limit: Int): List<SongStats>

    // Get unique songs count for a month
    @Query("""
        SELECT COUNT(DISTINCT song_id) 
        FROM listening_sessions 
        WHERE user_id = :userId AND month = :month
    """)
    fun getUniqueSongsCountForMonth(userId: Long, month: String): Int

    // Get unique artists count for a month
    @Query("""
        SELECT COUNT(DISTINCT artist_name) 
        FROM listening_sessions 
        WHERE user_id = :userId AND month = :month
    """)
    fun getUniqueArtistsCountForMonth(userId: Long, month: String): Int

    // Get songs played on specific dates (for streak calculation)
    @Query("""
        SELECT DISTINCT song_id, song_title, artist_name, date, is_online, online_id
        FROM listening_sessions 
        WHERE user_id = :userId AND date IN (:dates)
        ORDER BY date DESC
    """)
    fun getSongsPlayedOnDates(userId: Long, dates: List<String>): List<SongPlayedOnDate>

    // Real-time flow for current month analytics
    @Query("""
        SELECT COALESCE(SUM(duration_listened), 0) 
        FROM listening_sessions 
        WHERE user_id = :userId AND month = :month
    """)
    fun getCurrentMonthListeningTimeFlow(userId: Long, month: String): Flow<Long>

    // Data classes for query results
    data class ArtistStats(
        @ColumnInfo(name = "artist_name") val artistName: String,
        @ColumnInfo(name = "total_duration") val totalDuration: Long,
        @ColumnInfo(name = "play_count") val playCount: Int
    )

    data class SongStats(
        @ColumnInfo(name = "song_id") val songId: Long,
        @ColumnInfo(name = "song_title") val songTitle: String,
        @ColumnInfo(name = "artist_name") val artistName: String,
        @ColumnInfo(name = "total_duration") val totalDuration: Long,
        @ColumnInfo(name = "play_count") val playCount: Int
    )

    data class SongPlayedOnDate(
        @ColumnInfo(name = "song_id") val songId: Long,
        @ColumnInfo(name = "song_title") val songTitle: String,
        @ColumnInfo(name = "artist_name") val artistName: String,
        @ColumnInfo(name = "date") val date: String,
        @ColumnInfo(name = "is_online") val isOnline: Boolean,
        @ColumnInfo(name = "online_id") val onlineId: Int?
    )
}