package com.silentexec.android.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.silentexec.android.R

class LiveOverlayController(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var pillView: View? = null
    private var tvPillStatus: TextView? = null
    private var onCancelCallback: (() -> Unit)? = null

    val canDrawOverlays: Boolean
        get() = Settings.canDrawOverlays(context)

    fun show(
        initialText: String,
        onCancel: () -> Unit,
        fallbackNotifier: ((String) -> Unit)? = null
    ) {
        mainHandler.post {
            onCancelCallback = onCancel

            if (!canDrawOverlays) {
                fallbackNotifier?.invoke(initialText)
                return@post
            }

            if (pillView == null) {
                val inflater = LayoutInflater.from(context)
                val view = inflater.inflate(R.layout.layout_floating_pill, null)
                tvPillStatus = view.findViewById(R.id.tvPillStatus)
                val btnStop = view.findViewById<Button>(R.id.btnPillStop)

                btnStop.setOnClickListener {
                    onCancelCallback?.invoke()
                    hide()
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
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = 80
                }

                try {
                    windowManager.addView(view, params)
                    pillView = view
                } catch (_: Exception) {
                    pillView = null
                    fallbackNotifier?.invoke(initialText)
                    return@post
                }
            }

            tvPillStatus?.text = initialText
        }
    }

    fun update(stepText: String, fallbackNotifier: ((String) -> Unit)? = null) {
        mainHandler.post {
            if (pillView != null && tvPillStatus != null) {
                tvPillStatus?.text = stepText
            } else {
                fallbackNotifier?.invoke(stepText)
            }
        }
    }

    fun hide() {
        mainHandler.post {
            pillView?.let { view ->
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {
                    // Safe cleanup
                }
                pillView = null
                tvPillStatus = null
                onCancelCallback = null
            }
        }
    }
}
