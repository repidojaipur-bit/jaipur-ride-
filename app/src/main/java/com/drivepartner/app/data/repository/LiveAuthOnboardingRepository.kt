package com.drivepartner.app.data.repository

import com.drivepartner.app.core.auth.OtpChallengeResult
import com.drivepartner.app.core.auth.PhoneAuthBoundary
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.ApiErrorEnvelope
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.AuthSessionTokenProvider
import com.drivepartner.app.data.datasource.live.LiveApiDataSource
import com.drivepartner.app.data.model.AuthSessionState
import com.drivepartner.app.data.model.DriverProfileDetail
import com.drivepartner.app.data.model.KycDocumentItem
import com.drivepartner.app.data.model.OnboardingValidators
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.VehicleInfo
import com.drivepartner.app.data.model.toDomain
import com.drivepartner.app.data.remote.dto.AuthErrorCodes
import com.drivepartner.app.data.remote.dto.ConfirmDocumentUploadRequest
import com.drivepartner.app.data.remote.dto.DocumentUploadIntentRequest
import com.drivepartner.app.data.remote.dto.DocumentUploadIntentResponse
import com.drivepartner.app.data.remote.dto.DriverProfileRequest
import com.drivepartner.app.data.remote.dto.VehicleDetailsRequest
import com.drivepartner.app.data.remote.dto.VerifyAuthSessionRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LIVE Auth, Onboarding, Vehicle & Private KYC repository.
 *
 * Guarantees (`00_MASTER.md`, `06_RULES` AUTH-001..003 / KYC-001..006):
 * - Never fakes OTP verification or document approval.
 * - Never stores raw Aadhaar digits.
 * - Attaches backend/Firebase ID token through [AuthSessionTokenProvider].
 */
class LiveAuthOnboardingRepository(
    private val liveDataSource: LiveApiDataSource,
    private val phoneAuthBoundary: PhoneAuthBoundary,
    private val tokenProvider: AuthSessionTokenProvider
) : AuthOnboardingRepository {

    init {
        require(liveDataSource.origin == DataOrigin.LIVE) {
            "LiveAuthOnboardingRepository must only use a LIVE data source."
        }
    }

    override val dataOrigin: DataOrigin = DataOrigin.LIVE

    private val _currentSession = MutableStateFlow<AuthSessionState?>(null)
    override val currentSession: StateFlow<AuthSessionState?> = _currentSession.asStateFlow()

    private val _currentProfile = MutableStateFlow<DriverProfileDetail?>(null)
    override val currentProfile: StateFlow<DriverProfileDetail?> = _currentProfile.asStateFlow()

    private val _vehicles = MutableStateFlow<List<VehicleInfo>>(emptyList())
    override val vehicles: StateFlow<List<VehicleInfo>> = _vehicles.asStateFlow()

    private val _documents = MutableStateFlow<List<KycDocumentItem>>(emptyList())
    override val documents: StateFlow<List<KycDocumentItem>> = _documents.asStateFlow()

    private val _isSignedOut = MutableStateFlow(tokenProvider.currentToken().isNullOrBlank())
    override val isSignedOut: StateFlow<Boolean> = _isSignedOut.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    override suspend fun requestOtp(phone10Digits: String): ApiResult<OtpChallengeResult> =
        phoneAuthBoundary.requestOtp(phone10Digits).also { res ->
            if (res is ApiResult.HttpError) _lastErrorMessage.value = res.error.message
            if (res is ApiResult.BackendNotDeployed) _lastErrorMessage.value = res.reason
        }

    override suspend fun verifyOtpAndEstablishSession(
        verificationId: String,
        otpCode: String
    ): ApiResult<AuthSessionState> {
        val idToken = when (val authRes = phoneAuthBoundary.verifyOtp(verificationId, otpCode)) {
            is ApiResult.Success -> authRes.data
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = authRes.error.message
                return authRes
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = authRes.reason
                return authRes
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = authRes.causeMessage
                return authRes
            }
        }

        return when (
            val sessionRes = liveDataSource.authOnboardingApi.verifyAuthSession(
                VerifyAuthSessionRequest(idToken = idToken)
            )
        ) {
            is ApiResult.Success -> {
                tokenProvider.setToken(sessionRes.data.sessionToken ?: idToken)
                val mapped = sessionRes.data.toDomain(DataOrigin.LIVE)
                _currentSession.value = mapped
                _isSignedOut.value = false
                _lastErrorMessage.value = null
                ApiResult.Success(mapped, DataOrigin.LIVE)
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = sessionRes.error.message
                sessionRes
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = sessionRes.causeMessage
                sessionRes
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = sessionRes.reason
                sessionRes
            }
        }
    }

    override suspend fun fetchCurrentSession(): ApiResult<AuthSessionState> {
        if (tokenProvider.currentToken().isNullOrBlank()) {
            _isSignedOut.value = true
            return ApiResult.HttpError(
                httpStatus = 401,
                error = ApiErrorEnvelope(
                    code = AuthErrorCodes.INVALID_TOKEN,
                    message = "Sign in with your mobile number to continue."
                )
            )
        }
        return when (val res = liveDataSource.authOnboardingApi.getCurrentUserSession()) {
            is ApiResult.Success -> {
                val mapped = res.data.toDomain(DataOrigin.LIVE)
                _currentSession.value = mapped
                _isSignedOut.value = false
                _lastErrorMessage.value = null
                ApiResult.Success(mapped, DataOrigin.LIVE)
            }
            is ApiResult.HttpError -> {
                if (res.httpStatus == 401) {
                    tokenProvider.clearToken()
                    _isSignedOut.value = true
                }
                _lastErrorMessage.value = res.error.message
                res
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = res.causeMessage
                res
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = res.reason
                res
            }
        }
    }

    override suspend fun fetchProfile(): ApiResult<DriverProfileDetail> {
        return when (val res = liveDataSource.authOnboardingApi.getDriverProfile()) {
            is ApiResult.Success -> {
                val mapped = res.data.toDomain(DataOrigin.LIVE)
                _currentProfile.value = mapped
                val primary = _vehicles.value.firstOrNull()
                liveDataSource.updateConfirmedProfileFromOnboarding(
                    dto = res.data,
                    vehicleNumber = primary?.registrationNumber.orEmpty(),
                    vehicleModel = primary?.vehicleModel.orEmpty(),
                    serviceType = primary?.serviceType ?: ServiceType.BIKE_TAXI
                )
                _lastErrorMessage.value = null
                ApiResult.Success(mapped, DataOrigin.LIVE)
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = res.error.message
                res
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = res.causeMessage
                res
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = res.reason
                res
            }
        }
    }

    override suspend fun saveProfile(
        request: DriverProfileRequest,
        idempotencyKey: String
    ): ApiResult<DriverProfileDetail> {
        return when (val res = liveDataSource.authOnboardingApi.upsertDriverProfile(request, idempotencyKey)) {
            is ApiResult.Success -> {
                val mapped = res.data.toDomain(DataOrigin.LIVE)
                _currentProfile.value = mapped
                liveDataSource.updateConfirmedProfileFromOnboarding(res.data)
                _lastErrorMessage.value = null
                ApiResult.Success(mapped, DataOrigin.LIVE)
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = res.error.message
                res
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = res.causeMessage
                res
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = res.reason
                res
            }
        }
    }

    override suspend fun fetchVehicles(): ApiResult<List<VehicleInfo>> {
        return when (val res = liveDataSource.authOnboardingApi.getVehicles()) {
            is ApiResult.Success -> {
                val mapped = res.data.map { it.toDomain(DataOrigin.LIVE) }
                _vehicles.value = mapped
                _lastErrorMessage.value = null
                ApiResult.Success(mapped, DataOrigin.LIVE)
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = res.error.message
                res
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = res.causeMessage
                res
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = res.reason
                res
            }
        }
    }

    override suspend fun saveVehicle(
        request: VehicleDetailsRequest,
        idempotencyKey: String
    ): ApiResult<VehicleInfo> {
        return when (val res = liveDataSource.authOnboardingApi.upsertVehicle(request, idempotencyKey)) {
            is ApiResult.Success -> {
                val mapped = res.data.toDomain(DataOrigin.LIVE)
                _vehicles.value = listOf(mapped) + _vehicles.value.filterNot { it.vehicleId == mapped.vehicleId }
                _lastErrorMessage.value = null
                ApiResult.Success(mapped, DataOrigin.LIVE)
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = res.error.message
                res
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = res.causeMessage
                res
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = res.reason
                res
            }
        }
    }

    override suspend fun requestUploadIntent(
        request: DocumentUploadIntentRequest
    ): ApiResult<DocumentUploadIntentResponse> =
        liveDataSource.authOnboardingApi.createDocumentUploadIntent(request)

    override suspend fun registerDocument(
        request: ConfirmDocumentUploadRequest,
        idempotencyKey: String
    ): ApiResult<KycDocumentItem> {
        if (!request.maskedReference.isNullOrBlank() &&
            OnboardingValidators.containsRawAadhaarDigits(request.maskedReference)
        ) {
            return ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = AuthErrorCodes.INVALID_INPUT,
                    message = "Raw 12-digit Aadhaar numbers cannot be submitted or stored."
                )
            )
        }
        return when (val res = liveDataSource.authOnboardingApi.registerDocument(request, idempotencyKey)) {
            is ApiResult.Success -> {
                val mapped = res.data.toDomain(DataOrigin.LIVE)
                _documents.value = listOf(mapped) + _documents.value.filterNot { it.documentId == mapped.documentId }
                _lastErrorMessage.value = null
                ApiResult.Success(mapped, DataOrigin.LIVE)
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = res.error.message
                res
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = res.causeMessage
                res
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = res.reason
                res
            }
        }
    }

    override suspend fun fetchDocuments(): ApiResult<List<KycDocumentItem>> {
        return when (val res = liveDataSource.authOnboardingApi.getDocuments()) {
            is ApiResult.Success -> {
                val mapped = res.data.map { it.toDomain(DataOrigin.LIVE) }
                _documents.value = mapped
                _lastErrorMessage.value = null
                ApiResult.Success(mapped, DataOrigin.LIVE)
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = res.error.message
                res
            }
            is ApiResult.NetworkError -> {
                _lastErrorMessage.value = res.causeMessage
                res
            }
            is ApiResult.BackendNotDeployed -> {
                _lastErrorMessage.value = res.reason
                res
            }
        }
    }

    override suspend fun logout(hasActiveTrip: Boolean): ApiResult<Unit> {
        if (hasActiveTrip) {
            return ApiResult.HttpError(
                httpStatus = 409,
                error = ApiErrorEnvelope(
                    code = AuthErrorCodes.ACTIVE_TRIP_LOGOUT_BLOCKED,
                    message = "Finish or resolve your active trip before signing out."
                )
            )
        }
        val res = liveDataSource.authOnboardingApi.logout()
        tokenProvider.clearToken()
        liveDataSource.clearSessionOnLogout()
        _currentSession.value = null
        _currentProfile.value = null
        _vehicles.value = emptyList()
        _documents.value = emptyList()
        _isSignedOut.value = true
        return when (res) {
            is ApiResult.Success -> ApiResult.Success(Unit, DataOrigin.LIVE)
            else -> ApiResult.Success(Unit, DataOrigin.LIVE) // Local session cleared safely
        }
    }
}
