package com.drivepartner.app.data.model

import androidx.annotation.StringRes
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.remote.dto.AuthSessionResponse
import com.drivepartner.app.data.remote.dto.DriverAccountStatus
import com.drivepartner.app.data.remote.dto.DriverProfileResponse
import com.drivepartner.app.data.remote.dto.KycDocumentDto
import com.drivepartner.app.data.remote.dto.KycDocumentStatus
import com.drivepartner.app.data.remote.dto.KycDocumentType
import com.drivepartner.app.data.remote.dto.VehicleDetailsResponse

enum class OnboardingStage {
    SPLASH_LANGUAGE,
    PHONE_LOGIN,
    REGISTRATION_WIZARD,
    VERIFICATION_PENDING,
    AUTHENTICATED_ACTIVE
}

enum class RegistrationStep(val stepIndex: Int) {
    PERSONAL(0),
    VEHICLE(1),
    BANK(2),
    DOCUMENTS(3)
}

enum class ThemePreference(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromStorage(raw: String?): ThemePreference = when (raw?.lowercase()) {
            "dark" -> DARK
            "system" -> SYSTEM
            else -> LIGHT
        }
    }
}

data class AuthSessionState(
    val userId: String,
    val driverId: String,
    val accountStatus: DriverAccountStatus,
    val requiresProfile: Boolean,
    val requiresVehicle: Boolean,
    val requiresKyc: Boolean,
    val phoneMasked: String,
    val serverTimeUtc: String,
    val dataOrigin: DataOrigin
) {
    val canAccessMainDashboard: Boolean
        get() = accountStatus.isActive && !requiresProfile && !requiresVehicle && !requiresKyc
}

data class DriverProfileDetail(
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
    val acceptanceRate: Float? = null,
    val dataOrigin: DataOrigin
)

data class VehicleInfo(
    val vehicleId: String,
    val serviceType: ServiceType,
    val registrationNumber: String,
    val vehicleModel: String,
    val brand: String,
    val color: String,
    val year: Int?,
    val isApproved: Boolean,
    val status: String,
    val vehiclePhotoObjectKey: String?,
    val dataOrigin: DataOrigin
)

data class KycDocumentItem(
    val documentId: String,
    val documentType: KycDocumentType,
    val status: KycDocumentStatus,
    val rejectionReason: String?,
    val maskedReference: String?,
    val expiresAtUtc: String?,
    val updatedAtUtc: String,
    val dataOrigin: DataOrigin
)

object OnboardingValidators {
    private val phoneRegex = Regex("^[6-9]\\d{9}$")
    private val otpRegex = Regex("^\\d{6}$")
    private val pinRegex = Regex("^[1-9]\\d{5}$")
    private val vehicleRegRegex = Regex("^[A-Z]{2}\\d{1,2}[A-Z]{0,3}\\d{4}$")
    private val ifscRegex = Regex("^[A-Z]{4}0[A-Z0-9]{6}$")
    private val bankAccountRegex = Regex("^\\d{9,18}$")
    private val panRegex = Regex("^[A-Z]{5}\\d{4}[A-Z]$")
    private val rawAadhaarRegex = Regex("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b")

    fun isValidIndianPhone(digits: String): Boolean = phoneRegex.matches(digits.trim())

    fun isValidOtp(otp: String): Boolean = otpRegex.matches(otp.trim())

    fun isValidPinCode(pin: String): Boolean = pinRegex.matches(pin.trim())

    fun normalizeVehicleRegistration(raw: String): String =
        raw.uppercase().replace(Regex("[\\s-]"), "")

    fun isValidVehicleRegistration(raw: String): Boolean =
        vehicleRegRegex.matches(normalizeVehicleRegistration(raw))

    fun isValidIfsc(ifsc: String): Boolean = ifscRegex.matches(ifsc.trim().uppercase())

    fun isValidBankAccount(accountNumber: String): Boolean =
        bankAccountRegex.matches(accountNumber.trim())

    fun isValidPanReference(pan: String): Boolean =
        panRegex.matches(pan.trim().uppercase())

    /**
     * Compliance guard (`06_RULES` KYC-004): refuses raw 12-digit Aadhaar numbers.
     * Only compliant verification references or masked last-4 tokens may be entered.
     */
    fun containsRawAadhaarDigits(input: String): Boolean =
        rawAadhaarRegex.containsMatchIn(input.trim())

    fun maskLast4(raw: String): String {
        val clean = raw.trim()
        return if (clean.length <= 4) "XXXX$clean" else "XXXX${clean.takeLast(4)}"
    }
}

@StringRes
fun DriverAccountStatus.labelRes(): Int = when (this) {
    DriverAccountStatus.APPLICANT -> R.string.account_status_applicant
    DriverAccountStatus.PENDING_REVIEW -> R.string.account_status_pending_review
    DriverAccountStatus.ACTIVE -> R.string.account_status_active
    DriverAccountStatus.SUSPENDED -> R.string.account_status_suspended
    DriverAccountStatus.REJECTED -> R.string.account_status_rejected
    DriverAccountStatus.CLOSED -> R.string.account_status_closed
}

@StringRes
fun KycDocumentType.labelRes(): Int = when (this) {
    KycDocumentType.DRIVING_LICENSE -> R.string.kyc_doc_driving_license
    KycDocumentType.VEHICLE_RC -> R.string.kyc_doc_vehicle_rc
    KycDocumentType.VEHICLE_INSURANCE -> R.string.kyc_doc_vehicle_insurance
    KycDocumentType.PERMIT -> R.string.kyc_doc_permit
    KycDocumentType.PAN_CARD -> R.string.kyc_doc_pan_card
    KycDocumentType.AADHAAR_COMPLIANT_REF -> R.string.kyc_doc_aadhaar_ref
    KycDocumentType.PROFILE_PHOTO -> R.string.kyc_doc_profile_photo
}

@StringRes
fun KycDocumentStatus.labelRes(): Int = when (this) {
    KycDocumentStatus.NOT_SUBMITTED -> R.string.kyc_status_not_submitted
    KycDocumentStatus.UPLOADED -> R.string.kyc_status_uploaded
    KycDocumentStatus.UNDER_REVIEW -> R.string.kyc_status_under_review
    KycDocumentStatus.APPROVED -> R.string.kyc_status_approved
    KycDocumentStatus.REJECTED -> R.string.kyc_status_rejected
    KycDocumentStatus.EXPIRED -> R.string.kyc_status_expired
}

fun AuthSessionResponse.toDomain(dataOrigin: DataOrigin): AuthSessionState = AuthSessionState(
    userId = userId,
    driverId = driverId,
    accountStatus = accountStatus,
    requiresProfile = requiresProfile,
    requiresVehicle = requiresVehicle,
    requiresKyc = requiresKyc,
    phoneMasked = phoneMasked,
    serverTimeUtc = serverTimeUtc,
    dataOrigin = dataOrigin
)

fun DriverProfileResponse.toDomain(dataOrigin: DataOrigin): DriverProfileDetail = DriverProfileDetail(
    driverId = driverId,
    fullName = fullName,
    phoneMasked = phoneMasked,
    cityCode = cityCode,
    preferredLanguage = preferredLanguage,
    accountStatus = accountStatus,
    documentsVerified = documentsVerified,
    dateOfBirth = dateOfBirth,
    gender = gender,
    addressLine = addressLine,
    stateCode = stateCode,
    pinCode = pinCode,
    profilePhotoObjectKey = profilePhotoObjectKey,
    rating = rating,
    totalCompletedTrips = totalCompletedTrips,
    acceptanceRate = acceptanceRate,
    dataOrigin = dataOrigin
)

fun VehicleDetailsResponse.toDomain(dataOrigin: DataOrigin): VehicleInfo = VehicleInfo(
    vehicleId = vehicleId,
    serviceType = ServiceType.fromCode(serviceTypeCode),
    registrationNumber = registrationNumber,
    vehicleModel = vehicleModel,
    brand = brand,
    color = color,
    year = year,
    isApproved = isApproved,
    status = status,
    vehiclePhotoObjectKey = vehiclePhotoObjectKey,
    dataOrigin = dataOrigin
)

fun KycDocumentDto.toDomain(dataOrigin: DataOrigin): KycDocumentItem = KycDocumentItem(
    documentId = documentId,
    documentType = documentType,
    status = status,
    rejectionReason = rejectionReason,
    maskedReference = maskedReference,
    expiresAtUtc = expiresAtUtc,
    updatedAtUtc = updatedAtUtc,
    dataOrigin = dataOrigin
)
