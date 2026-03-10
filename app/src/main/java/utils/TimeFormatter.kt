package com.yourname.addictionmanager.utils

object TimeFormatter {
    fun formatTime(ms: Long): String {
        val minutes = ms / 1000 / 60
        val hours = minutes / 60
        val remainingMinutes = minutes % 60

        return if (hours > 0) {
            "${hours}h ${remainingMinutes}m"
        } else {
            "${remainingMinutes}m"
        }
    }
}
