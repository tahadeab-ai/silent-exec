package com.silentexec.android.exec

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Optional non-root execution fallback powered by RikkaApps/Shizuku-API (MIT).
 * Runs `input tap/swipe/text/keyevent` when Accessibility ACTION_SET_TEXT or ACTION_CLICK
 * fails on custom views (WebView, Flutter, game engines), gracefully degrading when
 * Shizuku is not installed, not running, or not authorized.
 */
object ShizukuInputFallback {

    @Volatile
    private var shellService: IShizukuShellService? = null

    @Volatile
    private var bindLatch: CountDownLatch? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            shellService = if (service != null) IShizukuShellService.Stub.asInterface(service) else null
            bindLatch?.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
        }
    }

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder() &&
                !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    @Synchronized
    private fun ensureService(context: Context?): IShizukuShellService? {
        shellService?.let { return it }
        if (context == null || !isAvailable()) return null
        return try {
            val latch = CountDownLatch(1)
            bindLatch = latch
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ShizukuShellService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("shizuku_input")
                .version(1)
            Shizuku.bindUserService(args, connection)
            latch.await(1500, TimeUnit.MILLISECONDS)
            shellService
        } catch (_: Throwable) {
            null
        }
    }

    fun tap(context: Context?, x: Float, y: Float): Boolean {
        val svc = ensureService(context) ?: return false
        return runCatching {
            svc.execInputCommand("input tap ${x.roundToInt()} ${y.roundToInt()}") == 0
        }.getOrDefault(false)
    }

    fun swipe(context: Context?, x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Int = 300): Boolean {
        val svc = ensureService(context) ?: return false
        return runCatching {
            svc.execInputCommand(
                "input swipe ${x1.roundToInt()} ${y1.roundToInt()} ${x2.roundToInt()} ${y2.roundToInt()} $durationMs"
            ) == 0
        }.getOrDefault(false)
    }

    fun inputText(context: Context?, text: String): Boolean {
        val svc = ensureService(context) ?: return false
        val escaped = escapeShellInputText(text)
        return runCatching {
            svc.execInputCommand("input text $escaped") == 0
        }.getOrDefault(false)
    }

    fun keyEvent(context: Context?, keyCode: Int): Boolean {
        val svc = ensureService(context) ?: return false
        return runCatching {
            svc.execInputCommand("input keyevent $keyCode") == 0
        }.getOrDefault(false)
    }

    internal fun escapeShellInputText(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            when (ch) {
                ' ' -> sb.append("%s")
                '\'', '"', '\\', '&', '|', ';', '<', '>', '(', ')', '$', '`', '*', '?', '~', '#' -> {
                    sb.append('\\').append(ch)
                }
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
