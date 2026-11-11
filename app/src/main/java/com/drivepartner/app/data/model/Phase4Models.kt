package com.drivepartner.app.data.model

import androidx.annotation.StringRes
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.data.remote.dto.AcceptOfferResponse
import com.drivepartner.app.data.remote.dto.OfferStatus
import com.drivepartner.app.data.remote.dto.TripStatus
import com.drivepartner.app.data.remote.dto.WorkOfferDto
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Domain model for an incoming work offer in Phase 4 (strictly separated from DTOs).
 * All money fields use integer minor units ([MoneyPaise]), distances use meters,
 * durations use seconds, and countdown calculations are offset-corrected against serverNowUtc.
 */
data class WorkOffer(
    val offerId: String,
    val orderId: String,
    val serviceType: ServiceType,
    val serviceCategoryCode: String,
    val priorityLabel: String? = null,
    val pickupSummary: String,
    val dropSummary: String,
    val pickupDistanceMeters: Int,
    val tripDistanceMeters: Int,
    val durationSeconds: Int,
    val estimatedBase: MoneyPaise,
    val eligibleIncentive: MoneyPaise,
    val estimatedTotal: MoneyPaise,
    val isEstimate: Boolean = true,
    val expiresAtEpochMs: Long,
    val serverNowEpochMs: Long,
    val serverClockOffsetMs: Long,
    val status: OfferStatus,
    val dataOrigin: DataOrigin
) {
    fun remainingMillis(clientNowEpochMs: Long): Long {
        val estimatedServerNowMs = clientNowEpochMs + serverClockOffsetMs
        return (expiresAtEpochMs - estimatedServerNowMs).coerceAtLeast(0L)
    }

    fun remainingSeconds(clientNowEpochMs: Long): Int {
        val remMs = remainingMillis(clientNowEpochMs)
        return if (remMs <= 0L) 0 else ((remMs + 999L) / 1000L).toInt()
    }

    fun isExpired(clientNowEpochMs: Long): Boolean =
        status == OfferStatus.EXPIRED || remainingMillis(clientNowEpochMs) <= 0L
}

data class AcceptedOfferAssignment(
    val tripId: String,
    val orderId: String,
    val tripStatus: TripStatus,
    val acceptedAtUtc: String,
    val dataOrigin: DataOrigin
)

enum class OfferAction {
    ACCEPT,
    REJECT
}

enum class OfferRejectReason(
    val code: String,
    @StringRes val labelRes: Int
) {
    DISTANCE_TOO_FAR("DISTANCE_TOO_FAR", R.string.offer_reject_reason_far),
    LOW_ESTIMATE("LOW_ESTIMATE", R.string.offer_reject_reason_fare),
    BREAK_NEEDED("BREAK_NEEDED", R.string.offer_reject_reason_break),
    OTHER("OTHER", R.string.offer_reject_reason_other)
}

sealed interface OfferOutcomeNotice {
    data class Accepted(val assignment: AcceptedOfferAssignment) : OfferOutcomeNotice
    data class Rejected(val offerId: String) : OfferOutcomeNotice
    data class AlreadyAssignedToOtherDriver(
        val offerId: String,
        val serverMessage: String? = null
    ) : OfferOutcomeNotice

    data class OfferExpired(
        val offerId: String,
        val serverMessage: String? = null
    ) : OfferOutcomeNotice

    data class DriverNotEligibleOrOffline(val serverMessage: String) : OfferOutcomeNotice

    /**
     * Explicitly clarifies that accept failures are NEVER queued offline.
     */
    data class NetworkFailedNoQueue(
        val offerId: String,
        val causeMessage: String
    ) : OfferOutcomeNotice

    data class BackendNotDeployed(val reason: String) : OfferOutcomeNotice
}

object DispatchTimestampParser {
    private val utcZone: TimeZone = TimeZone.getTimeZone("UTC")
    private val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX"
    )

    fun parseUtcIso8601EpochMs(rawIsoUtc: String): Long? {
        val normalized = normalizeFractionalSeconds(rawIsoUtc.trim())
        if (normalized.isEmpty()) return null
        for (pattern in patterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = utcZone
                    isLenient = false
                }
                val parsed = parser.parse(normalized)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // Try next ISO-8601 variant
            }
        }
        return null
    }

    private fun normalizeFractionalSeconds(raw: String): String {
        val dotIndex = raw.indexOf('.')
        if (dotIndex < 0) return raw
        val tzStart = raw.indexOfAny(charArrayOf('Z', '+', '-'), startIndex = dotIndex + 1)
        if (tzStart < 0) return raw
        val fraction = raw.substring(dotIndex + 1, tzStart)
        val millis = fraction.padEnd(3, '0').take(3)
        return raw.substring(0, dotIndex + 1) + millis + raw.substring(tzStart)
    }

    fun formatDistanceDisplay(meters: Int): String {
        val safeMeters = meters.coerceAtLeast(0)
        return if (safeMeters < 1000) {
            "$safeMeters m"
        } else {
            String.format(Locale.US, "%.1f km", safeMeters / 1000.0)
        }
    }

    fun formatDurationDisplay(seconds: Int): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        val totalMinutes = safeSeconds / 60
        return when {
            totalMinutes < 1 -> "< 1 min"
            totalMinutes < 60 -> "$totalMinutes min"
            else -> {
                val hours = totalMinutes / 60
                val remMins = totalMinutes % 60
                if (remMins == 0) "${hours}h" else "${hours}h ${remMins}m"
            }
        }
    }
}

fun WorkOfferDto.toDomain(
    dataOrigin: DataOrigin,
    receivedAtClientEpochMs: Long
): WorkOffer {
    val parsedServerNowMs = DispatchTimestampParser.parseUtcIso8601EpochMs(serverNowUtc)
        ?: receivedAtClientEpochMs
    val parsedExpiresAtMs = DispatchTimestampParser.parseUtcIso8601EpochMs(expiresAtUtc)
        ?: parsedServerNowMs
    val clockOffsetMs = parsedServerNowMs - receivedAtClientEpochMs

    return WorkOffer(
        offerId = offerId,
        orderId = orderId,
        serviceType = ServiceType.fromCode(serviceCategoryCode),
        serviceCategoryCode = serviceCategoryCode,
        priorityLabel = priorityLabel?.takeIf { it.isNotBlank() },
        pickupSummary = pickupSummary,
        dropSummary = dropSummary,
        pickupDistanceMeters = pickupDistanceMeters.coerceAtLeast(0),
        tripDistanceMeters = distanceMeters.coerceAtLeast(0),
        durationSeconds = durationSeconds.coerceAtLeast(0),
        estimatedBase = MoneyPaise(estimatedBaseMinor, currency),
        eligibleIncentive = MoneyPaise(eligibleIncentiveMinor, currency),
        estimatedTotal = MoneyPaise(estimatedTotalMinor, currency),
        isEstimate = isEstimate,
        expiresAtEpochMs = parsedExpiresAtMs,
        serverNowEpochMs = parsedServerNowMs,
        serverClockOffsetMs = clockOffsetMs,
        status = status,
        dataOrigin = dataOrigin
    )
}

fun AcceptOfferResponse.toDomain(dataOrigin: DataOrigin): AcceptedOfferAssignment =
    AcceptedOfferAssignment(
        tripId = tripId,
        orderId = orderId,
        tripStatus = tripStatus,
        acceptedAtUtc = acceptedAtUtc,
        dataOrigin = dataOrigin
    )
