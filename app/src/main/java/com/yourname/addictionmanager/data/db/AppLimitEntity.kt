package com.yourname.addictionmanager.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val timeLimit: Long, // in minutes
    val ultimateLockEnabled: Boolean = false // New field for the ultimate lock
)
