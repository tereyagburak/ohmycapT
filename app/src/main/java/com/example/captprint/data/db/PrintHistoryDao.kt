package com.example.captprint.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PrintHistoryDao {
    @Query("SELECT * FROM print_history ORDER BY timestamp DESC")
    fun getAllJobs(): Flow<List<PrintJobEntity>>

    @Query("SELECT COUNT(*) FROM print_history WHERE isSuccess = 1")
    fun getTotalPrintedJobsCount(): Flow<Int>

    @Query("SELECT SUM(pageCount * copies) FROM print_history WHERE isSuccess = 1")
    fun getTotalPagesPrinted(): Flow<Int?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: PrintJobEntity): Long

    @Query("DELETE FROM print_history WHERE id = :id")
    suspend fun deleteJob(id: Long)

    @Query("DELETE FROM print_history")
    suspend fun clearHistory()
}
