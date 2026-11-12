package com.drivepartner.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.di.AppContainer
import com.drivepartner.app.core.location.DriverLocationProvider
import com.drivepartner.app.core.network.ApiResult
import com.drivepartner.app.core.realtime.DispatchRealtimeBoundary
import com.drivepartner.app.core.realtime.OfferIdempotencyTracker
import com.drivepartner.app.core.realtime.RealtimeConnectionState
import com.drivepartner.app.core.realtime.RealtimeSignal
import com.drivepartner.app.core.realtime.TripTransitionIdempotencyTracker
import com.drivepartner.app.data.model.AcceptedOfferAssignment
import com.drivepartner.app.data.model.ActiveTrip
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.OfferAction
import com.drivepartner.app.data.model.OfferOutcomeNotice
import com.drivepartner.app.data.model.OfferRejectReason
import com.drivepartner.app.data.model.OrderDetail
import com.drivepartner.app.data.model.OrderHistoryItem
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.TripCancelReason
import com.drivepartner.app.data.model.TripOutcomeNotice
import com.drivepartner.app.data.model.WorkOffer
import com.drivepartner.app.data.remote.dto.DispatchErrorCodes
import com.drivepartner.app.data.remote.dto.OrderHistoryFilter
import com.drivepartner.app.data.remote.dto.TripErrorCodes
import com.drivepartner.app.data.remote.dto.TripStatus
import com.drivepartner.app.data.repository.DispatchRepository
import com.drivepartner.app.data.repository.DutyLocationRepository
import com.drivepartner.app.data.repository.PartnerRepository
import com.drivepartner.app.data.repository.TripHistoryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class OrdersTab {
    ACTIVE,
    COMPLETED,
    CANCELLED
}

data class OrdersUiState(
    val dataOrigin: DataOrigin = DataOrigin.LIVE,
    val selectedTab: OrdersTab = OrdersTab.ACTIVE,
    val partnerStatus: PartnerStatus = PartnerStatus.OFFLINE,
    val dutyControlState: DutyControlState = DutyControlState.OFFLINE,
    val realtimeConnectionState: RealtimeConnectionState = RealtimeConnectionState.UNCONFIGURED,
    val activeOffers: List<WorkOffer> = emptyList(),
    val remainingSecondsByOfferId: Map<String, Int> = emptyMap(),
    val isFetchingOffers: Boolean = false,
    val inFlightOfferId: String? = null,
    val inFlightAction: OfferAction? = null,
    val acceptedAssignment: AcceptedOfferAssignment? = null,
    val outcomeNotice: OfferOutcomeNotice? = null,
    val boundaryMessage: String? = null,
    val demoSimulateRaceConflict: Boolean = false,
    val isHistoryConnected: Boolean = false,
    // Phase 5 Active Trip & History State
    val activeTrip: ActiveTrip? = null,
    val isFetchingActiveTrip: Boolean = false,
    val inFlightTripTargetStatus: TripStatus? = null,
    val tripOutcomeNotice: TripOutcomeNotice? = null,
    val historyFilter: OrderHistoryFilter = OrderHistoryFilter.COMPLETED,
    val historyItems: List<OrderHistoryItem> = emptyList(),
    val hasMoreHistory: Boolean = false,
    val historyNextCursor: String? = null,
    val isFetchingHistory: Boolean = false,
    val isLoadingMoreHistory: Boolean = false,
    val historyErrorMessage: String? = null,
    val selectedOrderDetail: OrderDetail? = null,
    val isLoadingOrderDetail: Boolean = false,
    val orderDetailErrorMessage: String? = null
)

/**
 * Phase 4 + Phase 5 Orders, Dispatch, Active Trip Lifecycle, and Order History ViewModel.
 * - Backend remains the sole authority for all offer acceptance & trip status transitions.
 * - Never queues offers or trip transitions offline.
 * - Never calculates final earnings on Android.
 * - Restores persisted/active trips on startup and resume.
 */
class OrdersViewModel(
    private val repository: PartnerRepository,
    private val dutyLocationRepository: DutyLocationRepository? = null,
    private val dispatchRepository: DispatchRepository? = null,
    private val realtimeBoundary: DispatchRealtimeBoundary? = null,
    private val idempotencyTracker: OfferIdempotencyTracker = OfferIdempotencyTracker(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val enableCountdownTicker: Boolean = true,
    private val tripHistoryRepository: TripHistoryRepository? = null,
    private val tripTransitionIdempotencyTracker: TripTransitionIdempotencyTracker = TripTransitionIdempotencyTracker(),
    private val locationProvider: DriverLocationProvider? = null
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(OrdersTab.ACTIVE)
    private val _isFetchingOffers = MutableStateFlow(false)
    private val _inFlightOfferId = MutableStateFlow<String?>(null)
    private val _inFlightAction = MutableStateFlow<OfferAction?>(null)
    private val _outcomeNotice = MutableStateFlow<OfferOutcomeNotice?>(null)
    private val _tickClockMs = MutableStateFlow(clockMillis())
    private val _demoSimulateRaceConflict = MutableStateFlow(false)

    private val _isFetchingActiveTrip = MutableStateFlow(false)
    private val _inFlightTripTargetStatus = MutableStateFlow<TripStatus?>(null)
    private val _tripOutcomeNotice = MutableStateFlow<TripOutcomeNotice?>(null)

    private val _historyFilter = MutableStateFlow(OrderHistoryFilter.COMPLETED)
    private val _isFetchingHistory = MutableStateFlow(false)
    private val _isLoadingMoreHistory = MutableStateFlow(false)
    private val _historyErrorMessage = MutableStateFlow<String?>(null)

    private val _selectedOrderDetail = MutableStateFlow<OrderDetail?>(null)
    private val _isLoadingOrderDetail = MutableStateFlow(false)
    private val _orderDetailErrorMessage = MutableStateFlow<String?>(null)

    private var countdownJob: Job? = null

    private val partnerFlows = combine(
        _selectedTab,
        repository.partnerStatus,
        repository.boundaryMessage,
        dutyLocationRepository?.dutyControlState ?: flowOf(DutyControlState.OFFLINE)
    ) { tab, status, boundaryMsg, dutyState ->
        PartnerSnapshot(tab, status, boundaryMsg, dutyState)
    }

    private val dispatchFlows = combine(
        dispatchRepository?.activeOffers ?: flowOf(emptyList()),
        dispatchRepository?.lastAcceptedAssignment ?: flowOf(null),
        dispatchRepository?.lastErrorMessage ?: flowOf(null),
        realtimeBoundary?.connectionState ?: flowOf(RealtimeConnectionState.UNCONFIGURED)
    ) { offers, assignment, repoError, rtState ->
        DispatchSnapshot(offers, assignment, repoError, rtState)
    }

    private val actionFlows = combine(
        _isFetchingOffers,
        _inFlightOfferId,
        _inFlightAction,
        _outcomeNotice,
        _tickClockMs
    ) { fetching, inFlightId, inFlightAction, notice, nowMs ->
        ActionSnapshot(fetching, inFlightId, inFlightAction, notice, nowMs)
    }

    private val tripCoreFlows = combine(
        tripHistoryRepository?.activeTrip ?: flowOf(null),
        _isFetchingActiveTrip,
        _inFlightTripTargetStatus,
        _tripOutcomeNotice
    ) { activeTrip, isFetchingTrip, inFlightStatus, tripNotice ->
        TripCoreSnapshot(activeTrip, isFetchingTrip, inFlightStatus, tripNotice)
    }

    private val historyRepoFlows = combine(
        _historyFilter,
        tripHistoryRepository?.orderHistory ?: flowOf(emptyList()),
        tripHistoryRepository?.hasMoreHistory ?: flowOf(false),
        tripHistoryRepository?.nextHistoryCursor ?: flowOf(null),
        _isFetchingHistory
    ) { filter, items, hasMore, nextCursor, isFetching ->
        HistoryRepoSnapshot(filter, items, hasMore, nextCursor, isFetching)
    }

    private val detailAndErrorsFlows = combine(
        _isLoadingMoreHistory,
        _historyErrorMessage,
        tripHistoryRepository?.lastErrorMessage ?: flowOf(null),
        _selectedOrderDetail,
        _isLoadingOrderDetail
    ) { isLoadingMore, localHistoryError, repoHistoryError, orderDetail, isLoadingDetail ->
        DetailAndErrorsSnapshot(
            isLoadingMore = isLoadingMore,
            historyError = localHistoryError ?: repoHistoryError,
            orderDetail = orderDetail,
            isLoadingDetail = isLoadingDetail
        )
    }

    private val phase5Flows = combine(
        tripCoreFlows,
        historyRepoFlows,
        detailAndErrorsFlows,
        _orderDetailErrorMessage
    ) { tripCore, historyRepo, detailErrors, detailError ->
        Phase5Snapshot(tripCore, historyRepo, detailErrors, detailError)
    }

    val uiState: StateFlow<OrdersUiState> = combine(
        partnerFlows,
        dispatchFlows,
        actionFlows,
        _demoSimulateRaceConflict,
        phase5Flows
    ) { partner, dispatch, action, demoRace, phase5 ->
        val remainingMap = dispatch.offers.associate { offer ->
            offer.offerId to offer.remainingSeconds(action.nowMs)
        }
        val effectiveDutyState = when {
            dutyLocationRepository != null -> partner.dutyState
            partner.status.isOnline -> DutyControlState.ONLINE
            else -> DutyControlState.OFFLINE
        }
        val resolvedOrigin = tripHistoryRepository?.dataOrigin
            ?: dispatchRepository?.dataOrigin
            ?: repository.dataOrigin
        OrdersUiState(
            dataOrigin = resolvedOrigin,
            selectedTab = partner.tab,
            partnerStatus = partner.status,
            dutyControlState = effectiveDutyState,
            realtimeConnectionState = dispatch.realtimeState,
            activeOffers = dispatch.offers,
            remainingSecondsByOfferId = remainingMap,
            isFetchingOffers = action.isFetching,
            inFlightOfferId = action.inFlightOfferId,
            inFlightAction = action.inFlightAction,
            acceptedAssignment = dispatch.assignment,
            outcomeNotice = action.notice,
            boundaryMessage = dispatch.repoError ?: partner.boundaryMessage,
            demoSimulateRaceConflict = demoRace,
            isHistoryConnected = tripHistoryRepository != null,
            activeTrip = phase5.tripCore.activeTrip,
            isFetchingActiveTrip = phase5.tripCore.isFetchingActiveTrip,
            inFlightTripTargetStatus = phase5.tripCore.inFlightTargetStatus,
            tripOutcomeNotice = phase5.tripCore.notice,
            historyFilter = phase5.historyRepo.filter,
            historyItems = phase5.historyRepo.items,
            hasMoreHistory = phase5.historyRepo.hasMore,
            historyNextCursor = phase5.historyRepo.nextCursor,
            isFetchingHistory = phase5.historyRepo.isFetching,
            isLoadingMoreHistory = phase5.detailErrors.isLoadingMore,
            historyErrorMessage = phase5.detailErrors.historyError,
            selectedOrderDetail = phase5.detailErrors.orderDetail,
            isLoadingOrderDetail = phase5.detailErrors.isLoadingDetail,
            orderDetailErrorMessage = phase5.detailError
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = OrdersUiState(
            dataOrigin = tripHistoryRepository?.dataOrigin
                ?: dispatchRepository?.dataOrigin
                ?: repository.dataOrigin,
            isHistoryConnected = tripHistoryRepository != null
        )
    )

    init {
        if (tripHistoryRepository != null) {
            restoreActiveTrip()
        }
        if (dutyLocationRepository != null && dispatchRepository != null) {
            observeDutyForRealtime()
            observeActiveOffersForCountdown()
        }
        if (realtimeBoundary != null && (dispatchRepository != null || tripHistoryRepository != null)) {
            observeRealtimeSignals()
        }
    }

    private fun observeDutyForRealtime() {
        val dutyRepo = dutyLocationRepository ?: return
        viewModelScope.launch {
            dutyRepo.dutyControlState.collectLatest { dutyState ->
                if (dutyState == DutyControlState.ONLINE) {
                    realtimeBoundary?.connect()
                    refreshOffers()
                    if (tripHistoryRepository != null) {
                        restoreActiveTrip()
                    }
                } else {
                    realtimeBoundary?.disconnect()
                }
            }
        }
    }

    private fun observeRealtimeSignals() {
        val boundary = realtimeBoundary ?: return
        val repo = dispatchRepository
        viewModelScope.launch {
            boundary.signals.collect { signal ->
                when (signal) {
                    RealtimeSignal.FetchActiveOffers -> refreshOffers()
                    is RealtimeSignal.FetchActiveTrip -> restoreActiveTrip()
                    RealtimeSignal.ReconnectResync -> {
                        refreshOffers()
                        restoreActiveTrip()
                    }
                    is RealtimeSignal.RefreshWalletAndPayouts,
                    is RealtimeSignal.FetchNotifications -> Unit
                    is RealtimeSignal.FetchOfferDetail -> {
                        if (repo != null && isDutyConfirmedOnline()) {
                            repo.fetchOfferDetail(signal.offerId, clockMillis())
                            _tickClockMs.value = clockMillis()
                        }
                    }
                    is RealtimeSignal.OfferWithdrawn -> {
                        if (repo != null) {
                            val wasVisible = repo.activeOffers.value.any { it.offerId == signal.offerId }
                            repo.withdrawOfferLocally(signal.offerId)
                            idempotencyTracker.clearOffer(signal.offerId)
                            if (wasVisible) {
                                _outcomeNotice.value = OfferOutcomeNotice.AlreadyAssignedToOtherDriver(
                                    offerId = signal.offerId
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observeActiveOffersForCountdown() {
        val repo = dispatchRepository ?: return
        viewModelScope.launch {
            repo.activeOffers.collectLatest { offers ->
                countdownJob?.cancel()
                _tickClockMs.value = clockMillis()
                if (offers.isEmpty() || !enableCountdownTicker) return@collectLatest

                countdownJob = launch {
                    while (isActive) {
                        val now = clockMillis()
                        _tickClockMs.value = now
                        val currentOffers = repo.activeOffers.value
                        if (currentOffers.isEmpty()) break

                        val newlyExpired = currentOffers.filter { it.isExpired(now) }
                        if (newlyExpired.isNotEmpty()) {
                            newlyExpired.forEach { expired ->
                                repo.withdrawOfferLocally(expired.offerId)
                                idempotencyTracker.clearOffer(expired.offerId)
                                if (_inFlightOfferId.value != expired.offerId) {
                                    _outcomeNotice.value = OfferOutcomeNotice.OfferExpired(expired.offerId)
                                }
                            }
                            if (repo.activeOffers.value.isEmpty()) break
                        }
                        delay(1_000L)
                    }
                }
            }
        }
    }

    fun selectTab(tab: OrdersTab) {
        _selectedTab.value = tab
        when (tab) {
            OrdersTab.ACTIVE -> {
                if (tripHistoryRepository != null) {
                    restoreActiveTrip()
                }
            }
            OrdersTab.COMPLETED -> {
                _historyFilter.value = OrderHistoryFilter.COMPLETED
                refreshOrderHistory(OrderHistoryFilter.COMPLETED)
            }
            OrdersTab.CANCELLED -> {
                _historyFilter.value = OrderHistoryFilter.CANCELLED
                refreshOrderHistory(OrderHistoryFilter.CANCELLED)
            }
        }
    }

    fun selectHistoryFilter(filter: OrderHistoryFilter) {
        _historyFilter.value = filter
        when (filter) {
            OrderHistoryFilter.COMPLETED -> _selectedTab.value = OrdersTab.COMPLETED
            OrderHistoryFilter.CANCELLED -> _selectedTab.value = OrdersTab.CANCELLED
            OrderHistoryFilter.ALL -> {
                if (_selectedTab.value == OrdersTab.ACTIVE) {
                    _selectedTab.value = OrdersTab.COMPLETED
                }
            }
        }
        refreshOrderHistory(filter)
    }

    fun goOnline() {
        viewModelScope.launch {
            repository.setDutyStatus(true)
        }
    }

    fun refreshOffers() {
        val repo = dispatchRepository ?: return
        viewModelScope.launch {
            _isFetchingOffers.value = true
            try {
                when (val result = repo.refreshActiveOffers(clockMillis())) {
                    is ApiResult.BackendNotDeployed -> {
                        if (repo.dataOrigin == DataOrigin.LIVE) {
                            _outcomeNotice.value = OfferOutcomeNotice.BackendNotDeployed(result.reason)
                        }
                    }
                    else -> Unit
                }
                _tickClockMs.value = clockMillis()
            } finally {
                _isFetchingOffers.value = false
            }
        }
    }

    /**
     * Authoritatively restores any active trip on startup, process death recovery, or ON_RESUME.
     */
    fun restoreActiveTrip() {
        val repo = tripHistoryRepository ?: return
        viewModelScope.launch {
            _isFetchingActiveTrip.value = true
            try {
                when (val result = repo.fetchActiveTrip()) {
                    is ApiResult.BackendNotDeployed -> {
                        if (repo.dataOrigin == DataOrigin.LIVE && _selectedTab.value == OrdersTab.ACTIVE) {
                            _tripOutcomeNotice.value = TripOutcomeNotice.BackendNotDeployed(result.reason)
                        }
                    }
                    else -> Unit
                }
            } finally {
                _isFetchingActiveTrip.value = false
            }
        }
    }

    fun syncCountdownNow() {
        val repo = dispatchRepository ?: return
        val now = clockMillis()
        _tickClockMs.value = now
        val expired = repo.activeOffers.value.filter { it.isExpired(now) }
        expired.forEach { offer ->
            repo.withdrawOfferLocally(offer.offerId)
            idempotencyTracker.clearOffer(offer.offerId)
            _outcomeNotice.value = OfferOutcomeNotice.OfferExpired(offer.offerId)
        }
    }

    fun acceptOffer(offerId: String) {
        val repo = dispatchRepository ?: return
        if (_inFlightOfferId.value != null) return

        val offer = repo.activeOffers.value.find { it.offerId == offerId }
        val now = clockMillis()
        if (offer != null && offer.isExpired(now)) {
            repo.withdrawOfferLocally(offerId)
            idempotencyTracker.clearOffer(offerId)
            _outcomeNotice.value = OfferOutcomeNotice.OfferExpired(offerId)
            return
        }

        if (!isDutyConfirmedOnline()) {
            _outcomeNotice.value = OfferOutcomeNotice.DriverNotEligibleOrOffline(
                "Driver must be confirmed Online and eligible to accept work."
            )
            return
        }

        _inFlightOfferId.value = offerId
        _inFlightAction.value = OfferAction.ACCEPT
        viewModelScope.launch {
            val idempotencyKey = idempotencyTracker.getOrCreateKey(offerId, OfferAction.ACCEPT)
            try {
                when (val result = repo.acceptOffer(offerId, idempotencyKey, clockMillis())) {
                    is ApiResult.Success -> {
                        idempotencyTracker.clearOffer(offerId)
                        _outcomeNotice.value = OfferOutcomeNotice.Accepted(result.data)
                        // Hand off seamlessly to backend-authoritative Phase 5 active trip state
                        tripHistoryRepository?.fetchActiveTrip()
                    }
                    is ApiResult.HttpError -> {
                        when {
                            result.httpStatus == 409 ||
                                result.error.code == DispatchErrorCodes.ORDER_ALREADY_ASSIGNED ||
                                result.error.code == DispatchErrorCodes.OFFER_ALREADY_CLAIMED ||
                                result.error.code == DispatchErrorCodes.OFFER_SUPERSEDED -> {
                                idempotencyTracker.clearOffer(offerId)
                                _outcomeNotice.value = OfferOutcomeNotice.AlreadyAssignedToOtherDriver(
                                    offerId = offerId,
                                    serverMessage = result.error.message
                                )
                            }
                            result.httpStatus == 410 ||
                                result.error.code == DispatchErrorCodes.OFFER_EXPIRED -> {
                                idempotencyTracker.clearOffer(offerId)
                                _outcomeNotice.value = OfferOutcomeNotice.OfferExpired(
                                    offerId = offerId,
                                    serverMessage = result.error.message
                                )
                            }
                            result.httpStatus == 403 ||
                                result.httpStatus == 422 ||
                                result.error.code == DispatchErrorCodes.DRIVER_OFFLINE ||
                                result.error.code == DispatchErrorCodes.DRIVER_INELIGIBLE -> {
                                idempotencyTracker.clearOffer(offerId)
                                _outcomeNotice.value = OfferOutcomeNotice.DriverNotEligibleOrOffline(
                                    result.error.message
                                )
                            }
                            else -> {
                                _outcomeNotice.value = OfferOutcomeNotice.NetworkFailedNoQueue(
                                    offerId = offerId,
                                    causeMessage = result.error.message
                                )
                            }
                        }
                    }
                    is ApiResult.NetworkError -> {
                        _outcomeNotice.value = OfferOutcomeNotice.NetworkFailedNoQueue(
                            offerId = offerId,
                            causeMessage = result.causeMessage
                        )
                    }
                    is ApiResult.BackendNotDeployed -> {
                        idempotencyTracker.clearOffer(offerId)
                        _outcomeNotice.value = OfferOutcomeNotice.BackendNotDeployed(result.reason)
                    }
                }
            } finally {
                _inFlightOfferId.value = null
                _inFlightAction.value = null
                _tickClockMs.value = clockMillis()
            }
        }
    }

    fun rejectOffer(offerId: String, reason: OfferRejectReason? = null) {
        val repo = dispatchRepository ?: return
        if (_inFlightOfferId.value != null) return

        _inFlightOfferId.value = offerId
        _inFlightAction.value = OfferAction.REJECT
        viewModelScope.launch {
            val idempotencyKey = idempotencyTracker.getOrCreateKey(offerId, OfferAction.REJECT)
            try {
                when (val result = repo.rejectOffer(offerId, reason?.code, idempotencyKey)) {
                    is ApiResult.Success -> {
                        idempotencyTracker.clearOffer(offerId)
                        _outcomeNotice.value = OfferOutcomeNotice.Rejected(offerId)
                    }
                    is ApiResult.HttpError -> {
                        if (result.httpStatus == 409 || result.httpStatus == 410 || result.httpStatus == 404) {
                            idempotencyTracker.clearOffer(offerId)
                        }
                        _outcomeNotice.value = OfferOutcomeNotice.NetworkFailedNoQueue(
                            offerId = offerId,
                            causeMessage = result.error.message
                        )
                    }
                    is ApiResult.NetworkError -> {
                        _outcomeNotice.value = OfferOutcomeNotice.NetworkFailedNoQueue(
                            offerId = offerId,
                            causeMessage = result.causeMessage
                        )
                    }
                    is ApiResult.BackendNotDeployed -> {
                        idempotencyTracker.clearOffer(offerId)
                        _outcomeNotice.value = OfferOutcomeNotice.BackendNotDeployed(result.reason)
                    }
                }
            } finally {
                _inFlightOfferId.value = null
                _inFlightAction.value = null
                _tickClockMs.value = clockMillis()
            }
        }
    }

    // --- Phase 5 Trip Lifecycle Actions (Backend-Authoritative) ---

    fun headToPickup() = transitionActiveTrip(TripStatus.EN_ROUTE_TO_PICKUP)

    fun markReachedPickup() = transitionActiveTrip(TripStatus.ARRIVED_AT_PICKUP)

    fun startTrip(verificationPin: String? = null) = transitionActiveTrip(
        targetStatus = TripStatus.IN_PROGRESS,
        verificationPin = verificationPin?.trim()?.takeIf { it.isNotEmpty() }
    )

    fun completeTrip() = transitionActiveTrip(targetStatus = TripStatus.COMPLETED)

    fun cancelActiveTrip(reason: TripCancelReason) = transitionActiveTrip(
        targetStatus = TripStatus.CANCELLED,
        reasonCode = reason.code
    )

    private fun transitionActiveTrip(
        targetStatus: TripStatus,
        reasonCode: String? = null,
        verificationPin: String? = null
    ) {
        val repo = tripHistoryRepository ?: return
        val currentTrip = repo.activeTrip.value ?: return
        if (_inFlightTripTargetStatus.value != null) return

        _inFlightTripTargetStatus.value = targetStatus
        viewModelScope.launch {
            val idempotencyKey = tripTransitionIdempotencyTracker.getOrCreateKey(
                tripId = currentTrip.tripId,
                targetStatus = targetStatus,
                expectedVersion = currentTrip.version
            )
            val snapshotLocation = locationProvider?.getCurrentLocation()
            try {
                when (
                    val result = repo.submitTripTransition(
                        tripId = currentTrip.tripId,
                        targetStatus = targetStatus,
                        expectedVersion = currentTrip.version,
                        idempotencyKey = idempotencyKey,
                        reasonCode = reasonCode,
                        location = snapshotLocation,
                        verificationPin = verificationPin
                    )
                ) {
                    is ApiResult.Success -> {
                        tripTransitionIdempotencyTracker.clearTrip(currentTrip.tripId)
                        val outcome = result.data
                        when (outcome.status) {
                            TripStatus.COMPLETED -> {
                                dispatchRepository?.clearAcceptedAssignment()
                                _tripOutcomeNotice.value = TripOutcomeNotice.CompletedRecorded(
                                    tripId = outcome.tripId,
                                    earningSourceRefId = outcome.earningSourceRefId
                                )
                                refreshOrderHistoryIfVisible()
                            }
                            TripStatus.CANCELLED -> {
                                dispatchRepository?.clearAcceptedAssignment()
                                _tripOutcomeNotice.value = TripOutcomeNotice.Cancelled(outcome.tripId)
                                refreshOrderHistoryIfVisible()
                            }
                            else -> {
                                _tripOutcomeNotice.value = TripOutcomeNotice.TransitionConfirmed(outcome.status)
                            }
                        }
                    }
                    is ApiResult.HttpError -> {
                        when {
                            result.error.code == TripErrorCodes.INVALID_START_PIN -> {
                                _tripOutcomeNotice.value = TripOutcomeNotice.InvalidStartPin(
                                    result.error.message
                                )
                            }
                            result.httpStatus == 409 ||
                                result.error.code == TripErrorCodes.VERSION_CONFLICT -> {
                                tripTransitionIdempotencyTracker.clearTrip(currentTrip.tripId)
                                repo.fetchActiveTrip()
                                _tripOutcomeNotice.value = TripOutcomeNotice.IllegalTransitionOrConflict(
                                    result.error.message
                                )
                            }
                            result.httpStatus == 403 ||
                                result.httpStatus == 422 ||
                                result.error.code == TripErrorCodes.INVALID_TRIP_TRANSITION ||
                                result.error.code == TripErrorCodes.FORBIDDEN_CROSS_DRIVER -> {
                                tripTransitionIdempotencyTracker.clearTrip(currentTrip.tripId)
                                _tripOutcomeNotice.value = TripOutcomeNotice.IllegalTransitionOrConflict(
                                    result.error.message
                                )
                            }
                            else -> {
                                _tripOutcomeNotice.value = TripOutcomeNotice.NetworkFailedNoOfflineQueue(
                                    causeMessage = result.error.message
                                )
                            }
                        }
                    }
                    is ApiResult.NetworkError -> {
                        // Never queue trip transitions offline; re-fetch authoritative state instead
                        repo.fetchActiveTrip()
                        _tripOutcomeNotice.value = TripOutcomeNotice.NetworkFailedNoOfflineQueue(
                            causeMessage = result.causeMessage
                        )
                    }
                    is ApiResult.BackendNotDeployed -> {
                        tripTransitionIdempotencyTracker.clearTrip(currentTrip.tripId)
                        _tripOutcomeNotice.value = TripOutcomeNotice.BackendNotDeployed(result.reason)
                    }
                }
            } finally {
                _inFlightTripTargetStatus.value = null
            }
        }
    }

    fun dismissTripOutcomeNotice() {
        _tripOutcomeNotice.value = null
    }

    fun dismissTerminalTripSummary() {
        tripHistoryRepository?.dismissTerminalTrip()
    }

    // --- Phase 5 Order History & Order Detail ---

    fun refreshOrderHistory(filter: OrderHistoryFilter = _historyFilter.value) {
        val repo = tripHistoryRepository ?: return
        viewModelScope.launch {
            _isFetchingHistory.value = true
            _historyErrorMessage.value = null
            try {
                when (val result = repo.fetchOrderHistory(filter = filter, cursor = null, append = false)) {
                    is ApiResult.Success -> Unit
                    is ApiResult.HttpError -> _historyErrorMessage.value = result.error.message
                    is ApiResult.NetworkError -> _historyErrorMessage.value = result.causeMessage
                    is ApiResult.BackendNotDeployed -> _historyErrorMessage.value = result.reason
                }
            } finally {
                _isFetchingHistory.value = false
            }
        }
    }

    fun loadMoreHistory() {
        val repo = tripHistoryRepository ?: return
        val cursor = repo.nextHistoryCursor.value ?: return
        if (_isLoadingMoreHistory.value || _isFetchingHistory.value) return

        viewModelScope.launch {
            _isLoadingMoreHistory.value = true
            _historyErrorMessage.value = null
            try {
                when (
                    val result = repo.fetchOrderHistory(
                        filter = _historyFilter.value,
                        cursor = cursor,
                        append = true
                    )
                ) {
                    is ApiResult.Success -> Unit
                    is ApiResult.HttpError -> _historyErrorMessage.value = result.error.message
                    is ApiResult.NetworkError -> _historyErrorMessage.value = result.causeMessage
                    is ApiResult.BackendNotDeployed -> _historyErrorMessage.value = result.reason
                }
            } finally {
                _isLoadingMoreHistory.value = false
            }
        }
    }

    fun openOrderDetail(orderId: String) {
        val repo = tripHistoryRepository ?: return
        viewModelScope.launch {
            _isLoadingOrderDetail.value = true
            _orderDetailErrorMessage.value = null
            try {
                when (val result = repo.fetchOrderDetail(orderId)) {
                    is ApiResult.Success -> _selectedOrderDetail.value = result.data
                    is ApiResult.HttpError -> _orderDetailErrorMessage.value = result.error.message
                    is ApiResult.NetworkError -> _orderDetailErrorMessage.value = result.causeMessage
                    is ApiResult.BackendNotDeployed -> _orderDetailErrorMessage.value = result.reason
                }
            } finally {
                _isLoadingOrderDetail.value = false
            }
        }
    }

    fun closeOrderDetail() {
        _selectedOrderDetail.value = null
        _orderDetailErrorMessage.value = null
    }

    private fun refreshOrderHistoryIfVisible() {
        if (_selectedTab.value != OrdersTab.ACTIVE) {
            refreshOrderHistory(_historyFilter.value)
        }
    }

    fun dismissOutcomeNotice() {
        _outcomeNotice.value = null
    }

    fun dismissAcceptedAssignment() {
        dispatchRepository?.clearAcceptedAssignment()
    }

    fun toggleDemoRaceConflict(enabled: Boolean) {
        if ((dispatchRepository?.dataOrigin ?: repository.dataOrigin) != DataOrigin.DEMO) return
        _demoSimulateRaceConflict.value = enabled
    }

    fun triggerDemoOffer(serviceType: ServiceType = repository.partnerProfile.value?.serviceType ?: ServiceType.BIKE_TAXI) {
        val repo = dispatchRepository ?: return
        if (repo.dataOrigin != DataOrigin.DEMO) return
        if (!isDutyConfirmedOnline()) {
            _outcomeNotice.value = OfferOutcomeNotice.DriverNotEligibleOrOffline(
                "Go Online first on Home to simulate incoming DEMO offers."
            )
            return
        }
        viewModelScope.launch {
            repo.triggerDemoOfferFixture(
                serviceType = serviceType,
                simulateAlreadyClaimedByOther = _demoSimulateRaceConflict.value,
                nowEpochMs = clockMillis()
            )
            _tickClockMs.value = clockMillis()
        }
    }

    fun simulateDemoDuplicateRealtimeEvent() {
        val repo = dispatchRepository ?: return
        val boundary = realtimeBoundary ?: return
        if (repo.dataOrigin != DataOrigin.DEMO) return
        val targetOffer = repo.activeOffers.value.firstOrNull() ?: return
        val fixedEventId = "demo-dup-${targetOffer.offerId}"
        val payload = mapOf(
            "eventId" to fixedEventId,
            "eventType" to "offer.created",
            "resourceId" to targetOffer.offerId,
            "schemaVersion" to "1"
        )
        boundary.onPushHintReceived(payload)
        boundary.onPushHintReceived(payload)
    }

    private fun isDutyConfirmedOnline(): Boolean {
        val dutyOnline = dutyLocationRepository?.dutyControlState?.value == DutyControlState.ONLINE ||
            (dutyLocationRepository == null && repository.partnerStatus.value.isOnline)
        val eligible = dutyLocationRepository?.eligibility?.value?.isEligibleForOnline != false
        return dutyOnline && eligible
    }

    private data class PartnerSnapshot(
        val tab: OrdersTab,
        val status: PartnerStatus,
        val boundaryMessage: String?,
        val dutyState: DutyControlState
    )

    private data class DispatchSnapshot(
        val offers: List<WorkOffer>,
        val assignment: AcceptedOfferAssignment?,
        val repoError: String?,
        val realtimeState: RealtimeConnectionState
    )

    private data class ActionSnapshot(
        val isFetching: Boolean,
        val inFlightOfferId: String?,
        val inFlightAction: OfferAction?,
        val notice: OfferOutcomeNotice?,
        val nowMs: Long
    )

    private data class TripCoreSnapshot(
        val activeTrip: ActiveTrip?,
        val isFetchingActiveTrip: Boolean,
        val inFlightTargetStatus: TripStatus?,
        val notice: TripOutcomeNotice?
    )

    private data class HistoryRepoSnapshot(
        val filter: OrderHistoryFilter,
        val items: List<OrderHistoryItem>,
        val hasMore: Boolean,
        val nextCursor: String?,
        val isFetching: Boolean
    )

    private data class DetailAndErrorsSnapshot(
        val isLoadingMore: Boolean,
        val historyError: String?,
        val orderDetail: OrderDetail?,
        val isLoadingDetail: Boolean
    )

    private data class Phase5Snapshot(
        val tripCore: TripCoreSnapshot,
        val historyRepo: HistoryRepoSnapshot,
        val detailErrors: DetailAndErrorsSnapshot,
        val detailError: String?
    )

    companion object {
        fun provideFactory(appContainer: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                OrdersViewModel(
                    repository = appContainer.partnerRepository,
                    dutyLocationRepository = appContainer.dutyLocationRepository,
                    dispatchRepository = appContainer.dispatchRepository,
                    realtimeBoundary = appContainer.dispatchRealtimeBoundary,
                    tripHistoryRepository = appContainer.tripHistoryRepository,
                    locationProvider = appContainer.locationProvider
                )
            }
        }
    }
}
