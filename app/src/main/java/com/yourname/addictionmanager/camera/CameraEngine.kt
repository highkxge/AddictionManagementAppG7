package com.yourname.addictionmanager.camera

import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class CameraEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onUnsafe: (Boolean) -> Unit
) {

    private var provider: ProcessCameraProvider? = null

    fun start() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({

            provider = cameraProviderFuture.get()

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(
                ContextCompat.getMainExecutor(context),
                FaceDistanceAnalyzer { result ->
                    val tooClose = result is FaceDistanceAnalyzer.DistanceResult.TooClose
                    onUnsafe(tooClose)
                }
            )

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            provider?.unbindAll()
            provider?.bindToLifecycle(lifecycleOwner, cameraSelector, analysis)

        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        provider?.let {
            ContextCompat.getMainExecutor(context).execute {
                it.unbindAll()
            }
        }
    }
}
