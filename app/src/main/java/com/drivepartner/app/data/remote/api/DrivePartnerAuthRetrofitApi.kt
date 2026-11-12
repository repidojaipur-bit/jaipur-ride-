package com.drivepartner.app.data.remote.api

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
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Retrofit endpoints for Phase 2 (Authentication, Driver Profile, Vehicle & Private KYC Documents)
 * aligned 1-to-1 with `04_TECHSPEC.md` Section 4.
 */
interface DrivePartnerAuthRetrofitApi {

    @POST("api/v1/auth/session")
    suspend fun verifyAuthSession(
        @Header("X-Skip-Auth") skipAuth: String = "true",
        @Body request: VerifyAuthSessionRequest
    ): Response<AuthSessionResponse>

    @GET("api/v1/me")
    suspend fun getCurrentUserSession(): Response<AuthSessionResponse>

    @GET("api/v1/drivers/me")
    suspend fun getDriverProfile(): Response<DriverProfileResponse>

    @POST("api/v1/drivers/profile")
    suspend fun upsertDriverProfile(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: DriverProfileRequest
    ): Response<DriverProfileResponse>

    @GET("api/v1/vehicles")
    suspend fun getVehicles(): Response<List<VehicleDetailsResponse>>

    @POST("api/v1/vehicles")
    suspend fun upsertVehicle(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: VehicleDetailsRequest
    ): Response<VehicleDetailsResponse>

    @POST("api/v1/documents/upload-intent")
    suspend fun createDocumentUploadIntent(
        @Body request: DocumentUploadIntentRequest
    ): Response<DocumentUploadIntentResponse>

    @POST("api/v1/documents")
    suspend fun registerDocument(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: ConfirmDocumentUploadRequest
    ): Response<KycDocumentDto>

    @GET("api/v1/documents")
    suspend fun getDocuments(): Response<List<KycDocumentDto>>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<Unit>
}
