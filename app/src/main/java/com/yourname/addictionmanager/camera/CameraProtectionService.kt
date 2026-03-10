package com.yourname.addictionmanager.camera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AlertDialog
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.yourname.addictionmanager.R
import com.yourname.addictionmanager.data.db.AppDatabase
import com.yourname.addictionmanager.data.db.AppLimitEntity
import com.yourname.addictionmanager.data.db.UsageLimitEntity
import com.yourname.addictionmanager.ui.password.AppLockActivity
import kotlinx.coroutines.*
import java.util.concurrent.Executors

class CameraProtectionService : LifecycleService() {

    private var cameraProtectionEnabled = false
    private var warningShown = false
    private var consecutiveViolations = 0
    
    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private var currentStatusText = "Camera protection initializing..."
    private var globalConfig: UsageLimitEntity? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("CameraProtectionService", "onCreate started")
        if (!CameraGate.enabled) {
            Log.w("CameraProtectionService", "CameraGate.enabled is false, stopping service")
            stopSelf()
            return
        }
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(currentStatusText))
        
        startMonitoring()
    }

    private fun startMonitoring() {
        val dao = AppDatabase.get(this).usageLimitDao()
        
        lifecycleScope.launch {
            while (isActive) {
                globalConfig = dao.getOnce()
                val limit = globalConfig
                val protectionEnabled = limit?.cameraBlocking == true && limit.enabled
                val foregroundApp = getForegroundAppPackageName()
                val isSelf = foregroundApp == packageName

                if (protectionEnabled && foregroundApp != null && !isSelf) {
                    if (!cameraProtectionEnabled) {
                        cameraProtectionEnabled = true
                        Log.d("CameraProtectionService", "📷 ACTIVATING camera analysis for $foregroundApp")
                        withContext(Dispatchers.Main) {
                            startCameraAnalysis()
                            updateStatus("Monitoring distance...")
                        }
                    }
                } else {
                    if (cameraProtectionEnabled) {
                        cameraProtectionEnabled = false
                        Log.d("CameraProtectionService", "📷 DEACTIVATING camera analysis")
                        withContext(Dispatchers.Main) {
                            stopCameraAnalysis()
                            closeWarningDialog()
                            updateStatus("Camera protection standby")
                        }
                        consecutiveViolations = 0
                    }
                }
                
                delay(2000)
            }
        }
    }

    private fun getForegroundAppPackageName(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, time - 10000, time)
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    private fun startCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindAnalysis(cameraProvider!!)
            } catch (e: Exception) {
                Log.e("CameraProtectionService", "Failed to get camera provider", e)
                updateStatus("Camera Error: Failed to initialize")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindAnalysis(cameraProvider: ProcessCameraProvider) {
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(analysisExecutor, FaceDistanceAnalyzer { result ->
            lifecycleScope.launch(Dispatchers.Main) {
                when (result) {
                    FaceDistanceAnalyzer.DistanceResult.TooClose -> {
                        updateStatus("Too close! Move away.")
                        onTooClose()
                    }
                    FaceDistanceAnalyzer.DistanceResult.Safe -> {
                        updateStatus("Monitoring distance...")
                        onSafe()
                    }
                    FaceDistanceAnalyzer.DistanceResult.LowLight -> {
                        // Check if low light detection is enabled in settings
                        if (globalConfig?.lowLightDetection == true) {
                            updateStatus("Low light! Move to bright area.")
                            showWarningDialog("Low Light Detected", "It's too dark for camera analysis.")
                        } else {
                            updateStatus("Searching for face (Low light)...")
                        }
                    }
                    FaceDistanceAnalyzer.DistanceResult.NoFaceDetected -> {
                        updateStatus("Searching for face...")
                    }
                }
            }
        })

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
        } catch (e: Exception) {
            Log.e("CameraProtectionService", "Use case binding failed", e)
            updateStatus("Camera Error: Binding Failed")
        }
    }

    private fun stopCameraAnalysis() {
        cameraProvider?.unbindAll()
    }

    private fun updateStatus(text: String) {
        if (currentStatusText != text) {
            currentStatusText = text
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    fun onTooClose() {
        if (!warningShown) {
            consecutiveViolations++
            if (consecutiveViolations >= 3) {
                lockApp()
                return
            }
            warningShown = true
            showWarningDialog("Warning: Too Close!", "Please keep a safe distance from your phone. (Violation $consecutiveViolations/3)")
        }
    }

    fun onSafe() {}

    private var activeDialog: AlertDialog? = null

    private fun showWarningDialog(title: String, message: String) {
        if (activeDialog?.isShowing == true) return
        
        val alertDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                warningShown = false
                dialog.dismiss()
            }
            .setCancelable(false)
            .create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            alertDialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        alertDialog.show()
        activeDialog = alertDialog
    }

    private fun closeWarningDialog() {
        activeDialog?.dismiss()
        activeDialog = null
        warningShown = false
    }

    private fun lockApp() {
        val foregroundApp = getForegroundAppPackageName()
        if (foregroundApp != null && foregroundApp != packageName) {
            lifecycleScope.launch(Dispatchers.IO) {
                val db = AppDatabase.get(this@CameraProtectionService)
                val currentLimit = db.appLimitDao().getLimit(foregroundApp)
                val updatedLimit = currentLimit?.copy(timeLimit = 0L) ?: AppLimitEntity(foregroundApp, 0L)
                db.appLimitDao().setLimit(updatedLimit)
            }

            val intent = Intent(this, AppLockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(AppLockActivity.EXTRA_PACKAGE_NAME, foregroundApp)
                putExtra("REASON", "CAMERA_VIOLATION")
            }
            startActivity(intent)
        }
        consecutiveViolations = 0
        closeWarningDialog()
    }

    override fun onDestroy() {
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, "camera_protect")
            .setContentTitle("Screen Distance Protection")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "camera_protect",
                "Camera Protection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
