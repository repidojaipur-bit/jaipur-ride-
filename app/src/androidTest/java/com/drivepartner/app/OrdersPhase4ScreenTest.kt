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
import com.drivepartner.app.core.realtime.RealtimeConnectionState
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.OfferOutcomeNotice
import com.drivepartner.app.data.model.OfferRejectReason
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.WorkOffer
import com.drivepartner.app.data.remote.dto.OfferStatus
import com.drivepartner.app.ui.screens.orders.OrdersScreen
import com.drivepartner.app.ui.theme.DrivePartnerTheme
import com.drivepartner.app.ui.viewmodel.OrdersTab
import com.drivepartner.app.ui.viewmodel.OrdersUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrdersPhase4ScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun ordersScreen_displaysOfferCardWithFareBreakdownCountdownAndExplicitActions() {
        var rejectedReason: OfferRejectReason? = null
        val sampleOffer = WorkOffer(
            offerId = "DEMO-OFFER-1",
            orderId = "DEMO-ORD-1",
            serviceType = ServiceType.AUTO,
            serviceCategoryCode = "AUTO",
            priorityLabel = "Priority",
            pickupSummary = "MI Road Metro Gate 2, Jaipur",
            dropSummary = "Malviya Nagar Sector 4, Jaipur",
            pickupDistanceMeters = 1200,
            tripDistanceMeters = 6400,
            durationSeconds = 1080,
            estimatedBase = MoneyPaise(11500L),
            eligibleIncentive = MoneyPaise(1500L),
            estimatedTotal = MoneyPaise(13000L),
            isEstimate = true,
            expiresAtEpochMs = 1_700_000_030_000L,
            serverNowEpochMs = 1_700_000_000_000L,
            serverClockOffsetMs = 0L,
            status = OfferStatus.OFFERED,
            dataOrigin = DataOrigin.DEMO
        )

        val state = OrdersUiState(
            dataOrigin = DataOrigin.DEMO,
            selectedTab = OrdersTab.ACTIVE,
            partnerStatus = PartnerStatus.ONLINE,
            dutyControlState = DutyControlState.ONLINE,
            realtimeConnectionState = RealtimeConnectionState.CONNECTED,
            activeOffers = listOf(sampleOffer),
            remainingSecondsByOfferId = mapOf("DEMO-OFFER-1" to 24)
        )

        composeRule.setContent {
            DrivePartnerTheme {
                OrdersScreen(
                    uiState = state,
                    onSelectTab = {},
                    onGoOnlineClick = {},
                    onRejectOffer = { _, reason -> rejectedReason = reason }
                )
            }
        }

        composeRule.onNodeWithText("MI Road Metro Gate 2, Jaipur").assertIsDisplayed()
        composeRule.onNodeWithText("Malviya Nagar Sector 4, Jaipur").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.offer_countdown_remaining, 24))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.offer_estimate_disclaimer))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.offer_accept_action))
            .assertIsDisplayed()
            .assertIsEnabled()
        composeRule.onNodeWithText(context.getString(R.string.offer_reject_action))
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeRule.onNodeWithText(context.getString(R.string.offer_reject_dialog_confirm))
            .performClick()
        assertEquals(OfferRejectReason.DISTANCE_TOO_FAR, rejectedReason)
    }

    @Test
    fun ordersScreen_expiredOfferDisablesAccept_andLiveModeHidesDemoControls() {
        val expiredOffer = WorkOffer(
            offerId = "LIVE-OFFER-1",
            orderId = "LIVE-ORD-1",
            serviceType = ServiceType.BIKE_TAXI,
            serviceCategoryCode = "BIKE",
            pickupSummary = "Sindhi Camp",
            dropSummary = "Bapu Nagar",
            pickupDistanceMeters = 500,
            tripDistanceMeters = 4000,
            durationSeconds = 720,
            estimatedBase = MoneyPaise(5500L),
            eligibleIncentive = MoneyPaise.ZERO_INR,
            estimatedTotal = MoneyPaise(5500L),
            expiresAtEpochMs = 1_700_000_000_000L,
            serverNowEpochMs = 1_700_000_000_000L,
            serverClockOffsetMs = 0L,
            status = OfferStatus.EXPIRED,
            dataOrigin = DataOrigin.LIVE
        )

        val state = OrdersUiState(
            dataOrigin = DataOrigin.LIVE,
            selectedTab = OrdersTab.ACTIVE,
            partnerStatus = PartnerStatus.ONLINE,
            dutyControlState = DutyControlState.ONLINE,
            realtimeConnectionState = RealtimeConnectionState.UNCONFIGURED,
            activeOffers = listOf(expiredOffer),
            remainingSecondsByOfferId = mapOf("LIVE-OFFER-1" to 0),
            outcomeNotice = OfferOutcomeNotice.AlreadyAssignedToOtherDriver("LIVE-OFFER-9")
        )

        composeRule.setContent {
            DrivePartnerTheme {
                OrdersScreen(
                    uiState = state,
                    onSelectTab = {},
                    onGoOnlineClick = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.offer_countdown_expired))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.offer_accept_action))
            .assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.offer_outcome_unavailable_other_driver))
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.orders_demo_simulator_title))
            .assertDoesNotExist()
    }
}
