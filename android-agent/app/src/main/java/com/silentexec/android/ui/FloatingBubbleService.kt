package com.silentexec.android.ui

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.silentexec.android.R
import kotlin.math.abs

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootView: View? = null
    private var flBubbleIcon: FrameLayout? = null
    private var cardMiniConsole: LinearLayout? = null
    private var llFloatingPill: LinearLayout? = null
    private var tvFloatingPillStatus: TextView? = null
    private var btnFloatingPillStop: Button? = null
    private var etFloatingCommand: EditText? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private var onStopCallback: (() -> Unit)? = null

    companion object {
        private var instance: FloatingBubbleService? = null

        fun isRunning(): Boolean = instance != null

        fun start(context: Context) {
            if (Settings.canDrawOverlays(context)) {
                val intent = Intent(context, FloatingBubbleService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingBubbleService::class.java)
            context.stopService(intent)
        }

        fun updateProgress(text: String, onStop: (() -> Unit)? = null) {
            instance?.showProgress(text, onStop)
        }

        fun resetToBubble() {
            instance?.showBubble()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        initOverlayView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initOverlayView() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.layout_floating_bubble, null)
        rootView = view

        flBubbleIcon = view.findViewById(R.id.flBubbleIcon)
        cardMiniConsole = view.findViewById(R.id.cardMiniConsole)
        llFloatingPill = view.findViewById(R.id.llFloatingPill)
        tvFloatingPillStatus = view.findViewById(R.id.tvFloatingPillStatus)
        btnFloatingPillStop = view.findViewById(R.id.btnFloatingPillStop)
        etFloatingCommand = view.findViewById(R.id.etFloatingCommand)

        val btnClose = view.findViewById<TextView>(R.id.btnFloatingClose)
        val btnExecute = view.findViewById<Button>(R.id.btnFloatingExecute)
        val btnMic = view.findViewById<Button>(R.id.btnFloatingMic)

        btnClose.setOnClickListener {
            showBubble()
        }

        btnExecute.setOnClickListener {
            val cmd = etFloatingCommand?.text?.toString()?.trim().orEmpty()
            if (cmd.isNotEmpty()) {
                etFloatingCommand?.text?.clear()
                showProgress(cmd, null)
                dispatchCommand(cmd)
            }
        }

        btnMic.setOnClickListener {
            showBubble()
            val intent = Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_TRIGGER_MIC
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }

        btnFloatingPillStop?.setOnClickListener {
            onStopCallback?.invoke()
            showBubble()
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }
        windowParams = params

        setupDraggable(flBubbleIcon!!, params)

        try {
            windowManager.addView(view, params)
            showBubble()
        } catch (_: Exception) {
            stopSelf()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 10 || abs(dy) > 10) {
                        isClick = false
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    try {
                        windowManager.updateViewLayout(rootView, params)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        showConsole()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun showBubble() {
        mainHandler.post {
            flBubbleIcon?.visibility = View.VISIBLE
            cardMiniConsole?.visibility = View.GONE
            llFloatingPill?.visibility = View.GONE
            setFocusable(false)
        }
    }

    private fun showConsole() {
        mainHandler.post {
            flBubbleIcon?.visibility = View.GONE
            cardMiniConsole?.visibility = View.VISIBLE
            llFloatingPill?.visibility = View.GONE
            setFocusable(true)
        }
    }

    fun showProgress(text: String, onStop: (() -> Unit)?) {
        mainHandler.post {
            onStopCallback = onStop
            flBubbleIcon?.visibility = View.GONE
            cardMiniConsole?.visibility = View.GONE
            llFloatingPill?.visibility = View.VISIBLE
            tvFloatingPillStatus?.text = text
            setFocusable(false)
        }
    }

    private fun setFocusable(focusable: Boolean) {
        val params = windowParams ?: return
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        try {
            windowManager.updateViewLayout(rootView, params)
        } catch (_: Exception) {}
    }

    private fun dispatchCommand(command: String) {
        val main = MainActivity.activeInstance
        if (main != null) {
            main.handleCommandFromExternal(command)
        } else {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_EXECUTE_COMMAND
                putExtra(MainActivity.EXTRA_COMMAND, command)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        rootView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {}
        }
        rootView = null
    }
}
