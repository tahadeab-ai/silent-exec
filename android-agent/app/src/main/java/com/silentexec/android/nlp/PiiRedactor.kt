package com.silentexec.android.nlp

/**
 * Clean-Room On-Device PII Privacy Masker (MIT).
 * Pseudonymizes credit cards, emails, phone numbers, and 4-8 digit OTP codes
 * before sending UI trees over LAN or to external providers.
 */
object PiiRedactor {
    private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val CARD_REGEX = Regex("""(?<!\d)(?:\d{4}[-\s]?){3}\d{4}(?!\d)""")
    private val PHONE_REGEX = Regex("""(?<!\w)(?:\+\d{1,3}[-\s]?)?(?:\d{2,4}[-\s]?){2,3}\d{3,4}(?!\w)""")
    private val OTP_REGEX = Regex("""(?<!\d)\d{4,8}(?!\d)""")

    fun redact(input: String, enabled: Boolean = true): String {
        if (!enabled || input.isEmpty()) return input
        var text = input

        var emailIdx = 0
        text = EMAIL_REGEX.replace(text) {
            emailIdx++
            "[REDACTED_EMAIL_$emailIdx]"
        }

        var cardIdx = 0
        text = CARD_REGEX.replace(text) {
            cardIdx++
            "[REDACTED_CARD_$cardIdx]"
        }

        var phoneIdx = 0
        text = PHONE_REGEX.replace(text) { m ->
            val digits = m.value.filter { it.isDigit() }
            if (digits.length >= 8) {
                phoneIdx++
                "[REDACTED_PHONE_$phoneIdx]"
            } else {
                m.value
            }
        }

        var otpIdx = 0
        text = OTP_REGEX.replace(text) {
            otpIdx++
            "[REDACTED_OTP_$otpIdx]"
        }

        return text
    }

    fun redactList(items: List<String>, enabled: Boolean = true): List<String> {
        if (!enabled) return items
        return items.map { redact(it, enabled = true) }
    }
}
