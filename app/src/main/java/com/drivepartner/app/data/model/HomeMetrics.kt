package com.drivepartner.app.data.model

import androidx.annotation.StringRes
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.core.config.DataOrigin

/**
 * Screen 5 (spec section 9): "today's earnings summary" shown at the top of the home dashboard.
 *
 * Every figure is sourced from the backend (earnings summary + trip history). When the backend has
 * not published a figure the field is `null` and the UI renders an explicit "not available" state —
 * values are never estimated or invented on device.
 */
data class HomeTodayMetrics(
    val totalEarnings: MoneyPaise? = null,
    val incentiveEarnings: MoneyPaise? = null,
    val completedRides: Int? = null,
    val totalDistanceMeters: Int? = null,
    /** How many completed rides the distance figure actually covers (details are fetched in a budget). */
    val distanceCoversRides: Int = 0,
    val lastUpdatedUtc: String? = null,
    val dataOrigin: DataOrigin = DataOrigin.LIVE
) {
    val isComplete: Boolean
        get() = totalEarnings != null && completedRides != null
}

/**
 * Screen 5 performance card: daily progress against a target plus weekly performance.
 *
 * `dailyTarget` / `weekly*` are nullable on purpose: the platform does not ship a hard-coded target,
 * so until the backend publishes one the card says so instead of showing a fabricated goal.
 */
data class HomePerformanceSnapshot(
    val dailyCompletedOrders: Int = 0,
    val dailyTarget: Int? = null,
    val weeklyCompletedOrders: Int? = null,
    val weeklyTarget: Int? = null,
    val weeklyEarnings: MoneyPaise? = null,
    val acceptanceRatePercent: Int? = null,
    val cancellationRatePercent: Int? = null,
    val onlineMinutesToday: Int? = null,
    /** True only when every figure above came from a backend response. */
    val isBackendProvided: Boolean = false,
    val dataOrigin: DataOrigin = DataOrigin.LIVE
) {
    /** 0..1 progress towards the daily target, or null when no target is configured. */
    val dailyTargetProgress: Float?
        get() {
            val target = dailyTarget ?: return null
            if (target <= 0) return null
            return (dailyCompletedOrders.toFloat() / target.toFloat()).coerceIn(0f, 1f)
        }

    val remainingToDailyTarget: Int?
        get() {
            val target = dailyTarget ?: return null
            return (target - dailyCompletedOrders).coerceAtLeast(0)
        }
}

/** One improvement suggestion rendered under the performance card. */
data class PerformanceSuggestion(
    val id: String,
    @StringRes val messageRes: Int,
    val messageArg: Int? = null,
    val accent: SuggestionAccent = SuggestionAccent.NEUTRAL
)

enum class SuggestionAccent { NEUTRAL, POSITIVE, CAUTION }
