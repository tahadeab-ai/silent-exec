package com.silentexec.android.exec

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.lang.ref.WeakReference

data class UiElementInfo(
    val index: Int,
    val text: String,
    val contentDescription: String,
    val className: String,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val bounds: Rect,
    val resourceId: String = ""
) {
    fun formatForPrompt(): String {
        val label = when {
            text.isNotBlank() && contentDescription.isNotBlank() && text != contentDescription -> "$text ($contentDescription)"
            text.isNotBlank() -> text
            contentDescription.isNotBlank() -> contentDescription
            isEditable -> "input field"
            else -> className.substringAfterLast('.')
        }
        val role = when {
            isEditable -> "input"
            isClickable -> "button"
            isScrollable -> "list"
            else -> className.substringAfterLast('.').ifBlank { "view" }
        }
        val idSuffix = resourceId.substringAfterLast('/').takeIf { it.isNotBlank() }?.let { " #$it" } ?: ""
        return "[$index] $role: $label$idSuffix [${bounds.centerX()},${bounds.centerY()}]"
    }
}

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
     * Resolves the foreground non-self, non-overlay window root so SilentExec never inspects its own UI or overlay.
     */
    private fun resolveTargetWindowRoot(): AccessibilityNodeInfo? {
        val selfPkg = applicationContext.packageName
        val allWindows = windows
        if (!allWindows.isNullOrEmpty()) {
            // Prefer active application window that is NOT self and NOT an accessibility overlay
            for (win in allWindows) {
                if (win.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                val root = win.root ?: continue
                val pkg = root.packageName?.toString().orEmpty()
                if (pkg.isNotEmpty() && pkg == selfPkg) continue
                if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION && win.isActive) {
                    return root
                }
            }
            // Fallback to any non-self, non-overlay application/system window
            for (win in allWindows) {
                if (win.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                val root = win.root ?: continue
                val pkg = root.packageName?.toString().orEmpty()
                if (pkg.isNotEmpty() && pkg == selfPkg) continue
                if (win.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                    return root
                }
            }
        }
        val fallbackRoot = rootInActiveWindow ?: return null
        val fallbackPkg = fallbackRoot.packageName?.toString().orEmpty()
        return if (fallbackPkg == selfPkg) null else fallbackRoot
    }

    /**
     * Traverses the active non-self window and extracts a pruned, indexed summary of interactive/visible UI elements.
     */
    fun extractPrunedUiTree(): List<UiElementInfo> {
        val root = resolveTargetWindowRoot() ?: return emptyList()
        val elements = mutableListOf<UiElementInfo>()
        var counter = 0

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null || elements.size >= 35) return
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
                            index = counter++,
                            text = text,
                            contentDescription = desc,
                            className = node.className?.toString() ?: "",
                            isClickable = isClickable,
                            isEditable = isEditable,
                            isScrollable = isScrollable,
                            bounds = bounds,
                            resourceId = node.viewIdResourceName ?: ""
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
     * Returns the pruned screen elements formatted for prompt inclusion.
     */
    fun extractPrunedUiStrings(): List<String> {
        return extractPrunedUiTree().map { it.formatForPrompt() }
    }

    fun activePackageName(): String {
        return rootInActiveWindow?.packageName?.toString().orEmpty()
    }

    fun computeScreenSignature(): String {
        val pkg = activePackageName()
        val elements = extractPrunedUiStrings()
        return "$pkg|${elements.joinToString("\n")}".hashCode().toString()
    }

    /**
     * Executes `ui_click` by index (`#0`, `1`) OR matching target label with Arabic/English normalization,
     * clicking the node (or clickable parent), or falling back to a dispatchGesture tap at the center bounds.
     * If element is not found, scrolls smoothly and attempts re-resolution once.
     */
    fun performUiClick(target: String): Boolean {
        val trimmed = target.trim()
        if (trimmed.isEmpty()) return false

        // 1. Direct Index Click: "#0", "#1", "0", "1"
        val indexMatch = Regex("""^#?(\d+)$""").matchEntire(trimmed)
        if (indexMatch != null) {
            val targetIndex = indexMatch.groupValues[1].toInt()
            val elements = extractPrunedUiTree()
            val matched = elements.find { it.index == targetIndex }
            if (matched != null) {
                val cx = matched.bounds.centerX().toFloat()
                val cy = matched.bounds.centerY().toFloat()
                return dispatchTapGesture(cx, cy)
            }
        }

        // 2. Text Search
        var clicked = clickByText(trimmed)
        if (!clicked) {
            // Scroll down smoothly once and try again
            performUiScroll("down")
            try { Thread.sleep(350) } catch (_: InterruptedException) {}
            clicked = clickByText(trimmed)
        }

        return clicked
    }

    private fun clickByText(target: String): Boolean {
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

        // Try ACTION_CLICK directly on candidate
        if (candidate.isClickable && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }

        // Try climbing parent chain to find clickable container
        var parent = candidate.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            parent = parent.parent
        }

        // Fallback to physical gesture tap at center of bounds
        val bounds = Rect()
        candidate.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            return dispatchTapGesture(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }

        return false
    }

    /**
     * Executes `ui_type` by locating focused or target input field and setting text via ACTION_SET_TEXT.
     * Optionally triggers search/IME action if requested.
     */
    fun performUiType(text: String, targetField: String? = null, pressEnter: Boolean = false): Boolean {
        val root = rootInActiveWindow ?: return false

        var targetNode: AccessibilityNodeInfo? = null

        // 1. If targetField index provided ("#0")
        if (!targetField.isNullOrBlank()) {
            val indexMatch = Regex("""^#?(\d+)$""").matchEntire(targetField.trim())
            if (indexMatch != null) {
                val targetIndex = indexMatch.groupValues[1].toInt()
                val elements = extractPrunedUiTree()
                val matched = elements.find { it.index == targetIndex }
                if (matched != null) {
                    dispatchTapGesture(matched.bounds.centerX().toFloat(), matched.bounds.centerY().toFloat())
                    try { Thread.sleep(200) } catch (_: InterruptedException) {}
                    targetNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                }
            }
        }

        // 2. If targetField label provided, search for matching editable field
        if (targetNode == null && !targetField.isNullOrBlank()) {
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

        // 3. If no target found, locate currently focused input
        if (targetNode == null) {
            targetNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }

        // 4. If still null, pick first editable node
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
        val typed = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (typed && pressEnter) {
            // Attempt to trigger search/send button if visible
            fun tryClickSendOrSearch(n: AccessibilityNodeInfo?) {
                if (n == null) return
                val desc = normalizeText(n.contentDescription?.toString() ?: "")
                val txt = normalizeText(n.text?.toString() ?: "")
                if (desc.contains("send") || desc.contains("search") || desc.contains("ارسال") || desc.contains("بحث") ||
                    txt.contains("send") || txt.contains("search") || txt.contains("ارسال") || txt.contains("بحث")
                ) {
                    if (n.isClickable) {
                        n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return
                    }
                }
                for (i in 0 until n.childCount) {
                    tryClickSendOrSearch(n.getChild(i))
                }
            }
            tryClickSendOrSearch(rootInActiveWindow)
        }

        return typed
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
                startX = bounds.centerX().toFloat()
                startY = bounds.top + bounds.height() * 0.25f
                endX = startX
                endY = bounds.top + bounds.height() * 0.75f
            }
            isLeft -> {
                startX = bounds.left + bounds.width() * 0.25f
                startY = bounds.centerY().toFloat()
                endX = bounds.left + bounds.width() * 0.75f
                endY = startY
            }
            isRight -> {
                startX = bounds.left + bounds.width() * 0.75f
                startY = bounds.centerY().toFloat()
                endX = bounds.left + bounds.width() * 0.25f
                endY = startY
            }
            else -> {
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
