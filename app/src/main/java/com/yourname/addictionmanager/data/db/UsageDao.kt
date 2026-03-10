package com.yourname.addictionmanager.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<UsageEntity>)

    @Query("SELECT * FROM usage WHERE date = :date")
    fun getUsageForDate(date: String): Flow<List<UsageEntity>>

    @Query("SELECT SUM(minutesUsed) FROM usage WHERE date = :date")
    fun getTotalMinutesForDate(date: String): Flow<Int?>

    // 🔥 This prevents duplicate seeding
    @Query("SELECT COUNT(*) FROM usage WHERE date = :date")
    suspend fun getUsageCountForDate(date: String): Int
}
