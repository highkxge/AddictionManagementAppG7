package com.yourname.addictionmanager.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "limits")
data class LimitEntity(
    @PrimaryKey val id: Int = 1,

    val minutesLimit: Int,
    val alertsEnabled: Boolean,

    val lockApps: Boolean,
    val popupAlerts: Boolean,
    val notifications: Boolean,
    val cameraAnalysis: Boolean
)
