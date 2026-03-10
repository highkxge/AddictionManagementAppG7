package com.yourname.addictionmanager.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*

class FaceDistanceAnalyzer(
    private val onResult: (DistanceResult) -> Unit
) : ImageAnalysis.Analyzer {

    sealed class DistanceResult {
        object TooClose : DistanceResult()
        object Safe : DistanceResult()
        object NoFaceDetected : DistanceResult()
        object LowLight : DistanceResult()
    }

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
    )

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        // Calculate average luminance (brightness)
        // Y plane is usually the first plane in YUV_420_888
        val buffer = imageProxy.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        var sum = 0L
        for (b in data) {
            sum += b.toInt() and 0xFF
        }
        val avgLuminance = if (data.isNotEmpty()) sum / data.size else 0
        
        // Lowered threshold for better calibration in indoor lighting
        if (avgLuminance < 12) { 
            onResult(DistanceResult.LowLight)
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    // Sort by size to pick the most prominent face (usually the user)
                    val face = faces.maxByOrNull { it.boundingBox.width() }!!
                    val faceWidth = face.boundingBox.width()
                    
                    // Log face width to help with distance calibration
                    Log.d("FaceDistanceAnalyzer", "Luminance: $avgLuminance | Face Width: $faceWidth")

                    // Calibration: 450 is a common 'too close' threshold, 
                    // but we can adjust based on feedback.
                    if (faceWidth > 450) {
                        onResult(DistanceResult.TooClose)
                    } else {
                        onResult(DistanceResult.Safe)
                    }
                } else {
                    onResult(DistanceResult.NoFaceDetected)
                }
            }
            .addOnFailureListener { e ->
                Log.e("FaceDistanceAnalyzer", "Face detection failed", e)
                onResult(DistanceResult.NoFaceDetected)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
