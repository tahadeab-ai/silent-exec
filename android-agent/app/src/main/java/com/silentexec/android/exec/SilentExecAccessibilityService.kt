package com.silentexec.android.exec

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference

data class UiElementInfo(
    val index: Int,
    val text: String,
    val contentDescription: String,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val bounds: Rect
)

class SilentExecAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive observation for zero-chatter operation
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onDestroy() {
        super.onDestroy()
        instance.clear()
    }

    /**
     * Traverses the active window and extracts a pruned, indexed summary of interactive/visible UI elements.
     */
    fun extractPrunedUiTree(): List<UiElementInfo> {
        val root = rootInActiveWindow ?: return emptyList()
        val elements = mutableListOf<UiElementInfo>()
        var counter = 0

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            if (!node.isVisibleToUser) return

            val text = node.text?.toString()?.trim() ?: ""
            val desc = node.contentDescription?.toString()?.trim() ?: ""
            val isClickable = node.isClickable
            val isEditable = node.isEditable
            val isScrollable = node.isScrollable

            val isInteractive = isClickable || isEditable || isScrollable || text.isNotEmpty() || desc.isNotEmpty()

            if (isInteractive) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.width() > 0 && bounds.height() > 0) {
                    elements.add(
                        UiElementInfo(
                            index = ++counter,
                            text = text,
                            contentDescription = desc,
                            className = node.className?.toString() ?: "",
                            isClickable = isClickable,
                            isEditable = isEditable,
                            isScrollable = isScrollable,
                            bounds = bounds
                        )
                    )
                }
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(root)
        return elements
    }

    /**
     * Executes `ui_click` by matching target label with Arabic/English normalization,
     * clicking the node (or clickable parent), or falling back to a dispatchGesture tap at the center bounds.
     */
    fun performUiClick(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val normTarget = normalizeText(target)
        if (normTarget.isEmpty()) return false

        var targetNode: AccessibilityNodeInfo? = null
        var fallbackNode: AccessibilityNodeInfo? = null

        fun findMatchingNode(node: AccessibilityNodeInfo?) {
            if (node == null || targetNode != null) return
            if (!node.isVisibleToUser) return

            val nodeText = normalizeText(node.text?.toString() ?: "")
            val nodeDesc = normalizeText(node.contentDescription?.toString() ?: "")

            if (nodeText == normTarget || nodeDesc == normTarget) {
                targetNode = node
                return
            }

            if (fallbackNode == null && (nodeText.contains(normTarget) || nodeDesc.contains(normTarget))) {
                fallbackNode = node
            }

            for (i in 0 until node.childCount) {
                findMatchingNode(node.getChild(i))
                if (targetNode != null) return
            }
        }

        findMatchingNode(root)
        val candidate = targetNode ?: fallbackNode ?: return false

        // 1. Try ACTION_CLICK directly on candidate
        if (candidate.isClickable && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        // 2. Try climbing parent chain to find clickable container
        var parent = candidate.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            parent = parent.parent
        }

        // 3. Fallback to physical gesture tap at center of bounds
        val bounds = Rect()
        candidate.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            return dispatchTapGesture(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }

        return false
    }

    /**
     * Executes `ui_type` by locating focused or target input field and setting text via ACTION_SET_TEXT.
     */
    fun performUiType(text: String, targetField: String? = null): Boolean {
        val root = rootInActiveWindow ?: return false

        var targetNode: AccessibilityNodeInfo? = null

        // 1. If targetField label provided, search for matching editable field
        if (!targetField.isNullOrBlank()) {
            val normField = normalizeText(targetField)
            fun findEditableByLabel(node: AccessibilityNodeInfo?) {
                if (node == null || targetNode != null) return
                if (node.isEditable) {
                    val nodeText = normalizeText(node.text?.toString() ?: "")
                    val nodeDesc = normalizeText(node.contentDescription?.toString() ?: "")
                    if (nodeText.contains(normField) || nodeDesc.contains(normField)) {
                        targetNode = node
                        return
                    }
                }
                for (i in 0 until node.childCount) {
                    findEditableByLabel(node.getChild(i))
                    if (targetNode != null) return
                }
            }
            findEditableByLabel(root)
        }

        // 2. If no target found, locate currently focused input
        if (targetNode == null) {
            targetNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }

        // 3. If still null, pick first editable node
        if (targetNode == null) {
            fun findFirstEditable(node: AccessibilityNodeInfo?) {
                if (node == null || targetNode != null) return
                if (node.isEditable && node.isVisibleToUser) {
                    targetNode = node
                    return
                }
                for (i in 0 until node.childCount) {
                    findFirstEditable(node.getChild(i))
                    if (targetNode != null) return
                }
            }
            findFirstEditable(root)
        }

        val node = targetNode ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    /**
     * Executes `ui_scroll` with direction ("up" | "down" | "left" | "right").
     */
    fun performUiScroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val normDir = direction.trim().lowercase()
        val isUp = normDir in listOf("up", "فوق", "اعلى", "أعلى")
        val isLeft = normDir in listOf("left", "يسار", "شمال")
        val isRight = normDir in listOf("right", "يمين")

        // 1. Try finding scrollable node
        var scrollableNode: AccessibilityNodeInfo? = null
        fun findScrollable(node: AccessibilityNodeInfo?) {
            if (node == null || scrollableNode != null) return
            if (node.isScrollable && node.isVisibleToUser) {
                scrollableNode = node
                return
            }
            for (i in 0 until node.childCount) {
                findScrollable(node.getChild(i))
                if (scrollableNode != null) return
            }
        }
        findScrollable(root)

        val target = scrollableNode ?: root
        val action = if (isUp || isLeft) {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }

        if (target.performAction(action)) {
            return true
        }

        // 2. Fallback to swipe gesture across screen bounds
        val bounds = Rect()
        target.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            bounds.set(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        }

        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float

        when {
            isUp -> {
                // Swipe downward to scroll up
                startX = bounds.centerX().toFloat()
                startY = bounds.top + bounds.height() * 0.25f
                endX = startX
                endY = bounds.top + bounds.height() * 0.75f
            }
            isLeft -> {
                // Swipe right to scroll left
                startX = bounds.left + bounds.width() * 0.25f
                startY = bounds.centerY().toFloat()
                endX = bounds.left + bounds.width() * 0.75f
                endY = startY
            }
            isRight -> {
                // Swipe left to scroll right
                startX = bounds.left + bounds.width() * 0.75f
                startY = bounds.centerY().toFloat()
                endX = bounds.left + bounds.width() * 0.25f
                endY = startY
            }
            else -> {
                // Swipe upward to scroll down
                startX = bounds.centerX().toFloat()
                startY = bounds.top + bounds.height() * 0.75f
                endX = startX
                endY = bounds.top + bounds.height() * 0.25f
            }
        }

        return dispatchSwipeGesture(startX, startY, endX, endY)
    }

    fun performUiBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performUiHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun dispatchTapGesture(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    private fun dispatchSwipeGesture(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 300)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    companion object {
        private var instance: WeakReference<SilentExecAccessibilityService> = WeakReference(null)

        fun get(): SilentExecAccessibilityService? = instance.get()

        /**
         * Normalizes text for robust element matching:
         * - Strips Arabic Tashkeel / diacritics
         * - Normalizes Alef variants (أ / إ / آ -> ا)
         * - Normalizes Teh Marbuta (ة -> ه)
         * - Normalizes Alef Maksura (ى -> ي)
         * - Strips Tatweel (ـ)
         * - Lowercases English, trims whitespace
         */
        fun normalizeText(input: String): String {
            return input
                .replace(Regex("""[\u064B-\u065F\u0670]"""), "") // Tashkeel
                .replace('أ', 'ا')
                .replace('إ', 'ا')
                .replace('آ', 'ا')
                .replace('ة', 'ه')
                .replace('ى', 'ي')
                .replace("ـ", "") // Tatweel
                .lowercase()
                .trim()
        }
    }
}
