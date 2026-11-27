package com.silentexec.android

import com.silentexec.android.core.ActionJson
import com.silentexec.android.core.ActionType
import com.silentexec.android.security.PiiRedactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiRedactorSecurityTest {

    @Test
    fun test01_luhnValidationAndJsonPreservingRedaction() {
        assertTrue(PiiRedactor.isLuhnValid("4111-1111-1111-1111"))
        assertFalse(PiiRedactor.isLuhnValid("4111-1111-1111-1112"))

        val rawJson = """{"thought":"Type OTP","action":"ui_type","target":"OTP 482910 card 4111-1111-1111-1111 token sk-secret123456 email user@test.com phone +967777123456","password":"Pass123!","platform":"android","confidence":0.95}"""
        val redacted = PiiRedactor.redact(rawJson, enabled = true, maskContactInfo = true)

        assertTrue(redacted.contains("[REDACTED_OTP_1]"))
        assertTrue(redacted.contains("[REDACTED_CARD_1]"))
        assertTrue(redacted.contains("[REDACTED_TOKEN_1]"))
        assertTrue(redacted.contains("[REDACTED_EMAIL_1]"))
        assertTrue(redacted.contains("[REDACTED_PHONE_1]"))
        assertTrue(redacted.contains("[REDACTED_PASSWORD]"))
        assertFalse(redacted.contains("Pass123!"))

        // Verify JSON / ActionCommand still parses cleanly
        val cmd = ActionJson.fromLlmJson(redacted)
        assertEquals(ActionType.UI_TYPE, cmd.action)
    }
}
