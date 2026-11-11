package com.drivepartner.app.core.network

/**
 * In-memory authenticated token provider for LIVE HTTP requests (`Authorization: Bearer <token>`).
 * Tokens are kept strictly in memory (never written to plaintext SharedPreferences or logs).
 */
interface AuthSessionTokenProvider {
    fun currentToken(): String?
    fun setToken(token: String?)
    fun clearToken()
}

class InMemoryAuthSessionTokenProvider(
    initialToken: String? = null
) : AuthSessionTokenProvider {

    @Volatile
    private var token: String? = initialToken?.takeIf { it.isNotBlank() }

    override fun currentToken(): String? = token

    override fun setToken(token: String?) {
        this.token = token?.trim()?.takeIf { it.isNotEmpty() }
    }

    override fun clearToken() {
        this.token = null
    }
}
