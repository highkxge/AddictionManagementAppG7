package com.yourname.addictionmanager.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLimitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setLimit(appLimit: AppLimitEntity)

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName")
    suspend fun getLimit(packageName: String): AppLimitEntity?

    @Query("SELECT * FROM app_limits")
    fun getAllLimits(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits")
    suspend fun getOnceList(): List<AppLimitEntity>

    @Query("UPDATE app_limits SET ultimateLockEnabled = :enabled WHERE packageName = :packageName")
    suspend fun setUltimateLockEnabled(packageName: String, enabled: Boolean)
    
    @Query("DELETE FROM app_limits WHERE packageName = :packageName")
    suspend fun removeLimit(packageName: String)
}
