package com.example.launcherlock.queue

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface PendingSubmissionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: PendingSubmissionEntity): Long

    @Query("SELECT * FROM pending_submissions WHERE nextRetryAtMillis <= :now ORDER BY id ASC LIMIT :limit")
    suspend fun findReady(now: Long, limit: Int = 20): List<PendingSubmissionEntity>

    @Update
    suspend fun update(item: PendingSubmissionEntity)

    @Delete
    suspend fun delete(item: PendingSubmissionEntity)
}
