package com.drivepartner.app.data.model

import androidx.annotation.StringRes
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.data.remote.dto.EligibilityBlockingReason

/**
 * Six explicit duty control states per Design.md Section 5.
 */
enum class DutyControlState(
    @StringRes val labelRes: Int,
    val isOnline: Boolean,
    val isTransitionPending: Boolean
) {
    OFFLINE(R.string.duty_state_offline, isOnline = false, isTransitionPending = false),
    TURNING_ONLINE(R.string.duty_state_turning_online, isOnline = false, isTransitionPending = true),
    ONLINE(R.string.duty_state_online, isOnline = true, isTransitionPending = false),
    TURNING_OFFLINE(R.string.duty_state_turning_offline, isOnline = true, isTransitionPending = true),
    BLOCKED(R.string.duty_state_blocked, isOnline = false, isTransitionPending = false),
    CONNECTION_UNKNOWN(R.string.duty_state_connection_unknown, isOnline = false, isTransitionPending = false);

    fun toPartnerStatus(): PartnerStatus =
        if (isOnline) PartnerStatus.ONLINE else PartnerStatus.OFFLINE
}

/**
 * Domain model for server-authoritative driver eligibility check (Phase 3).
 * UI must not override blocking reasons returned by backend.
 */
data class DriverEligibility(
    val isEligibleForOnline: Boolean,
    val blockingReasons: List<EligibilityBlockingReason>,
    val approvedServiceCategoryCodes: List<String>,
    val assignedZoneIds: List<String>,
    val evaluatedAtUtc: String
)

/**
 * Domain model representing a backend-configured service zone.
 * No fabricated demand or surge multipliers are stored or displayed here.
 */
data class ServiceZone(
    val zoneId: String,
    val cityCode: String,
    val name: String,
    val isEnabled: Boolean,
    val isDemoZone: Boolean = false
)

/**
 * Domain model for Phase 3 service-area & Auto Boost preferences.
 * Auto Boost is a backend preference ONLY and NEVER enables auto-accept (ADR-008).
 */
data class DriverPreferences(
    val preferredZoneIds: List<String>,
    val autoBoostEnabled: Boolean,
    val activeServiceType: ServiceType
)

/**
 * Domain model for Home Dashboard operational summary.
 */
data class HomeDashboardSummary(
    val dutyControlState: DutyControlState,
    val todayCompletedCount: Int,
    val todayEstimatedEarnings: MoneyPaise,
    val activeZones: List<ServiceZone>,
    val lastSyncedUtc: String?,
    val dataOrigin: DataOrigin
)
