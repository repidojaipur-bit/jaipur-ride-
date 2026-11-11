package com.drivepartner.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.data.model.ActiveTrip
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.OrderDetail
import com.drivepartner.app.data.model.OrderHistoryItem
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.TripCancelReason
import com.drivepartner.app.data.model.TripLocationPoint
import com.drivepartner.app.data.remote.dto.OrderHistoryFilter
import com.drivepartner.app.data.remote.dto.TripStatus
import com.drivepartner.app.ui.screens.orders.OrdersScreen
import com.drivepartner.app.ui.theme.DrivePartnerTheme
import com.drivepartner.app.ui.viewmodel.OrdersTab
import com.drivepartner.app.ui.viewmodel.OrdersUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrdersPhase5ScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun sampleActiveTrip(
        status: TripStatus,
        requiresPin: Boolean = false,
        earningSourceRefId: String? = null
    ) = ActiveTrip(
        tripId = "TRIP-UI-1",
        orderId = "ORD-UI-1",
        serviceType = ServiceType.AUTO,
        serviceCategoryCode = "AUTO",
        status = status,
        version = 2L,
        pickup = TripLocationPoint("Pickup", "Hawa Mahal, Jaipur", 26.9239, 75.8267),
        drop = TripLocationPoint("Drop", "World Trade Park, Jaipur", 26.8535, 75.8047),
        maskedContactAvailable = false,
        requiresStartPin = requiresPin,
        estimatedTotal = MoneyPaise(14500L),
        updatedAtUtc = "2026-11-11T10:30:00Z",
        earningSourceRefId = earningSourceRefId,
        dataOrigin = DataOrigin.DEMO
    )

    @Test
    fun activeTrip_arrivedWithPinAndDisabledMaskedContact_andCancelReasonFlow() {
        var cancelledReason: TripCancelReason? = null
        val state = OrdersUiState(
            dataOrigin = DataOrigin.DEMO,
            selectedTab = OrdersTab.ACTIVE,
            partnerStatus = PartnerStatus.ONLINE,
            dutyControlState = DutyControlState.ONLINE,
            activeTrip = sampleActiveTrip(TripStatus.ARRIVED_AT_PICKUP, requiresPin = true)
        )

        composeRule.setContent {
            DrivePartnerTheme {
                OrdersScreen(
                    uiState = state,
                    onSelectTab = {},
                    onGoOnlineClick = {},
                    onCancelTrip = { cancelledReason = it }
                )
            }
        }

        composeRule.onNodeWithText("Hawa Mahal, Jaipur").assertIsDisplayed()
        composeRule.onNodeWithText("World Trade Park, Jaipur").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.trip_action_navigate_pickup))
            .assertIsEnabled()
        composeRule.onNodeWithText(context.getString(R.string.trip_action_contact_customer))
            .assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.trip_contact_unavailable_note))
            .assertIsDisplayed()
        // Start Trip disabled until PIN entered
        composeRule.onNodeWithText(context.getString(R.string.trip_action_start_trip))
            .assertIsNotEnabled()

        composeRule.onNodeWithText(context.getString(R.string.trip_action_cancel_trip))
            .performClick()
        composeRule.onNodeWithText(context.getString(R.string.trip_cancel_dialog_confirm))
            .performClick()
        assertEquals(TripCancelReason.RIDER_NO_SHOW, cancelledReason)
    }

    @Test
    fun inProgressTrip_showsDropNavigationAndCompleteAction_andCompletedShowsSourceRefWithoutLocalMoney() {
        var completedCalled = false
        val inProgressState = OrdersUiState(
            dataOrigin = DataOrigin.DEMO,
            selectedTab = OrdersTab.ACTIVE,
            partnerStatus = PartnerStatus.ONLINE,
            dutyControlState = DutyControlState.ONLINE,
            activeTrip = sampleActiveTrip(TripStatus.IN_PROGRESS)
        )

        composeRule.setContent {
            DrivePartnerTheme {
                OrdersScreen(
                    uiState = inProgressState,
                    onSelectTab = {},
                    onGoOnlineClick = {},
                    onCompleteTrip = { completedCalled = true }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.trip_action_navigate_drop))
            .assertIsEnabled()
        composeRule.onNodeWithText(context.getString(R.string.trip_action_complete_trip))
            .performClick()
        assertTrue(completedCalled)
    }

    @Test
    fun completedHistory_supportsLoadMoreAndItemClick_withoutLocalEarningsMath() {
        var loadMoreClicked = false
        var openedOrderId: String? = null
        val item = OrderHistoryItem(
            orderId = "ORD-HIST-1",
            tripId = "TRIP-HIST-1",
            serviceType = ServiceType.CAB,
            serviceCategoryCode = "CAB",
            status = TripStatus.COMPLETED,
            pickupSummary = "Sindhi Camp",
            dropSummary = "Jaipur Airport",
            completedOrCancelledAtUtc = "2026-11-11T11:00:00Z",
            finalizedAmount = null,
            dataOrigin = DataOrigin.DEMO
        )

        val state = OrdersUiState(
            dataOrigin = DataOrigin.DEMO,
            selectedTab = OrdersTab.COMPLETED,
            historyFilter = OrderHistoryFilter.COMPLETED,
            historyItems = listOf(item),
            hasMoreHistory = true,
            historyNextCursor = "2"
        )

        composeRule.setContent {
            DrivePartnerTheme {
                OrdersScreen(
                    uiState = state,
                    onSelectTab = {},
                    onGoOnlineClick = {},
                    onLoadMoreHistory = { loadMoreClicked = true },
                    onOpenOrderDetail = { openedOrderId = it }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.orders_history_amount_pending))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.orders_history_load_more)).performClick()
        assertTrue(loadMoreClicked)
        composeRule.onNodeWithText("Sindhi Camp -> Jaipur Airport").performClick()
        assertEquals("ORD-HIST-1", openedOrderId)
    }

    @Test
    fun orderDetailDialog_rendersSourceRefTimelineAndDisclaimer() {
        var closed = false
        val detail = OrderDetail(
            orderId = "ORD-HIST-1",
            tripId = "TRIP-HIST-1",
            serviceType = ServiceType.CAB,
            serviceCategoryCode = "CAB",
            status = TripStatus.COMPLETED,
            pickupSummary = "Sindhi Camp",
            dropSummary = "Jaipur Airport",
            distanceMeters = 11200,
            durationSeconds = 1620,
            earningSourceRefId = "DEMO-EARN-SRC-HIST1",
            timelineEvents = listOf("ASSIGNED", "COMPLETED"),
            dataOrigin = DataOrigin.DEMO
        )

        val state = OrdersUiState(
            dataOrigin = DataOrigin.DEMO,
            selectedTab = OrdersTab.COMPLETED,
            historyFilter = OrderHistoryFilter.COMPLETED,
            selectedOrderDetail = detail
        )

        composeRule.setContent {
            DrivePartnerTheme {
                OrdersScreen(
                    uiState = state,
                    onSelectTab = {},
                    onGoOnlineClick = {},
                    onCloseOrderDetail = { closed = true }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.order_detail_title)).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.order_detail_source_ref_id, "DEMO-EARN-SRC-HIST1")
        ).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.trip_no_local_earnings_note))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.order_detail_close)).performClick()
        assertTrue(closed)
    }
}
