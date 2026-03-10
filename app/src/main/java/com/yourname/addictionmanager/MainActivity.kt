package com.yourname.addictionmanager

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.yourname.addictionmanager.camera.CameraProtectionService
import com.yourname.addictionmanager.data.PasswordManager
import com.yourname.addictionmanager.services.AppMonitorService
import com.yourname.addictionmanager.ui.alerts.AlertsFragment
import com.yourname.addictionmanager.ui.apps.AppsFragment
import com.yourname.addictionmanager.ui.home.HomeFragment
import com.yourname.addictionmanager.ui.password.PasswordAction
import com.yourname.addictionmanager.ui.password.PasswordActivity
import com.yourname.addictionmanager.ui.reports.ReportsFragment
import com.yourname.addictionmanager.ui.settings.SettingsFragment
import com.yourname.addictionmanager.utils.UsageStatsHelper

class MainActivity : AppCompatActivity() {

    private lateinit var passwordManager: PasswordManager

    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            switchFragment(SettingsFragment())
        }
    }

    private val alertsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            switchFragment(AlertsFragment())
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startCameraService()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        if (!Settings.canDrawOverlays(this)) {
            // Permission still not granted
        }
    }
    private val usagePermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ -> }
    private val accessibilityPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        passwordManager = PasswordManager(this)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        if (savedInstanceState == null) {
            switchFragment(HomeFragment())
            bottomNav.selectedItemId = R.id.nav_home
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { switchFragment(HomeFragment()); true }
                R.id.nav_apps -> { switchFragment(AppsFragment()); true }
                R.id.nav_data -> { switchFragment(ReportsFragment()); true }
                R.id.nav_alerts -> {
                    if (passwordManager.isPasswordSet()) {
                        val intent = Intent(this, PasswordActivity::class.java).apply {
                            putExtra(PasswordActivity.EXTRA_ACTION, PasswordAction.ENTER)
                        }
                        alertsLauncher.launch(intent)
                    } else {
                        switchFragment(AlertsFragment())
                    }
                    true
                }
                R.id.nav_settings -> {
                    if (passwordManager.isPasswordSet()) {
                        val intent = Intent(this, PasswordActivity::class.java).apply {
                            putExtra(PasswordActivity.EXTRA_ACTION, PasswordAction.ENTER)
                        }
                        settingsLauncher.launch(intent)
                    } else {
                        switchFragment(SettingsFragment())
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!UsageStatsHelper.hasUsagePermission(this)) {
            requestUsagePermissionWithDialog()
        } else if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermissionWithDialog()
        } else if (!isAccessibilityServiceEnabled()) {
            requestAccessibilityPermissionWithDialog()
        }
        checkCameraPermissionAndStartServices()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "$packageName/${MyAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (enabledServices == null) return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun checkCameraPermissionAndStartServices() {
        startService(Intent(this, AppMonitorService::class.java))
        if (com.yourname.addictionmanager.camera.CameraGate.enabled) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCameraService()
            } else {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCameraService() {
        startService(Intent(this, CameraProtectionService::class.java))
    }

    private fun requestUsagePermissionWithDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Needed")
            .setMessage("This app needs 'Usage Access' permission.")
            .setPositiveButton("Grant Permission") { _, _ ->
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                usagePermissionLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestOverlayPermissionWithDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Needed")
            .setMessage("To show warnings and lock apps, this app needs permission to display over other apps.")
            .setPositiveButton("Grant Permission") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                overlayPermissionLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestAccessibilityPermissionWithDialog() {
        AlertDialog.Builder(this)
            .setTitle("Accessibility Permission Needed")
            .setMessage("To reliably lock apps when time is up, this app needs Accessibility permission. Please find 'Addiction Manager' in the list and turn it ON.")
            .setPositiveButton("Grant Permission") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                accessibilityPermissionLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
