package com.example.demo

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Helper class to set up CameraX and handle image analysis for object detection.
 */
class CameraHelper(
    private val context: Context,
    private val previewView: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val objectDetectorHelper: ObjectDetectorHelper
) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * Starts the camera and binds use cases.
     */
    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Binds preview and analysis use cases to the camera.
     */
    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        val rotation = previewView.display?.rotation ?: 0

        preview = Preview.Builder()
            .setTargetResolution(Size(640, 480))
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            // Fixed resolution so bounding box coordinates are consistent
            .setTargetResolution(Size(640, 480))
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        var lastInferenceTime = 0L

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            try {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastInferenceTime >= 1000) {
                    lastInferenceTime = currentTime

                    val bitmap = imageProxy.toBitmap()

                    // Crop bitmap to square from center
                    // This fixes the offset issue where detections only appear on left side
                    val croppedBitmap = cropToSquare(bitmap)

                    objectDetectorHelper.detect(
                        croppedBitmap,
                        imageProxy.imageInfo.rotationDegrees
                    )
                }
            } catch (e: Exception) {
                Log.e("CameraHelper", "Analysis failed: ${e.message}")
            } finally {
                // Always close imageProxy to prevent buffer overflow error
                imageProxy.close()
            }
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            preview?.setSurfaceProvider(previewView.surfaceProvider)
            Log.d("CameraHelper", "Camera bound successfully")
        } catch (e: Exception) {
            Log.e("CameraHelper", "Use case binding failed: ${e.message}")
        }
    }

    /**
     * Crops bitmap to a centered square
     * This ensures bounding box X coordinates cover full 0 to width range
     * fixing the issue where all detections appeared on the left side
     */
    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        return if (width > height) {
            // Landscape — crop width to match height, from center
            val xOffset = (width - height) / 2
            Bitmap.createBitmap(bitmap, xOffset, 0, height, height)
        } else if (height > width) {
            // Portrait — crop height to match width, from center
            val yOffset = (height - width) / 2
            Bitmap.createBitmap(bitmap, 0, yOffset, width, width)
        } else {
            // Already square
            bitmap
        }
    }

    /**
     * Stops the camera and releases resources.
     */
    fun stopCamera() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraExecutor.shutdown()
        Log.d("CameraHelper", "Camera stopped")
    }

    /**
     * Extension to convert ImageProxy to Bitmap for TFLite processing.
     */
    private fun ImageProxy.toBitmap(): Bitmap {
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width
        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return bitmap
    }
}