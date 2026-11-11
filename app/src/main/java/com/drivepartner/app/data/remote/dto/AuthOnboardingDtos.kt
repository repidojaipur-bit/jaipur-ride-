package com.drivepartner.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Typed DTOs for Phase 2 (Identity, Profile, Vehicle & Private KYC) aligned with
 * 04_TECHSPEC.md, 05_SCHEMA (1).md (`account_status`, `document_status`), and 06_RULES (1).md.
 * Raw Aadhaar data is excluded by default per PRD.md FR-03 & 06_RULES (1).md KYC-004.
 */
@Serializable
enum class DriverAccountStatus {
    APPLICANT,
    PENDING_REVIEW,
    ACTIVE,
    SUSPENDED,
    REJECTED,
    CLOSED;

    val isActive: Boolean
        get() = this == ACTIVE

    val isPendingReview: Boolean
        get() = this == PENDING_REVIEW

    val isBlocked: Boolean
        get() = this == SUSPENDED || this == REJECTED || this == CLOSED
}

@Serializable
enum class KycDocumentType {
    DRIVING_LICENSE,
    VEHICLE_RC,
    VEHICLE_INSURANCE,
    PERMIT,
    PAN_CARD,
    AADHAAR_COMPLIANT_REF,
    PROFILE_PHOTO
}

@Serializable
enum class KycDocumentStatus {
    NOT_SUBMITTED,
    UPLOADED,
    UNDER_REVIEW,
    APPROVED,
    REJECTED,
    EXPIRED
}

@Serializable
data class VerifyAuthSessionRequest(
    val idToken: String,
    val phoneE164: String? = null
)

@Serializable
data class AuthSessionResponse(
    val userId: String,
    val driverId: String,
    val accountStatus: DriverAccountStatus,
    val requiresProfile: Boolean,
    val requiresVehicle: Boolean,
    val requiresKyc: Boolean,
    val serverTimeUtc: String,
    val phoneMasked: String = "",
    val sessionToken: String? = null
)

@Serializable
data class DriverProfileRequest(
    val fullName: String,
    val cityCode: String,
    val preferredLanguage: String,
    val dateOfBirth: String? = null,
    val gender: String? = null,
    val addressLine: String? = null,
    val stateCode: String? = null,
    val pinCode: String? = null,
    val profilePhotoObjectKey: String? = null
)

@Serializable
data class DriverProfileResponse(
    val driverId: String,
    val fullName: String,
    val phoneMasked: String,
    val cityCode: String,
    val preferredLanguage: String,
    val accountStatus: DriverAccountStatus,
    val documentsVerified: Boolean,
    val dateOfBirth: String? = null,
    val gender: String? = null,
    val addressLine: String? = null,
    val stateCode: String? = null,
    val pinCode: String? = null,
    val profilePhotoObjectKey: String? = null,
    val rating: Float? = null,
    val totalCompletedTrips: Int = 0,
    val acceptanceRate: Float? = null
)

@Serializable
data class VehicleDetailsRequest(
    val serviceTypeCode: String,
    val registrationNumber: String,
    val vehicleModel: String,
    val brand: String = "",
    val color: String = "",
    val year: Int? = null,
    val vehiclePhotoObjectKey: String? = null
)

@Serializable
data class VehicleDetailsResponse(
    val vehicleId: String,
    val serviceTypeCode: String,
    val registrationNumber: String,
    val vehicleModel: String,
    val isApproved: Boolean,
    val brand: String = "",
    val color: String = "",
    val year: Int? = null,
    val status: String = "PENDING_REVIEW",
    val vehiclePhotoObjectKey: String? = null
)

@Serializable
data class DocumentUploadIntentRequest(
    val documentType: KycDocumentType,
    val mimeType: String = "image/jpeg",
    val sizeBytes: Long = 285_000L,
    val checksumSha256: String? = null
)

@Serializable
data class DocumentUploadIntentResponse(
    val documentId: String,
    val uploadUrl: String,
    val objectKey: String = "",
    val expiresAtUtc: String,
    val requiredHeaders: Map<String, String> = emptyMap()
)

@Serializable
data class ConfirmDocumentUploadRequest(
    val documentId: String,
    val storageObjectKey: String,
    val expiresOnUtc: String? = null,
    val maskedReference: String? = null
)

@Serializable
data class KycDocumentDto(
    val documentId: String,
    val documentType: KycDocumentType,
    val status: KycDocumentStatus,
    val rejectionReason: String? = null,
    val maskedReference: String? = null,
    val expiresAtUtc: String? = null,
    val updatedAtUtc: String
)

object AuthErrorCodes {
    const val INVALID_PHONE = "INVALID_PHONE"
    const val INVALID_OTP = "INVALID_OTP"
    const val OTP_EXPIRED = "OTP_EXPIRED"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val INVALID_TOKEN = "INVALID_TOKEN"
    const val LIVE_AUTH_UNCONFIGURED = "LIVE_AUTH_UNCONFIGURED"
    const val DUPLICATE_VEHICLE_REGISTRATION = "DUPLICATE_VEHICLE_REGISTRATION"
    const val INVALID_INPUT = "INVALID_INPUT"
    const val ACTIVE_TRIP_LOGOUT_BLOCKED = "ACTIVE_TRIP_LOGOUT_BLOCKED"
    const val FORBIDDEN_CROSS_DRIVER = "FORBIDDEN_CROSS_DRIVER"
}
