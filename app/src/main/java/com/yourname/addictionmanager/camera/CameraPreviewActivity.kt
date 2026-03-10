package com.yourname.addictionmanager.camera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat

class CameraPreviewActivity : ComponentActivity() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make truly background and non-touchable
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        window.attributes.alpha = 0.0f // Fully transparent

        WindowCompat.setDecorFitsSystemWindows(window, false)

        CameraEngineHolder.start(this)
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, IntentFilter("STOP_CAMERA"), RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, IntentFilter("STOP_CAMERA"))
        }
    }

    override fun onStop() {
        unregisterReceiver(receiver)
        super.onStop()
    }

    override fun onDestroy() {
        CameraEngineHolder.stop()
        super.onDestroy()
    }
}
