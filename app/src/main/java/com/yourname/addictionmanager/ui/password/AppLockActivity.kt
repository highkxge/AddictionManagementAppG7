package com.yourname.addictionmanager.ui.password

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yourname.addictionmanager.R
import com.yourname.addictionmanager.camera.WarningState
import com.yourname.addictionmanager.data.PasswordManager
import com.yourname.addictionmanager.data.db.AppDatabase
import com.yourname.addictionmanager.utils.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class AppLockActivity : AppCompatActivity() {

    private lateinit var passwordManager: PasswordManager
    private lateinit var pinDigits: List<EditText>
    private var targetPackage: String? = null
    private lateinit var db: AppDatabase
    private var lockReason: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_lock)

        passwordManager = PasswordManager(this)
        db = AppDatabase.get(this)
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        lockReason = intent.getStringExtra("REASON")

        val lockedAppIcon: ImageView = findViewById(R.id.locked_app_icon)
        val lockedAppName: TextView = findViewById(R.id.locked_app_name)
        val statusText: TextView = findViewById(R.id.status_text)
        val pinContainer: View = findViewById(R.id.pin_container)
        val btnContinue: Button = findViewById(R.id.btn_continue)

        pinDigits = listOf(
            findViewById(R.id.pin_digit_1),
            findViewById(R.id.pin_digit_2),
            findViewById(R.id.pin_digit_3),
            findViewById(R.id.pin_digit_4)
        )

        pinContainer.visibility = View.GONE
        btnContinue.visibility = View.VISIBLE

        updateStatusText(statusText, lockReason)

        targetPackage?.let { pkg ->
            lifecycleScope.launch {
                try {
                    if (pkg == "Total Usage Limit Reached") {
                        lockedAppName.text = pkg
                    } else {
                        val appInfo = packageManager.getApplicationInfo(pkg, 0)
                        lockedAppIcon.setImageDrawable(appInfo.loadIcon(packageManager))
                        lockedAppName.text = packageManager.getApplicationLabel(appInfo).toString()
                    }
                } catch (e: Exception) {
                    lockedAppName.text = pkg
                }

                btnContinue.setOnClickListener {
                    lifecycleScope.launch {
                        val appLimit = withContext(Dispatchers.IO) { db.appLimitDao().getLimit(pkg) }
                        val globalLimit = withContext(Dispatchers.IO) { db.usageLimitDao().getOnce() }
                        
                        val addictionAppUsage = withContext(Dispatchers.IO) { 
                            UsageStatsHelper.getAddictionAppUsage(this@AppLockActivity, UsageStatsHelper.TimePeriod.DAY) 
                        }
                        val appUsage = addictionAppUsage.find { it.first == pkg }?.second ?: 0L
                        val totalUsage = addictionAppUsage.sumOf { it.second }

                        var isTimeLimitExceeded = false
                        
                        // Check custom app limit
                        if (appLimit != null && appLimit.timeLimit > 0) {
                            val limitMillis = TimeUnit.MINUTES.toMillis(appLimit.timeLimit)
                            if (appUsage >= limitMillis) {
                                isTimeLimitExceeded = true
                            }
                        }
                        
                        // Check global limits
                        if (globalLimit?.enabled == true && globalLimit.lockApps) {
                            // General per-app
                            if (appLimit == null || appLimit.timeLimit == -1L) {
                                val generalLimitMillis = TimeUnit.MINUTES.toMillis(globalLimit.minutesLimit.toLong())
                                if (appUsage >= generalLimitMillis) {
                                    isTimeLimitExceeded = true
                                }
                            }
                            // Total daily
                            val totalLimitMillis = TimeUnit.MINUTES.toMillis(globalLimit.totalMinutesLimit.toLong())
                            if (totalUsage >= totalLimitMillis) {
                                isTimeLimitExceeded = true
                            }
                        }

                        btnContinue.visibility = View.GONE

                        if (isTimeLimitExceeded) {
                            statusText.text = "Time Limit Over\nAccess Denied"
                            pinContainer.visibility = View.GONE
                        } else if (appLimit?.ultimateLockEnabled == true || lockReason == "ULTIMATE_LOCK") {
                            statusText.text = "Ultimate Lock Active\nAccess Denied"
                            pinContainer.visibility = View.GONE
                        } else {
                            updateStatusText(statusText, lockReason)
                            statusText.append("\nEnter PIN to Access")
                            pinContainer.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }

        setupPinListeners()
    }

    private fun updateStatusText(textView: TextView, reason: String?) {
        when (reason) {
            "LIMIT_REACHED", "TOTAL_LIMIT_REACHED" -> textView.text = "Time Limit Over"
            "CAMERA_VIOLATION" -> textView.text = "Camera Distance Violation"
            "MANUAL_LOCK" -> textView.text = "App Locked"
            "ULTIMATE_LOCK" -> textView.text = "Ultimate Lock Active"
            else -> textView.text = "App Locked"
        }
    }

    private fun setupPinListeners() {
        for (i in pinDigits.indices) {
            pinDigits[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s?.length == 1 && i < pinDigits.size - 1) {
                        pinDigits[i + 1].requestFocus()
                    }
                    val pin = pinDigits.joinToString("") { it.text.toString() }
                    if (pin.length == 4) {
                        handlePin(pin)
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }

    private fun handlePin(pin: String) {
        if (passwordManager.checkPassword(pin)) {
            // Only CAMERA_VIOLATION results in permanent unlock via PIN
            if (lockReason == "CAMERA_VIOLATION") {
                targetPackage?.let { pkg ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.appLimitDao().removeLimit(pkg)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@AppLockActivity, "App permanently unlocked", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            
            // MANUAL_LOCK stays in DB, but we allow one-time access for this session
            WarningState.setUnlocked(targetPackage)
            finish()
        } else {
            Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
            pinDigits.forEach { it.text.clear() }
            pinDigits[0].requestFocus()
        }
    }

    override fun onBackPressed() {
        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)
        startMain.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(startMain)
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME"
    }
}
