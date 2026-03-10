package com.yourname.addictionmanager.camera

import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import com.yourname.addictionmanager.R

class WarningActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_warning)

        findViewById<Button>(R.id.btnOverride).setOnClickListener {
            WarningState.snooze()
            finish()
        }
    }
}
