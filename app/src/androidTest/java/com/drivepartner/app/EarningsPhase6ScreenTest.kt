package com.drivepartner.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.data.model.EarningStatement
import com.drivepartner.app.data.model.EarningsSummary
import com.drivepartner.app.data.model.LedgerEntry
import com.drivepartner.app.data.model.PayoutAccount
import com.drivepartner.app.data.model.PayoutGateReason
import com.drivepartner.app.data.model.PayoutRecord
import com.drivepartner.app.data.model.WalletSummary
import com.drivepartner.app.data.remote.dto.LedgerEntryType
import com.drivepartner.app.data.remote.dto.PayoutAccountStatus
import com.drivepartner.app.data.remote.dto.PayoutStatus
import com.drivepartner.app.ui.screens.earnings.EarningsScreen
import com.drivepartner.app.ui.theme.DrivePartnerTheme
import com.drivepartner.app.ui.viewmodel.EarningsPeriod
import com.drivepartner.app.ui.viewmodel.EarningsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EarningsPhase6ScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun sampleWallet(livePayoutsEnabled: Boolean) = WalletSummary(
        available = MoneyPaise(50_000L),
        pending = MoneyPaise(15_000L),
        settled = MoneyPaise(100_000L),
        todayEarning = MoneyPaise(25_000L),
        minimumPayout = MoneyPaise(10_000L),
        maximumPayout = MoneyPaise(2_000_000L),
        livePayoutsEnabled = livePayoutsEnabled,
        derivedAtUtc = "2026-11-11T12:00:00Z",
        dataOrigin = DataOrigin.DEMO
    )

    @Test
    fun livePayoutDisabled_disablesWithdrawWithClearReason_andShowsWalletAndDailyWeekly() {
        val state = EarningsUiState(
            dataOrigin = DataOrigin.LIVE,
            selectedPeriod = EarningsPeriod.TODAY,
            dailySummary = EarningsSummary(
                period = "today",
                base = MoneyPaise(20_000L),
                incentive = MoneyPaise(5_000L),
                referral = MoneyPaise.ZERO_INR,
                adjustment = MoneyPaise.ZERO_INR,
                finalizedTotal = MoneyPaise(25_000L),
                lastUpdatedUtc = "2026-11-11T12:00:00Z",
                dataOrigin = DataOrigin.LIVE
            ),
            weeklySummary = EarningsSummary(
                period = "week",
                base = MoneyPaise(140_000L),
                incentive = MoneyPaise(25_000L),
                referral = MoneyPaise(5_000L),
                adjustment = MoneyPaise.ZERO_INR,
                finalizedTotal = MoneyPaise(170_000L),
                lastUpdatedUtc = "2026-11-11T12:00:00Z",
                dataOrigin = DataOrigin.LIVE
            ),
            walletSummary = sampleWallet(livePayoutsEnabled = false),
            payoutGateReason = PayoutGateReason.LIVE_PAYOUTS_DISABLED
        )

        composeRule.setContent {
            DrivePartnerTheme {
                EarningsScreen(
                    uiState = state,
                    onSelectPeriod = {}
                )
            }
        }

        composeRule.onNodeWithText("₹500.00").assertIsDisplayed()
        composeRule.onNodeWithText("₹150.00").assertIsDisplayed()
        composeRule.onNodeWithText("₹1000.00").assertIsDisplayed()
        composeRule.onNodeWithText("₹250.00").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("₹1700.00").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.earnings_withdraw_action))
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(context.getString(R.string.payout_disabled_no_provider))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun enabledWithdrawDialog_parsesIntegerPaiseAndSubmitsExactAmount() {
        var submittedToken: String? = null
        var submittedAmountMinor: Long? = null

        val account = PayoutAccount(
            payoutAccountToken = "DEMO-TOK-99",
            accountLabel = "Demo Driver (HDFC0001234)",
            maskedAccountNumber = "XXXX5678",
            ifscCode = "HDFC0001234",
            status = PayoutAccountStatus.VERIFIED,
            dataOrigin = DataOrigin.DEMO
        )
        val state = EarningsUiState(
            dataOrigin = DataOrigin.DEMO,
            walletSummary = sampleWallet(livePayoutsEnabled = true),
            payoutAccounts = listOf(account),
            payouts = listOf(
                PayoutRecord(
                    payoutId = "PAY-1",
                    payoutAccountToken = "DEMO-TOK-99",
                    amount = MoneyPaise(15_000L),
                    status = PayoutStatus.REQUESTED,
                    requestedAtUtc = "2026-11-11T11:55:00Z",
                    dataOrigin = DataOrigin.DEMO
                )
            ),
            payoutGateReason = null
        )

        composeRule.setContent {
            DrivePartnerTheme {
                EarningsScreen(
                    uiState = state,
                    onSelectPeriod = {},
                    onRequestWithdrawal = { token, amount ->
                        submittedToken = token
                        submittedAmountMinor = amount
                    }
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.earnings_withdraw_action))
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeRule.onNodeWithText(context.getString(R.string.payout_dialog_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.payout_dialog_amount_label))
            .performTextInput("250.50")
        composeRule.onNodeWithText(context.getString(R.string.payout_dialog_submit))
            .assertIsEnabled()
            .performClick()

        assertEquals("DEMO-TOK-99", submittedToken)
        assertEquals(25_050L, submittedAmountMinor)
    }

    @Test
    fun ledgerEntryAndTransactionDetailDialog_displayItemizedStatementAndImmutabilityNote() {
        val entry = LedgerEntry(
            entryId = "LED-100",
            sourceType = "EARNING_STATEMENT",
            sourceId = "STMT-100",
            entryType = LedgerEntryType.TRIP_EARNING_CREDIT,
            amount = MoneyPaise(12_500L),
            balanceAfter = MoneyPaise(50_000L),
            createdAtUtc = "2026-11-11T11:00:00Z",
            description = "Trip DEMO-TRIP-1 finalized",
            statementId = "STMT-100",
            dataOrigin = DataOrigin.DEMO
        )
        val statement = EarningStatement(
            statementId = "STMT-100",
            earningSourceRefId = "SRC-100",
            tripId = "DEMO-TRIP-1",
            fareConfigVersion = "DEMO-FARE-CFG-2026-11",
            quoteSnapshotId = "DEMO-QUOTE-1",
            baseFare = MoneyPaise(2_500L),
            distanceFare = MoneyPaise(8_000L),
            timeFare = MoneyPaise(1_500L),
            incentive = MoneyPaise(2_000L),
            platformFee = MoneyPaise(1_270L),
            tax = MoneyPaise(230L),
            netEarning = MoneyPaise(12_500L),
            finalizedAtUtc = "2026-11-11T11:00:00Z",
            dataOrigin = DataOrigin.DEMO
        )

        val state = EarningsUiState(
            dataOrigin = DataOrigin.DEMO,
            ledgerEntries = listOf(entry),
            selectedLedgerEntry = entry,
            selectedStatementDetail = statement
        )

        composeRule.setContent {
            DrivePartnerTheme {
                EarningsScreen(
                    uiState = state,
                    onSelectPeriod = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.transaction_detail_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.transaction_detail_fare_version, "DEMO-FARE-CFG-2026-11")
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.transaction_detail_net_earning, "₹125.00")
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.transaction_detail_immutable_note))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
