package com.github.importantamigo.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.BitmapKt.createBitmap
import kotlin.math.*

/* https://github.com/GerardBradshaw/ColorPicker/ */
class RadialColorPicker(context: Context) : View(context) {
    private var circleDiameter = 0
    private var radius = 0f
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    
    private var spectrumBitmap: Bitmap? = null
    
    private var thumbX = 0f
    private var thumbY = 0f
    
    var onColorChanged: ((Int) -> Unit)? = null
    
    private var currentColor = Color.RED

    @SuppressLint("DrawAllocation")
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        circleDiameter = min(width, height)
        radius = circleDiameter / 2f
        
        if (changed || spectrumBitmap == null) {
            createSpectrumBitmap()
            thumbX = radius
            thumbY = radius
        }
    }

    private fun createSpectrumBitmap() {
        if (circleDiameter <= 0) return
        
        val bitmap = createBitmap(circleDiameter, circleDiameter, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        val colors = intArrayOf(
            Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN,
            Color.BLUE, Color.MAGENTA, Color.RED,
        )
        val sweepGradient = SweepGradient(radius, radius, colors, null)
        
        paint.shader = sweepGradient
        canvas.drawCircle(radius, radius, radius, paint)
        
        val radialGradient = RadialGradient(
            radius, radius, radius,
            Color.WHITE, Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        paint.shader = radialGradient
        canvas.drawCircle(radius, radius, radius, paint)
        
        spectrumBitmap = bitmap
    }

    override fun onDraw(canvas: Canvas) {
        spectrumBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }
        
        canvas.drawCircle(thumbX, thumbY, 15f, thumbPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                updateThumb(event.x, event.y)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateThumb(x: Float, y: Float) {
        val dx = x - radius
        val dy = y - radius
        val distance = sqrt(dx * dx + dy * dy)
        
        if (distance <= radius) {
            thumbX = x
            thumbY = y
        } else {
            thumbX = radius + (dx / distance) * radius
            thumbY = radius + (dy / distance) * radius
        }
        
        currentColor = getColorAt(thumbX, thumbY)
        onColorChanged?.invoke(currentColor)
        invalidate()
    }

    private fun getColorAt(x: Float, y: Float): Int {
        val dx = x - radius
        val dy = y - radius
        val distance = sqrt(dx * dx + dy * dy)
        
        val angle = atan2(dy, dx)
        var hue = (angle * 180 / PI).toFloat()
        if (hue < 0) hue += 360f
        
        val saturation = min(1f, distance / radius)
        
        return Color.HSVToColor(floatArrayOf(hue, saturation, 1f))
    }
    
    fun setColor(color: Int) {
        currentColor = color
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        
        val hue = hsv[0]
        val saturation = hsv[1]
        
        val angle = (hue * PI / 180)
        val dist = saturation * radius
        
        thumbX = radius + (cos(angle) * dist).toFloat()
        thumbY = radius + (sin(angle) * dist).toFloat()
        
        invalidate()
    }
}
