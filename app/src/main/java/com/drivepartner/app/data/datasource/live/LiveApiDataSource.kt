package com.drivepartner.app.data.datasource.live

import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.network.HealthResponse
import com.drivepartner.app.data.datasource.PartnerDataSource
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.remote.api.AuthOnboardingApi
import com.drivepartner.app.data.remote.api.DispatchApi
import com.drivepartner.app.data.remote.api.DutyLocationApi
import com.drivepartner.app.data.remote.api.FinancialApi
import com.drivepartner.app.data.remote.api.HealthApi
import com.drivepartner.app.data.remote.api.RetentionSafetyApi
import com.drivepartner.app.data.remote.api.TripsOrdersApi
import com.drivepartner.app.data.remote.dto.DriverAccountStatus
import com.drivepartner.app.data.remote.dto.RemoteDutyStatus
import com.drivepartner.app.data.remote.dto.UpdateDutyStatusRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Live backend connection boundary for the separate TypeScript + PostgreSQL + PostGIS API.
 *
 * Architectural guarantees:
 * - Never falls back to [com.drivepartner.app.data.datasource.demo.DemoDataSource].
 * - Never pretends backend APIs are deployed when [AppEnvironmentConfig.isLiveBackendConfigured] is false.
 * - Does NOT use Firestore for orders, trips, or financial ledger state.
 */
class LiveApiDataSource(
    val environmentConfig: AppEnvironmentConfig,
    val healthApi: HealthApi,
    val authOnboardingApi: AuthOnboardingApi,
    val dutyLocationApi: DutyLocationApi,
    val dispatchApi: DispatchApi,
    val tripsOrdersApi: TripsOrdersApi,
    val financialApi: FinancialApi,
    val retentionSafetyApi: RetentionSafetyApi
) : PartnerDataSource {

    override val origin: DataOrigin = DataOrigin.LIVE

    private val _partnerStatus = MutableStateFlow(PartnerStatus.OFFLINE)
    override val partnerStatus: StateFlow<PartnerStatus> = _partnerStatus.asStateFlow()

    private val _dutyControlState = MutableStateFlow(
        if (environmentConfig.isLiveBackendConfigured) {
            DutyControlState.OFFLINE
        } else {
            DutyControlState.CONNECTION_UNKNOWN
        }
    )
    val dutyControlState: StateFlow<DutyControlState> = _dutyControlState.asStateFlow()

    private val _partnerProfile = MutableStateFlow<PartnerProfile?>(null)
    override val partnerProfile: StateFlow<PartnerProfile?> = _partnerProfile.asStateFlow()

    private val _lastBoundaryMessage = MutableStateFlow<String?>(
        if (environmentConfig.isLiveBackendConfigured) {
            null
        } else {
            "Live backend API is not configured or deployed yet."
        }
    )
    override val lastBoundaryMessage: StateFlow<String?> = _lastBoundaryMessage.asStateFlow()

    suspend fun checkBackendHealth(): ApiResult<HealthResponse> =
        healthApi.getHealth()

    override suspend fun setDutyStatus(isOnline: Boolean): ApiResult<PartnerStatus> =
        setDutyStatusWithLocation(isOnline = isOnline, location = null)

    suspend fun setDutyStatusWithLocation(
        isOnline: Boolean,
        location: DriverLocationSnapshot?
    ): ApiResult<PartnerStatus> {
        val request = UpdateDutyStatusRequest(
            idempotencyKey = UUID.randomUUID().toString(),
            latitude = location?.latitude,
            longitude = location?.longitude,
            accuracyMeters = location?.accuracyMeters,
            capturedAtUtc = location?.capturedAtUtc
        )
        val result = if (isOnline) {
            dutyLocationApi.goOnline(request)
        } else {
            dutyLocationApi.goOffline(request)
        }
        return when (result) {
            is ApiResult.Success -> {
                val online = result.data.status == RemoteDutyStatus.ONLINE
                val confirmed = PartnerStatus.fromBoolean(online)
                _partnerStatus.value = confirmed
                _dutyControlState.value = if (online) DutyControlState.ONLINE else DutyControlState.OFFLINE
                _lastBoundaryMessage.value = null
                ApiResult.Success(confirmed, origin = DataOrigin.LIVE)
            }
            is ApiResult.BackendNotDeployed -> {
                _partnerStatus.value = PartnerStatus.OFFLINE
                _dutyControlState.value = DutyControlState.CONNECTION_UNKNOWN
                _lastBoundaryMessage.value = result.reason
                result
            }
            is ApiResult.HttpError -> {
                _lastBoundaryMessage.value = result.error.message
                if (result.httpStatus == 403 || result.error.code.contains("ELIGIB", ignoreCase = true)) {
                    _partnerStatus.value = PartnerStatus.OFFLINE
                    _dutyControlState.value = DutyControlState.BLOCKED
                }
                result
            }
            is ApiResult.NetworkError -> {
                _lastBoundaryMessage.value = result.causeMessage
                _dutyControlState.value = DutyControlState.CONNECTION_UNKNOWN
                result
            }
        }
    }

    internal fun updateConfirmedStateFromHome(
        remoteStatus: RemoteDutyStatus,
        activeServiceType: ServiceType? = null
    ) {
        val online = remoteStatus == RemoteDutyStatus.ONLINE
        _partnerStatus.value = PartnerStatus.fromBoolean(online)
        _dutyControlState.value = if (online) DutyControlState.ONLINE else DutyControlState.OFFLINE
        if (activeServiceType != null && _partnerProfile.value != null) {
            _partnerProfile.value = _partnerProfile.value?.copy(serviceType = activeServiceType)
        }
        _lastBoundaryMessage.value = null
    }

    internal fun markConnectionUnknown(message: String?) {
        _dutyControlState.value = DutyControlState.CONNECTION_UNKNOWN
        _lastBoundaryMessage.value = message
    }

    override suspend fun toggleDutyStatus(): ApiResult<PartnerStatus> =
        setDutyStatus(!_partnerStatus.value.isOnline)

    override suspend fun switchServiceType(serviceType: ServiceType): ApiResult<Unit> {
        return ApiResult.BackendNotDeployed(
            endpoint = "/api/v1/drivers/preferences",
            reason = "Service category changes require live backend preference endpoint (not deployed yet)."
        )
    }

    internal fun updateConfirmedProfileFromOnboarding(
        dto: com.drivepartner.app.data.remote.dto.DriverProfileResponse,
        vehicleNumber: String = _partnerProfile.value?.vehicleNumber.orEmpty(),
        vehicleModel: String = _partnerProfile.value?.vehicleModel.orEmpty(),
        serviceType: ServiceType = _partnerProfile.value?.serviceType ?: ServiceType.BIKE_TAXI
    ) {
        _partnerProfile.value = PartnerProfile(
            id = dto.driverId,
            name = dto.fullName,
            phone = dto.phoneMasked,
            vehicleNumber = vehicleNumber,
            serviceType = serviceType,
            city = dto.cityCode,
            vehicleModel = vehicleModel,
            isVerified = dto.accountStatus == DriverAccountStatus.ACTIVE,
            documentsVerified = dto.documentsVerified,
            rating = dto.rating ?: 0.0f,
            totalCompletedTrips = dto.totalCompletedTrips,
            acceptanceRate = dto.acceptanceRate ?: 0.0f,
            isDemoProfile = false
        )
        _lastBoundaryMessage.value = null
    }

    internal fun clearSessionOnLogout() {
        _partnerProfile.value = null
        _partnerStatus.value = PartnerStatus.OFFLINE
        _dutyControlState.value = if (environmentConfig.isLiveBackendConfigured) {
            DutyControlState.OFFLINE
        } else {
            DutyControlState.CONNECTION_UNKNOWN
        }
    }

    override suspend fun refreshFromSource(): ApiResult<Unit> {
        return when (val profileResult = authOnboardingApi.getDriverProfile()) {
            is ApiResult.Success -> {
                val dto = profileResult.data
                _partnerProfile.value = PartnerProfile(
                    id = dto.driverId,
                    name = dto.fullName,
                    phone = dto.phoneMasked,
                    vehicleNumber = "",
                    serviceType = ServiceType.BIKE_TAXI,
                    city = dto.cityCode,
                    vehicleModel = "",
                    isVerified = dto.accountStatus == DriverAccountStatus.ACTIVE,
                    documentsVerified = dto.documentsVerified,
                    rating = dto.rating ?: 0.0f,
                    totalCompletedTrips = dto.totalCompletedTrips,
                    acceptanceRate = dto.acceptanceRate ?: 0.0f,
                    isDemoProfile = false
                )
                _lastBoundaryMessage.value = null
                ApiResult.Success(Unit, origin = DataOrigin.LIVE)
            }
            is ApiResult.BackendNotDeployed -> {
                _lastBoundaryMessage.value = profileResult.reason
                profileResult
            }
            is ApiResult.HttpError -> {
                _lastBoundaryMessage.value = profileResult.error.message
                profileResult
            }
            is ApiResult.NetworkError -> {
                _lastBoundaryMessage.value = profileResult.causeMessage
                profileResult
            }
        }
    }
}
