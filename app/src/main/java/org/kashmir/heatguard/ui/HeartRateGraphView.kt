package org.kashmir.heatguard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import org.kashmir.heatguard.R

class HeartRateGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val samples = ArrayDeque<Int>()

    fun addSample(bpm: Int) {
        samples.addLast(bpm)
        if (samples.size > MAX_SAMPLES) samples.removeFirst()
        invalidate()
    }

    fun clear() {
        samples.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (samples.size < 2) return
        linePaint.color = ContextCompat.getColor(context, R.color.warm_text_secondary)

        val w = width.toFloat()
        val h = height.toFloat()
        val min = samples.min().toFloat()
        val max = samples.max().toFloat()
        val range = (max - min).coerceAtLeast(10f)
        val padding = h * 0.1f

        val path = Path()
        samples.forEachIndexed { i, bpm ->
            val x = i / (samples.size - 1).toFloat() * w
            val y = h - padding - (bpm - min) / range * (h - 2 * padding)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
    }

    companion object {
        private const val MAX_SAMPLES = 20
    }
}
