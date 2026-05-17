package com.example.demo

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector

/**
 * Helper class for object detection using TFLite.
 */
class ObjectDetectorHelper(
    val context: Context,
    val objectDetectorListener: DetectorListener?
) {

    private var objectDetector: ObjectDetector? = null

    init {
        setupObjectDetector()
    }

    /**
     * Loads labels from labelmap.txt in assets folder
     */
    private fun loadLabels(): List<String> {
        return try {
            context.assets.open("labelmap.txt")
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("ObjectDetectorHelper", "Error loading labels: ${e.message}")
            emptyList()
        }
    }

    /**
     * Sets up the TFLite object detector with options.
     */
    private fun setupObjectDetector() {
        val optionsBuilder = ObjectDetector.ObjectDetectorOptions.builder()
            .setScoreThreshold(0.5f)
            .setMaxResults(5)

        // Load labels from labelmap.txt
        val labels = loadLabels()
        if (labels.isNotEmpty()) {
            optionsBuilder.setLabelAllowList(labels)
            Log.d("ObjectDetectorHelper", "Loaded ${labels.size} labels from labelmap.txt")
        } else {
            Log.w("ObjectDetectorHelper", "No labels loaded — labelmap.txt may be missing")
        }

        val baseOptionsBuilder = BaseOptions.builder()

        // Use GPU if available, otherwise fallback to CPU
        if (CompatibilityList().isDelegateSupportedOnThisDevice) {
            baseOptionsBuilder.useGpu()
            Log.d("ObjectDetectorHelper", "Using GPU delegate")
        } else {
            Log.d("ObjectDetectorHelper", "GPU not supported, using CPU")
        }

        optionsBuilder.setBaseOptions(baseOptionsBuilder.build())

        val modelName = "detect.tflite"

        try {
            objectDetector = ObjectDetector.createFromFileAndOptions(
                context,
                modelName,
                optionsBuilder.build()
            )
            Log.d("ObjectDetectorHelper", "Model loaded successfully")
        } catch (e: Exception) {
            objectDetectorListener?.onError(
                "Object detector failed to initialize. Check if $modelName is in assets."
            )
            Log.e("ObjectDetectorHelper", "TFLite failed to load model: ${e.message}")
        }
    }

    /**
     * Runs inference on the provided bitmap.
     */
    fun detect(bitmap: Bitmap, imageRotation: Int) {
        if (objectDetector == null) {
            Log.w("ObjectDetectorHelper", "Detector is null, reinitializing...")
            setupObjectDetector()
        }

        val inferenceTime = SystemClock.uptimeMillis()

        // Preprocess image — changed to 320x320 to match your model input
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(320, 320, ResizeOp.ResizeMethod.BILINEAR))
            .build()

        val tensorImage = imageProcessor.process(TensorImage.fromBitmap(bitmap))

        val results = objectDetector?.detect(tensorImage)
        val elapsedTime = SystemClock.uptimeMillis() - inferenceTime

        Log.d("ObjectDetectorHelper", "Detected ${results?.size ?: 0} objects in ${elapsedTime}ms")

        val detectionResults = results?.mapNotNull { detection ->
            // Safety check — skip if no categories found
            if (detection.categories.isEmpty()) return@mapNotNull null

            DetectionResult(
                label = detection.categories.first().label,
                confidence = detection.categories.first().score,
                boundingBox = detection.boundingBox
            )
        } ?: emptyList()

        objectDetectorListener?.onResults(
            detectionResults,
            elapsedTime,
            bitmap.height,
            bitmap.width
        )
    }

    /**
     * Cleans up the detector when no longer needed
     */
    fun clearObjectDetector() {
        objectDetector = null
    }

    interface DetectorListener {
        fun onError(error: String)
        fun onResults(
            results: List<DetectionResult>,
            inferenceTime: Long,
            imageHeight: Int,
            imageWidth: Int
        )
    }
}