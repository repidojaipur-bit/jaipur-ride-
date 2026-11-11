package com.drivepartner.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.repository.PartnerRepository
import com.drivepartner.app.data.repository.PartnerRepositoryImpl
import com.drivepartner.app.ui.components.state.DriveUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MainViewModel - Single source of truth for the Single Activity Drive Partner application.
 * Follows Android Architecture Guidelines (MVVM + UDF + Coroutines).
 */
class MainViewModel(
    private val repository: PartnerRepository = PartnerRepositoryImpl()
) : ViewModel() {

    private val _currentDestination = MutableStateFlow("home")
    private val _uiState = MutableStateFlow<DriveUiState>(DriveUiState.Success)
    private val _isHindiLanguage = MutableStateFlow(false)
    private val _notificationBadge = MutableStateFlow(false)

    val mainUiState: StateFlow<MainUiState> = combine(
        repository.partnerStatus,
        repository.partnerProfile,
        _currentDestination,
        _uiState,
        _isHindiLanguage,
        _notificationBadge
    ) { status, profile, destination, uiState, isHindi, hasNotification ->
        MainUiState(
            partnerStatus = status,
            partnerProfile = profile,
            currentDestination = destination,
            uiState = uiState,
            selectedService = profile.serviceType,
            isNotificationBadgeActive = hasNotification,
            isHindiLanguage = isHindi
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun toggleDutyStatus() {
        viewModelScope.launch {
            repository.toggleDutyStatus()
        }
    }

    fun setDutyStatus(isOnline: Boolean) {
        viewModelScope.launch {
            repository.setDutyStatus(isOnline)
        }
    }

    fun setDestination(route: String) {
        _currentDestination.update { route }
    }

    fun switchServiceType(serviceType: ServiceType) {
        viewModelScope.launch {
            repository.switchServiceType(serviceType)
        }
    }

    fun toggleLanguage() {
        _isHindiLanguage.update { !it }
    }

    fun updateProfileDetails(
        name: String? = null,
        phone: String? = null,
        vehicleNumber: String? = null,
        vehicleModel: String? = null,
        city: String? = null
    ) {
        viewModelScope.launch {
            repository.updateProfileDetails(name, phone, vehicleNumber, vehicleModel, city)
        }
    }

    fun setUiState(state: DriveUiState) {
        _uiState.update { state }
    }
}
