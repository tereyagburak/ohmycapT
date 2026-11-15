package com.example.captprint.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "print_history")
data class PrintJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val pageCount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true,
    val copies: Int = 1,
    val manualFeedUsed: Boolean = false,
    val failureReason: String? = null
)
