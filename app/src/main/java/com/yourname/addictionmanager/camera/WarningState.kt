package com.yourname.addictionmanager.camera

object WarningState {
    private var ignoreUntil = 0L
    var unlockedPackage: String? = null

    fun isSuppressed(packageName: String): Boolean {
        // If this specific package was just unlocked, allow it
        if (packageName == unlockedPackage) return true
        
        // Fallback for camera/global warnings if still needed
        return System.currentTimeMillis() < ignoreUntil
    }

    fun snooze(minutes: Int = 5) {
        ignoreUntil = System.currentTimeMillis() + (minutes * 60_000)
    }

    fun setUnlocked(packageName: String?) {
        unlockedPackage = packageName
    }

    fun reset() {
        ignoreUntil = 0L
        unlockedPackage = null
    }
}
