package com.yourname.addictionmanager.camera

import android.content.Context
import androidx.lifecycle.LifecycleOwner

object CameraEngineHolder {

    private var engine: CameraEngine? = null
    private var overlay: OverlayController? = null

    fun start(owner: LifecycleOwner) {
        val ctx = (owner as Context)
        overlay = OverlayController(ctx)

        engine = CameraEngine(ctx, owner) { tooClose ->
            if (tooClose) overlay?.show()
            else overlay?.hide()
        }

        engine?.start()
    }

    fun stop() {
        engine?.stop()
        overlay?.hide()
        engine = null
        overlay = null
    }
}
