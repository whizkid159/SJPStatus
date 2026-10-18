package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface SjpDao {
    @Query("SELECT * FROM applied_jobs ORDER BY lastUpdatedTimestamp DESC, applicationId DESC")
    fun observeAllJobs(): Flow<List<AppliedJobEntity>>

    @Query("SELECT * FROM applied_jobs")
    suspend fun getAllJobsSnapshot(): List<AppliedJobEntity>

    @Query("SELECT * FROM applied_jobs WHERE applicationId = :appId LIMIT 1")
    suspend fun getJobById(appId: String): AppliedJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJobs(jobs: List<AppliedJobEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJob(job: AppliedJobEntity)

    @Query("UPDATE applied_jobs SET userNotes = :notes, interviewTimestamp = :interviewTimestamp WHERE applicationId = :appId")
    suspend fun updateNotesAndReminder(appId: String, notes: String, interviewTimestamp: Long?)

    @Query("UPDATE applied_jobs SET hasUnreadChange = 0 WHERE applicationId = :appId")
    suspend fun markJobChangeRead(appId: String)

    @Query("UPDATE applied_jobs SET hasUnreadChange = 0")
    suspend fun markAllJobChangesRead()

    @Query("DELETE FROM applied_jobs")
    suspend fun clearAllJobs()

    @Query("SELECT * FROM status_history ORDER BY detectedAtTimestamp DESC")
    fun observeStatusHistory(): Flow<List<StatusHistoryEntity>>

    @Query("SELECT * FROM status_history WHERE applicationId = :appId ORDER BY detectedAtTimestamp DESC")
    fun observeHistoryForJob(appId: String): Flow<List<StatusHistoryEntity>>

    @Query("SELECT COUNT(*) FROM status_history WHERE applicationId = :appId AND newRawStatus = :status AND changedAtFormatted = :changedAt")
    suspend fun countMatchingHistoryEntry(appId: String, status: String, changedAt: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatusHistory(history: StatusHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatusHistoryList(items: List<StatusHistoryEntity>)

    @Query("UPDATE status_history SET isUnread = 0")
    suspend fun markAllHistoryRead()

    @Query("DELETE FROM status_history")
    suspend fun clearAllHistory()
}

@Database(
    entities = [AppliedJobEntity::class, StatusHistoryEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SjpDatabase : RoomDatabase() {
    abstract fun sjpDao(): SjpDao

    companion object {
        @Volatile
        private var INSTANCE: SjpDatabase? = null

        fun getInstance(context: Context): SjpDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SjpDatabase::class.java,
                    "sjp_pulse_database.db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
