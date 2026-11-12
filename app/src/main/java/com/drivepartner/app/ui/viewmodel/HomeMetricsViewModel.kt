package com.drivepartner.app.ui.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drivepartner.app.core.di.AppContainer
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.data.local.PerformanceTargetRepository
import com.drivepartner.app.data.model.EarningsSummary
import com.drivepartner.app.data.model.HomePerformanceSnapshot
import com.drivepartner.app.data.model.HomeTodayMetrics
import com.drivepartner.app.data.model.OrderHistoryItem
import com.drivepartner.app.data.model.PerformanceSuggestion
import com.drivepartner.app.data.model.SuggestionAccent
import com.drivepartner.app.data.remote.dto.OrderHistoryFilter
import com.drivepartner.app.data.remote.dto.TripStatus
import com.drivepartner.app.data.repository.FinancialRepository
import com.drivepartner.app.data.repository.TripHistoryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeMetricsUiState(
    val today: HomeTodayMetrics? = null,
    val performance: HomePerformanceSnapshot = HomePerformanceSnapshot(),
    val suggestions: List<PerformanceSuggestion> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    @StringRes val errorMessageRes: Int? = null,
    val dataOrigin: DataOrigin = DataOrigin.LIVE
)

/**
 * Screen 5 (spec section 9): today's earnings summary plus the performance card.
 *
 * Money comes from the earnings-summary endpoint; ride counts and distance come from trip history.
 * Figures the backend does not publish stay null so the UI can say "not available" instead of
 * inventing numbers. Distance is read from order details inside a fixed request budget, and the
 * card states how many rides that figure covers when the budget is smaller than the day.
 */
class HomeMetricsViewModel(
    private val financialRepository: FinancialRepository,
    private val tripHistoryRepository: TripHistoryRepository,
    private val performanceTargetRepository: PerformanceTargetRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeMetricsUiState(dataOrigin = financialRepository.origin)
    )
    val uiState: StateFlow<HomeMetricsUiState> = _uiState.asStateFlow()

    private var todayEarnings: EarningsSummary? = null
    private var weekEarnings: EarningsSummary? = null
    private var historyItems: List<OrderHistoryItem> = emptyList()
    private var isHistoryComplete: Boolean = false
    private var distanceMeters: Int? = null
    private var distanceCoversRides: Int = 0
    private var personalDailyTarget: Int? = null
    private var isLoading: Boolean = true
    private var isRefreshing: Boolean = false

    @StringRes
    private var errorMessageRes: Int? = null

    private val utcDayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        viewModelScope.launch {
            performanceTargetRepository.dailyTarget.collect { target ->
                personalDailyTarget = target
                publish()
            }
        }
        viewModelScope.launch {
            tripHistoryRepository.orderHistory.collect { items ->
                historyItems = items
                publish()
            }
        }
        viewModelScope.launch {
            tripHistoryRepository.hasMoreHistory.collect { hasMore ->
                isHistoryComplete = !hasMore
                publish()
            }
        }
        refresh()
    }

    /** Reloads money and history from the backend (also wired to pull-to-refresh). */
    fun refresh() {
        if (isRefreshing) return
        viewModelScope.launch {
            isRefreshing = true
            errorMessageRes = null
            publish()

            val todayResult = financialRepository.fetchEarningsSummary(PERIOD_TODAY)
            val weekResult = financialRepository.fetchEarningsSummary(PERIOD_WEEK)
            when (todayResult) {
                is ApiResult.Success -> todayEarnings = todayResult.data
                is ApiResult.HttpError,
                is ApiResult.NetworkError,
                is ApiResult.BackendNotDeployed -> errorMessageRes = R.string.home_metrics_error
            }
            weekEarnings = (weekResult as? ApiResult.Success)?.data

            val historyResult = tripHistoryRepository.fetchOrderHistory(
                filter = OrderHistoryFilter.ALL,
                limit = HISTORY_LIMIT
            )
            when (historyResult) {
                is ApiResult.Success -> {
                    historyItems = historyResult.data.items
                    isHistoryComplete = !historyResult.data.hasMore
                }
                is ApiResult.HttpError,
                is ApiResult.NetworkError,
                is ApiResult.BackendNotDeployed -> {
                    if (errorMessageRes == null) errorMessageRes = R.string.home_metrics_error
                }
            }

            loadTodayDistance()
            isRefreshing = false
            isLoading = false
            publish()
        }
    }

    fun setDailyTarget(target: Int?) {
        viewModelScope.launch {
            runCatching { performanceTargetRepository.setDailyTarget(target) }
                .onSuccess { personalDailyTarget = target }
            publish()
        }
    }

    /** Fetches order details for today's completed rides inside [DISTANCE_DETAIL_BUDGET]. */
    private suspend fun loadTodayDistance() {
        val completedToday = completedRidesSince(historyItems, days = 0)
        if (completedToday.isEmpty()) {
            distanceMeters = 0
            distanceCoversRides = 0
            return
        }
        var total = 0
        var covered = 0
        completedToday.take(DISTANCE_DETAIL_BUDGET).forEach { item ->
            when (val detail = tripHistoryRepository.fetchOrderDetail(item.orderId)) {
                is ApiResult.Success -> {
                    total += detail.data.distanceMeters
                    covered += 1
                }
                else -> Unit
            }
        }
        distanceMeters = if (covered > 0) total else null
        distanceCoversRides = covered
    }

    private fun publish() {
        val dataOrigin = financialRepository.origin
        val completedToday = completedRidesSince(historyItems, days = 0).size
        val weeklyCompleted = if (isHistoryComplete) {
            completedRidesSince(historyItems, days = WEEK_DAYS).size
        } else {
            null
        }
        val cancellationRate = if (isHistoryComplete) {
            cancellationRatePercent(historyItems)
        } else {
            null
        }

        val today = todayEarnings?.let { summary ->
            HomeTodayMetrics(
                totalEarnings = summary.finalizedTotal,
                incentiveEarnings = summary.incentive,
                completedRides = completedToday,
                totalDistanceMeters = distanceMeters,
                distanceCoversRides = distanceCoversRides,
                lastUpdatedUtc = summary.lastUpdatedUtc,
                dataOrigin = dataOrigin
            )
        }

        val performance = HomePerformanceSnapshot(
            dailyCompletedOrders = completedToday,
            dailyTarget = personalDailyTarget,
            weeklyCompletedOrders = weeklyCompleted,
            weeklyTarget = null,
            weeklyEarnings = weekEarnings?.finalizedTotal,
            acceptanceRatePercent = null,
            cancellationRatePercent = cancellationRate,
            onlineMinutesToday = null,
            isBackendProvided = todayEarnings != null && isHistoryComplete,
            dataOrigin = dataOrigin
        )

        _uiState.value = HomeMetricsUiState(
            today = today,
            performance = performance,
            suggestions = buildSuggestions(performance),
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            errorMessageRes = errorMessageRes,
            dataOrigin = dataOrigin
        )
    }

    private fun buildSuggestions(performance: HomePerformanceSnapshot): List<PerformanceSuggestion> {
        val suggestions = mutableListOf<PerformanceSuggestion>()
        val remaining = performance.remainingToDailyTarget
        if (performance.dailyTarget != null && remaining != null) {
            if (remaining > 0) {
                suggestions += PerformanceSuggestion(
                    id = "target_progress",
                    messageRes = R.string.home_suggestion_target_progress,
                    messageArg = remaining
                )
            } else {
                suggestions += PerformanceSuggestion(
                    id = "target_reached",
                    messageRes = R.string.home_suggestion_target_reached,
                    accent = SuggestionAccent.POSITIVE
                )
            }
        }
        val cancellation = performance.cancellationRatePercent
        if (cancellation != null && cancellation >= CANCELLATION_ALERT_PERCENT) {
            suggestions += PerformanceSuggestion(
                id = "cancellation",
                messageRes = R.string.home_suggestion_cancellation,
                accent = SuggestionAccent.CAUTION
            )
        }
        suggestions += PerformanceSuggestion(
            id = "acceptance",
            messageRes = R.string.home_suggestion_acceptance
        )
        suggestions += PerformanceSuggestion(
            id = "zone",
            messageRes = R.string.home_suggestion_zone
        )
        return suggestions.take(MAX_SUGGESTIONS)
    }

    /** Completed rides finished within the last [days] UTC days (0 = today). */
    private fun completedRidesSince(items: List<OrderHistoryItem>, days: Int): List<OrderHistoryItem> {
        val cutoffDay = dayOffsetFromToday(-days)
        return items.filter { item ->
            item.status == TripStatus.COMPLETED &&
                dayOf(item.completedOrCancelledAtUtc)?.let { it >= cutoffDay } == true
        }
    }

    private fun cancellationRatePercent(items: List<OrderHistoryItem>): Int? {
        val finished = items.filter {
            it.status == TripStatus.COMPLETED || it.status == TripStatus.CANCELLED
        }
        if (finished.isEmpty()) return null
        val cancelled = finished.count { it.status == TripStatus.CANCELLED }
        return (cancelled * 100) / finished.size
    }

    /** UTC midnight of today, shifted by [offsetDays]. */
    private fun dayOffsetFromToday(offsetDays: Int): Long {
        val now = Date().time
        val todayUtcMidnight = now - now % DAY_MILLIS
        return todayUtcMidnight + offsetDays * DAY_MILLIS
    }

    /** Day bucket (UTC midnight millis) of an ISO-8601 timestamp, or null when unparseable. */
    private fun dayOf(timestampUtc: String?): Long? {
        if (timestampUtc.isNullOrBlank()) return null
        val normalized = timestampUtc.trim().removeSuffix("Z")
        val datePart = normalized.take(10)
        return runCatching { utcDayFormat.parse(datePart)?.time }.getOrNull()
    }

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeMetricsViewModel(
                    financialRepository = appContainer.financialRepository,
                    tripHistoryRepository = appContainer.tripHistoryRepository,
                    performanceTargetRepository = appContainer.performanceTargetRepository
                )
            }
        }
    }

}

private const val PERIOD_TODAY = "today"
private const val PERIOD_WEEK = "week"
private const val HISTORY_LIMIT = 40
private const val WEEK_DAYS = 6

/** Order-detail requests spent on today's distance figure. */
private const val DISTANCE_DETAIL_BUDGET = 6
private const val MAX_SUGGESTIONS = 4
private const val CANCELLATION_ALERT_PERCENT = 10
private const val DAY_MILLIS = 86_400_000L
