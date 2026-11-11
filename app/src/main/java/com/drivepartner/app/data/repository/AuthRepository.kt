package com.drivepartner.app.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * AuthState - Represents authentication session status.
 */
sealed class AuthState {
    data object Idle : AuthState()
    data object OtpSent : AuthState()
    data class Authenticated(val token: String, val phoneNumber: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

/**
 * AuthRepository - Interface managing driver phone authentication & OTP verification.
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>
    val currentPhoneNumber: StateFlow<String>
    suspend fun requestOtp(phoneNumber: String): Boolean
    suspend fun verifyOtp(phoneNumber: String, otp: String): Boolean
    suspend fun logout()
}

/**
 * AuthRepositoryImpl - Implementation managing authentication state flow.
 */
class AuthRepositoryImpl(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentPhoneNumber = MutableStateFlow("")
    override val currentPhoneNumber: StateFlow<String> = _currentPhoneNumber.asStateFlow()

    override suspend fun requestOtp(phoneNumber: String): Boolean = withContext(ioDispatcher) {
        delay(500) // Simulate network delay
        _currentPhoneNumber.value = phoneNumber
        _authState.value = AuthState.OtpSent
        true
    }

    override suspend fun verifyOtp(phoneNumber: String, otp: String): Boolean = withContext(ioDispatcher) {
        delay(600) // Simulate network delay
        if (otp == "123456" || otp.length == 6) {
            val token = "jwt_partner_session_${System.currentTimeMillis()}"
            _authState.value = AuthState.Authenticated(token, phoneNumber)
            true
        } else {
            _authState.value = AuthState.Error("Invalid OTP. Default code is 123456.")
            false
        }
    }

    override suspend fun logout() = withContext(ioDispatcher) {
        _currentPhoneNumber.value = ""
        _authState.value = AuthState.Idle
    }
}
