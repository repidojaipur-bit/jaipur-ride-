package com.drivepartner.app.data.datasource.demo

import com.drivepartner.app.core.config.AppEnvironmentConfig
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.core.network.ApiErrorEnvelope
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.datasource.PartnerDataSource
import com.drivepartner.app.data.model.DriverEligibility
import com.drivepartner.app.data.model.DriverPreferences
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.HomeDashboardSummary
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.ServiceZone
import com.drivepartner.app.data.remote.dto.EligibilityBlockingReason
import com.drivepartner.app.data.remote.dto.LocationPingAckResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Isolated demo fixture models. These types are distinct from live financial/order DTOs so
 * demo earnings or orders can never be mixed with PostgreSQL ledger or dispatch records.
 */
data class DemoEarningsSnapshot(
    val todayDemoEarning: MoneyPaise,
    val note: String = "DEMO ONLY — Not connected to live ledger or payouts"
)

data class DemoOrderFixture(
    val demoOrderId: String,
    val serviceType: ServiceType,
    val pickupLabel: String,
    val dropLabel: String,
    val estimatedDemoFare: MoneyPaise
)

/**
 * Isolated local testing data source for the `demo` configuration ONLY.
 * Throws immediately if someone attempts to instantiate it under a `LIVE` configuration.
 */
class DemoDataSource(
    environmentConfig: AppEnvironmentConfig
) : PartnerDataSource {

    init {
        require(environmentConfig.isDemoMode) {
            "DemoDataSource is strictly restricted to DEMO configuration and cannot run in LIVE mode."
        }
    }

    override val origin: DataOrigin = DataOrigin.DEMO

    private val _partnerStatus = MutableStateFlow(PartnerStatus.OFFLINE)
    override val partnerStatus: StateFlow<PartnerStatus> = _partnerStatus.asStateFlow()

    private val _dutyControlState = MutableStateFlow(DutyControlState.OFFLINE)
    val dutyControlState: StateFlow<DutyControlState> = _dutyControlState.asStateFlow()

    private val _partnerProfile = MutableStateFlow<PartnerProfile?>(
        PartnerProfile(
            id = "DEMO-DP-0001",
            name = "Demo Driver",
            phone = "+91 00000 00000",
            vehicleNumber = "DEMO-VEHICLE",
            vehicleModel = "Local Test Vehicle",
            serviceType = ServiceType.BIKE_TAXI,
            city = "Jaipur (DEMO)",
            isVerified = true,
            documentsVerified = true,
            rating = 0.0f,
            isDemoProfile = true
        )
    )
    override val partnerProfile: StateFlow<PartnerProfile?> = _partnerProfile.asStateFlow()

    private val _lastBoundaryMessage = MutableStateFlow<String?>(
        "DEMO MODE: Isolated local test data only. No live orders, ledger, or payouts."
    )
    override val lastBoundaryMessage: StateFlow<String?> = _lastBoundaryMessage.asStateFlow()

    private val _demoZones = MutableStateFlow(
        listOf(
            ServiceZone(
                zoneId = "DEMO-ZONE-MI-ROAD",
                cityCode = "JAI",
                name = "MI Road & Sindhi Camp",
                isEnabled = true,
                isDemoZone = true
            ),
            ServiceZone(
                zoneId = "DEMO-ZONE-MALVIYA",
                cityCode = "JAI",
                name = "Malviya Nagar",
                isEnabled = true,
                isDemoZone = true
            ),
            ServiceZone(
                zoneId = "DEMO-ZONE-VAISHALI",
                cityCode = "JAI",
                name = "Vaishali Nagar",
                isEnabled = true,
                isDemoZone = true
            )
        )
    )
    val demoZones: StateFlow<List<ServiceZone>> = _demoZones.asStateFlow()

    private val _demoEligibility = MutableStateFlow(
        DriverEligibility(
            isEligibleForOnline = true,
            blockingReasons = emptyList(),
            approvedServiceCategoryCodes = ServiceType.entries.map { it.code },
            assignedZoneIds = listOf("DEMO-ZONE-MI-ROAD", "DEMO-ZONE-MALVIYA"),
            evaluatedAtUtc = DriverLocationSnapshot.formatUtcIso8601(System.currentTimeMillis())
        )
    )
    val demoEligibility: StateFlow<DriverEligibility> = _demoEligibility.asStateFlow()

    private val _demoPreferences = MutableStateFlow(
        DriverPreferences(
            preferredZoneIds = listOf("DEMO-ZONE-MI-ROAD"),
            autoBoostEnabled = false,
            activeServiceType = ServiceType.BIKE_TAXI
        )
    )
    val demoPreferences: StateFlow<DriverPreferences> = _demoPreferences.asStateFlow()

    private val _demoEarnings = MutableStateFlow(
        DemoEarningsSnapshot(todayDemoEarning = MoneyPaise.ZERO_INR)
    )
    val demoEarnings: StateFlow<DemoEarningsSnapshot> = _demoEarnings.asStateFlow()

    private val _demoOrders = MutableStateFlow<List<DemoOrderFixture>>(emptyList())
    val demoOrders: StateFlow<List<DemoOrderFixture>> = _demoOrders.asStateFlow()

    private val _homeSummary = MutableStateFlow(
        HomeDashboardSummary(
            dutyControlState = DutyControlState.OFFLINE,
            todayCompletedCount = 0,
            todayEstimatedEarnings = MoneyPaise.ZERO_INR,
            activeZones = _demoZones.value,
            lastSyncedUtc = DriverLocationSnapshot.formatUtcIso8601(System.currentTimeMillis()),
            dataOrigin = DataOrigin.DEMO
        )
    )
    val homeSummary: StateFlow<HomeDashboardSummary> = _homeSummary.asStateFlow()

    fun setDemoEligibilityForTesting(
        isEligible: Boolean,
        blockingReasons: List<EligibilityBlockingReason> = emptyList()
    ) {
        val next = _demoEligibility.value.copy(
            isEligibleForOnline = isEligible,
            blockingReasons = if (isEligible) emptyList() else blockingReasons.ifEmpty {
                listOf(EligibilityBlockingReason.DOCUMENT_EXPIRED)
            },
            evaluatedAtUtc = DriverLocationSnapshot.formatUtcIso8601(System.currentTimeMillis())
        )
        _demoEligibility.value = next
        _partnerProfile.update { profile ->
            profile?.copy(
                isVerified = isEligible,
                documentsVerified = isEligible
            )
        }
        if (!isEligible && _dutyControlState.value.isOnline) {
            _partnerStatus.value = PartnerStatus.OFFLINE
            _dutyControlState.value = DutyControlState.BLOCKED
            _homeSummary.update { it.copy(dutyControlState = DutyControlState.BLOCKED) }
        }
    }

    override suspend fun setDutyStatus(isOnline: Boolean): ApiResult<PartnerStatus> {
        if (isOnline && !_demoEligibility.value.isEligibleForOnline) {
            _partnerStatus.value = PartnerStatus.OFFLINE
            _dutyControlState.value = DutyControlState.BLOCKED
            _homeSummary.update { it.copy(dutyControlState = DutyControlState.BLOCKED) }
            return ApiResult.HttpError(
                httpStatus = 403,
                error = ApiErrorEnvelope(
                    code = "DRIVER_INELIGIBLE",
                    message = "Demo eligibility check blocked going online."
                )
            )
        }
        val next = PartnerStatus.fromBoolean(isOnline)
        val nextControl = if (isOnline) DutyControlState.ONLINE else DutyControlState.OFFLINE
        _partnerStatus.value = next
        _dutyControlState.value = nextControl
        _homeSummary.update {
            it.copy(
                dutyControlState = nextControl,
                lastSyncedUtc = DriverLocationSnapshot.formatUtcIso8601(System.currentTimeMillis())
            )
        }
        return ApiResult.Success(next, origin = DataOrigin.DEMO)
    }

    override suspend fun toggleDutyStatus(): ApiResult<PartnerStatus> =
        setDutyStatus(!_partnerStatus.value.isOnline)

    override suspend fun switchServiceType(serviceType: ServiceType): ApiResult<Unit> {
        _partnerProfile.update { current -> current?.copy(serviceType = serviceType) }
        _demoPreferences.update { current -> current.copy(activeServiceType = serviceType) }
        return ApiResult.Success(Unit, origin = DataOrigin.DEMO)
    }

    fun updateDemoPreferences(
        preferredZoneIds: List<String>,
        autoBoostEnabled: Boolean,
        activeServiceType: ServiceType
    ): ApiResult<DriverPreferences> {
        val updated = DriverPreferences(
            preferredZoneIds = preferredZoneIds,
            autoBoostEnabled = autoBoostEnabled,
            activeServiceType = activeServiceType
        )
        _demoPreferences.value = updated
        _partnerProfile.update { it?.copy(serviceType = activeServiceType) }
        return ApiResult.Success(updated, origin = DataOrigin.DEMO)
    }

    fun recordDemoLocationPing(snapshot: DriverLocationSnapshot): ApiResult<LocationPingAckResponse> {
        return ApiResult.Success(
            data = LocationPingAckResponse(
                accepted = _dutyControlState.value.isOnline,
                staleAfterSeconds = 90,
                serverTimeUtc = snapshot.capturedAtUtc
            ),
            origin = DataOrigin.DEMO
        )
    }

    fun updateDemoIdentityFromOnboarding(
        fullName: String,
        phoneMasked: String,
        cityCode: String,
        vehicleNumber: String?,
        vehicleModel: String?,
        serviceType: ServiceType?,
        isVerified: Boolean,
        documentsVerified: Boolean
    ) {
        _partnerProfile.update { current ->
            val base = current ?: PartnerProfile(
                id = "DEMO-DP-0001",
                name = fullName,
                phone = phoneMasked,
                vehicleNumber = vehicleNumber ?: "",
                serviceType = serviceType ?: ServiceType.BIKE_TAXI,
                city = cityCode,
                vehicleModel = vehicleModel ?: "",
                isVerified = isVerified,
                documentsVerified = documentsVerified,
                isDemoProfile = true
            )
            base.copy(
                name = fullName.ifBlank { base.name },
                phone = phoneMasked.ifBlank { base.phone },
                city = cityCode.ifBlank { base.city },
                vehicleNumber = vehicleNumber ?: base.vehicleNumber,
                vehicleModel = vehicleModel ?: base.vehicleModel,
                serviceType = serviceType ?: base.serviceType,
                isVerified = isVerified,
                documentsVerified = documentsVerified
            )
        }
        if (serviceType != null) {
            _demoPreferences.update { it.copy(activeServiceType = serviceType) }
        }
        setDemoEligibilityForTesting(isEligible = isVerified && documentsVerified)
    }

    override suspend fun refreshFromSource(): ApiResult<Unit> {
        _homeSummary.update {
            it.copy(
                activeZones = _demoZones.value,
                lastSyncedUtc = DriverLocationSnapshot.formatUtcIso8601(System.currentTimeMillis())
            )
        }
        return ApiResult.Success(Unit, origin = DataOrigin.DEMO)
    }
}
