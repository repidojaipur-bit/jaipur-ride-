package com.drivepartner.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.di.AppContainer
import com.drivepartner.app.core.location.DriverLocationProvider
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.realtime.RetentionSafetyIdempotencyTracker
import com.drivepartner.app.data.model.EmergencyContact
import com.drivepartner.app.data.model.InsuranceStatus
import com.drivepartner.app.data.model.RetentionSafetyOutcomeNotice
import com.drivepartner.app.data.model.SosAlertState
import com.drivepartner.app.data.remote.dto.EmergencyContactRequest
import com.drivepartner.app.data.remote.dto.IncidentReportRequest
import com.drivepartner.app.data.remote.dto.SosStatus
import com.drivepartner.app.data.remote.dto.SosTriggerRequest
import com.drivepartner.app.data.repository.DemoRetentionSafetyRepository
import com.drivepartner.app.data.repository.RetentionSafetyRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SafetyUiState(
    val dataOrigin: DataOrigin = DataOrigin.LIVE,
    val emergencyContacts: List<EmergencyContact> = emptyList(),
    val latestSosAlert: SosAlertState? = null,
    val insuranceStatus: InsuranceStatus? = null,
    val isRefreshing: Boolean = false,
    val isSendingSos: Boolean = false,
    val isAddingContact: Boolean = false,
    val isSubmittingIncident: Boolean = false,
    val demoSosResponderEnabled: Boolean = false,
    val outcomeNotice: RetentionSafetyOutcomeNotice? = null,
    val boundaryMessage: String? = null
)

/**
 * Phase 7 Safety Center ViewModel:
 * - Emergency Contacts
 * - SOS Workflow (strictly never shows SOS contacted/acknowledged unless server returns ACKNOWLEDGED)
 * - Incident Reporting
 * - Insurance Information (never claims unverified insurance coverage)
 */
class SafetyViewModel(
    private val retentionSafetyRepository: RetentionSafetyRepository,
    private val locationProvider: DriverLocationProvider? = null,
    private val idempotencyTracker: RetentionSafetyIdempotencyTracker = RetentionSafetyIdempotencyTracker()
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    private val _isSendingSos = MutableStateFlow(false)
    private val _isAddingContact = MutableStateFlow(false)
    private val _isSubmittingIncident = MutableStateFlow(false)
    private val _demoSosResponderEnabled = MutableStateFlow(false)
    private val _outcomeNotice = MutableStateFlow<RetentionSafetyOutcomeNotice?>(null)

    private val repoFlows = combine(
        retentionSafetyRepository.emergencyContacts,
        retentionSafetyRepository.latestSosAlert,
        retentionSafetyRepository.insuranceStatus,
        retentionSafetyRepository.lastErrorMessage
    ) { contacts, sosAlert, insurance, errorMsg ->
        SafetyRepoSnapshot(contacts, sosAlert, insurance, errorMsg)
    }

    private val loadingFlows = combine(
        _isRefreshing,
        _isSendingSos,
        _isAddingContact,
        _isSubmittingIncident,
        _demoSosResponderEnabled
    ) { refreshing, sendingSos, addingContact, submittingIncident, demoSos ->
        SafetyControlSnapshot(refreshing, sendingSos, addingContact, submittingIncident, demoSos)
    }

    val uiState: StateFlow<SafetyUiState> = combine(
        repoFlows,
        loadingFlows,
        _outcomeNotice
    ) { repoSnapshot, controls, notice ->
        SafetyUiState(
            dataOrigin = retentionSafetyRepository.origin,
            emergencyContacts = repoSnapshot.contacts,
            latestSosAlert = repoSnapshot.sosAlert,
            insuranceStatus = repoSnapshot.insurance,
            isRefreshing = controls.isRefreshing,
            isSendingSos = controls.isSendingSos,
            isAddingContact = controls.isAddingContact,
            isSubmittingIncident = controls.isSubmittingIncident,
            demoSosResponderEnabled = controls.demoSosResponderEnabled,
            outcomeNotice = notice,
            boundaryMessage = repoSnapshot.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SafetyUiState(dataOrigin = retentionSafetyRepository.origin)
    )

    init {
        refreshSafetyData()
    }

    fun refreshSafetyData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                listOf(
                    async { retentionSafetyRepository.fetchEmergencyContacts() },
                    async { retentionSafetyRepository.fetchInsuranceStatus() }
                ).awaitAll()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun addEmergencyContact(name: String, phoneNumber: String, relationship: String) {
        if (_isAddingContact.value) return
        val digits = phoneNumber.filter { it.isDigit() }
        val last4 = digits.takeLast(4)
        viewModelScope.launch {
            _isAddingContact.value = true
            val key = idempotencyTracker.getOrCreateContactKey(last4, name)
            try {
                val request = EmergencyContactRequest(
                    name = name.trim(),
                    phoneNumber = digits,
                    relationship = relationship.trim(),
                    idempotencyKey = key
                )
                when (val res = retentionSafetyRepository.addEmergencyContact(request)) {
                    is ApiResult.Success -> {
                        idempotencyTracker.clearContactKey(last4, name)
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.SuccessMessage(
                            "Emergency contact saved (${res.data.phoneMasked})."
                        )
                    }
                    is ApiResult.HttpError -> {
                        if (res.httpStatus in listOf(403, 409, 422)) {
                            idempotencyTracker.clearContactKey(last4, name)
                        }
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.Rejected(res.error.message)
                    }
                    is ApiResult.NetworkError -> {
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.NetworkFailure(res.causeMessage)
                    }
                    is ApiResult.BackendNotDeployed -> {
                        idempotencyTracker.clearContactKey(last4, name)
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.BackendUnavailable(res.reason)
                    }
                }
            } finally {
                _isAddingContact.value = false
            }
        }
    }

    fun triggerSos(activeTripId: String? = null) {
        if (_isSendingSos.value) return
        viewModelScope.launch {
            _isSendingSos.value = true
            val key = idempotencyTracker.getOrCreateSosKey(activeTripId)
            try {
                val snapshot = if (locationProvider?.checkPermissionStatus()?.hasLocationAccess == true) {
                    locationProvider.getCurrentLocation()
                } else {
                    null
                }
                val req = SosTriggerRequest(
                    latitude = snapshot?.latitude ?: 0.0,
                    longitude = snapshot?.longitude ?: 0.0,
                    activeTripId = activeTripId,
                    idempotencyKey = key
                )
                when (val res = retentionSafetyRepository.triggerSos(req)) {
                    is ApiResult.Success -> {
                        if (res.data.status == SosStatus.ACKNOWLEDGED && res.data.alertId.isNotBlank()) {
                            idempotencyTracker.clearSosKey(activeTripId)
                            _outcomeNotice.value = RetentionSafetyOutcomeNotice.SuccessMessage(res.data.message)
                        } else {
                            _outcomeNotice.value = RetentionSafetyOutcomeNotice.Rejected(res.data.message)
                        }
                    }
                    is ApiResult.HttpError -> {
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.Rejected(res.error.message)
                    }
                    is ApiResult.NetworkError -> {
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.NetworkFailure(res.causeMessage)
                    }
                    is ApiResult.BackendNotDeployed -> {
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.BackendUnavailable(res.reason)
                    }
                }
            } finally {
                _isSendingSos.value = false
            }
        }
    }

    fun submitIncidentReport(category: String, description: String, relatedTripId: String? = null) {
        if (_isSubmittingIncident.value) return
        val cleanDesc = description.trim()
        val hash = cleanDesc.hashCode()
        viewModelScope.launch {
            _isSubmittingIncident.value = true
            val key = idempotencyTracker.getOrCreateIncidentKey(category, hash)
            try {
                val req = IncidentReportRequest(
                    category = category,
                    description = cleanDesc,
                    relatedTripId = relatedTripId,
                    idempotencyKey = key
                )
                when (val res = retentionSafetyRepository.submitIncidentReport(req)) {
                    is ApiResult.Success -> {
                        idempotencyTracker.clearIncidentKey(category, hash)
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.SuccessMessage(
                            "Incident logged (${res.data.incidentId})."
                        )
                    }
                    is ApiResult.HttpError -> {
                        if (res.httpStatus in listOf(403, 409, 422)) {
                            idempotencyTracker.clearIncidentKey(category, hash)
                        }
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.Rejected(res.error.message)
                    }
                    is ApiResult.NetworkError -> {
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.NetworkFailure(res.causeMessage)
                    }
                    is ApiResult.BackendNotDeployed -> {
                        idempotencyTracker.clearIncidentKey(category, hash)
                        _outcomeNotice.value = RetentionSafetyOutcomeNotice.BackendUnavailable(res.reason)
                    }
                }
            } finally {
                _isSubmittingIncident.value = false
            }
        }
    }

    fun dismissOutcomeNotice() {
        _outcomeNotice.value = null
    }

    // --- Isolated DEMO Sandbox Helpers (never available in LIVE builds) ---

    fun toggleDemoSosResponder(enabled: Boolean) {
        val demoRepo = retentionSafetyRepository as? DemoRetentionSafetyRepository ?: return
        _demoSosResponderEnabled.value = enabled
        viewModelScope.launch {
            demoRepo.toggleDemoSosResponder(enabled)
        }
    }

    fun toggleDemoInsurancePreview(confirmed: Boolean) {
        val demoRepo = retentionSafetyRepository as? DemoRetentionSafetyRepository ?: return
        viewModelScope.launch {
            demoRepo.toggleDemoInsurancePreview(confirmed)
        }
    }

    private data class SafetyRepoSnapshot(
        val contacts: List<EmergencyContact>,
        val sosAlert: SosAlertState?,
        val insurance: InsuranceStatus?,
        val errorMessage: String?
    )

    private data class SafetyControlSnapshot(
        val isRefreshing: Boolean,
        val isSendingSos: Boolean,
        val isAddingContact: Boolean,
        val isSubmittingIncident: Boolean,
        val demoSosResponderEnabled: Boolean
    )

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SafetyViewModel(
                    retentionSafetyRepository = appContainer.retentionSafetyRepository,
                    locationProvider = appContainer.locationProvider
                )
            }
        }
    }
}
