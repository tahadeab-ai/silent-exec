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
    private var llPillConfirmActions: View? = null
    private var btnPillAllow: Button? = null
    private var btnPillDeny: Button? = null
    private var onCancelCallback: (() -> Unit)? = null
    private var onConfirmDecisionCallback: ((Boolean) -> Unit)? = null

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
                llPillConfirmActions = view.findViewById(R.id.llPillConfirmActions)
                btnPillAllow = view.findViewById(R.id.btnPillAllow)
                btnPillDeny = view.findViewById(R.id.btnPillDeny)
                val btnStop = view.findViewById<Button>(R.id.btnPillStop)

                btnStop.setOnClickListener {
                    onConfirmDecisionCallback?.invoke(false)
                    onConfirmDecisionCallback = null
                    onCancelCallback?.invoke()
                    hide()
                }

                btnPillAllow?.setOnClickListener {
                    val cb = onConfirmDecisionCallback
                    onConfirmDecisionCallback = null
                    llPillConfirmActions?.visibility = View.GONE
                    cb?.invoke(true)
                }

                btnPillDeny?.setOnClickListener {
                    val cb = onConfirmDecisionCallback
                    onConfirmDecisionCallback = null
                    llPillConfirmActions?.visibility = View.GONE
                    cb?.invoke(false)
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
                llPillConfirmActions?.visibility = View.GONE
                onConfirmDecisionCallback = null
                tvPillStatus?.text = stepText
            } else {
                fallbackNotifier?.invoke(stepText)
            }
        }
    }

    fun showConfirmation(
        actionSummary: String,
        onDecision: (Boolean) -> Unit
    ) {
        mainHandler.post {
            if (!canDrawOverlays) {
                return@post
            }
            if (pillView == null) {
                show(actionSummary, onCancel = { onDecision(false) })
            }
            mainHandler.post {
                onConfirmDecisionCallback = onDecision
                tvPillStatus?.text = actionSummary
                llPillConfirmActions?.visibility = View.VISIBLE
            }
        }
    }

    fun hideConfirmation() {
        mainHandler.post {
            onConfirmDecisionCallback = null
            llPillConfirmActions?.visibility = View.GONE
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
                llPillConfirmActions = null
                btnPillAllow = null
                btnPillDeny = null
                onCancelCallback = null
                onConfirmDecisionCallback = null
            }
        }
    }
}
