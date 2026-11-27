package com.silentexec.android.security

/**
 * Clean-Room On-Device PII & Secret Redactor (MIT).
 * Redacts Luhn-valid credit cards, OTP codes, API keys/Bearer tokens,
 * emails, phone numbers, and password fields before sending prompts to
 * remote cloud providers, without corrupting JSON/tool-call syntax.
 */
object PiiRedactor {
    private val TOKEN_REGEX = Regex("""(Bearer\s+[A-Za-z0-9._\-]{7,}|(?:sk-|AIza|ghp_|gho_)[A-Za-z0-9._\-]{6,})""")
    private val PASSWORD_JSON_REGEX = Regex("""("password"\s*:\s*")([^"]*)(")""", RegexOption.IGNORE_CASE)
    private val EMAIL_REGEX = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val CARD_REGEX = Regex("""(?<!\d)(?:\d{4}[-\s]?){3}\d{4}(?!\d)""")
    private val PHONE_REGEX = Regex("""(?<!\w)\+\d{1,3}[-\s]?(?:\d{2,4}[-\s]?){2,3}\d{3,4}(?!\w)""")
    private val OTP_REGEX = Regex("""(?<!\d)\d{4,8}(?!\d)""")

    fun isLuhnValid(digits: String): Boolean {
        val nums = digits.filter { it.isDigit() }.map { it - '0' }
        if (nums.size !in 13..19) return false
        var sum = 0
        var double = false
        for (i in nums.indices.reversed()) {
            var v = nums[i]
            if (double) {
                v *= 2
                if (v > 9) v -= 9
            }
            sum += v
            double = !double
        }
        return sum % 10 == 0
    }

    fun redact(input: String, enabled: Boolean = true, maskContactInfo: Boolean = true): String {
        if (!enabled || input.isEmpty()) return input
        var text = input

        var tokenIdx = 0
        text = TOKEN_REGEX.replace(text) { m ->
            tokenIdx++
            if (m.value.startsWith("Bearer", ignoreCase = true)) {
                "Bearer [REDACTED_TOKEN_$tokenIdx]"
            } else {
                "[REDACTED_TOKEN_$tokenIdx]"
            }
        }

        text = PASSWORD_JSON_REGEX.replace(text) { m ->
            "${m.groupValues[1]}[REDACTED_PASSWORD]${m.groupValues[3]}"
        }

        var cardIdx = 0
        text = CARD_REGEX.replace(text) { m ->
            if (isLuhnValid(m.value)) {
                cardIdx++
                "[REDACTED_CARD_$cardIdx]"
            } else {
                m.value
            }
        }

        if (maskContactInfo) {
            var emailIdx = 0
            text = EMAIL_REGEX.replace(text) {
                emailIdx++
                "[REDACTED_EMAIL_$emailIdx]"
            }

            var phoneIdx = 0
            text = PHONE_REGEX.replace(text) {
                phoneIdx++
                "[REDACTED_PHONE_$phoneIdx]"
            }
        }

        var otpIdx = 0
        text = OTP_REGEX.replace(text) {
            otpIdx++
            "[REDACTED_OTP_$otpIdx]"
        }

        return text
    }
}
