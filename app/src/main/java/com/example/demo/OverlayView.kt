package com.example.demo

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/**
 * Custom View to draw bounding boxes and labels over the camera preview.
 */
class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    private var results: List<DetectionResult> = emptyList()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        initPaints()
    }

    private fun initPaints() {
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.alpha = 160

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 50f

        boxPaint.color = ContextCompat.getColor(context, android.R.color.holo_green_light)
        boxPaint.strokeWidth = 8f
        boxPaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        for (result in results) {
            val boundingBox = result.boundingBox

            val top = boundingBox.top * scaleFactor
            val bottom = boundingBox.bottom * scaleFactor
            val left = boundingBox.left * scaleFactor
            val right = boundingBox.right * scaleFactor

            // Draw bounding box
            val drawableRect = RectF(left, top, right, bottom)
            canvas.drawRect(drawableRect, boxPaint)

            // Create text to display
            val drawableText = "${result.label} " +
                    String.format("%.1f%%", result.confidence * 100)

            // Draw text background and text
            val textWidth = textPaint.measureText(drawableText)
            canvas.drawRect(
                left,
                top,
                left + textWidth,
                top + 60f,
                textBackgroundPaint
            )
            canvas.drawText(drawableText, left, top + 50f, textPaint)
        }
    }

    /**
     * Updates the results and triggers a redraw.
     */
    fun setResults(
        detectionResults: List<DetectionResult>,
        imageHeight: Int,
        imageWidth: Int
    ) {
        results = detectionResults

        // Calculate scale factor to map coordinates from image to view
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth
        
        // Assuming the preview view matches the aspect ratio of the analysis image
        scaleFactor = height.toFloat() / imageHeight.toFloat()

        invalidate()
    }
}
