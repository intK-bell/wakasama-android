package com.example.launcherlock.queue

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_submissions")
data class PendingSubmissionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payloadJson: String,
    val retryCount: Int = 0,
    val nextRetryAtMillis: Long = System.currentTimeMillis()
)
