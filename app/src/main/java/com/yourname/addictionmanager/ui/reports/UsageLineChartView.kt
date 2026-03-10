package com.yourname.addictionmanager.ui.reports

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.yourname.addictionmanager.R

class UsageLineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val leftPadding = 60f * density
    private val rightPadding = 40f * density
    private val topPadding = 40f * density
    private val bottomPadding = 60f * density

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.green_ok)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.green_ok)
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        strokeWidth = 1.5f * density
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40FFFFFF")
        strokeWidth = 1f * density
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * density
    }

    private var dataPoints = emptyList<Float>()
    private var labels = emptyList<String>()

    fun setData(points: List<Float>, xLabels: List<String>) {
        this.dataPoints = points
        this.labels = xLabels
        postInvalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = 1200 * density.toInt()
        val width = resolveSize(desiredWidth, widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val graphWidth = w - leftPadding - rightPadding
        val graphHeight = h - topPadding - bottomPadding

        if (graphWidth <= 0 || graphHeight <= 0) return

        val maxVal = (dataPoints.maxOrNull() ?: 0f).coerceAtLeast(60f)
        
        // Draw Grid and Y-Axis Labels
        val ySteps = 5
        for (i in 0..ySteps) {
            val y = topPadding + graphHeight - (i * graphHeight / ySteps)
            val hours = (i * maxVal / ySteps) / 60f
            
            canvas.drawLine(leftPadding, y, w - rightPadding, y, gridPaint)
            canvas.drawText(String.format("%.1fh", hours), 10f * density, y + 4f * density, textPaint)
        }

        // Draw Axes
        canvas.drawLine(leftPadding, topPadding + graphHeight, w - rightPadding, topPadding + graphHeight, axisPaint)
        canvas.drawLine(leftPadding, topPadding, leftPadding, topPadding + graphHeight, axisPaint)

        if (dataPoints.isEmpty() || dataPoints.all { it == 0f }) {
            val msg = "No usage data found for this period"
            val textWidth = textPaint.measureText(msg)
            canvas.drawText(msg, (w / 2) - (textWidth / 2), h / 2, textPaint)
            return
        }

        val xStep = graphWidth / (dataPoints.size - 1).coerceAtLeast(1)
        val path = Path()
        val pointsToDraw = mutableListOf<PointF>()

        dataPoints.forEachIndexed { index, value ->
            val x = leftPadding + index * xStep
            val y = topPadding + graphHeight - (value / maxVal * graphHeight)
            pointsToDraw.add(PointF(x, y))

            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)

            if (labels.size > index && index % 4 == 0) {
                canvas.drawText(labels[index], x - 15f * density, h - 20f * density, textPaint)
            }
        }

        // Draw Path
        canvas.drawPath(path, linePaint)

        // Draw Dots
        for (pt in pointsToDraw) {
            canvas.drawCircle(pt.x, pt.y, 5f * density, dotPaint)
            val whitePaint = Paint(dotPaint).apply { color = Color.WHITE }
            canvas.drawCircle(pt.x, pt.y, 2f * density, whitePaint)
        }
    }
}
