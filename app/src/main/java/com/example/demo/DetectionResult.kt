package com.example.demo

import android.graphics.RectF

/**
 * Data class representing a single object detection result.
 * @property label The name of the detected object.
 * @property confidence The confidence score of the detection (0.0 to 1.0).
 * @property boundingBox The location of the object in the image.
 */
data class DetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF
)
