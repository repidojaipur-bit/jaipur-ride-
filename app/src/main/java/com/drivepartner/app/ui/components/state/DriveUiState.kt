package com.drivepartner.app.ui.components.state

sealed interface DriveUiState {
    data object Idle : DriveUiState
    data object Loading : DriveUiState
    data object Success : DriveUiState
    data class Empty(
        val title: String? = null,
        val message: String? = null
    ) : DriveUiState
    data class Error(
        val message: String? = null,
        val canRetry: Boolean = true
    ) : DriveUiState
    data object Offline : DriveUiState
}
