package com.example.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null
    private val path = Path()
    private val paint = Paint()
    private val bitmapPaint = Paint(Paint.DITHER_FLAG)

    private var isEraser = false
    private var penWidth = 8f          // سمك القلم (يخزّن منفصل عن الممحاة)
    private val eraserFactor = 3f      // الممحاة أسمك من القلم بهالمعامل

    private var lastX = 0f
    private var lastY = 0f

    // الخط المختار (محفوظ للاستخدام مستقبلاً بأداة النص)
    var currentTypeface: Typeface = Typeface.DEFAULT
        private set

    init {
        paint.color = Color.BLACK
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        applyWidth()
    }

    private fun applyWidth() {
        paint.strokeWidth = if (isEraser) penWidth * eraserFactor else penWidth
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val newBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val newCanvas = Canvas(newBitmap)
        bitmap?.let { newCanvas.drawBitmap(it, 0f, 0f, null) }
        bitmap = newBitmap
        bitmapCanvas = newCanvas
    }

    override fun onDraw(canvas: Canvas) {
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = x
                lastY = y
                // نقطة عند الضغط
                bitmapCanvas?.drawPoint(x, y, paint)
            }
            MotionEvent.ACTION_MOVE -> {
                path.reset()
                path.moveTo(lastX, lastY)
                path.lineTo(x, y)
                // الرسم على Bitmap حتى تشتغل الممحاة (CLEAR) صح
                bitmapCanvas?.drawPath(path, paint)
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                path.reset()
            }
            else -> return false
        }
        invalidate()
        return true
    }

    fun setStrokeWidth(width: Float) {
        penWidth = width
        applyWidth()
    }

    fun toggleEraser(): Boolean {
        isEraser = !isEraser
        paint.xfermode = if (isEraser) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
        applyWidth()
        return isEraser
    }

    fun setFont(fontName: String) {
        // لإضافة خطوط حقيقية: حط ملفات ttf بمجلد res/font أو assets
        // وحمّلها هنا بـ Typeface.createFromAsset(...)
        currentTypeface = Typeface.DEFAULT
    }

    fun clear() {
        bitmap?.eraseColor(Color.TRANSPARENT)
        path.reset()
        invalidate()
    }
}
