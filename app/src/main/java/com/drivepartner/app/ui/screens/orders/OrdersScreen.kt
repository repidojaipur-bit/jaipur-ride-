package com.drivepartner.app.ui.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.realtime.RealtimeConnectionState
import com.drivepartner.app.data.model.AcceptedOfferAssignment
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.OfferOutcomeNotice
import com.drivepartner.app.data.model.OfferRejectReason
import com.drivepartner.app.data.model.OrderHistoryItem
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.TripCancelReason
import com.drivepartner.app.data.model.TripOutcomeNotice
import com.drivepartner.app.data.remote.dto.OrderHistoryFilter
import com.drivepartner.app.data.remote.dto.TripStatus
import com.drivepartner.app.ui.components.DriveCard
import com.drivepartner.app.ui.components.DriveOutlinedButton
import com.drivepartner.app.ui.components.DrivePrimaryButton
import com.drivepartner.app.ui.components.state.DriveEmptyView
import com.drivepartner.app.ui.components.state.DriveLoadingView
import com.drivepartner.app.ui.components.state.DriveOfflineView
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.viewmodel.OrdersTab
import com.drivepartner.app.ui.viewmodel.OrdersUiState
import com.drivepartner.app.ui.viewmodel.OrdersViewModel

@Composable
fun OrdersRoute(
    viewModel: OrdersViewModel,
    onGoOnlineClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.syncCountdownNow()
        viewModel.restoreActiveTrip()
        if (uiState.dutyControlState == DutyControlState.ONLINE) {
            viewModel.refreshOffers()
        }
        if (uiState.selectedTab != OrdersTab.ACTIVE) {
            viewModel.refreshOrderHistory()
        }
    }

    OrdersScreen(
        uiState = uiState,
        onSelectTab = viewModel::selectTab,
        onGoOnlineClick = onGoOnlineClick,
        onRefreshOffers = viewModel::refreshOffers,
        onAcceptOffer = viewModel::acceptOffer,
        onRejectOffer = viewModel::rejectOffer,
        onDismissOutcomeNotice = viewModel::dismissOutcomeNotice,
        onDismissAssignment = viewModel::dismissAcceptedAssignment,
        onTriggerDemoOffer = { viewModel.triggerDemoOffer() },
        onToggleDemoRaceConflict = viewModel::toggleDemoRaceConflict,
        onSimulateDemoDuplicateEvent = viewModel::simulateDemoDuplicateRealtimeEvent,
        onHeadToPickup = viewModel::headToPickup,
        onReachedPickup = viewModel::markReachedPickup,
        onStartTrip = viewModel::startTrip,
        onCompleteTrip = viewModel::completeTrip,
        onCancelTrip = viewModel::cancelActiveTrip,
        onDismissTripOutcomeNotice = viewModel::dismissTripOutcomeNotice,
        onDismissTerminalTripSummary = viewModel::dismissTerminalTripSummary,
        onSelectHistoryFilter = viewModel::selectHistoryFilter,
        onRefreshHistory = { viewModel.refreshOrderHistory() },
        onLoadMoreHistory = viewModel::loadMoreHistory,
        onOpenOrderDetail = viewModel::openOrderDetail,
        onCloseOrderDetail = viewModel::closeOrderDetail,
        modifier = modifier
    )
}

@Composable
fun OrdersScreen(
    partnerStatus: PartnerStatus,
    selectedTab: OrdersTab,
    onSelectTab: (OrdersTab) -> Unit,
    onGoOnlineClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OrdersScreen(
        uiState = OrdersUiState(
            selectedTab = selectedTab,
            partnerStatus = partnerStatus,
            dutyControlState = if (partnerStatus.isOnline) DutyControlState.ONLINE else DutyControlState.OFFLINE
        ),
        onSelectTab = onSelectTab,
        onGoOnlineClick = onGoOnlineClick,
        modifier = modifier
    )
}

@Composable
fun OrdersScreen(
    uiState: OrdersUiState,
    onSelectTab: (OrdersTab) -> Unit,
    onGoOnlineClick: () -> Unit,
    onRefreshOffers: () -> Unit = {},
    onAcceptOffer: (String) -> Unit = {},
    onRejectOffer: (String, OfferRejectReason?) -> Unit = { _, _ -> },
    onDismissOutcomeNotice: () -> Unit = {},
    onDismissAssignment: () -> Unit = {},
    onTriggerDemoOffer: () -> Unit = {},
    onToggleDemoRaceConflict: (Boolean) -> Unit = {},
    onSimulateDemoDuplicateEvent: () -> Unit = {},
    onHeadToPickup: () -> Unit = {},
    onReachedPickup: () -> Unit = {},
    onStartTrip: (String?) -> Unit = {},
    onCompleteTrip: () -> Unit = {},
    onCancelTrip: (TripCancelReason) -> Unit = {},
    onDismissTripOutcomeNotice: () -> Unit = {},
    onDismissTerminalTripSummary: () -> Unit = {},
    onSelectHistoryFilter: (OrderHistoryFilter) -> Unit = {},
    onRefreshHistory: () -> Unit = {},
    onLoadMoreHistory: () -> Unit = {},
    onOpenOrderDetail: (String) -> Unit = {},
    onCloseOrderDetail: () -> Unit = {},
    onLaunchNavigation: ((Double, Double, String) -> Boolean)? = null,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        OrdersTab.ACTIVE to stringResource(R.string.orders_tab_active),
        OrdersTab.COMPLETED to stringResource(R.string.orders_tab_completed),
        OrdersTab.CANCELLED to stringResource(R.string.orders_tab_cancelled)
    )
    val selectedIndex = tabs.indexOfFirst { it.first == uiState.selectedTab }.coerceAtLeast(0)

    uiState.selectedOrderDetail?.let { detail ->
        OrderDetailDialog(
            detail = detail,
            onDismiss = onCloseOrderDetail
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DriveColors.current.backgroundSubtle)
    ) {
        TabRow(
            selectedTabIndex = selectedIndex,
            containerColor = DriveColors.current.surface,
            contentColor = DriveColors.current.brand,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = DriveColors.current.brand
                )
            }
        ) {
            tabs.forEach { (tab, title) ->
                val selected = uiState.selectedTab == tab
                Tab(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) DriveColors.current.brand else DriveColors.current.textTertiary
                        )
                    }
                )
            }
        }

        if (uiState.selectedTab != OrdersTab.ACTIVE) {
            OrderHistorySection(
                uiState = uiState,
                onSelectHistoryFilter = onSelectHistoryFilter,
                onRefreshHistory = onRefreshHistory,
                onLoadMoreHistory = onLoadMoreHistory,
                onOpenOrderDetail = onOpenOrderDetail
            )
            return@Column
        }

        val isConfirmedOnline = uiState.dutyControlState == DutyControlState.ONLINE

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(DriveSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DriveSpacing.md)
        ) {
            // Realtime Channel Status + Authoritative Refresh Strip
            RealtimeStatusStrip(
                connectionState = uiState.realtimeConnectionState,
                isFetching = uiState.isFetchingOffers || uiState.isFetchingActiveTrip,
                isOnline = isConfirmedOnline || uiState.activeTrip != null,
                onRefresh = onRefreshOffers
            )

            // Live Backend Deployment Boundary Notice
            if (uiState.dataOrigin == DataOrigin.LIVE && !uiState.boundaryMessage.isNullOrBlank()) {
                DriveCard(
                    backgroundColor = DriveColors.current.dangerContainer,
                    borderColor = DriveColors.current.danger.copy(alpha = 0.35f)
                ) {
                    Text(
                        text = uiState.boundaryMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = DriveColors.current.onDangerContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Phase 5 Trip Lifecycle Outcome Banner
            uiState.tripOutcomeNotice?.let { notice ->
                TripOutcomeBanner(
                    notice = notice,
                    onDismiss = onDismissTripOutcomeNotice
                )
            }

            // Phase 4 Offer Outcome Banner
            uiState.outcomeNotice?.let { notice ->
                OfferOutcomeBanner(
                    notice = notice,
                    onDismiss = onDismissOutcomeNotice
                )
            }

            // Phase 5 Active or Recently Terminated Trip Card
            val currentActiveTrip = uiState.activeTrip
            if (currentActiveTrip != null && currentActiveTrip.isTerminal) {
                TerminalTripSummaryCard(
                    trip = currentActiveTrip,
                    onDismiss = onDismissTerminalTripSummary
                )
            } else if (currentActiveTrip != null) {
                ActiveTripCard(
                    trip = currentActiveTrip,
                    inFlightTargetStatus = uiState.inFlightTripTargetStatus,
                    onHeadToPickup = onHeadToPickup,
                    onReachedPickup = onReachedPickup,
                    onStartTrip = onStartTrip,
                    onCompleteTrip = onCompleteTrip,
                    onCancelTrip = onCancelTrip,
                    onLaunchNavigation = onLaunchNavigation
                )
            } else {
                // Fallback handoff notice if repository hasn't loaded activeTrip yet
                uiState.acceptedAssignment?.let { assignment ->
                    AcceptedAssignmentHandoffCard(
                        assignment = assignment,
                        onDismiss = onDismissAssignment
                    )
                }
            }

            // Dispatch Offers / Offline / Empty State (shown when no ongoing execution trip)
            val hasRunningTrip = currentActiveTrip != null && currentActiveTrip.isExecutionActive
            if (!hasRunningTrip) {
                if (!isConfirmedOnline) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        DriveOfflineView(
                            title = stringResource(R.string.state_offline_title),
                            subtitle = stringResource(R.string.orders_empty_description),
                            onGoOnlineClick = onGoOnlineClick
                        )
                    }
                } else if (uiState.activeOffers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        DriveEmptyView(
                            icon = Icons.Outlined.ListAlt,
                            title = stringResource(R.string.orders_empty_title),
                            description = stringResource(R.string.orders_empty_description),
                            actionContent = {
                                DriveOutlinedButton(onClick = onRefreshOffers) {
                                    Text(text = stringResource(R.string.action_refresh))
                                }
                            }
                        )
                    }
                } else {
                    uiState.activeOffers.forEach { offer ->
                        val remaining = uiState.remainingSecondsByOfferId[offer.offerId] ?: 0
                        OfferRequestCard(
                            offer = offer,
                            remainingSeconds = remaining,
                            isActionInFlight = uiState.inFlightOfferId == offer.offerId,
                            inFlightAction = if (uiState.inFlightOfferId == offer.offerId) {
                                uiState.inFlightAction
                            } else {
                                null
                            },
                            onAccept = { onAcceptOffer(offer.offerId) },
                            onReject = { reason -> onRejectOffer(offer.offerId, reason) }
                        )
                    }
                }
            }

            // Isolated DEMO Offer Testing Simulator (NEVER visible in LIVE builds)
            if (uiState.dataOrigin == DataOrigin.DEMO && !hasRunningTrip) {
                DemoDispatchSimulatorCard(
                    isOnline = isConfirmedOnline,
                    hasActiveOffers = uiState.activeOffers.isNotEmpty(),
                    simulateRaceConflict = uiState.demoSimulateRaceConflict,
                    onTriggerDemoOffer = onTriggerDemoOffer,
                    onToggleRaceConflict = onToggleDemoRaceConflict,
                    onSimulateDuplicateEvent = onSimulateDemoDuplicateEvent
                )
            }
        }
    }
}

@Composable
private fun OrderHistorySection(
    uiState: OrdersUiState,
    onSelectHistoryFilter: (OrderHistoryFilter) -> Unit,
    onRefreshHistory: () -> Unit,
    onLoadMoreHistory: () -> Unit,
    onOpenOrderDetail: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DriveSpacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(DriveSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(DriveSpacing.sm)) {
                FilterChip(
                    selected = uiState.historyFilter == OrderHistoryFilter.ALL,
                    onClick = { onSelectHistoryFilter(OrderHistoryFilter.ALL) },
                    label = { Text(stringResource(R.string.orders_history_filter_all)) }
                )
                FilterChip(
                    selected = uiState.historyFilter == OrderHistoryFilter.COMPLETED,
                    onClick = { onSelectHistoryFilter(OrderHistoryFilter.COMPLETED) },
                    label = { Text(stringResource(R.string.orders_history_filter_completed)) }
                )
                FilterChip(
                    selected = uiState.historyFilter == OrderHistoryFilter.CANCELLED,
                    onClick = { onSelectHistoryFilter(OrderHistoryFilter.CANCELLED) },
                    label = { Text(stringResource(R.string.orders_history_filter_cancelled)) }
                )
            }

            IconButton(
                onClick = onRefreshHistory,
                enabled = !uiState.isFetchingHistory
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.action_refresh),
                    tint = DriveColors.current.brand
                )
            }
        }

        uiState.historyErrorMessage?.let { errorMsg ->
            DriveCard(
                backgroundColor = DriveColors.current.dangerContainer,
                borderColor = DriveColors.current.danger.copy(alpha = 0.4f)
            ) {
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveColors.current.onDangerContainer
                )
                Spacer(modifier = Modifier.height(DriveSpacing.sm))
                DriveOutlinedButton(onClick = onRefreshHistory) {
                    Text(text = stringResource(R.string.state_retry))
                }
            }
        }

        uiState.orderDetailErrorMessage?.let { detailErr ->
            DriveCard(backgroundColor = DriveColors.current.dangerContainer) {
                Text(
                    text = detailErr,
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveColors.current.onDangerContainer
                )
            }
        }

        if (uiState.isFetchingHistory && uiState.historyItems.isEmpty()) {
            DriveLoadingView(modifier = Modifier.fillMaxWidth())
        } else if (uiState.historyItems.isEmpty()) {
            DriveEmptyView(
                icon = Icons.Outlined.ListAlt,
                title = stringResource(R.string.orders_history_empty_title),
                description = stringResource(R.string.orders_history_empty_description)
            )
        } else {
            uiState.historyItems.forEach { item ->
                OrderHistoryItemCard(
                    item = item,
                    onClick = { onOpenOrderDetail(item.orderId) }
                )
            }

            if (uiState.hasMoreHistory && uiState.historyNextCursor != null) {
                DriveOutlinedButton(
                    onClick = onLoadMoreHistory,
                    enabled = !uiState.isLoadingMoreHistory
                ) {
                    if (uiState.isLoadingMoreHistory) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.orders_history_loading_more))
                    } else {
                        Text(text = stringResource(R.string.orders_history_load_more))
                    }
                }
            }
        }

        if (uiState.isLoadingOrderDetail) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun OrderHistoryItemCard(
    item: OrderHistoryItem,
    onClick: () -> Unit
) {
    val isCompleted = item.status == TripStatus.COMPLETED
    val badgeBg = if (isCompleted) DriveColors.current.successContainer else DriveColors.current.dangerContainer
    val badgeFg = if (isCompleted) DriveColors.current.onSuccessContainer else DriveColors.current.onDangerContainer

    DriveCard(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = DriveChipShape, color = badgeBg) {
                    Text(
                        text = item.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeFg,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (item.dataOrigin == DataOrigin.DEMO) {
                    Surface(shape = DriveChipShape, color = DriveColors.current.warningContainer) {
                        Text(
                            text = "DEMO",
                            style = MaterialTheme.typography.labelSmall,
                            color = DriveColors.current.onWarningContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.finalizedAmount?.formatDisplay()
                        ?: stringResource(R.string.orders_history_amount_pending),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = DriveColors.current.textTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(DriveSpacing.sm))
        Text(
            text = "${item.pickupSummary} -> ${item.dropSummary}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${item.orderId} • ${item.completedOrCancelledAtUtc}",
            style = MaterialTheme.typography.labelSmall,
            color = DriveColors.current.textSecondary
        )
    }
}

@Composable
private fun TripOutcomeBanner(
    notice: TripOutcomeNotice,
    onDismiss: () -> Unit
) {
    val (bgColor, fgColor, text) = when (notice) {
        is TripOutcomeNotice.TransitionConfirmed -> Triple(
            DriveColors.current.brandContainer,
            DriveColors.current.onBrandContainer,
            stringResource(R.string.trip_outcome_transition_confirmed, notice.status.name)
        )
        is TripOutcomeNotice.CompletedRecorded -> Triple(
            DriveColors.current.successContainer,
            DriveColors.current.onSuccessContainer,
            stringResource(R.string.trip_outcome_completed)
        )
        is TripOutcomeNotice.Cancelled -> Triple(
            DriveColors.current.warningContainer,
            DriveColors.current.onWarningContainer,
            stringResource(R.string.trip_outcome_cancelled)
        )
        is TripOutcomeNotice.IllegalTransitionOrConflict -> Triple(
            DriveColors.current.warningContainer,
            DriveColors.current.onWarningContainer,
            notice.serverMessage
        )
        is TripOutcomeNotice.InvalidStartPin -> Triple(
            DriveColors.current.dangerContainer,
            DriveColors.current.onDangerContainer,
            notice.serverMessage
        )
        is TripOutcomeNotice.NetworkFailedNoOfflineQueue -> Triple(
            DriveColors.current.dangerContainer,
            DriveColors.current.onDangerContainer,
            stringResource(R.string.trip_outcome_network_no_queue)
        )
        is TripOutcomeNotice.BackendNotDeployed -> Triple(
            DriveColors.current.dangerContainer,
            DriveColors.current.onDangerContainer,
            notice.reason
        )
    }

    DriveCard(backgroundColor = bgColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.sm))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = fgColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.offer_dismiss_notice),
                    color = fgColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RealtimeStatusStrip(
    connectionState: RealtimeConnectionState,
    isFetching: Boolean,
    isOnline: Boolean,
    onRefresh: () -> Unit
) {
    val labelRes = when (connectionState) {
        RealtimeConnectionState.CONNECTED -> R.string.orders_realtime_connected
        RealtimeConnectionState.CONNECTING -> R.string.orders_realtime_connecting
        RealtimeConnectionState.RECONNECTING -> R.string.orders_realtime_reconnecting
        RealtimeConnectionState.DISCONNECTED -> R.string.orders_realtime_disconnected
        RealtimeConnectionState.UNCONFIGURED -> R.string.orders_realtime_unconfigured
    }
    val icon = when (connectionState) {
        RealtimeConnectionState.CONNECTED -> Icons.Outlined.Wifi
        RealtimeConnectionState.UNCONFIGURED -> Icons.Outlined.CloudOff
        else -> Icons.Outlined.WifiOff
    }
    val tint = when (connectionState) {
        RealtimeConnectionState.CONNECTED -> DriveColors.current.success
        RealtimeConnectionState.CONNECTING,
        RealtimeConnectionState.RECONNECTING -> DriveColors.current.warning
        else -> DriveColors.current.textSecondary
    }

    DriveCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.sm))
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = DriveColors.current.textSecondary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRefresh,
                enabled = isOnline && !isFetching
            ) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.action_refresh),
                    tint = if (isOnline) DriveColors.current.brand else DriveColors.current.textTertiary
                )
            }
        }
    }
}

@Composable
private fun OfferOutcomeBanner(
    notice: OfferOutcomeNotice,
    onDismiss: () -> Unit
) {
    val (bgColor, fgColor, text) = when (notice) {
        is OfferOutcomeNotice.Accepted -> Triple(
            DriveColors.current.successContainer,
            DriveColors.current.onSuccessContainer,
            stringResource(R.string.offer_outcome_accepted, notice.assignment.tripId)
        )
        is OfferOutcomeNotice.Rejected -> Triple(
            DriveColors.current.brandContainer,
            DriveColors.current.onBrandContainer,
            stringResource(R.string.offer_outcome_rejected)
        )
        is OfferOutcomeNotice.AlreadyAssignedToOtherDriver -> Triple(
            DriveColors.current.warningContainer,
            DriveColors.current.onWarningContainer,
            notice.serverMessage ?: stringResource(R.string.offer_outcome_unavailable_other_driver)
        )
        is OfferOutcomeNotice.OfferExpired -> Triple(
            DriveColors.current.dangerContainer,
            DriveColors.current.onDangerContainer,
            notice.serverMessage ?: stringResource(R.string.offer_outcome_expired)
        )
        is OfferOutcomeNotice.DriverNotEligibleOrOffline -> Triple(
            DriveColors.current.dangerContainer,
            DriveColors.current.onDangerContainer,
            notice.serverMessage.ifBlank { stringResource(R.string.offer_outcome_not_eligible) }
        )
        is OfferOutcomeNotice.NetworkFailedNoQueue -> Triple(
            DriveColors.current.dangerContainer,
            DriveColors.current.onDangerContainer,
            stringResource(R.string.offer_outcome_network_no_queue)
        )
        is OfferOutcomeNotice.BackendNotDeployed -> Triple(
            DriveColors.current.dangerContainer,
            DriveColors.current.onDangerContainer,
            notice.reason
        )
    }

    DriveCard(backgroundColor = bgColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.sm))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = fgColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.offer_dismiss_notice),
                    color = fgColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AcceptedAssignmentHandoffCard(
    assignment: AcceptedOfferAssignment,
    onDismiss: () -> Unit
) {
    DriveCard(
        backgroundColor = DriveColors.current.successContainer,
        borderColor = DriveColors.current.success.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = DriveColors.current.success,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.offer_assigned_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = DriveColors.current.onSuccessContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        R.string.offer_assigned_subtitle,
                        assignment.tripId,
                        assignment.orderId,
                        assignment.tripStatus.name
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveColors.current.onSuccessContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.offer_assigned_phase5_notice),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveColors.current.onSuccessContainer.copy(alpha = 0.85f)
                )
            }
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.offer_dismiss_notice),
                    color = DriveColors.current.onSuccessContainer
                )
            }
        }
    }
}

@Composable
private fun DemoDispatchSimulatorCard(
    isOnline: Boolean,
    hasActiveOffers: Boolean,
    simulateRaceConflict: Boolean,
    onTriggerDemoOffer: () -> Unit,
    onToggleRaceConflict: (Boolean) -> Unit,
    onSimulateDuplicateEvent: () -> Unit
) {
    DriveCard(
        backgroundColor = DriveColors.current.warningContainer,
        borderColor = DriveColors.current.warning.copy(alpha = 0.5f)
    ) {
        Text(
            text = stringResource(R.string.orders_demo_simulator_title),
            style = MaterialTheme.typography.titleSmall,
            color = DriveColors.current.onWarningContainer,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(DriveSpacing.sm))

        if (!isOnline) {
            Text(
                text = stringResource(R.string.orders_demo_must_be_online),
                style = MaterialTheme.typography.bodySmall,
                color = DriveColors.current.onWarningContainer
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.orders_demo_simulate_race),
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveColors.current.onWarningContainer,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(DriveSpacing.sm))
                Switch(
                    checked = simulateRaceConflict,
                    onCheckedChange = onToggleRaceConflict
                )
            }
            Spacer(modifier = Modifier.height(DriveSpacing.sm))
            DrivePrimaryButton(onClick = onTriggerDemoOffer) {
                Text(text = stringResource(R.string.orders_demo_create_offer))
            }
            if (hasActiveOffers) {
                Spacer(modifier = Modifier.height(DriveSpacing.sm))
                DriveOutlinedButton(onClick = onSimulateDuplicateEvent) {
                    Text(text = stringResource(R.string.orders_demo_duplicate_event))
                }
            }
        }
    }
}
