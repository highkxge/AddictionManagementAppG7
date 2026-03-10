package com.yourname.addictionmanager.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageLimitDao {

    @Query("SELECT * FROM usage_limit WHERE id = 0")
    fun observeLimit(): Flow<UsageLimitEntity?>

    @Query("SELECT * FROM usage_limit WHERE id = 0")
    suspend fun getOnce(): UsageLimitEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(limit: UsageLimitEntity)

    @Update
    suspend fun update(limit: UsageLimitEntity)
}
