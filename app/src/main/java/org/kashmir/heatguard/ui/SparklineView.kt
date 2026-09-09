package org.kashmir.heatguard.ui

import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/** Minimal line+fill trend chart for a small rolling history of vitals values. No chart library needed. */
class SparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var values: List<Float> = emptyList()
    private var lineColor: Int = Color.GRAY

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun setData(points: List<Float>, color: Int) {
        values = points
        lineColor = color
        linePaint.color = color
        invalidate()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        if (values.size < 2) return

        val min = values.min()
        val max = values.max()
        val range = (max - min).let { if (it < 0.01f) 1f else it }
        val w = width.toFloat()
        val h = height.toFloat()
        val stepX = w / (values.size - 1)

        fillPaint.shader = LinearGradient(0f, 0f, 0f, h, Color.argb(70, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor)), Color.argb(0, 0, 0, 0), Shader.TileMode.CLAMP)

        val linePath = Path()
        val fillPath = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = h - ((v - min) / range) * h
            if (i == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(w, h)
        fillPath.close()

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
    }
}
