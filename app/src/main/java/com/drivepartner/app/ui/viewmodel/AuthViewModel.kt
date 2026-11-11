package com.drivepartner.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivepartner.app.data.repository.AuthRepository
import com.drivepartner.app.data.repository.AuthRepositoryImpl
import com.drivepartner.app.data.repository.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * AuthUiState - State representation for driver authentication screen.
 */
data class AuthUiState(
    val authState: AuthState = AuthState.Idle,
    val phoneNumber: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * AuthViewModel - ViewModel handling driver phone login and OTP verification.
 * Follows Android Architecture Guidelines (MVVM + StateFlow + Coroutines).
 */
class AuthViewModel(
    private val authRepository: AuthRepository = AuthRepositoryImpl()
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AuthUiState> = combine(
        authRepository.authState,
        authRepository.currentPhoneNumber,
        _isLoading,
        _errorMessage
    ) { authState, phone, loading, error ->
        AuthUiState(
            authState = authState,
            phoneNumber = phone,
            isLoading = loading,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AuthUiState()
    )

    fun requestOtp(phoneNumber: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val success = authRepository.requestOtp(phoneNumber)
            _isLoading.value = false
            if (!success) {
                _errorMessage.value = "Failed to request OTP. Please check your connection."
            }
        }
    }

    fun verifyOtp(otp: String) {
        val currentPhone = uiState.value.phoneNumber
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val success = authRepository.verifyOtp(currentPhone, otp)
            _isLoading.value = false
            if (!success) {
                _errorMessage.value = "Invalid OTP verification code. Default code is 123456."
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
