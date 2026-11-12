package com.drivepartner.app.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.UUID

/**
 * OkHttp interceptor enforcing X-Request-Id correlation and redacting sensitive headers
 * before diagnostic logging per 04_TECHSPEC.md Section 3 & Engineering-Practices.md Section 8.
 */
class RequestMetadataInterceptor(
    private val requestIdGenerator: () -> String = { UUID.randomUUID().toString() },
    private val tokenProvider: AuthSessionTokenProvider? = null
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val skipAuth = original.header(HEADER_SKIP_AUTH).equals("true", ignoreCase = true)
        val builder = original.newBuilder().removeHeader(HEADER_SKIP_AUTH)

        if (original.header(HEADER_REQUEST_ID).isNullOrBlank()) {
            builder.header(HEADER_REQUEST_ID, requestIdGenerator())
        }
        if (!skipAuth && original.header(HEADER_AUTHORIZATION).isNullOrBlank()) {
            val token = tokenProvider?.currentToken()
            if (!token.isNullOrBlank()) {
                builder.header(HEADER_AUTHORIZATION, "Bearer $token")
            }
        }
        return chain.proceed(builder.build())
    }

    companion object {
        const val HEADER_REQUEST_ID = "X-Request-Id"
        const val HEADER_IDEMPOTENCY_KEY = "Idempotency-Key"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HEADER_SKIP_AUTH = "X-Skip-Auth"
    }
}

/**
 * Pure helper to sanitize log payloads/headers so tokens, OTPs, phone numbers,
 * bank details, and identity documents never enter log streams.
 */
object SafeLogSanitizer {
    private val sensitiveHeaderNames = setOf(
        "authorization",
        "apikey",
        "cookie",
        "set-cookie",
        "x-api-key",
        "x-auth-token"
    )

    private val sensitiveJsonKeys = listOf(
        "firebaseIdToken",
        "idToken",
        "access_token",
        "refresh_token",
        "apikey",
        "token",
        "otp",
        "phone",
        "phoneNumber",
        "accountNumber",
        "ifscCode",
        "aadhaar"
    )

    fun sanitizeHeader(name: String, value: String): String {
        return if (name.lowercase() in sensitiveHeaderNames) "[REDACTED]" else value
    }

    fun redactSensitiveFields(rawText: String): String {
        var sanitized = rawText
        for (key in sensitiveJsonKeys) {
            val quotedRegex = Regex("(\"$key\"\\s*:\\s*\")([^\"]*)(\")", RegexOption.IGNORE_CASE)
            sanitized = quotedRegex.replace(sanitized) { matchResult ->
                "${matchResult.groupValues[1]}[REDACTED]${matchResult.groupValues[3]}"
            }
            val numericRegex = Regex("(\"$key\"\\s*:\\s*)(\\d+)", RegexOption.IGNORE_CASE)
            sanitized = numericRegex.replace(sanitized) { matchResult ->
                "${matchResult.groupValues[1]}[REDACTED]"
            }
            val queryRegex = Regex("([?&]$key=)([^&\\s]+)", RegexOption.IGNORE_CASE)
            sanitized = queryRegex.replace(sanitized) { matchResult ->
                "${matchResult.groupValues[1]}[REDACTED]"
            }
        }
        return sanitized
    }
}
