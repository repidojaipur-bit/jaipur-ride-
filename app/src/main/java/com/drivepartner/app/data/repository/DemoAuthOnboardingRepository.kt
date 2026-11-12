package com.drivepartner.app.data.repository

import com.drivepartner.app.core.auth.DemoPhoneAuthBoundary
import com.drivepartner.app.core.auth.OtpChallengeResult
import com.drivepartner.app.core.auth.PhoneAuthBoundary
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.network.ApiErrorEnvelope
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.datasource.demo.DemoDataSource
import com.drivepartner.app.data.local.DemoAuthDriverSnapshot
import com.drivepartner.app.data.local.DemoAuthPersistence
import com.drivepartner.app.data.local.InMemoryDemoAuthPersistence
import com.drivepartner.app.data.model.AuthSessionState
import com.drivepartner.app.data.model.DriverProfileDetail
import com.drivepartner.app.data.model.KycDocumentItem
import com.drivepartner.app.data.model.OnboardingValidators
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.VehicleInfo
import com.drivepartner.app.data.model.toDomain
import com.drivepartner.app.data.remote.dto.AuthErrorCodes
import com.drivepartner.app.data.remote.dto.AuthSessionResponse
import com.drivepartner.app.data.remote.dto.ConfirmDocumentUploadRequest
import com.drivepartner.app.data.remote.dto.DocumentUploadIntentRequest
import com.drivepartner.app.data.remote.dto.DocumentUploadIntentResponse
import com.drivepartner.app.data.remote.dto.DriverAccountStatus
import com.drivepartner.app.data.remote.dto.DriverProfileRequest
import com.drivepartner.app.data.remote.dto.DriverProfileResponse
import com.drivepartner.app.data.remote.dto.KycDocumentDto
import com.drivepartner.app.data.remote.dto.KycDocumentStatus
import com.drivepartner.app.data.remote.dto.KycDocumentType
import com.drivepartner.app.data.remote.dto.VehicleDetailsRequest
import com.drivepartner.app.data.remote.dto.VehicleDetailsResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Isolated DEMO-only onboarding, auth & KYC coordinator.
 * Enforces realistic validation, duplicate vehicle registration protection, private object-key
 * upload semantics, and zero raw Aadhaar storage while never touching production backend state.
 */
class DemoAuthOnboardingCoordinator(
    private val persistence: DemoAuthPersistence = InMemoryDemoAuthPersistence(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val startAuthenticatedActive: Boolean = true
) {
    private val mutex = Mutex()
    private var loaded = false
    private lateinit var snapshot: DemoAuthDriverSnapshot
    private val seenVehicleRegByDriver = mutableMapOf<String, String>()

    private suspend fun ensureLoadedLocked(): DemoAuthDriverSnapshot {
        if (!loaded) {
            val existing = persistence.loadSnapshot()
            snapshot = existing ?: defaultDemoSnapshot(startAuthenticatedActive)
            snapshot.vehicles.forEach { v ->
                seenVehicleRegByDriver[OnboardingValidators.normalizeVehicleRegistration(v.registrationNumber)] =
                    snapshot.session.driverId
            }
            if (existing == null) {
                persistence.saveSnapshot(snapshot)
            }
            loaded = true
        }
        return snapshot
    }

    private fun defaultDemoSnapshot(active: Boolean): DemoAuthDriverSnapshot {
        val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        val docStatus = if (active) KycDocumentStatus.APPROVED else KycDocumentStatus.NOT_SUBMITTED
        val accountStatus = if (active) DriverAccountStatus.ACTIVE else DriverAccountStatus.APPLICANT
        return DemoAuthDriverSnapshot(
            session = AuthSessionResponse(
                userId = "DEMO-USER-0001",
                driverId = "DEMO-DP-0001",
                accountStatus = accountStatus,
                requiresProfile = !active,
                requiresVehicle = !active,
                requiresKyc = !active,
                serverTimeUtc = nowUtc,
                phoneMasked = "+91 XXXXX X0000"
            ),
            profile = DriverProfileResponse(
                driverId = "DEMO-DP-0001",
                fullName = if (active) "Demo Driver" else "",
                phoneMasked = "+91 XXXXX X0000",
                cityCode = if (active) "JAI" else "",
                preferredLanguage = "en",
                accountStatus = accountStatus,
                documentsVerified = active,
                dateOfBirth = if (active) "1995-05-14" else null,
                addressLine = if (active) "MI Road Test Zone" else null,
                stateCode = if (active) "RJ" else null,
                pinCode = if (active) "302001" else null,
                rating = 0.0f,
                totalCompletedTrips = 0,
                acceptanceRate = 0.0f
            ),
            vehicles = if (active) {
                listOf(
                    VehicleDetailsResponse(
                        vehicleId = "DEMO-VEH-0001",
                        serviceTypeCode = "BIKE",
                        registrationNumber = "RJ14DM0001",
                        vehicleModel = "Local Test Vehicle",
                        isApproved = true,
                        brand = "Test Motors",
                        color = "Blue",
                        year = 2024,
                        status = "APPROVED"
                    )
                )
            } else {
                emptyList()
            },
            documents = mandatoryDocTypes.map { type ->
                KycDocumentDto(
                    documentId = "DEMO-DOC-${type.name}",
                    documentType = type,
                    status = docStatus,
                    maskedReference = if (active) "XXXX0001" else null,
                    expiresAtUtc = if (active && type != KycDocumentType.PROFILE_PHOTO && type != KycDocumentType.PAN_CARD) {
                        "2028-12-31T00:00:00Z"
                    } else null,
                    updatedAtUtc = nowUtc
                )
            },
            isSignedOut = !active
        )
    }

    suspend fun getSnapshot(): DemoAuthDriverSnapshot = mutex.withLock {
        ensureLoadedLocked()
    }

    suspend fun establishSessionFromToken(firebaseToken: String): ApiResult<DemoAuthDriverSnapshot> = mutex.withLock {
        val current = ensureLoadedLocked()
        val phoneSuffix = firebaseToken.takeLast(4).filter { it.isDigit() }.ifBlank { "0000" }
        val phoneMasked = "+91 XXXXX X$phoneSuffix"
        val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        val updated = current.copy(
            session = current.session.copy(
                phoneMasked = phoneMasked,
                serverTimeUtc = nowUtc
            ),
            profile = current.profile.copy(phoneMasked = phoneMasked),
            isSignedOut = false
        )
        snapshot = updated
        persistence.saveSnapshot(updated)
        ApiResult.Success(updated, origin = DataOrigin.DEMO)
    }

    suspend fun saveProfile(request: DriverProfileRequest): ApiResult<DemoAuthDriverSnapshot> = mutex.withLock {
        val current = ensureLoadedLocked()
        if (request.fullName.trim().length < 2 || request.cityCode.trim().isBlank()) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = AuthErrorCodes.INVALID_INPUT,
                    message = "Full name and city are required."
                )
            )
        }
        val pin = request.pinCode?.trim()
        if (!pin.isNullOrEmpty() && !OnboardingValidators.isValidPinCode(pin)) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = AuthErrorCodes.INVALID_INPUT,
                    message = "Enter a valid 6-digit Indian PIN code."
                )
            )
        }
        val updatedProfile = current.profile.copy(
            fullName = request.fullName.trim(),
            cityCode = request.cityCode.trim().uppercase(),
            preferredLanguage = request.preferredLanguage,
            dateOfBirth = request.dateOfBirth?.trim(),
            gender = request.gender?.trim(),
            addressLine = request.addressLine?.trim(),
            stateCode = request.stateCode?.trim()?.uppercase(),
            pinCode = pin,
            profilePhotoObjectKey = request.profilePhotoObjectKey ?: current.profile.profilePhotoObjectKey
        )
        val updatedSession = current.session.copy(
            requiresProfile = false,
            serverTimeUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        )
        val next = current.copy(session = updatedSession, profile = updatedProfile)
        snapshot = next
        persistence.saveSnapshot(next)
        ApiResult.Success(next, origin = DataOrigin.DEMO)
    }

    suspend fun saveVehicle(request: VehicleDetailsRequest): ApiResult<DemoAuthDriverSnapshot> = mutex.withLock {
        val current = ensureLoadedLocked()
        val normalizedReg = OnboardingValidators.normalizeVehicleRegistration(request.registrationNumber)
        if (!OnboardingValidators.isValidVehicleRegistration(normalizedReg)) {
            return@withLock ApiResult.HttpError(
                httpStatus = 422,
                error = ApiErrorEnvelope(
                    code = AuthErrorCodes.INVALID_INPUT,
                    message = "Enter a valid Indian registration number (e.g. RJ14AB1234)."
                )
            )
        }
        val existingOwner = seenVehicleRegByDriver[normalizedReg]
        if (existingOwner != null && existingOwner != current.session.driverId) {
            return@withLock ApiResult.HttpError(
                httpStatus = 409,
                error = ApiErrorEnvelope(
                    code = AuthErrorCodes.DUPLICATE_VEHICLE_REGISTRATION,
                    message = "Vehicle registration $normalizedReg is already registered to another driver."
                )
            )
        }
        seenVehicleRegByDriver[normalizedReg] = current.session.driverId
        val vehicle = VehicleDetailsResponse(
            vehicleId = current.vehicles.firstOrNull()?.vehicleId ?: "DEMO-VEH-${UUID.randomUUID().toString().take(6).uppercase()}",
            serviceTypeCode = ServiceType.fromCode(request.serviceTypeCode).code,
            registrationNumber = normalizedReg,
            vehicleModel = request.vehicleModel.trim().ifBlank { "Standard" },
            isApproved = current.session.accountStatus.isActive,
            brand = request.brand.trim(),
            color = request.color.trim(),
            year = request.year,
            status = if (current.session.accountStatus.isActive) "APPROVED" else "PENDING_REVIEW",
            vehiclePhotoObjectKey = request.vehiclePhotoObjectKey
        )
        val next = current.copy(
            session = current.session.copy(
                requiresVehicle = false,
                serverTimeUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
            ),
            vehicles = listOf(vehicle)
        )
        snapshot = next
        persistence.saveSnapshot(next)
        ApiResult.Success(next, origin = DataOrigin.DEMO)
    }

    suspend fun createUploadIntent(request: DocumentUploadIntentRequest): ApiResult<DocumentUploadIntentResponse> =
        mutex.withLock {
            val current = ensureLoadedLocked()
            val docId = current.documents.find { it.documentType == request.documentType }?.documentId
                ?: "DEMO-DOC-${request.documentType.name}"
            val objectKey = "private/kyc/${current.session.driverId}/${request.documentType.name.lowercase()}-${UUID.randomUUID().toString().take(8)}"
            ApiResult.Success(
                data = DocumentUploadIntentResponse(
                    documentId = docId,
                    uploadUrl = "demo-private-storage://$objectKey",
                    objectKey = objectKey,
                    expiresAtUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis() + 300_000L),
                    requiredHeaders = mapOf("x-demo-private-upload" to "true")
                ),
                origin = DataOrigin.DEMO
            )
        }

    suspend fun registerDocument(request: ConfirmDocumentUploadRequest): ApiResult<Pair<DemoAuthDriverSnapshot, KycDocumentDto>> =
        mutex.withLock {
            val current = ensureLoadedLocked()
            if (request.storageObjectKey.startsWith("http://", ignoreCase = true) ||
                request.storageObjectKey.startsWith("https://", ignoreCase = true)
            ) {
                return@withLock ApiResult.HttpError(
                    httpStatus = 422,
                    error = ApiErrorEnvelope(
                        code = AuthErrorCodes.INVALID_INPUT,
                        message = "Documents must use private object storage keys, never public URLs."
                    )
                )
            }
            if (!request.maskedReference.isNullOrBlank() &&
                OnboardingValidators.containsRawAadhaarDigits(request.maskedReference)
            ) {
                return@withLock ApiResult.HttpError(
                    httpStatus = 422,
                    error = ApiErrorEnvelope(
                        code = AuthErrorCodes.INVALID_INPUT,
                        message = "Raw 12-digit Aadhaar numbers cannot be submitted or stored."
                    )
                )
            }
            val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
            val updatedDocs = current.documents.map { doc ->
                if (doc.documentId == request.documentId) {
                    doc.copy(
                        status = KycDocumentStatus.UNDER_REVIEW,
                        rejectionReason = null,
                        maskedReference = request.maskedReference ?: doc.maskedReference,
                        expiresAtUtc = request.expiresOnUtc ?: doc.expiresAtUtc,
                        updatedAtUtc = nowUtc
                    )
                } else {
                    doc
                }
            }
            val registered = updatedDocs.find { it.documentId == request.documentId }
                ?: return@withLock ApiResult.HttpError(
                    httpStatus = 404,
                    error = ApiErrorEnvelope(
                        code = "DOCUMENT_NOT_FOUND",
                        message = "Document ${request.documentId} not found."
                    )
                )
            val allMandatorySubmitted = mandatoryDocTypes.all { type ->
                val status = updatedDocs.find { it.documentType == type }?.status
                status != null && status != KycDocumentStatus.NOT_SUBMITTED &&
                    status != KycDocumentStatus.REJECTED && status != KycDocumentStatus.EXPIRED
            }
            val nextAccountStatus = if (current.session.accountStatus == DriverAccountStatus.ACTIVE) {
                DriverAccountStatus.ACTIVE
            } else if (allMandatorySubmitted) {
                DriverAccountStatus.PENDING_REVIEW
            } else {
                DriverAccountStatus.APPLICANT
            }
            val next = current.copy(
                session = current.session.copy(
                    accountStatus = nextAccountStatus,
                    requiresKyc = !allMandatorySubmitted,
                    serverTimeUtc = nowUtc
                ),
                profile = current.profile.copy(
                    accountStatus = nextAccountStatus,
                    documentsVerified = nextAccountStatus == DriverAccountStatus.ACTIVE
                ),
                documents = updatedDocs
            )
            snapshot = next
            persistence.saveSnapshot(next)
            ApiResult.Success(next to registered, origin = DataOrigin.DEMO)
        }

    suspend fun simulateReviewOutcome(
        accountStatus: DriverAccountStatus,
        documentStatusByType: Map<KycDocumentType, Pair<KycDocumentStatus, String?>>
    ): DemoAuthDriverSnapshot = mutex.withLock {
        val current = ensureLoadedLocked()
        val nowUtc = DriverLocationSnapshot.formatUtcIso8601(clockMillis())
        val updatedDocs = current.documents.map { doc ->
            val override = documentStatusByType[doc.documentType]
            when {
                override != null -> doc.copy(
                    status = override.first,
                    rejectionReason = override.second,
                    updatedAtUtc = nowUtc
                )
                accountStatus == DriverAccountStatus.ACTIVE -> doc.copy(
                    status = KycDocumentStatus.APPROVED,
                    rejectionReason = null,
                    updatedAtUtc = nowUtc
                )
                else -> doc
            }
        }
        val docsApproved = accountStatus == DriverAccountStatus.ACTIVE &&
            updatedDocs.all { it.status == KycDocumentStatus.APPROVED }
        val updatedVehicles = current.vehicles.map { v ->
            v.copy(
                isApproved = accountStatus == DriverAccountStatus.ACTIVE,
                status = if (accountStatus == DriverAccountStatus.ACTIVE) "APPROVED" else accountStatus.name
            )
        }
        val next = current.copy(
            session = current.session.copy(
                accountStatus = accountStatus,
                requiresProfile = current.profile.fullName.isBlank(),
                requiresVehicle = updatedVehicles.isEmpty(),
                requiresKyc = !docsApproved,
                serverTimeUtc = nowUtc
            ),
            profile = current.profile.copy(
                accountStatus = accountStatus,
                documentsVerified = docsApproved
            ),
            vehicles = updatedVehicles,
            documents = updatedDocs
        )
        snapshot = next
        persistence.saveSnapshot(next)
        next
    }

    suspend fun logout(hasActiveTrip: Boolean): ApiResult<DemoAuthDriverSnapshot> = mutex.withLock {
        if (hasActiveTrip) {
            return@withLock ApiResult.HttpError(
                httpStatus = 409,
                error = ApiErrorEnvelope(
                    code = AuthErrorCodes.ACTIVE_TRIP_LOGOUT_BLOCKED,
                    message = "Complete or cancel your active trip before logging out."
                )
            )
        }
        val fresh = defaultDemoSnapshot(active = false).copy(isSignedOut = true)
        snapshot = fresh
        persistence.saveSnapshot(fresh)
        ApiResult.Success(fresh, origin = DataOrigin.DEMO)
    }

    companion object {
        val mandatoryDocTypes = listOf(
            KycDocumentType.DRIVING_LICENSE,
            KycDocumentType.VEHICLE_RC,
            KycDocumentType.VEHICLE_INSURANCE,
            KycDocumentType.PAN_CARD,
            KycDocumentType.AADHAAR_COMPLIANT_REF,
            KycDocumentType.PERMIT,
            KycDocumentType.PROFILE_PHOTO
        )
    }
}

/**
 * Isolated DEMO repository for Phase 2 Auth, Onboarding, Vehicle & KYC testing.
 */
class DemoAuthOnboardingRepository(
    private val demoDataSource: DemoDataSource,
    private val phoneAuthBoundary: PhoneAuthBoundary,
    private val coordinator: DemoAuthOnboardingCoordinator = DemoAuthOnboardingCoordinator()
) : AuthOnboardingRepository {

    init {
        require(demoDataSource.origin == DataOrigin.DEMO) {
            "DemoAuthOnboardingRepository must only use a DEMO data source."
        }
        require(phoneAuthBoundary is DemoPhoneAuthBoundary || phoneAuthBoundary.providerMode.name == "DEMO_SANDBOX") {
            "DemoAuthOnboardingRepository requires DEMO phone auth boundary."
        }
    }

    override val dataOrigin: DataOrigin = DataOrigin.DEMO

    private val _currentSession = MutableStateFlow<AuthSessionState?>(null)
    override val currentSession: StateFlow<AuthSessionState?> = _currentSession.asStateFlow()

    private val _currentProfile = MutableStateFlow<DriverProfileDetail?>(null)
    override val currentProfile: StateFlow<DriverProfileDetail?> = _currentProfile.asStateFlow()

    private val _vehicles = MutableStateFlow<List<VehicleInfo>>(emptyList())
    override val vehicles: StateFlow<List<VehicleInfo>> = _vehicles.asStateFlow()

    private val _documents = MutableStateFlow<List<KycDocumentItem>>(emptyList())
    override val documents: StateFlow<List<KycDocumentItem>> = _documents.asStateFlow()

    private val _isSignedOut = MutableStateFlow(false)
    override val isSignedOut: StateFlow<Boolean> = _isSignedOut.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    override val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private fun applySnapshot(snapshot: DemoAuthDriverSnapshot) {
        _currentSession.value = snapshot.session.toDomain(DataOrigin.DEMO)
        _currentProfile.value = snapshot.profile.toDomain(DataOrigin.DEMO)
        _vehicles.value = snapshot.vehicles.map { it.toDomain(DataOrigin.DEMO) }
        _documents.value = snapshot.documents.map { it.toDomain(DataOrigin.DEMO) }
        _isSignedOut.value = snapshot.isSignedOut
        val primaryVehicle = snapshot.vehicles.firstOrNull()
        demoDataSource.updateDemoIdentityFromOnboarding(
            fullName = snapshot.profile.fullName,
            phoneMasked = snapshot.profile.phoneMasked,
            cityCode = snapshot.profile.cityCode,
            vehicleNumber = primaryVehicle?.registrationNumber,
            vehicleModel = primaryVehicle?.vehicleModel,
            serviceType = primaryVehicle?.let { ServiceType.fromCode(it.serviceTypeCode) },
            isVerified = snapshot.session.accountStatus.isActive,
            documentsVerified = snapshot.profile.documentsVerified
        )
    }

    override suspend fun requestOtp(phone10Digits: String): ApiResult<OtpChallengeResult> =
        phoneAuthBoundary.requestOtp(phone10Digits).also { res ->
            if (res is ApiResult.HttpError) _lastErrorMessage.value = res.error.message
        }

    override suspend fun verifyOtpAndEstablishSession(
        verificationId: String,
        otpCode: String
    ): ApiResult<AuthSessionState> {
        return when (val tokenRes = phoneAuthBoundary.verifyOtp(verificationId, otpCode)) {
            is ApiResult.Success -> {
                val res = coordinator.establishSessionFromToken(tokenRes.data)
                if (res is ApiResult.Success) {
                    applySnapshot(res.data)
                    _lastErrorMessage.value = null
                    ApiResult.Success(res.data.session.toDomain(DataOrigin.DEMO), DataOrigin.DEMO)
                } else {
                    ApiResult.HttpError(500, ApiErrorEnvelope("DEMO_SESSION_ERROR", "Unable to establish demo session."))
                }
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = tokenRes.error.message
                tokenRes
            }
            is ApiResult.NetworkError -> tokenRes
            is ApiResult.BackendNotDeployed -> tokenRes
        }
    }

    override suspend fun fetchCurrentSession(): ApiResult<AuthSessionState> {
        val snap = coordinator.getSnapshot()
        applySnapshot(snap)
        return ApiResult.Success(snap.session.toDomain(DataOrigin.DEMO), DataOrigin.DEMO)
    }

    override suspend fun fetchProfile(): ApiResult<DriverProfileDetail> {
        val snap = coordinator.getSnapshot()
        applySnapshot(snap)
        return ApiResult.Success(snap.profile.toDomain(DataOrigin.DEMO), DataOrigin.DEMO)
    }

    override suspend fun saveProfile(
        request: DriverProfileRequest,
        idempotencyKey: String
    ): ApiResult<DriverProfileDetail> {
        return when (val res = coordinator.saveProfile(request)) {
            is ApiResult.Success -> {
                applySnapshot(res.data)
                _lastErrorMessage.value = null
                ApiResult.Success(res.data.profile.toDomain(DataOrigin.DEMO), DataOrigin.DEMO)
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = res.error.message
                res
            }
            is ApiResult.NetworkError -> res
            is ApiResult.BackendNotDeployed -> res
        }
    }

    override suspend fun fetchVehicles(): ApiResult<List<VehicleInfo>> {
        val snap = coordinator.getSnapshot()
        applySnapshot(snap)
        return ApiResult.Success(snap.vehicles.map { it.toDomain(DataOrigin.DEMO) }, DataOrigin.DEMO)
    }

    override suspend fun saveVehicle(
        request: VehicleDetailsRequest,
        idempotencyKey: String
    ): ApiResult<VehicleInfo> {
        return when (val res = coordinator.saveVehicle(request)) {
            is ApiResult.Success -> {
                applySnapshot(res.data)
                _lastErrorMessage.value = null
                ApiResult.Success(res.data.vehicles.first().toDomain(DataOrigin.DEMO), DataOrigin.DEMO)
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = res.error.message
                res
            }
            is ApiResult.NetworkError -> res
            is ApiResult.BackendNotDeployed -> res
        }
    }

    override suspend fun requestUploadIntent(
        request: DocumentUploadIntentRequest
    ): ApiResult<DocumentUploadIntentResponse> = coordinator.createUploadIntent(request)

    override suspend fun registerDocument(
        request: ConfirmDocumentUploadRequest,
        idempotencyKey: String
    ): ApiResult<KycDocumentItem> {
        return when (val res = coordinator.registerDocument(request)) {
            is ApiResult.Success -> {
                applySnapshot(res.data.first)
                _lastErrorMessage.value = null
                ApiResult.Success(res.data.second.toDomain(DataOrigin.DEMO), DataOrigin.DEMO)
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = res.error.message
                res
            }
            is ApiResult.NetworkError -> res
            is ApiResult.BackendNotDeployed -> res
        }
    }

    override suspend fun fetchDocuments(): ApiResult<List<KycDocumentItem>> {
        val snap = coordinator.getSnapshot()
        applySnapshot(snap)
        return ApiResult.Success(snap.documents.map { it.toDomain(DataOrigin.DEMO) }, DataOrigin.DEMO)
    }

    override suspend fun logout(hasActiveTrip: Boolean): ApiResult<Unit> {
        return when (val res = coordinator.logout(hasActiveTrip)) {
            is ApiResult.Success -> {
                applySnapshot(res.data)
                _lastErrorMessage.value = null
                ApiResult.Success(Unit, DataOrigin.DEMO)
            }
            is ApiResult.HttpError -> {
                _lastErrorMessage.value = res.error.message
                res
            }
            is ApiResult.NetworkError -> res
            is ApiResult.BackendNotDeployed -> res
        }
    }

    override suspend fun simulateDemoReviewOutcome(
        accountStatus: DriverAccountStatus,
        documentStatusByType: Map<KycDocumentType, Pair<KycDocumentStatus, String?>>
    ): ApiResult<AuthSessionState> {
        val snap = coordinator.simulateReviewOutcome(accountStatus, documentStatusByType)
        applySnapshot(snap)
        return ApiResult.Success(snap.session.toDomain(DataOrigin.DEMO), DataOrigin.DEMO)
    }

    override fun enterDemoOnboardingPreview() {
        _isSignedOut.value = true
    }
}
