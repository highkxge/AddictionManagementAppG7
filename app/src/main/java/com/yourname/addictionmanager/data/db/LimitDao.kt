package com.yourname.addictionmanager.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LimitDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(limit: LimitEntity)

    @Query("SELECT * FROM limits WHERE id = 1")
    fun getLimit(): Flow<LimitEntity?>

    @Query("SELECT * FROM limits WHERE id = 1")
    fun getLimitSync(): LimitEntity?
}
