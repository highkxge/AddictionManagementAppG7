package com.yourname.addictionmanager.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_limit")
data class UsageLimitEntity(
    @PrimaryKey val id: Int = 0,
    val minutesLimit: Int = 120, // This is now per-app general limit
    val totalMinutesLimit: Int = 300, // New: Total combined limit for all apps
    val enabled: Boolean = false,
    val lockApps: Boolean = false,
    val popupAlerts: Boolean = false,
    val notifications: Boolean = false,
    val cameraBlocking: Boolean = false,
    val lowLightDetection: Boolean = false
)
