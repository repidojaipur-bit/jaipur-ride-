package com.drivepartner.app.data.repository

import com.drivepartner.app.core.auth.OtpChallengeResult
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.model.AuthSessionState
import com.drivepartner.app.data.model.DriverProfileDetail
import com.drivepartner.app.data.model.KycDocumentItem
import com.drivepartner.app.data.model.VehicleInfo
import com.drivepartner.app.data.remote.dto.ConfirmDocumentUploadRequest
import com.drivepartner.app.data.remote.dto.DocumentUploadIntentRequest
import com.drivepartner.app.data.remote.dto.DocumentUploadIntentResponse
import com.drivepartner.app.data.remote.dto.DriverAccountStatus
import com.drivepartner.app.data.remote.dto.DriverProfileRequest
import com.drivepartner.app.data.remote.dto.KycDocumentStatus
import com.drivepartner.app.data.remote.dto.KycDocumentType
import com.drivepartner.app.data.remote.dto.VehicleDetailsRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * Phase 2 Repository boundary for Phone OTP Auth, Onboarding, Vehicle & Private KYC documents.
 * Firebase Phone Auth is used only to obtain an ID token; internal identity, driver activation,
 * and KYC approval remain owned by the separate PostgreSQL backend.
 */
interface AuthOnboardingRepository {
    val dataOrigin: DataOrigin
    val currentSession: StateFlow<AuthSessionState?>
    val currentProfile: StateFlow<DriverProfileDetail?>
    val vehicles: StateFlow<List<VehicleInfo>>
    val documents: StateFlow<List<KycDocumentItem>>
    val isSignedOut: StateFlow<Boolean>
    val lastErrorMessage: StateFlow<String?>

    suspend fun requestOtp(phone10Digits: String): ApiResult<OtpChallengeResult>
    suspend fun verifyOtpAndEstablishSession(verificationId: String, otpCode: String): ApiResult<AuthSessionState>
    suspend fun fetchCurrentSession(): ApiResult<AuthSessionState>
    suspend fun fetchProfile(): ApiResult<DriverProfileDetail>
    suspend fun saveProfile(request: DriverProfileRequest, idempotencyKey: String = ""): ApiResult<DriverProfileDetail>
    suspend fun fetchVehicles(): ApiResult<List<VehicleInfo>>
    suspend fun saveVehicle(request: VehicleDetailsRequest, idempotencyKey: String = ""): ApiResult<VehicleInfo>
    suspend fun requestUploadIntent(request: DocumentUploadIntentRequest): ApiResult<DocumentUploadIntentResponse>
    suspend fun registerDocument(request: ConfirmDocumentUploadRequest, idempotencyKey: String = ""): ApiResult<KycDocumentItem>
    suspend fun fetchDocuments(): ApiResult<List<KycDocumentItem>>
    suspend fun logout(hasActiveTrip: Boolean): ApiResult<Unit>

    /**
     * DEMO-ONLY reviewer/sandbox hooks. Returns [ApiResult.HttpError] / no-op in LIVE mode.
     */
    suspend fun simulateDemoReviewOutcome(
        accountStatus: DriverAccountStatus,
        documentStatusByType: Map<KycDocumentType, Pair<KycDocumentStatus, String?>> = emptyMap()
    ): ApiResult<AuthSessionState> = ApiResult.BackendNotDeployed(
        endpoint = "/api/v1/admin/review",
        reason = "Document review is restricted to authorized back-office administrators."
    )

    fun enterDemoOnboardingPreview() {}
}
