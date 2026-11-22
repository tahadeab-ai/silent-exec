package com.silentexec.android.exec

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

class SilentExecAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Zero-chatter background event processing
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        instance.clear()
    }

    companion object {
        private var instance: WeakReference<SilentExecAccessibilityService> = WeakReference(null)

        fun get(): SilentExecAccessibilityService? = instance.get()
    }
}
