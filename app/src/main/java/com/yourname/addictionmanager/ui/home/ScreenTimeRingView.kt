package com.yourname.addictionmanager.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.yourname.addictionmanager.R

data class RingSegment(val percentage: Float, val color: Int)

class ScreenTimeRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var segments = emptyList<RingSegment>()

    private val defaultColors = listOf(
        ContextCompat.getColor(context, R.color.green_ok),
        ContextCompat.getColor(context, R.color.orange_warn),
        ContextCompat.getColor(context, R.color.red_danger),
        ContextCompat.getColor(context, R.color.purple_200), // Assuming you have these colors
        ContextCompat.getColor(context, R.color.teal_200)   // Assuming you have these colors
    )

    init {
        paint.style = Paint.Style.STROKE
    }

    fun submitSegments(newSegments: List<Float>) {
        val total = newSegments.sum()
        if (total == 0f) {
            this.segments = emptyList()
            invalidate()
            return
        }
        this.segments = newSegments.mapIndexed { index, value ->
            RingSegment(value / total, defaultColors[index % defaultColors.size])
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val strokeWidth = width * 0.15f
        paint.strokeWidth = strokeWidth

        val centerX = width / 2f
        val centerY = height / 2f
        val radius = (width - strokeWidth) / 2f

        rect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)

        var startAngle = -90f
        if (segments.isEmpty()) {
            paint.color = ContextCompat.getColor(context, R.color.grey_400) // A default grey color
            canvas.drawArc(rect, 0f, 360f, false, paint)
        } else {
            segments.forEach { segment ->
                paint.color = segment.color
                val sweepAngle = segment.percentage * 360f
                canvas.drawArc(rect, startAngle, sweepAngle, false, paint)
                startAngle += sweepAngle
            }
        }
    }
}
