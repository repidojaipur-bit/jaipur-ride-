package com.drivepartner.app.data.remote.api

import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.remote.dto.AuthSessionResponse
import com.drivepartner.app.data.remote.dto.ConfirmDocumentUploadRequest
import com.drivepartner.app.data.remote.dto.DocumentUploadIntentRequest
import com.drivepartner.app.data.remote.dto.DocumentUploadIntentResponse
import com.drivepartner.app.data.remote.dto.DriverProfileRequest
import com.drivepartner.app.data.remote.dto.DriverProfileResponse
import com.drivepartner.app.data.remote.dto.KycDocumentDto
import com.drivepartner.app.data.remote.dto.VehicleDetailsRequest
import com.drivepartner.app.data.remote.dto.VehicleDetailsResponse

import com.drivepartner.app.data.remote.dto.VerifyAuthSessionRequest

interface AuthOnboardingApi {
    suspend fun verifyAuthSession(request: VerifyAuthSessionRequest): ApiResult<AuthSessionResponse>
    suspend fun getCurrentUserSession(): ApiResult<AuthSessionResponse>
    suspend fun getDriverProfile(): ApiResult<DriverProfileResponse>
    suspend fun upsertDriverProfile(
        request: DriverProfileRequest,
        idempotencyKey: String = ""
    ): ApiResult<DriverProfileResponse>
    suspend fun getVehicles(): ApiResult<List<VehicleDetailsResponse>>
    suspend fun upsertVehicle(
        request: VehicleDetailsRequest,
        idempotencyKey: String = ""
    ): ApiResult<VehicleDetailsResponse>
    suspend fun createDocumentUploadIntent(request: DocumentUploadIntentRequest): ApiResult<DocumentUploadIntentResponse>
    suspend fun registerDocument(
        request: ConfirmDocumentUploadRequest,
        idempotencyKey: String = ""
    ): ApiResult<KycDocumentDto>
    suspend fun getDocuments(): ApiResult<List<KycDocumentDto>>
    suspend fun logout(): ApiResult<Unit>
}
