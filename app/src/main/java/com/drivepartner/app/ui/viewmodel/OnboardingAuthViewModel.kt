package com.drivepartner.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.di.AppContainer
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.realtime.OnboardingIdempotencyTracker
import com.drivepartner.app.data.local.AppLanguage
import com.drivepartner.app.data.model.AuthSessionState
import com.drivepartner.app.data.model.DriverProfileDetail
import com.drivepartner.app.data.model.KycDocumentItem
import com.drivepartner.app.data.model.OnboardingStage
import com.drivepartner.app.data.model.OnboardingValidators
import com.drivepartner.app.data.model.PayoutAccount
import com.drivepartner.app.data.model.RegistrationStep
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.VehicleInfo
import com.drivepartner.app.data.remote.dto.ConfirmDocumentUploadRequest
import com.drivepartner.app.data.remote.dto.CreatePayoutAccountRequest
import com.drivepartner.app.data.remote.dto.DocumentUploadIntentRequest
import com.drivepartner.app.data.remote.dto.DriverAccountStatus
import com.drivepartner.app.data.remote.dto.DriverProfileRequest
import com.drivepartner.app.data.remote.dto.KycDocumentStatus
import com.drivepartner.app.data.remote.dto.KycDocumentType
import com.drivepartner.app.data.remote.dto.VehicleDetailsRequest
import com.drivepartner.app.data.repository.AuthOnboardingRepository
import com.drivepartner.app.data.repository.FinancialRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingAuthUiState(
    val dataOrigin: DataOrigin = DataOrigin.LIVE,
    val stage: OnboardingStage = OnboardingStage.SPLASH_LANGUAGE,
    val registrationStep: RegistrationStep = RegistrationStep.PERSONAL,
    val isLoading: Boolean = false,
    val phoneInput: String = "",
    val otpInput: String = "",
    val verificationId: String? = null,
    val demoOtpHint: String? = null,
    val resendSecondsRemaining: Int = 0,
    val isOtpSent: Boolean = false,
    val session: AuthSessionState? = null,
    val profile: DriverProfileDetail? = null,
    val vehicles: List<VehicleInfo> = emptyList(),
    val documents: List<KycDocumentItem> = emptyList(),
    val linkedPayoutAccounts: List<PayoutAccount> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    val isAuthenticatedAndActive: Boolean
        get() = stage == OnboardingStage.AUTHENTICATED_ACTIVE && session?.canAccessMainDashboard == true
}

class OnboardingAuthViewModel(
    private val authRepository: AuthOnboardingRepository,
    private val financialRepository: FinancialRepository? = null,
    private val idempotencyTracker: OnboardingIdempotencyTracker = OnboardingIdempotencyTracker()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        OnboardingAuthUiState(
            dataOrigin = authRepository.dataOrigin,
            stage = if (authRepository.dataOrigin == DataOrigin.DEMO) {
                OnboardingStage.AUTHENTICATED_ACTIVE
            } else {
                OnboardingStage.SPLASH_LANGUAGE
            }
        )
    )
    val uiState: StateFlow<OnboardingAuthUiState> = _uiState.asStateFlow()

    private var resendTimerJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.currentSession.collect { session ->
                _uiState.update { state -> state.copy(session = session) }
            }
        }
        viewModelScope.launch {
            authRepository.currentProfile.collect { profile ->
                _uiState.update { state -> state.copy(profile = profile) }
            }
        }
        viewModelScope.launch {
            authRepository.vehicles.collect { vehicles ->
                _uiState.update { state -> state.copy(vehicles = vehicles) }
            }
        }
        viewModelScope.launch {
            authRepository.documents.collect { docs ->
                _uiState.update { state -> state.copy(documents = docs) }
            }
        }
        if (financialRepository != null) {
            viewModelScope.launch {
                financialRepository.payoutAccounts.collect { accounts ->
                    _uiState.update { state -> state.copy(linkedPayoutAccounts = accounts) }
                }
            }
        }
        refreshOnboardingState()
    }

    fun refreshOnboardingState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val sessionRes = authRepository.fetchCurrentSession()
            if (sessionRes is ApiResult.Success) {
                authRepository.fetchProfile()
                authRepository.fetchVehicles()
                authRepository.fetchDocuments()
                financialRepository?.fetchPayoutAccounts()
                val session = sessionRes.data
                val signedOut = authRepository.isSignedOut.value
                val nextStage = resolveStage(session = session, isSignedOut = signedOut)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        session = session,
                        stage = nextStage,
                        errorMessage = null
                    )
                }
            } else {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        stage = if (state.dataOrigin == DataOrigin.DEMO && !authRepository.isSignedOut.value) {
                            OnboardingStage.AUTHENTICATED_ACTIVE
                        } else if (state.stage == OnboardingStage.AUTHENTICATED_ACTIVE) {
                            OnboardingStage.PHONE_LOGIN
                        } else {
                            state.stage
                        }
                    )
                }
            }
        }
    }

    fun continueFromSplash() {
        val current = _uiState.value
        val next = resolveStage(
            session = current.session,
            isSignedOut = authRepository.isSignedOut.value || current.session == null
        ).let { resolved ->
            if (resolved == OnboardingStage.SPLASH_LANGUAGE) OnboardingStage.PHONE_LOGIN else resolved
        }
        _uiState.update { it.copy(stage = next, errorMessage = null) }
    }

    fun startOnboardingPreview() {
        authRepository.enterDemoOnboardingPreview()
        _uiState.update {
            it.copy(
                stage = OnboardingStage.SPLASH_LANGUAGE,
                registrationStep = RegistrationStep.PERSONAL,
                isOtpSent = false,
                otpInput = "",
                verificationId = null,
                demoOtpHint = null,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun updatePhoneInput(phone: String) {
        val digits = phone.filter { it.isDigit() }.take(10)
        _uiState.update { it.copy(phoneInput = digits, errorMessage = null) }
    }

    fun updateOtpInput(otp: String) {
        val digits = otp.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(otpInput = digits, errorMessage = null) }
    }

    fun editPhoneNumber() {
        resendTimerJob?.cancel()
        _uiState.update {
            it.copy(
                isOtpSent = false,
                otpInput = "",
                verificationId = null,
                resendSecondsRemaining = 0,
                errorMessage = null
            )
        }
    }

    fun requestOtp() {
        val phone = _uiState.value.phoneInput
        if (!OnboardingValidators.isValidIndianPhone(phone)) {
            _uiState.update {
                it.copy(errorMessage = "Enter a valid 10-digit Indian mobile number starting with 6–9.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            when (val res = authRepository.requestOtp(phone)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isOtpSent = true,
                            verificationId = res.data.verificationId,
                            demoOtpHint = res.data.demoOtpHint,
                            resendSecondsRemaining = res.data.resendAfterSeconds,
                            errorMessage = null
                        )
                    }
                    startResendCountdown(res.data.resendAfterSeconds)
                }
                is ApiResult.HttpError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.error.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.causeMessage)
                }
                is ApiResult.BackendNotDeployed -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.reason)
                }
            }
        }
    }

    fun verifyOtp() {
        val state = _uiState.value
        val verifId = state.verificationId
        if (verifId.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = "Request an OTP first.") }
            return
        }
        if (!OnboardingValidators.isValidOtp(state.otpInput)) {
            _uiState.update { it.copy(errorMessage = "Enter a valid 6-digit OTP.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = authRepository.verifyOtpAndEstablishSession(verifId, state.otpInput)) {
                is ApiResult.Success -> {
                    resendTimerJob?.cancel()
                    authRepository.fetchProfile()
                    authRepository.fetchVehicles()
                    authRepository.fetchDocuments()
                    financialRepository?.fetchPayoutAccounts()
                    val session = res.data
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            session = session,
                            stage = resolveStage(session, isSignedOut = false),
                            registrationStep = firstIncompleteStep(session),
                            errorMessage = null
                        )
                    }
                }
                is ApiResult.HttpError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.error.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.causeMessage)
                }
                is ApiResult.BackendNotDeployed -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.reason)
                }
            }
        }
    }

    fun selectRegistrationStep(step: RegistrationStep) {
        _uiState.update { it.copy(registrationStep = step, errorMessage = null, successMessage = null) }
    }

    fun openDocumentsFixStep() {
        _uiState.update {
            it.copy(
                stage = OnboardingStage.REGISTRATION_WIZARD,
                registrationStep = RegistrationStep.DOCUMENTS,
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun savePersonalDetails(
        fullName: String,
        cityCode: String,
        language: AppLanguage,
        dateOfBirth: String,
        gender: String?,
        addressLine: String,
        stateCode: String,
        pinCode: String,
        advanceStep: Boolean = true
    ) {
        if (fullName.trim().length < 2) {
            _uiState.update { it.copy(errorMessage = "Full name must be at least 2 characters.") }
            return
        }
        if (cityCode.trim().isBlank()) {
            _uiState.update { it.copy(errorMessage = "City code/name is required.") }
            return
        }
        if (pinCode.isNotBlank() && !OnboardingValidators.isValidPinCode(pinCode)) {
            _uiState.update { it.copy(errorMessage = "Enter a valid 6-digit Indian PIN code.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            val key = idempotencyTracker.getOrCreateKey("profile")
            val req = DriverProfileRequest(
                fullName = fullName.trim(),
                cityCode = cityCode.trim().uppercase(),
                preferredLanguage = language.bcp47Tag,
                dateOfBirth = dateOfBirth.trim().takeIf { it.isNotEmpty() },
                gender = gender?.trim()?.takeIf { it.isNotEmpty() },
                addressLine = addressLine.trim().takeIf { it.isNotEmpty() },
                stateCode = stateCode.trim().uppercase().takeIf { it.isNotEmpty() },
                pinCode = pinCode.trim().takeIf { it.isNotEmpty() }
            )
            when (val res = authRepository.saveProfile(req, key)) {
                is ApiResult.Success -> {
                    idempotencyTracker.clearKey("profile")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            profile = res.data,
                            registrationStep = if (advanceStep) RegistrationStep.VEHICLE else it.registrationStep,
                            successMessage = "Personal details saved.",
                            errorMessage = null
                        )
                    }
                }
                is ApiResult.HttpError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.error.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.causeMessage)
                }
                is ApiResult.BackendNotDeployed -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.reason)
                }
            }
        }
    }

    fun saveVehicleDetails(
        serviceType: ServiceType,
        registrationNumber: String,
        brand: String,
        model: String,
        color: String,
        yearText: String,
        advanceStep: Boolean = true
    ) {
        val normalized = OnboardingValidators.normalizeVehicleRegistration(registrationNumber)
        if (!OnboardingValidators.isValidVehicleRegistration(normalized)) {
            _uiState.update {
                it.copy(errorMessage = "Enter a valid Indian vehicle number (e.g. RJ14AB1234).")
            }
            return
        }
        if (model.trim().length < 2) {
            _uiState.update { it.copy(errorMessage = "Enter vehicle model name.") }
            return
        }
        val parsedYear = yearText.trim().toIntOrNull()
        if (yearText.isNotBlank() && (parsedYear == null || parsedYear < 1995 || parsedYear > 2030)) {
            _uiState.update { it.copy(errorMessage = "Enter a valid vehicle manufacturing year.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            val key = idempotencyTracker.getOrCreateKey("vehicle:$normalized")
            val req = VehicleDetailsRequest(
                serviceTypeCode = serviceType.code,
                registrationNumber = normalized,
                vehicleModel = model.trim(),
                brand = brand.trim(),
                color = color.trim(),
                year = parsedYear
            )
            when (val res = authRepository.saveVehicle(req, key)) {
                is ApiResult.Success -> {
                    idempotencyTracker.clearKey("vehicle:$normalized")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            registrationStep = if (advanceStep) RegistrationStep.BANK else it.registrationStep,
                            successMessage = "Vehicle details saved.",
                            errorMessage = null
                        )
                    }
                }
                is ApiResult.HttpError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.error.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.causeMessage)
                }
                is ApiResult.BackendNotDeployed -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.reason)
                }
            }
        }
    }

    fun saveBankAccountStep(
        accountHolderName: String,
        accountNumber: String,
        ifscCode: String,
        advanceStep: Boolean = true
    ) {
        if (accountHolderName.trim().length < 2) {
            _uiState.update { it.copy(errorMessage = "Enter bank account holder name.") }
            return
        }
        if (!OnboardingValidators.isValidBankAccount(accountNumber)) {
            _uiState.update { it.copy(errorMessage = "Enter a valid 9–18 digit bank account number.") }
            return
        }
        if (!OnboardingValidators.isValidIfsc(ifscCode)) {
            _uiState.update { it.copy(errorMessage = "Enter a valid 11-character IFSC code (e.g. HDFC0001234).") }
            return
        }
        val repo = financialRepository
        if (repo == null) {
            _uiState.update {
                it.copy(
                    registrationStep = if (advanceStep) RegistrationStep.DOCUMENTS else it.registrationStep,
                    errorMessage = "Bank linking service is unavailable."
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            val key = idempotencyTracker.getOrCreateKey("bank:${ifscCode.uppercase()}")
            when (
                val res = repo.addPayoutAccount(
                    CreatePayoutAccountRequest(
                        accountHolderName = accountHolderName.trim(),
                        accountNumber = accountNumber.trim(),
                        ifscCode = ifscCode.trim().uppercase(),
                        idempotencyKey = key
                    )
                )
            ) {
                is ApiResult.Success -> {
                    idempotencyTracker.clearKey("bank:${ifscCode.uppercase()}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            registrationStep = if (advanceStep) RegistrationStep.DOCUMENTS else it.registrationStep,
                            successMessage = "Bank payout account tokenized & submitted.",
                            errorMessage = null
                        )
                    }
                }
                is ApiResult.HttpError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.error.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.causeMessage)
                }
                is ApiResult.BackendNotDeployed -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        registrationStep = if (advanceStep) RegistrationStep.DOCUMENTS else it.registrationStep,
                        errorMessage = res.reason
                    )
                }
            }
        }
    }

    fun uploadAndRegisterDocument(
        documentType: KycDocumentType,
        mimeType: String = "image/jpeg",
        sizeBytes: Long = 245_000L,
        maskedReference: String? = null,
        expiresOnUtc: String? = null
    ) {
        if (!maskedReference.isNullOrBlank() && OnboardingValidators.containsRawAadhaarDigits(maskedReference)) {
            _uiState.update {
                it.copy(errorMessage = "Never enter a full 12-digit Aadhaar number. Use a verified reference or last-4 token.")
            }
            return
        }
        if (documentType == KycDocumentType.PAN_CARD &&
            !maskedReference.isNullOrBlank() &&
            !OnboardingValidators.isValidPanReference(maskedReference)
        ) {
            _uiState.update {
                it.copy(errorMessage = "Enter a valid PAN format (e.g. ABCDE1234F) or leave blank.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }
            val intentRes = authRepository.requestUploadIntent(
                DocumentUploadIntentRequest(
                    documentType = documentType,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes
                )
            )
            when (intentRes) {
                is ApiResult.Success -> {
                    val intent = intentRes.data
                    val objectKey = intent.objectKey.ifBlank {
                        "private/kyc/${documentType.name.lowercase()}-${intent.documentId}"
                    }
                    val opKey = idempotencyTracker.getOrCreateKey("doc:${documentType.name}")
                    val confirmRes = authRepository.registerDocument(
                        request = ConfirmDocumentUploadRequest(
                            documentId = intent.documentId,
                            storageObjectKey = objectKey,
                            expiresOnUtc = expiresOnUtc?.trim()?.takeIf { it.isNotEmpty() },
                            maskedReference = maskedReference?.trim()?.takeIf { it.isNotEmpty() }
                        ),
                        idempotencyKey = opKey
                    )
                    when (confirmRes) {
                        is ApiResult.Success -> {
                            idempotencyTracker.clearKey("doc:${documentType.name}")
                            authRepository.fetchCurrentSession()
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    successMessage = "Document uploaded securely for review.",
                                    errorMessage = null
                                )
                            }
                        }
                        is ApiResult.HttpError -> _uiState.update {
                            it.copy(isLoading = false, errorMessage = confirmRes.error.message)
                        }
                        is ApiResult.NetworkError -> _uiState.update {
                            it.copy(isLoading = false, errorMessage = confirmRes.causeMessage)
                        }
                        is ApiResult.BackendNotDeployed -> _uiState.update {
                            it.copy(isLoading = false, errorMessage = confirmRes.reason)
                        }
                    }
                }
                is ApiResult.HttpError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = intentRes.error.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = intentRes.causeMessage)
                }
                is ApiResult.BackendNotDeployed -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = intentRes.reason)
                }
            }
        }
    }

    fun submitForReview() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val sessionRes = authRepository.fetchCurrentSession()) {
                is ApiResult.Success -> {
                    val session = sessionRes.data
                    val nextStage = resolveStage(session, isSignedOut = false)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            session = session,
                            stage = nextStage,
                            errorMessage = if (nextStage == OnboardingStage.REGISTRATION_WIZARD) {
                                "Complete all required profile, vehicle, and document steps before review."
                            } else null
                        )
                    }
                }
                is ApiResult.HttpError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = sessionRes.error.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = sessionRes.causeMessage)
                }
                is ApiResult.BackendNotDeployed -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = sessionRes.reason)
                }
            }
        }
    }

    fun simulateDemoReviewerDecision(
        approveAll: Boolean,
        rejectType: KycDocumentType? = null,
        rejectionReason: String? = null
    ) {
        if (authRepository.dataOrigin != DataOrigin.DEMO) return
        viewModelScope.launch {
            val overrides = if (!approveAll && rejectType != null) {
                mapOf(rejectType to (KycDocumentStatus.REJECTED to (rejectionReason ?: "Image unclear — please re-upload.")))
            } else {
                emptyMap()
            }
            val nextAccountStatus = if (approveAll) {
                DriverAccountStatus.ACTIVE
            } else {
                DriverAccountStatus.REJECTED
            }
            when (val res = authRepository.simulateDemoReviewOutcome(nextAccountStatus, overrides)) {
                is ApiResult.Success -> {
                    val session = res.data
                    _uiState.update {
                        it.copy(
                            session = session,
                            stage = resolveStage(session, isSignedOut = false),
                            successMessage = if (approveAll) {
                                "DEMO Reviewer Approved: Partner Activated."
                            } else {
                                "DEMO Reviewer Rejected document."
                            },
                            errorMessage = null
                        )
                    }
                }
                else -> Unit
            }
        }
    }

    fun logout(hasActiveTrip: Boolean, onLoggedOut: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = authRepository.logout(hasActiveTrip)) {
                is ApiResult.Success -> {
                    idempotencyTracker.resetAll()
                    _uiState.update {
                        OnboardingAuthUiState(
                            dataOrigin = it.dataOrigin,
                            stage = OnboardingStage.PHONE_LOGIN,
                            isLoading = false,
                            successMessage = "Signed out safely."
                        )
                    }
                    onLoggedOut()
                }
                is ApiResult.HttpError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.error.message)
                }
                is ApiResult.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.causeMessage)
                }
                is ApiResult.BackendNotDeployed -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = res.reason)
                }
            }
        }
    }

    fun dismissMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }

    private fun startResendCountdown(seconds: Int) {
        resendTimerJob?.cancel()
        resendTimerJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1_000L)
                remaining -= 1
                _uiState.update { it.copy(resendSecondsRemaining = remaining) }
            }
        }
    }

    private fun resolveStage(session: AuthSessionState?, isSignedOut: Boolean): OnboardingStage {
        if (isSignedOut || session == null) return OnboardingStage.PHONE_LOGIN
        if (session.accountStatus.isActive && !session.requiresProfile && !session.requiresVehicle && !session.requiresKyc) {
            return OnboardingStage.AUTHENTICATED_ACTIVE
        }
        if (session.requiresProfile || session.requiresVehicle || session.requiresKyc) {
            if (session.accountStatus == DriverAccountStatus.REJECTED ||
                session.accountStatus == DriverAccountStatus.SUSPENDED ||
                session.accountStatus == DriverAccountStatus.CLOSED
            ) {
                return OnboardingStage.VERIFICATION_PENDING
            }
            return OnboardingStage.REGISTRATION_WIZARD
        }
        return OnboardingStage.VERIFICATION_PENDING
    }

    private fun firstIncompleteStep(session: AuthSessionState): RegistrationStep = when {
        session.requiresProfile -> RegistrationStep.PERSONAL
        session.requiresVehicle -> RegistrationStep.VEHICLE
        else -> RegistrationStep.DOCUMENTS
    }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                OnboardingAuthViewModel(
                    authRepository = appContainer.authOnboardingRepository,
                    financialRepository = appContainer.financialRepository
                )
            }
        }
    }
}
