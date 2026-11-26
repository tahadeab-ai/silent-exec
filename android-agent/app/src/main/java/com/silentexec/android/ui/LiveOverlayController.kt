package com.silentexec.android.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.text.BidiFormatter
import com.silentexec.android.R
import com.silentexec.android.exec.SilentExecAccessibilityService
import kotlin.math.abs

class LiveOverlayController(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var activeWindowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var pillView: View? = null
    private var pillParams: WindowManager.LayoutParams? = null
    private var statusDotView: View? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var tvPillStatus: TextView? = null
    private var llPillConfirmActions: View? = null
    private var btnPillAllow: Button? = null
    private var btnPillDeny: Button? = null
    private var onCancelCallback: (() -> Unit)? = null
    private var onConfirmDecisionCallback: ((Boolean) -> Unit)? = null

    @Volatile
    private var isAppInForeground: Boolean = true

    val canDrawOverlays: Boolean
        get() = Settings.canDrawOverlays(context) || SilentExecAccessibilityService.get() != null

    companion object {
        @Volatile
        private var activeInstance: LiveOverlayController? = null

        fun setMomentarilyInvisible(invisible: Boolean) {
            activeInstance?.applyMomentaryInvisibility(invisible)
        }
    }

    init {
        activeInstance = this
    }

    fun setAppForeground(inForeground: Boolean) {
        isAppInForeground = inForeground
        mainHandler.post {
            pillView?.visibility = if (inForeground) View.GONE else View.VISIBLE
        }
    }

    private fun isSelfForeground(): Boolean {
        if (isAppInForeground) return true
        val svcPkg = SilentExecAccessibilityService.get()?.activePackageName().orEmpty()
        return svcPkg == context.packageName
    }

    private fun computeSafeTopOffsetPx(): Int {
        val density = context.resources.displayMetrics.density
        val clearance12dp = (12f * density).toInt()
        val statusBarHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                activeWindowManager.currentWindowMetrics.windowInsets
                    .getInsets(WindowInsets.Type.statusBars()).top
            }.getOrDefault(0)
        } else {
            val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) context.resources.getDimensionPixelSize(resId) else (24f * density).toInt()
        }
        return statusBarHeight + clearance12dp
    }

    private fun applyMomentaryInvisibility(invisible: Boolean) {
        mainHandler.post {
            val view = pillView ?: return@post
            val params = pillParams ?: return@post
            view.alpha = if (invisible) 0f else 1f
            params.flags = if (invisible) {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            try {
                activeWindowManager.updateViewLayout(view, params)
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("ClickableViewAccessibility")
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
                val a11yService = SilentExecAccessibilityService.get()
                activeWindowManager = if (a11yService != null) {
                    a11yService.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                } else {
                    context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                }

                val inflater = LayoutInflater.from(a11yService ?: context)
                val view = inflater.inflate(R.layout.layout_floating_pill, null)
                statusDotView = view.findViewById(R.id.vPillStatusDot)
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

                val layoutType = when {
                    a11yService != null -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else -> {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    }
                }

                val density = context.resources.displayMetrics.density
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
                    x = (12f * density).toInt()
                    y = computeSafeTopOffsetPx()
                }
                pillParams = params

                var initX = 0
                var initY = 0
                var touchX = 0f
                var touchY = 0f
                view.setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initX = params.x
                            initY = params.y
                            touchX = event.rawX
                            touchY = event.rawY
                            false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - touchX).toInt()
                            val dy = (event.rawY - touchY).toInt()
                            if (abs(dx) > 6 || abs(dy) > 6) {
                                params.x = initX + dx
                                params.y = (initY + dy).coerceAtLeast(computeSafeTopOffsetPx())
                                try {
                                    activeWindowManager.updateViewLayout(view, params)
                                } catch (_: Exception) {}
                                true
                            } else {
                                false
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            val screenW = context.resources.displayMetrics.widthPixels
                            val w = view.width.takeIf { it > 0 } ?: (220 * density).toInt()
                            val margin = (12f * density).toInt()
                            val snapX = if (params.x + w / 2 > screenW / 2) screenW - w - margin else margin
                            ValueAnimator.ofInt(params.x, snapX).apply {
                                duration = 180
                                addUpdateListener { anim ->
                                    params.x = anim.animatedValue as Int
                                    try {
                                        activeWindowManager.updateViewLayout(view, params)
                                    } catch (_: Exception) {}
                                }
                                start()
                            }
                            false
                        }
                        else -> false
                    }
                }

                try {
                    activeWindowManager.addView(view, params)
                    pillView = view
                    startPulse()
                } catch (_: Exception) {
                    pillView = null
                    pillParams = null
                    fallbackNotifier?.invoke(initialText)
                    return@post
                }
            }

            pillView?.visibility = if (isSelfForeground()) View.GONE else View.VISIBLE
            tvPillStatus?.text = BidiFormatter.getInstance().unicodeWrap(initialText)
        }
    }

    private fun startPulse() {
        val dot = statusDotView ?: return
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.35f).apply {
            duration = 700
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    fun update(stepText: String, fallbackNotifier: ((String) -> Unit)? = null) {
        mainHandler.post {
            val view = pillView
            if (view != null && tvPillStatus != null) {
                llPillConfirmActions?.visibility = View.GONE
                onConfirmDecisionCallback = null
                view.visibility = if (isSelfForeground()) View.GONE else View.VISIBLE
                tvPillStatus?.text = BidiFormatter.getInstance().unicodeWrap(stepText)
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
            if (!canDrawOverlays) return@post
            if (pillView == null) {
                show(actionSummary, onCancel = { onDecision(false) })
            }
            mainHandler.post {
                onConfirmDecisionCallback = onDecision
                pillView?.visibility = if (isSelfForeground()) View.GONE else View.VISIBLE
                tvPillStatus?.text = BidiFormatter.getInstance().unicodeWrap(actionSummary)
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
            pulseAnimator?.cancel()
            pulseAnimator = null
            pillView?.let { view ->
                try {
                    activeWindowManager.removeView(view)
                } catch (_: Exception) {}
                pillView = null
                pillParams = null
                statusDotView = null
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
