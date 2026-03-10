package com.yourname.addictionmanager.camera

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import com.yourname.addictionmanager.R

class OverlayController(private val context: Context) {

    private var view: View? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun show() {
        if (view != null) return

        val inflater = LayoutInflater.from(context)
        view = inflater.inflate(R.layout.overlay_warning, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(view, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hide() {
        view?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            view = null
        }
    }
}
