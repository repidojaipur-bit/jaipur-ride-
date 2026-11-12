package com.drivepartner.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drivepartner.app.R
import com.drivepartner.app.core.common.UiText
import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.di.AppContainer
import com.drivepartner.app.core.location.DriverLocationProvider
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.location.DutyLocationTrackerController
import com.drivepartner.app.core.location.GpsReadinessStatus
import com.drivepartner.app.core.location.LocationPermissionStatus
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.model.DriverEligibility
import com.drivepartner.app.data.model.DriverPreferences
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.HomeDashboardSummary
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.ServiceZone
import com.drivepartner.app.data.repository.DutyLocationRepository
import com.drivepartner.app.data.repository.PartnerRepository
import com.drivepartner.app.ui.components.state.DriveUiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val dataOrigin: DataOrigin = DataOrigin.LIVE,
    val isMapsKeyConfigured: Boolean = false,
    val dutyControlState: DutyControlState = DutyControlState.OFFLINE,
    val partnerProfile: PartnerProfile? = null,
    val dashboardSummary: HomeDashboardSummary? = null,
    val eligibility: DriverEligibility? = null,
    val preferences: DriverPreferences? = null,
    val availableZones: List<ServiceZone> = emptyList(),
    val currentLocation: DriverLocationSnapshot? = null,
    val isLocationStale: Boolean = false,
    val permissionStatus: LocationPermissionStatus = LocationPermissionStatus.DENIED_CAN_ASK,
    val gpsStatus: GpsReadinessStatus = GpsReadinessStatus.READY,
    val isTrackingActive: Boolean = false,
    val isActionInFlight: Boolean = false,
    val showPermissionEducation: Boolean = false,
    val actionFeedback: UiText? = null,
    val boundaryMessage: String? = null,
    val screenState: DriveUiState = DriveUiState.Success
)

sealed interface HomeUiEvent {
    data object RequestLocationPermissions : HomeUiEvent
    data object OpenAppSettings : HomeUiEvent
    data object OpenLocationSettings : HomeUiEvent
}

/**
 * Phase 3 ViewModel managing Driver Home Dashboard, Eligibility Checks,
 * Location Permissions & GPS Readiness, Service Area & Auto Boost Preferences,
 * and Server-Authoritative Online/Offline Duty transitions.
 */
class HomeViewModel(
    private val partnerRepository: PartnerRepository,
    private val dutyLocationRepository: DutyLocationRepository,
    private val locationProvider: DriverLocationProvider,
    private val trackerController: DutyLocationTrackerController,
    private val environmentConfig: AppEnvironmentConfig,
    private val clockMillis: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _permissionStatus = MutableStateFlow(LocationPermissionStatus.DENIED_CAN_ASK)
    private val _gpsStatus = MutableStateFlow(GpsReadinessStatus.READY)
    private val _currentLocation = MutableStateFlow<DriverLocationSnapshot?>(null)
    private val _hasPromptedPermission = MutableStateFlow(false)
    private val _showPermissionEducation = MutableStateFlow(false)
    private val _pendingGoOnlineAfterPermission = MutableStateFlow(false)
    private val _pendingDutyTransition = MutableStateFlow<DutyControlState?>(null)
    private val _localBlockedOverride = MutableStateFlow(false)
    private val _actionFeedback = MutableStateFlow<UiText?>(null)
    private val _screenState = MutableStateFlow<DriveUiState>(DriveUiState.Success)

    private val _events = MutableSharedFlow<HomeUiEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<HomeUiEvent> = _events.asSharedFlow()

    private val domainFlows = combine(
        partnerRepository.partnerProfile,
        partnerRepository.boundaryMessage,
        dutyLocationRepository.dutyControlState,
        dutyLocationRepository.homeSummary,
        dutyLocationRepository.eligibility
    ) { profile, boundaryMsg, dutyState, summary, eligibility ->
        DomainSnapshot(profile, boundaryMsg, dutyState, summary, eligibility)
    }

    private val preferenceAndZoneFlows = combine(
        dutyLocationRepository.preferences,
        dutyLocationRepository.availableZones,
        dutyLocationRepository.lastErrorMessage,
        trackerController.isTracking
    ) { prefs, zones, repoError, isTracking ->
        PrefsSnapshot(prefs, zones, repoError, isTracking)
    }

    private val deviceLocationFlows = combine(
        _permissionStatus,
        _gpsStatus,
        _currentLocation,
        _showPermissionEducation,
        _pendingDutyTransition
    ) { perm, gps, loc, showEdu, pendingDuty ->
        DeviceSnapshot(perm, gps, loc, showEdu, pendingDuty)
    }

    private val uiFeedbackFlows = combine(
        _actionFeedback,
        _screenState,
        _localBlockedOverride
    ) { feedback, screenState, blockedOverride ->
        Triple(feedback, screenState, blockedOverride)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        domainFlows,
        preferenceAndZoneFlows,
        deviceLocationFlows,
        uiFeedbackFlows
    ) { domain, prefs, device, uiTuple ->
        val (feedback, screenState, blockedOverride) = uiTuple
        val nowMs = clockMillis()
        val isStale = device.location?.isStale(nowMs) == true
        val resolvedDutyState = when {
            device.pendingDuty != null -> device.pendingDuty
            blockedOverride || (domain.eligibility != null && !domain.eligibility.isEligibleForOnline) ->
                DutyControlState.BLOCKED
            else -> domain.dutyState
        }

        HomeUiState(
            dataOrigin = dutyLocationRepository.dataOrigin,
            isMapsKeyConfigured = environmentConfig.isMapsKeyConfigured,
            dutyControlState = resolvedDutyState,
            partnerProfile = domain.profile,
            dashboardSummary = domain.summary,
            eligibility = domain.eligibility,
            preferences = prefs.preferences,
            availableZones = prefs.zones,
            currentLocation = device.location,
            isLocationStale = isStale,
            permissionStatus = device.permission,
            gpsStatus = device.gps,
            isTrackingActive = prefs.isTracking,
            isActionInFlight = device.pendingDuty != null,
            showPermissionEducation = device.showEducation,
            actionFeedback = feedback,
            boundaryMessage = prefs.repoError ?: domain.boundaryMsg,
            screenState = screenState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(
            dataOrigin = dutyLocationRepository.dataOrigin,
            isMapsKeyConfigured = environmentConfig.isMapsKeyConfigured
        )
    )

    fun refreshDashboard() {
        viewModelScope.launch {
            _screenState.value = DriveUiState.Loading
            refreshDeviceLocationReadiness()

            val cityCode = partnerRepository.partnerProfile.value?.city?.takeIf { it.isNotBlank() } ?: "JAI"
            dutyLocationRepository.refreshDashboard(cityCode)
            val eligibilityResult = dutyLocationRepository.checkEligibility()
            _localBlockedOverride.value = (eligibilityResult as? ApiResult.Success)
                ?.data?.isEligibleForOnline == false

            val confirmedOnline = dutyLocationRepository.dutyControlState.value.isOnline
            if (confirmedOnline && _permissionStatus.value.hasLocationAccess && _gpsStatus.value == GpsReadinessStatus.READY) {
                trackerController.startTracking()
            } else if (!confirmedOnline) {
                trackerController.stopTracking()
            }
            _screenState.value = DriveUiState.Success
        }
    }

    fun onToggleDutyStatus() {
        if (_pendingDutyTransition.value != null) return

        viewModelScope.launch {
            val currentlyOnline = dutyLocationRepository.dutyControlState.value.isOnline
            if (currentlyOnline) {
                performGoOffline()
            } else {
                performGoOnline()
            }
        }
    }

    private suspend fun performGoOffline() {
        _pendingDutyTransition.value = DutyControlState.TURNING_OFFLINE
        try {
            when (val result = dutyLocationRepository.requestGoOffline()) {
                is ApiResult.Success -> {
                    if (result.data == DutyControlState.OFFLINE) {
                        trackerController.stopTracking()
                        _actionFeedback.value = null
                    }
                }
                is ApiResult.BackendNotDeployed -> {
                    _actionFeedback.value = UiText.DynamicString(result.reason)
                }
                is ApiResult.HttpError -> {
                    _actionFeedback.value = UiText.StringResource(R.string.duty_change_not_confirmed)
                }
                is ApiResult.NetworkError -> {
                    _actionFeedback.value = UiText.StringResource(R.string.duty_change_not_confirmed)
                }
            }
        } finally {
            _pendingDutyTransition.value = null
        }
    }

    private suspend fun performGoOnline() {
        refreshDeviceLocationReadiness()

        if (_gpsStatus.value == GpsReadinessStatus.DISABLED) {
            _actionFeedback.value = UiText.StringResource(R.string.error_gps_required_for_online)
            return
        }

        val perm = _permissionStatus.value
        if (!perm.hasLocationAccess) {
            _pendingGoOnlineAfterPermission.value = true
            if (perm == LocationPermissionStatus.PERMANENTLY_DENIED) {
                _actionFeedback.value = UiText.StringResource(R.string.permission_permanently_denied_banner)
                _events.tryEmit(HomeUiEvent.OpenAppSettings)
            } else {
                _showPermissionEducation.value = true
            }
            return
        }

        val snapshot = _currentLocation.value ?: locationProvider.getCurrentLocation()?.also {
            _currentLocation.value = it
        }
        if (snapshot == null) {
            _actionFeedback.value = UiText.StringResource(R.string.error_location_unavailable_for_online)
            return
        }

        _pendingDutyTransition.value = DutyControlState.TURNING_ONLINE
        try {
            when (val eligibilityRes = dutyLocationRepository.checkEligibility()) {
                is ApiResult.Success -> {
                    if (!eligibilityRes.data.isEligibleForOnline) {
                        _localBlockedOverride.value = true
                        _actionFeedback.value = UiText.StringResource(R.string.duty_blocked_msg)
                        return
                    }
                    _localBlockedOverride.value = false
                }
                is ApiResult.BackendNotDeployed -> {
                    _actionFeedback.value = UiText.DynamicString(eligibilityRes.reason)
                    return
                }
                is ApiResult.HttpError -> {
                    _actionFeedback.value = UiText.DynamicString(eligibilityRes.error.message)
                    return
                }
                is ApiResult.NetworkError -> {
                    _actionFeedback.value = UiText.StringResource(R.string.duty_change_not_confirmed)
                    return
                }
            }

            when (val onlineRes = dutyLocationRepository.requestGoOnline(snapshot)) {
                is ApiResult.Success -> {
                    if (onlineRes.data == DutyControlState.ONLINE) {
                        trackerController.startTracking()
                        dutyLocationRepository.submitLocationPing(snapshot)
                        _actionFeedback.value = null
                    } else {
                        trackerController.stopTracking()
                    }
                }
                is ApiResult.BackendNotDeployed -> {
                    trackerController.stopTracking()
                    _actionFeedback.value = UiText.DynamicString(onlineRes.reason)
                }
                is ApiResult.HttpError -> {
                    trackerController.stopTracking()
                    if (onlineRes.httpStatus == 403) {
                        _localBlockedOverride.value = true
                    }
                    _actionFeedback.value = UiText.DynamicString(onlineRes.error.message)
                }
                is ApiResult.NetworkError -> {
                    trackerController.stopTracking()
                    _actionFeedback.value = UiText.StringResource(R.string.duty_change_not_confirmed)
                }
            }
        } finally {
            _pendingDutyTransition.value = null
        }
    }

    fun onRequestLocationFromBanner() {
        val currentPerm = locationProvider.checkPermissionStatus(_hasPromptedPermission.value)
        _permissionStatus.value = currentPerm
        if (currentPerm == LocationPermissionStatus.PERMANENTLY_DENIED) {
            _events.tryEmit(HomeUiEvent.OpenAppSettings)
        } else {
            _showPermissionEducation.value = true
        }
    }

    fun onProceedFromPermissionEducation() {
        _showPermissionEducation.value = false
        _hasPromptedPermission.value = true
        _events.tryEmit(HomeUiEvent.RequestLocationPermissions)
    }

    fun onDismissPermissionEducation() {
        _showPermissionEducation.value = false
        _pendingGoOnlineAfterPermission.value = false
        _actionFeedback.value = UiText.StringResource(R.string.error_location_permission_required)
    }

    fun onLocationPermissionResult(
        fineGranted: Boolean,
        coarseGranted: Boolean,
        canShowRationaleAgain: Boolean
    ) {
        val resolved = when {
            fineGranted -> LocationPermissionStatus.GRANTED_FINE
            coarseGranted -> LocationPermissionStatus.GRANTED_APPROXIMATE
            !canShowRationaleAgain -> LocationPermissionStatus.PERMANENTLY_DENIED
            else -> LocationPermissionStatus.DENIED_CAN_ASK
        }
        _permissionStatus.value = resolved

        viewModelScope.launch {
            if (resolved.hasLocationAccess) {
                refreshDeviceLocationReadiness()
                if (_pendingGoOnlineAfterPermission.value) {
                    _pendingGoOnlineAfterPermission.value = false
                    performGoOnline()
                }
            } else {
                _pendingGoOnlineAfterPermission.value = false
                _actionFeedback.value = if (resolved == LocationPermissionStatus.PERMANENTLY_DENIED) {
                    UiText.StringResource(R.string.permission_permanently_denied_banner)
                } else {
                    UiText.StringResource(R.string.permission_denied_banner)
                }
            }
        }
    }

    fun onOpenLocationSettingsClick() {
        _events.tryEmit(HomeUiEvent.OpenLocationSettings)
    }

    fun onRefreshLocationClick() {
        viewModelScope.launch {
            refreshDeviceLocationReadiness()
            val loc = _currentLocation.value
            if (loc != null && dutyLocationRepository.dutyControlState.value.isOnline) {
                dutyLocationRepository.submitLocationPing(loc)
            }
        }
    }

    fun onSaveServicePreferences(
        preferredZoneIds: List<String>,
        autoBoostEnabled: Boolean,
        activeServiceType: ServiceType
    ) {
        viewModelScope.launch {
            when (val res = dutyLocationRepository.updatePreferences(
                preferredZoneIds = preferredZoneIds,
                autoBoostEnabled = autoBoostEnabled,
                activeServiceType = activeServiceType
            )) {
                is ApiResult.Success -> {
                    _actionFeedback.value = UiText.StringResource(R.string.prefs_save_success)
                }
                is ApiResult.BackendNotDeployed -> {
                    _actionFeedback.value = UiText.DynamicString(res.reason)
                }
                is ApiResult.HttpError -> {
                    _actionFeedback.value = UiText.DynamicString(res.error.message)
                }
                is ApiResult.NetworkError -> {
                    _actionFeedback.value = UiText.DynamicString(res.causeMessage)
                }
            }
        }
    }

    fun onToggleDemoEligibility(isEligible: Boolean) {
        dutyLocationRepository.setDemoEligibilityOverride(isEligible)
        _localBlockedOverride.value = !isEligible
        if (!isEligible) {
            trackerController.stopTracking()
        }
    }

    fun clearFeedback() {
        _actionFeedback.value = null
    }

    private suspend fun refreshDeviceLocationReadiness() {
        val perm = locationProvider.checkPermissionStatus(_hasPromptedPermission.value)
        _permissionStatus.value = perm
        val gpsReady = locationProvider.isGpsEnabled()
        _gpsStatus.value = if (gpsReady) GpsReadinessStatus.READY else GpsReadinessStatus.DISABLED

        if (perm.hasLocationAccess && gpsReady) {
            val snapshot = locationProvider.getCurrentLocation()
            if (snapshot != null) {
                _currentLocation.value = snapshot
            }
        }
    }

    private data class DomainSnapshot(
        val profile: PartnerProfile?,
        val boundaryMsg: String?,
        val dutyState: DutyControlState,
        val summary: HomeDashboardSummary?,
        val eligibility: DriverEligibility?
    )

    private data class PrefsSnapshot(
        val preferences: DriverPreferences?,
        val zones: List<ServiceZone>,
        val repoError: String?,
        val isTracking: Boolean
    )

    private data class DeviceSnapshot(
        val permission: LocationPermissionStatus,
        val gps: GpsReadinessStatus,
        val location: DriverLocationSnapshot?,
        val showEducation: Boolean,
        val pendingDuty: DutyControlState?
    )

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    partnerRepository = appContainer.partnerRepository,
                    dutyLocationRepository = appContainer.dutyLocationRepository,
                    locationProvider = appContainer.locationProvider,
                    trackerController = appContainer.dutyLocationTrackerController,
                    environmentConfig = appContainer.environmentConfig
                )
            }
        }
    }
}
