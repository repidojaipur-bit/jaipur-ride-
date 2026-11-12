package com.drivepartner.app.ui.viewmodel

import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.local.AppLanguage
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.ThemePreference
import com.drivepartner.app.navigation.DriveDestination
import com.drivepartner.app.ui.components.state.DriveUiState

data class MainUiState(
    val dataOrigin: DataOrigin = DataOrigin.LIVE,
    val partnerStatus: PartnerStatus = PartnerStatus.OFFLINE,
    val dutyControlState: DutyControlState = DutyControlState.OFFLINE,
    val partnerProfile: PartnerProfile? = null,
    val currentDestination: String = DriveDestination.Home.route,
    val uiState: DriveUiState = DriveUiState.Success,
    val selectedService: ServiceType = ServiceType.BIKE_TAXI,
    val isNotificationBadgeActive: Boolean = false,
    val preferredLanguage: AppLanguage = AppLanguage.ENGLISH,
    val themePreference: ThemePreference = ThemePreference.LIGHT,
    val boundaryMessage: String? = null
) {
    val isHindiLanguage: Boolean
        get() = preferredLanguage == AppLanguage.HINDI
}
