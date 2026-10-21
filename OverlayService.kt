package com.example.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var windowManager: WindowManager
    private var canvasView: DrawingView? = null
    private var toolbarView: View? = null
    private lateinit var canvasParams: WindowManager.LayoutParams

    private lateinit var opacitySeekBar: SeekBar
    private lateinit var strokeSeekBar: SeekBar
    private lateinit var fontSpinner: Spinner
    private lateinit var historyTextView: TextView
    private lateinit var btnClear: Button
    private lateinit var btnEraser: Button
    private lateinit var btnMode: Button
    private lateinit var btnClose: Button

    private val historyList = mutableListOf<String>()
    private var drawMode = true
    private var lastFontPosition = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

        // نافذة الرسم (ملء الشاشة) - نفصلها عن الأدوات حتى نكدر نخلي اللمس يمر للتطبيقات
        val canvas = DrawingView(this)
        canvasParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            baseFlags,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        canvasView = canvas

        // نافذة الأدوات (صغيرة) فوق نافذة الرسم
        val toolbar = LayoutInflater.from(this).inflate(R.layout.layout_overlay, null)
        toolbarView = toolbar
        val toolbarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            baseFlags,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        bindViews(toolbar, canvas)

        windowManager.addView(canvas, canvasParams)
        windowManager.addView(toolbar, toolbarParams)
    }

    private fun bindViews(toolbar: View, canvas: DrawingView) {
        opacitySeekBar = toolbar.findViewById(R.id.opacitySeekBar)
        strokeSeekBar = toolbar.findViewById(R.id.strokeSeekBar)
        fontSpinner = toolbar.findViewById(R.id.fontSpinner)
        historyTextView = toolbar.findViewById(R.id.historyTextView)
        btnClear = toolbar.findViewById(R.id.btnClear)
        btnEraser = toolbar.findViewById(R.id.btnEraser)
        btnMode = toolbar.findViewById(R.id.btnMode)
        btnClose = toolbar.findViewById(R.id.btnClose)

        // التحكم بالشفافية
        opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                canvas.alpha = progress.coerceAtLeast(10) / 100f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // التحكم بسمك القلم
        strokeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                canvas.setStrokeWidth(progress.toFloat().coerceAtLeast(2f))
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // اختيار الخط
        val fonts = arrayOf("عادي", "ديواني", "رقعة", "نسخ")
        fontSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, fonts)
        fontSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == lastFontPosition) return
                val firstTime = lastFontPosition == -1
                lastFontPosition = position
                canvas.setFont(fonts[position])
                if (!firstTime) addToHistory("تم تغيير الخط إلى: ${fonts[position]}")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // مسح الكل
        btnClear.setOnClickListener {
            canvas.clear()
            addToHistory("تم مسح الشاشة")
        }

        // التبديل للممحاة
        btnEraser.setOnClickListener {
            val isEraser = canvas.toggleEraser()
            btnEraser.text = if (isEraser) "قلم" else "ممحاة"
        }

        // التبديل بين وضع الرسم ووضع التمرير (اللمس يمر للتطبيق اللي تحت)
        btnMode.setOnClickListener {
            drawMode = !drawMode
            canvasParams.flags = if (drawMode) {
                canvasParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                canvasParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            windowManager.updateViewLayout(canvas, canvasParams)
            btnMode.text = if (drawMode) "تمرير" else "رسم"
            addToHistory(if (drawMode) "وضع الرسم" else "وضع التمرير")
        }

        // إغلاق الأداة
        btnClose.setOnClickListener { stopSelf() }
    }

    private fun startAsForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Overlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("أداة الرسم شغالة")
            .setContentText("اضغط زر إغلاق من الأدوات لإيقافها")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun addToHistory(item: String) {
        historyList.add(0, item)
        if (historyList.size > 10) historyList.removeAt(historyList.size - 1)
        historyTextView.text = historyList.joinToString("\n")
    }

    override fun onDestroy() {
        super.onDestroy()
        removeViewSafely(toolbarView)
        removeViewSafely(canvasView)
        toolbarView = null
        canvasView = null
    }

    private fun removeViewSafely(view: View?) {
        if (view == null) return
        try {
            windowManager.removeView(view)
        } catch (e: IllegalArgumentException) {
            // النافذة محذوفة مسبقاً
        }
    }
}
