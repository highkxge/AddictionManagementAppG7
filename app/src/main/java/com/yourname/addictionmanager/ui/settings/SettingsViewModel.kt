package com.yourname.addictionmanager.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.yourname.addictionmanager.data.db.AppDatabase
import com.yourname.addictionmanager.data.db.UsageLimitEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.get(application)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _settings = MutableLiveData<UsageLimitEntity>()
    val settings: LiveData<UsageLimitEntity> = _settings

    fun loadSettings() {
        scope.launch {
            var currentSettings = db.usageLimitDao().getOnce()
            if (currentSettings == null) {
                // Create default settings if none exist
                currentSettings = UsageLimitEntity()
                db.usageLimitDao().insert(currentSettings)
            }
            _settings.postValue(currentSettings)
        }
    }

    fun saveSettings(settings: UsageLimitEntity) {
        scope.launch {
            db.usageLimitDao().update(settings)
        }
    }

    fun resetAllCustomLimits() {
        scope.launch {
            // Delete all entries in app_limits table to revert to default
            val limits = db.appLimitDao().getOnceList()
            limits.forEach { 
                db.appLimitDao().removeLimit(it.packageName)
            }
        }
    }
}
