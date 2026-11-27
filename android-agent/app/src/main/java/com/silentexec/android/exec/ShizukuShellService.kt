package com.silentexec.android.exec

import android.content.Context
import android.util.Log

/**
 * Privileged Shizuku UserService (MIT Shizuku-API) running under ADB UID 2000.
 * Executes `input tap/swipe/text/keyevent` when Accessibility actions fail on custom views.
 */
class ShizukuShellService : IShizukuShellService.Stub {

    constructor() : super()
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : super()

    override fun execInputCommand(command: String): Int {
        val trimmed = command.trim()
        if (!trimmed.startsWith("input ")) {
            return -1
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", trimmed))
            process.waitFor()
        } catch (t: Throwable) {
            Log.e("ShizukuShellService", "Failed executing '$trimmed': ${t.message}")
            -1
        }
    }

    override fun destroy() {
        Runtime.getRuntime().exit(0)
    }
}
