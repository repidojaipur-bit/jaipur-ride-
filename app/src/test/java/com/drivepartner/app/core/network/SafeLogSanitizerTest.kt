package com.drivepartner.app.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SafeLogSanitizerTest {

    @Test
    fun sanitizeHeader_redactsAuthAndCookieHeadersOnly() {
        assertEquals("[REDACTED]", SafeLogSanitizer.sanitizeHeader("Authorization", "Bearer secret"))
        assertEquals("[REDACTED]", SafeLogSanitizer.sanitizeHeader("Cookie", "session=123"))
        assertEquals("req-42", SafeLogSanitizer.sanitizeHeader("X-Request-Id", "req-42"))
    }

    @Test
    fun redactSensitiveFields_masksTokensOtpPhoneAndBankFields() {
        val payload = """{"firebaseIdToken":"abc123","otp":4567,"phoneNumber":"+919999999999","city":"JAI"}"""
        val sanitized = SafeLogSanitizer.redactSensitiveFields(payload)
        assertFalse(sanitized.contains("abc123"))
        assertFalse(sanitized.contains("4567"))
        assertFalse(sanitized.contains("9999999999"))
        assertEquals(
            """{"firebaseIdToken":"[REDACTED]","otp":[REDACTED],"phoneNumber":"[REDACTED]","city":"JAI"}""",
            sanitized
        )

        val urlLine = "GET https://api.example.com/v1/verify?token=secretTok&phone=9988776655&city=JAI"
        val sanitizedUrl = SafeLogSanitizer.redactSensitiveFields(urlLine)
        assertFalse(sanitizedUrl.contains("secretTok"))
        assertFalse(sanitizedUrl.contains("9988776655"))
    }
}
