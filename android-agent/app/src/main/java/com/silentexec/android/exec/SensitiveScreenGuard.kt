package com.silentexec.android.exec

import com.silentexec.android.core.ActionCommand

/**
 * Sensitive Screen & TakeOver Guard (Open-AutoGLM Apache-2.0 pattern).
 * Detects FLAG_SECURE screens, password input fields, and payment/checkout flows,
 * pausing autonomous execution and emitting TakeOverRequired for user control.
 */
object SensitiveScreenGuard {

    private val PASSWORD_FIELD_KEYWORDS = listOf(
        "password", "passcode", "pin", "cvv", "cvc", "otp",
        "كلمة المرور", "كلمة السر", "الرمز السري", "رمز التحقق"
    )

    private val PAYMENT_SCREEN_KEYWORDS = listOf(
        "pay now", "checkout", "credit card", "debit card", "card number",
        "google pay", "apple pay", "billing", "confirm payment", "bank transfer",
        "الدفع", "ادفع الآن", "بطاقة ائتمان", "رقم البطاقة", "تأكيد الدفع", "تحويل بنكي", "سداد"
    )

    fun evaluate(
        stepIndex: Int,
        cmd: ActionCommand?,
        phoneState: PhoneState?,
        uiElements: List<UiElementInfo> = emptyList(),
        uiStrings: List<String> = emptyList()
    ): TakeOverRequest? {
        // 1. Delegate base FLAG_SECURE & sensitive action checks to TakeOverGate
        TakeOverGate.evaluate(stepIndex, cmd, phoneState)?.let { return it }

        // 2. Password field detection via AccessibilityNodeInfo.isPassword or field labels
        val passwordNode = uiElements.firstOrNull { node ->
            if (node.isPassword) return@firstOrNull true
            if (node.isEditable) {
                val combined = "${node.text} ${node.contentDescription} ${node.resourceId}".lowercase()
                PASSWORD_FIELD_KEYWORDS.any { combined.contains(it) }
            } else {
                false
            }
        }
        if (passwordNode != null) {
            return TakeOverRequest(
                stepIndex = stepIndex,
                reason = "Password input field detected; user take-over required",
                isSecureScreen = false,
                sensitiveAction = cmd
            )
        }

        // 3. Payment / checkout screen detection across visible UI text
        val screenBlob = buildString {
            uiElements.forEach {
                append(it.text).append(' ').append(it.contentDescription).append(' ')
            }
            uiStrings.forEach { append(it).append(' ') }
        }.lowercase()

        val matchedPayment = PAYMENT_SCREEN_KEYWORDS.firstOrNull { screenBlob.contains(it) }
        if (matchedPayment != null) {
            return TakeOverRequest(
                stepIndex = stepIndex,
                reason = "Payment screen detected ('$matchedPayment'); user take-over required",
                isSecureScreen = false,
                sensitiveAction = cmd
            )
        }

        return null
    }
}
