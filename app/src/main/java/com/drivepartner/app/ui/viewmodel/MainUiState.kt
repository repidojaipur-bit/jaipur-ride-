package com.drivepartner.app.ui.viewmodel

import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.ui.components.state.DriveUiState

data class MainUiState(
    val partnerStatus: PartnerStatus = PartnerStatus.OFFLINE,
    val partnerProfile: PartnerProfile? = null,
    val currentDestination: String = "home",
    val uiState: DriveUiState = DriveUiState.Success,
    val selectedService: ServiceType = ServiceType.BIKE_TAXI,
    val isNotificationBadgeActive: Boolean = false,
    val isHindiLanguage: Boolean = false
)
