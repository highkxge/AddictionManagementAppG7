package com.yourname.addictionmanager.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage")
data class UsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val appName: String,
    val minutesUsed: Int,
    val date: String
)
