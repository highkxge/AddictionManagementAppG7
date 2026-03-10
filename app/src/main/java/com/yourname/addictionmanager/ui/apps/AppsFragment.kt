package com.yourname.addictionmanager.ui.apps

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.slider.Slider
import com.yourname.addictionmanager.R
import com.yourname.addictionmanager.data.PasswordManager
import com.yourname.addictionmanager.data.db.AppDatabase
import com.yourname.addictionmanager.data.db.AppLimitEntity
import com.yourname.addictionmanager.ui.password.PasswordAction
import com.yourname.addictionmanager.ui.password.PasswordActivity
import com.yourname.addictionmanager.utils.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AppsFragment : Fragment() {

    private val viewModel: AppsViewModel by viewModels()
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var db: AppDatabase
    private lateinit var passwordManager: PasswordManager

    private var selectedApp: AppInfo? = null
    private var pendingAction: (() -> Unit)? = null

    private val passwordLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            pendingAction?.invoke()
            pendingAction = null
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_apps, container, false)
        appsRecyclerView = view.findViewById(R.id.apps_recycler_view)
        db = AppDatabase.get(requireContext())
        passwordManager = PasswordManager(requireContext())

        val toolbar: Toolbar = view.findViewById(R.id.toolbar)
        (activity as AppCompatActivity).setSupportActionBar(toolbar)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.apps.observe(viewLifecycleOwner) { apps ->
            val adapter = AppsAdapter(apps) { app ->
                selectedApp = app
                showAppOptionsDialog(app)
            }
            appsRecyclerView.adapter = adapter
        }

        viewModel.loadApps()
    }

    private fun showAppOptionsDialog(app: AppInfo) {
        viewLifecycleOwner.lifecycleScope.launch {
            val globalLimit = db.usageLimitDao().getOnce() ?: return@launch
            if (!globalLimit.lockApps) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Please enable App Locking in Settings first", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            val currentLimit = db.appLimitDao().getLimit(app.packageName)
            val isPasswordLocked = passwordManager.isPasswordSet()
            val isAppAlreadyLocked = currentLimit != null && currentLimit.timeLimit <= 0
            val isUltimateLockEnabled = currentLimit?.ultimateLockEnabled == true

            // Calculate current usage
            val allAppUsage = withContext(Dispatchers.IO) { 
                UsageStatsHelper.getAllAppUsage(requireContext(), UsageStatsHelper.TimePeriod.DAY) 
            }
            val appUsageMillis = allAppUsage.find { it.first == app.packageName }?.second ?: 0L

            // Calculate the effective time limit
            val effectiveLimitMinutes = if (currentLimit != null && currentLimit.timeLimit > 0) {
                currentLimit.timeLimit
            } else {
                globalLimit.minutesLimit.toLong()
            }
            
            val isPastLimit = appUsageMillis > TimeUnit.MINUTES.toMillis(effectiveLimitMinutes)
            val limitDisplayString = if (isAppAlreadyLocked) "Always Locked" 
                                     else if (isPastLimit) "${formatMinutes(effectiveLimitMinutes)} (Time Over)"
                                     else formatMinutes(effectiveLimitMinutes)

            val setLimitLabel = if (currentLimit != null && currentLimit.timeLimit > 0) "Update Custom Time Limit" else "Set Custom Time Limit"
            val lockLabel = if (isAppAlreadyLocked) "Unlock App" else "Lock App"
            
            // New label logic for Ultimate Lock
            val ultimateLockToggleLabel = when {
                isPastLimit -> "Ultimate Lock (Required - Time Over)"
                isUltimateLockEnabled -> "Disable Ultimate Lock"
                else -> "Enable Ultimate Lock"
            }

            val options = mutableListOf<String>()
            options.add(setLimitLabel)
            if (isPasswordLocked) {
                options.add(lockLabel)
                if (currentLimit != null) {
                   options.add(ultimateLockToggleLabel)
                }
                options.add("Remove Limit")
            } else {
                options.add("Set Password to Lock")
            }

            withContext(Dispatchers.Main) {
                val dialogBuilder = AlertDialog.Builder(requireContext())
                    .setTitle("${app.name} (Limit: $limitDisplayString)")
                    .setItems(options.toTypedArray()) { _, which ->
                        when (options[which]) {
                            setLimitLabel -> requirePassword { showSetCustomLimitDialog(app) }
                            lockLabel -> requirePassword { toggleLockApp(app) }
                            ultimateLockToggleLabel -> {
                                if (isPastLimit) {
                                    Toast.makeText(requireContext(), "Cannot disable lock while past limit. Extend limit first.", Toast.LENGTH_LONG).show()
                                } else {
                                    requirePassword { toggleUltimateLock(app) }
                                }
                            }
                            "Remove Limit" -> requirePassword { removeLimit(app) }
                            "Set Password to Lock" -> {
                                val intent = Intent(requireContext(), PasswordActivity::class.java).apply {
                                    putExtra(PasswordActivity.EXTRA_ACTION, PasswordAction.CREATE)
                                }
                                passwordLauncher.launch(intent)
                            }
                        }
                    }
                dialogBuilder.show()
            }
        }
    }

    private fun formatMinutes(totalMinutes: Long): String {
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun requirePassword(action: () -> Unit) {
        if (passwordManager.isPasswordSet()) {
            pendingAction = action
            val intent = Intent(requireContext(), PasswordActivity::class.java).apply {
                putExtra(PasswordActivity.EXTRA_ACTION, PasswordAction.ENTER)
            }
            passwordLauncher.launch(intent)
        } else {
            action()
        }
    }

    private fun showSetCustomLimitDialog(app: AppInfo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_limit, null)
        val slider = dialogView.findViewById<Slider>(R.id.limit_slider)
        val sliderValueText = dialogView.findViewById<TextView>(R.id.slider_value_text)

        slider.stepSize = 2f
        slider.valueFrom = 2f
        slider.valueTo = 360f // 6 hours * 60 minutes

        viewLifecycleOwner.lifecycleScope.launch {
            val currentLimitEntity = db.appLimitDao().getLimit(app.packageName)
            val globalSettings = db.usageLimitDao().getOnce()
            
            val defaultLimit = globalSettings?.minutesLimit?.toLong() ?: 120L
            val currentLimitMinutes = (currentLimitEntity?.timeLimit ?: defaultLimit).coerceIn(2, 360)
            
            withContext(Dispatchers.Main) {
                slider.value = currentLimitMinutes.toFloat()
                updateSliderText(sliderValueText, currentLimitMinutes.toFloat())

                slider.addOnChangeListener { _, value, _ ->
                    val roundedValue = kotlin.math.round(value / 2) * 2
                    slider.value = roundedValue
                    updateSliderText(sliderValueText, roundedValue)
                }
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Set Custom Limit for ${app.name}")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val limitInMinutes = slider.value.toLong()
                viewLifecycleOwner.lifecycleScope.launch {
                    val currentLimit = db.appLimitDao().getLimit(app.packageName)
                    val updatedLimit = currentLimit?.copy(timeLimit = limitInMinutes) ?: AppLimitEntity(app.packageName, limitInMinutes)
                    db.appLimitDao().setLimit(updatedLimit)
                    withContext(Dispatchers.Main) {
                        viewModel.loadApps()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateSliderText(textView: TextView, value: Float) {
        val hours = (value / 60).toInt()
        val minutes = (value % 60).toInt()
        textView.text = String.format("%dh %dm", hours, minutes)
    }

    private fun toggleLockApp(app: AppInfo) {
        viewLifecycleOwner.lifecycleScope.launch {
            val currentLimit = db.appLimitDao().getLimit(app.packageName)
            if (currentLimit?.timeLimit == 0L) {
                db.appLimitDao().removeLimit(app.packageName)
            } else {
                val updatedLimit = currentLimit?.copy(timeLimit = 0L) ?: AppLimitEntity(app.packageName, 0L)
                db.appLimitDao().setLimit(updatedLimit)
            }
            withContext(Dispatchers.Main) {
                viewModel.loadApps()
            }
        }
    }

    private fun toggleUltimateLock(app: AppInfo) {
        viewLifecycleOwner.lifecycleScope.launch {
            val currentLimit = db.appLimitDao().getLimit(app.packageName) ?: return@launch
            val newUltimateLockState = !currentLimit.ultimateLockEnabled
            db.appLimitDao().setUltimateLockEnabled(app.packageName, newUltimateLockState)
            withContext(Dispatchers.Main) {
                val status = if (newUltimateLockState) "enabled" else "disabled"
                Toast.makeText(requireContext(), "Ultimate Lock $status for ${app.name}", Toast.LENGTH_SHORT).show()
                viewModel.loadApps()
            }
        }
    }

    private fun removeLimit(app: AppInfo) {
        viewLifecycleOwner.lifecycleScope.launch {
            db.appLimitDao().removeLimit(app.packageName)
            withContext(Dispatchers.Main) {
                viewModel.loadApps()
            }
        }
    }
}
