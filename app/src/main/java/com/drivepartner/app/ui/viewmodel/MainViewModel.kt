package com.drivepartner.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drivepartner.app.core.di.AppContainer
import com.drivepartner.app.core.location.DriverLocationProvider
import com.drivepartner.app.core.location.DutyLocationTrackerController
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.local.AppLanguage
import com.drivepartner.app.data.local.LanguagePreferencesRepository
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.ThemePreference
import com.drivepartner.app.data.repository.DutyLocationRepository
import com.drivepartner.app.data.repository.PartnerRepository
import com.drivepartner.app.data.repository.PartnerRepositoryImpl
import com.drivepartner.app.data.repository.RetentionSafetyRepository
import com.drivepartner.app.navigation.DriveDestination
import com.drivepartner.app.ui.components.state.DriveUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MainViewModel - Single source of truth for the Single Activity Drive Partner shell.
 * Enforces Phase 3 permission/GPS/eligibility/server confirmation checks even when duty is toggled
 * from the persistent top app bar.
 */
class MainViewModel(
    private val repository: PartnerRepository = PartnerRepositoryImpl(),
    private val languagePreferencesRepository: LanguagePreferencesRepository? = null,
    private val dutyLocationRepository: DutyLocationRepository? = null,
    private val locationProvider: DriverLocationProvider? = null,
    private val trackerController: DutyLocationTrackerController? = null,
    private val retentionSafetyRepository: RetentionSafetyRepository? = null
) : ViewModel() {

    private val _currentDestination = MutableStateFlow(DriveDestination.Home.route)
    private val _uiState = MutableStateFlow<DriveUiState>(DriveUiState.Success)
    private val _fallbackLanguage = MutableStateFlow(AppLanguage.ENGLISH)
    private val _fallbackTheme = MutableStateFlow(ThemePreference.LIGHT)
    private val _notificationBadge = MutableStateFlow(false)
    private val _unreadNotificationCount = MutableStateFlow(0)
    private val _dutyNoticeOverride = MutableStateFlow<String?>(null)
    private val _isDutyTransitionPending = MutableStateFlow(false)

    init {
        if (retentionSafetyRepository != null) {
            viewModelScope.launch {
                retentionSafetyRepository.unreadNotificationCount.collect { count ->
                    _unreadNotificationCount.value = count
                    _notificationBadge.value = count > 0
                }
            }
            viewModelScope.launch {
                retentionSafetyRepository.fetchNotifications()
            }
        }
    }

    private val languageFlow: Flow<AppLanguage> =
        languagePreferencesRepository?.preferredLanguage ?: _fallbackLanguage

    private val themeFlow: Flow<ThemePreference> =
        languagePreferencesRepository?.themePreference ?: _fallbackTheme

    private val dutyStateFlow: Flow<DutyControlState> =
        dutyLocationRepository?.dutyControlState ?: MutableStateFlow(DutyControlState.OFFLINE)

    private val repositoryFlows = combine(
        repository.partnerStatus,
        repository.partnerProfile,
        repository.boundaryMessage,
        dutyStateFlow
    ) { status, profile, boundaryMsg, dutyControl ->
        RepoSnapshot(status, profile, boundaryMsg, dutyControl)
    }

    private val uiControlFlows = combine(
        _currentDestination,
        _uiState,
        languageFlow,
        _notificationBadge,
        _dutyNoticeOverride
    ) { destination, uiState, language, hasNotification, dutyNotice ->
        UiControlsSnapshot(destination, uiState, language, hasNotification, dutyNotice)
    }

    /** Live unread notification count behind the top-bar badge (spec section 4). */
    val unreadNotificationCount: StateFlow<Int> = _unreadNotificationCount.asStateFlow()

    val mainUiState: StateFlow<MainUiState> = combine(
        repositoryFlows,
        uiControlFlows,
        _isDutyTransitionPending,
        themeFlow
    ) { repoSnapshot: RepoSnapshot, uiSnapshot: UiControlsSnapshot, pending: Boolean, themePref: ThemePreference ->
        val resolvedDutyState = when {
            pending && repoSnapshot.status.isOnline -> DutyControlState.TURNING_OFFLINE
            pending && !repoSnapshot.status.isOnline -> DutyControlState.TURNING_ONLINE
            dutyLocationRepository != null -> repoSnapshot.dutyControl
            repoSnapshot.status.isOnline -> DutyControlState.ONLINE
            else -> DutyControlState.OFFLINE
        }
        MainUiState(
            dataOrigin = repository.dataOrigin,
            partnerStatus = repoSnapshot.status,
            dutyControlState = resolvedDutyState,
            partnerProfile = repoSnapshot.profile,
            currentDestination = uiSnapshot.destination,
            uiState = uiSnapshot.uiState,
            selectedService = repoSnapshot.profile?.serviceType ?: ServiceType.BIKE_TAXI,
            isNotificationBadgeActive = uiSnapshot.hasNotification,
            preferredLanguage = uiSnapshot.language,
            themePreference = themePref,
            boundaryMessage = uiSnapshot.dutyNotice ?: repoSnapshot.boundaryMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState(dataOrigin = repository.dataOrigin)
    )

    fun toggleDutyStatus() {
        val currentOnline = mainUiState.value.partnerStatus.isOnline
        setDutyStatus(!currentOnline)
    }

    fun setDutyStatus(isOnline: Boolean) {
        if (_isDutyTransitionPending.value) return

        viewModelScope.launch {
            if (dutyLocationRepository == null) {
                repository.setDutyStatus(isOnline)
                return@launch
            }

            _isDutyTransitionPending.value = true
            try {
                if (!isOnline) {
                    val result = dutyLocationRepository.requestGoOffline()
                    if (result is ApiResult.Success && result.data == DutyControlState.OFFLINE) {
                        trackerController?.stopTracking()
                        _dutyNoticeOverride.value = null
                    } else {
                        _dutyNoticeOverride.value = dutyLocationRepository.lastErrorMessage.value
                    }
                    return@launch
                }

                if (locationProvider != null) {
                    if (!locationProvider.isGpsEnabled()) {
                        _dutyNoticeOverride.value = "Turn on device GPS before going Online."
                        return@launch
                    }
                    if (!locationProvider.checkPermissionStatus().hasLocationAccess) {
                        _dutyNoticeOverride.value = "Grant location permission from Home before going Online."
                        return@launch
                    }
                }

                when (val elig = dutyLocationRepository.checkEligibility()) {
                    is ApiResult.Success -> {
                        if (!elig.data.isEligibleForOnline) {
                            _dutyNoticeOverride.value = "Driver eligibility requirements are incomplete."
                            return@launch
                        }
                    }
                    is ApiResult.BackendNotDeployed -> {
                        _dutyNoticeOverride.value = elig.reason
                        return@launch
                    }
                    is ApiResult.HttpError -> {
                        _dutyNoticeOverride.value = elig.error.message
                        return@launch
                    }
                    is ApiResult.NetworkError -> {
                        _dutyNoticeOverride.value = elig.causeMessage
                        return@launch
                    }
                }

                val snapshot = locationProvider?.getCurrentLocation()
                val goOnlineResult = dutyLocationRepository.requestGoOnline(snapshot)
                if (goOnlineResult is ApiResult.Success && goOnlineResult.data == DutyControlState.ONLINE) {
                    trackerController?.startTracking()
                    if (snapshot != null) {
                        dutyLocationRepository.submitLocationPing(snapshot)
                    }
                    _dutyNoticeOverride.value = null
                } else {
                    trackerController?.stopTracking()
                    _dutyNoticeOverride.value = dutyLocationRepository.lastErrorMessage.value
                }
            } finally {
                _isDutyTransitionPending.value = false
            }
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

    fun toggleLanguage(onLanguageSelected: (AppLanguage) -> Unit = {}) {
        val next = mainUiState.value.preferredLanguage.toggle()
        setLanguage(next, onLanguageSelected)
    }

    fun setLanguage(language: AppLanguage, onLanguageSelected: (AppLanguage) -> Unit = {}) {
        _fallbackLanguage.value = language
        viewModelScope.launch {
            languagePreferencesRepository?.setPreferredLanguage(language)
        }
        onLanguageSelected(language)
    }

    fun setThemePreference(theme: ThemePreference) {
        _fallbackTheme.value = theme
        viewModelScope.launch {
            languagePreferencesRepository?.setThemePreference(theme)
        }
    }

    fun refreshProfile() {
        viewModelScope.launch {
            _uiState.value = DriveUiState.Loading
            repository.refreshProfile()
            _uiState.value = DriveUiState.Success
        }
    }

    fun setUiState(state: DriveUiState) {
        _uiState.update { state }
    }

    private data class RepoSnapshot(
        val status: PartnerStatus,
        val profile: PartnerProfile?,
        val boundaryMsg: String?,
        val dutyControl: DutyControlState
    )

    private data class UiControlsSnapshot(
        val destination: String,
        val uiState: DriveUiState,
        val language: AppLanguage,
        val hasNotification: Boolean,
        val dutyNotice: String?
    )

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MainViewModel(
                    repository = appContainer.partnerRepository,
                    languagePreferencesRepository = appContainer.languagePreferencesRepository,
                    dutyLocationRepository = appContainer.dutyLocationRepository,
                    locationProvider = appContainer.locationProvider,
                    trackerController = appContainer.dutyLocationTrackerController,
                    retentionSafetyRepository = appContainer.retentionSafetyRepository
                )
            }
        }
    }
}
