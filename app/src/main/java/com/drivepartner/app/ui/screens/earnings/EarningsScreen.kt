package com.drivepartner.app.ui.screens.earnings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.data.model.EarningsSummary
import com.drivepartner.app.data.model.FinancialOutcomeNotice
import com.drivepartner.app.data.model.LedgerEntry
import com.drivepartner.app.data.model.PayoutAccount
import com.drivepartner.app.data.model.PayoutGateReason
import com.drivepartner.app.data.model.PayoutRecord
import com.drivepartner.app.data.model.WalletSummary
import com.drivepartner.app.data.model.labelRes
import com.drivepartner.app.data.remote.dto.PayoutAccountStatus
import com.drivepartner.app.data.remote.dto.PayoutStatus
import com.drivepartner.app.ui.components.DriveCard
import com.drivepartner.app.ui.components.DriveOutlinedButton
import com.drivepartner.app.ui.components.DrivePrimaryButton
import com.drivepartner.app.ui.components.state.DriveEmptyView
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.viewmodel.EarningsPeriod
import com.drivepartner.app.ui.viewmodel.EarningsUiState
import com.drivepartner.app.ui.viewmodel.EarningsViewModel

@Composable
fun EarningsRoute(
    viewModel: EarningsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshAllFinancials()
    }

    EarningsScreen(
        uiState = uiState,
        onSelectPeriod = viewModel::selectPeriod,
        onRefreshAll = viewModel::refreshAllFinancials,
        onOpenTransactionDetail = viewModel::openLedgerTransactionDetail,
        onCloseTransactionDetail = viewModel::closeTransactionDetail,
        onLoadMoreLedger = viewModel::loadMoreLedgerTransactions,
        onLoadMorePayouts = viewModel::loadMorePayoutHistory,
        onLinkPayoutAccount = viewModel::linkPayoutAccount,
        onRequestWithdrawal = viewModel::requestWithdrawal,
        onDismissOutcomeNotice = viewModel::dismissOutcomeNotice,
        onToggleDemoSandboxPayouts = viewModel::toggleDemoSandboxPayouts,
        onSeedDemoTripEarning = viewModel::seedDemoCompletedTripEarning,
        onSimulateDemoWebhook = viewModel::simulateDemoWebhookSettlement,
        modifier = modifier
    )
}

@Composable
fun EarningsScreen(
    selectedPeriod: EarningsPeriod = EarningsPeriod.TODAY,
    onSelectPeriod: (EarningsPeriod) -> Unit = {},
    modifier: Modifier = Modifier,
    dataOrigin: DataOrigin = DataOrigin.LIVE,
    boundaryMessage: String? = null
) {
    EarningsScreen(
        uiState = EarningsUiState(
            dataOrigin = dataOrigin,
            selectedPeriod = selectedPeriod,
            boundaryMessage = boundaryMessage
        ),
        onSelectPeriod = onSelectPeriod,
        modifier = modifier
    )
}

@Composable
fun EarningsScreen(
    uiState: EarningsUiState,
    onSelectPeriod: (EarningsPeriod) -> Unit,
    onRefreshAll: () -> Unit = {},
    onOpenTransactionDetail: (LedgerEntry) -> Unit = {},
    onCloseTransactionDetail: () -> Unit = {},
    onLoadMoreLedger: () -> Unit = {},
    onLoadMorePayouts: () -> Unit = {},
    onLinkPayoutAccount: (String, String, String) -> Unit = { _, _, _ -> },
    onRequestWithdrawal: (String, Long) -> Unit = { _, _ -> },
    onDismissOutcomeNotice: () -> Unit = {},
    onToggleDemoSandboxPayouts: (Boolean) -> Unit = {},
    onSeedDemoTripEarning: () -> Unit = {},
    onSimulateDemoWebhook: (String, PayoutStatus) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var showLinkAccountDialog by remember { mutableStateOf(false) }

    uiState.selectedLedgerEntry?.let { entry ->
        TransactionDetailDialog(
            entry = entry,
            statement = uiState.selectedStatementDetail,
            isLoadingStatement = uiState.isLoadingStatementDetail,
            statementError = uiState.statementDetailError,
            onDismiss = onCloseTransactionDetail
        )
    }

    val wallet = uiState.walletSummary
    if (showWithdrawDialog && wallet != null) {
        WithdrawPayoutDialog(
            wallet = wallet,
            verifiedAccounts = uiState.payoutAccounts.filter {
                it.status == PayoutAccountStatus.VERIFIED
            },
            isSubmitting = uiState.isSubmittingPayout,
            onDismiss = { showWithdrawDialog = false },
            onSubmit = { token, amountMinor ->
                showWithdrawDialog = false
                onRequestWithdrawal(token, amountMinor)
            }
        )
    }

    if (showLinkAccountDialog) {
        LinkPayoutAccountDialog(
            isSubmitting = uiState.isSubmittingAccountLink,
            onDismiss = { showLinkAccountDialog = false },
            onSubmit = { holder, accountNum, ifsc ->
                showLinkAccountDialog = false
                onLinkPayoutAccount(holder, accountNum, ifsc)
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(DriveColors.current.backgroundSubtle)
    ) {
        val isWideViewport = maxWidth >= 600.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(DriveSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DriveSpacing.lg)
        ) {
            // Top Boundary or Outcome Notice
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

            uiState.outcomeNotice?.let { notice ->
                FinancialOutcomeBanner(
                    notice = notice,
                    onDismiss = onDismissOutcomeNotice
                )
            }

            // Wallet Summary Card (Ledger-Derived Available / Pending / Settled)
            WalletSummaryCard(
                wallet = wallet,
                dataOrigin = uiState.dataOrigin,
                isRefreshing = uiState.isRefreshing,
                payoutGateReason = uiState.payoutGateReason,
                onRefresh = onRefreshAll,
                onWithdrawClick = { showWithdrawDialog = true }
            )

            // Daily & Weekly Earnings Summary Cards
            val todayDisplay = (uiState.dailySummary?.finalizedTotal
                ?: wallet?.todayEarning
                ?: MoneyPaise.ZERO_INR).formatDisplay()
            val weekDisplay = (uiState.weeklySummary?.finalizedTotal ?: MoneyPaise.ZERO_INR).formatDisplay()

            DailyAndWeeklyCards(
                todayDisplay = todayDisplay,
                weekDisplay = weekDisplay,
                dataOrigin = uiState.dataOrigin,
                isWide = isWideViewport
            )

            // Period Selector + Itemized Breakdown (Base / Incentives / Referrals / Adjustments / Finalized)
            Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DriveSpacing.sm)
                ) {
                    val periods = listOf(
                        EarningsPeriod.TODAY to R.string.earnings_period_today,
                        EarningsPeriod.WEEK to R.string.earnings_period_week,
                        EarningsPeriod.MONTH to R.string.earnings_period_month
                    )
                    periods.forEach { (period, labelRes) ->
                        val selected = uiState.selectedPeriod == period
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectPeriod(period) },
                            label = { Text(stringResource(labelRes)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DriveColors.current.brandContainer,
                                selectedLabelColor = DriveColors.current.brand
                            )
                        )
                    }
                }

                uiState.periodSummary?.let { summary ->
                    EarningsBreakdownCard(summary = summary)
                }
            }

            // Payout Accounts & Withdrawal Gate Card
            PayoutAccountCard(
                accounts = uiState.payoutAccounts,
                dataOrigin = uiState.dataOrigin,
                boundaryMessage = uiState.boundaryMessage,
                canLinkAccount = uiState.dataOrigin == DataOrigin.DEMO || wallet != null,
                onLinkAccountClick = { showLinkAccountDialog = true }
            )

            // Payout Status & History Section
            PayoutHistoryCard(
                payouts = uiState.payouts,
                hasMore = uiState.hasMorePayouts,
                isLoadingMore = uiState.isLoadingMorePayouts,
                dataOrigin = uiState.dataOrigin,
                onLoadMore = onLoadMorePayouts,
                onSimulateDemoWebhook = onSimulateDemoWebhook
            )

            // Append-Only Ledger Transaction History
            DriveCard {
                Text(
                    text = stringResource(R.string.earnings_transactions_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(DriveSpacing.md))

                if (uiState.ledgerEntries.isEmpty()) {
                    DriveEmptyView(
                        modifier = Modifier.height(200.dp),
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = stringResource(R.string.earnings_empty_title),
                        description = stringResource(R.string.earnings_empty_description)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)) {
                        uiState.ledgerEntries.forEach { entry ->
                            LedgerEntryRow(
                                entry = entry,
                                onClick = { onOpenTransactionDetail(entry) }
                            )
                            HorizontalDivider()
                        }
                        if (uiState.hasMoreLedger) {
                            DriveOutlinedButton(
                                onClick = onLoadMoreLedger,
                                enabled = !uiState.isLoadingMoreLedger
                            ) {
                                Text(text = stringResource(R.string.ledger_load_more))
                            }
                        }
                    }
                }
            }

            // Isolated DEMO Sandbox Controls (NEVER visible in LIVE builds)
            if (uiState.dataOrigin == DataOrigin.DEMO) {
                DemoFinancialSandboxCard(
                    sandboxPayoutsEnabled = wallet?.livePayoutsEnabled == true,
                    onToggleSandboxPayouts = onToggleDemoSandboxPayouts,
                    onSeedCompletedTrip = onSeedDemoTripEarning
                )
            }
        }
    }
}

@Composable
private fun WalletSummaryCard(
    wallet: WalletSummary?,
    dataOrigin: DataOrigin,
    isRefreshing: Boolean,
    payoutGateReason: PayoutGateReason?,
    onRefresh: () -> Unit,
    onWithdrawClick: () -> Unit
) {
    DriveCard(
        backgroundColor = DriveColors.current.warningContainer,
        borderColor = DriveColors.current.warning.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(DriveColors.current.warning, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CurrencyRupee,
                    contentDescription = null,
                    tint = DriveColors.current.onBrand,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.wallet_summary_heading),
                        style = MaterialTheme.typography.titleMedium,
                        color = DriveColors.current.onWarningContainer,
                        fontWeight = FontWeight.Bold
                    )
                    if (dataOrigin == DataOrigin.DEMO) {
                        Surface(shape = DriveChipShape, color = DriveColors.current.onBrand) {
                            Text(
                                text = "DEMO",
                                style = MaterialTheme.typography.labelSmall,
                                color = DriveColors.current.onWarningContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = (wallet?.available ?: MoneyPaise.ZERO_INR).formatDisplay(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = DriveColors.current.onWarningContainer
                )
                Text(
                    text = stringResource(R.string.wallet_available_balance),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveColors.current.onWarningContainer
                )
            }
            IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.action_refresh),
                    tint = DriveColors.current.onWarningContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(DriveSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.wallet_pending_balance),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveColors.current.onWarningContainer
                )
                Text(
                    text = (wallet?.pending ?: MoneyPaise.ZERO_INR).formatDisplay(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DriveColors.current.onWarningContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.wallet_settled_total),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveColors.current.onWarningContainer
                )
                Text(
                    text = (wallet?.settled ?: MoneyPaise.ZERO_INR).formatDisplay(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = DriveColors.current.onWarningContainer
                )
            }
        }

        if (wallet != null) {
            Spacer(modifier = Modifier.height(DriveSpacing.xs))
            Text(
                text = stringResource(
                    R.string.wallet_limits_info,
                    wallet.minimumPayout.formatDisplay(),
                    wallet.maximumPayout.formatDisplay()
                ),
                style = MaterialTheme.typography.labelSmall,
                color = DriveColors.current.onWarningContainer
            )
            Text(
                text = stringResource(R.string.wallet_derived_at, wallet.derivedAtUtc),
                style = MaterialTheme.typography.labelSmall,
                color = DriveColors.current.onWarningContainer.copy(alpha = 0.85f)
            )
        }

        Spacer(modifier = Modifier.height(DriveSpacing.sm))
        Text(
            text = stringResource(R.string.wallet_backend_authority_note),
            style = MaterialTheme.typography.labelSmall,
            color = DriveColors.current.onWarningContainer.copy(alpha = 0.9f)
        )

        Spacer(modifier = Modifier.height(DriveSpacing.md))
        DrivePrimaryButton(
            onClick = onWithdrawClick,
            enabled = payoutGateReason == null
        ) {
            Text(text = stringResource(R.string.earnings_withdraw_action))
        }

        if (payoutGateReason != null) {
            Spacer(modifier = Modifier.height(DriveSpacing.xs))
            val reasonText = when (payoutGateReason) {
                PayoutGateReason.BACKEND_NOT_DEPLOYED ->
                    stringResource(R.string.payout_disabled_backend_unconfigured)
                PayoutGateReason.LIVE_PAYOUTS_DISABLED ->
                    stringResource(R.string.payout_disabled_no_provider)
                PayoutGateReason.NO_VERIFIED_ACCOUNT ->
                    stringResource(R.string.payout_disabled_no_verified_account)
                PayoutGateReason.INSUFFICIENT_AVAILABLE_BALANCE ->
                    stringResource(
                        R.string.payout_disabled_insufficient_balance,
                        (wallet?.minimumPayout ?: MoneyPaise(10_000L)).formatDisplay()
                    )
            }
            Text(
                text = reasonText,
                style = MaterialTheme.typography.labelSmall,
                color = DriveColors.current.onWarningContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun DailyAndWeeklyCards(
    todayDisplay: String,
    weekDisplay: String,
    dataOrigin: DataOrigin,
    isWide: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DriveSpacing.md)
    ) {
        DriveCard(modifier = Modifier.weight(1f)) {
            Text(
                text = if (dataOrigin == DataOrigin.DEMO) {
                    "${stringResource(R.string.earnings_today)} (DEMO)"
                } else {
                    stringResource(R.string.earnings_today)
                },
                style = MaterialTheme.typography.labelMedium,
                color = DriveColors.current.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = todayDisplay,
                style = if (isWide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        DriveCard(modifier = Modifier.weight(1f)) {
            Text(
                text = if (dataOrigin == DataOrigin.DEMO) {
                    "${stringResource(R.string.earnings_this_week)} (DEMO)"
                } else {
                    stringResource(R.string.earnings_this_week)
                },
                style = MaterialTheme.typography.labelMedium,
                color = DriveColors.current.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = weekDisplay,
                style = if (isWide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EarningsBreakdownCard(summary: EarningsSummary) {
    DriveCard {
        Text(
            text = stringResource(R.string.earnings_breakdown_heading),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(DriveSpacing.sm))
        BreakdownRow(stringResource(R.string.earnings_base_label), summary.base.formatDisplay())
        BreakdownRow(stringResource(R.string.earnings_incentives), summary.incentive.formatDisplay())
        BreakdownRow(stringResource(R.string.earnings_referrals_label), summary.referral.formatDisplay())
        BreakdownRow(stringResource(R.string.earnings_adjustments_label), summary.adjustment.formatDisplay())
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        BreakdownRow(
            stringResource(R.string.earnings_finalized_total_label),
            summary.finalizedTotal.formatDisplay(),
            bold = true
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.earnings_last_updated, summary.lastUpdatedUtc),
            style = MaterialTheme.typography.labelSmall,
            color = DriveColors.current.textSecondary
        )
    }
}

@Composable
private fun BreakdownRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) MaterialTheme.colorScheme.onSurface else DriveColors.current.textSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun PayoutAccountCard(
    accounts: List<PayoutAccount>,
    dataOrigin: DataOrigin,
    boundaryMessage: String?,
    canLinkAccount: Boolean,
    onLinkAccountClick: () -> Unit
) {
    DriveCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.AccountBalance,
                contentDescription = null,
                tint = DriveColors.current.brand,
                modifier = Modifier.size(DriveSpacing.iconMedium)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.earnings_payout_account_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (accounts.isEmpty()) {
                    Text(
                        text = if (dataOrigin == DataOrigin.DEMO) {
                            stringResource(R.string.demo_mode_banner)
                        } else {
                            boundaryMessage ?: stringResource(R.string.earnings_payout_unlinked)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = DriveColors.current.textSecondary
                    )
                }
            }
            TextButton(onClick = onLinkAccountClick, enabled = canLinkAccount) {
                Text(text = stringResource(R.string.earnings_link_account_action))
            }
        }

        if (accounts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(DriveSpacing.sm))
            accounts.forEach { account ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account.accountLabel,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${account.maskedAccountNumber} • ${account.ifscCode}",
                            style = MaterialTheme.typography.labelSmall,
                            color = DriveColors.current.textSecondary
                        )
                    }
                    Surface(
                        shape = DriveChipShape,
                        color = if (account.status == PayoutAccountStatus.VERIFIED) {
                            DriveColors.current.successContainer
                        } else {
                            DriveColors.current.warningContainer
                        }
                    ) {
                        Text(
                            text = stringResource(account.status.labelRes()),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (account.status == PayoutAccountStatus.VERIFIED) {
                                DriveColors.current.onSuccessContainer
                            } else {
                                DriveColors.current.onWarningContainer
                            },
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PayoutHistoryCard(
    payouts: List<PayoutRecord>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    dataOrigin: DataOrigin,
    onLoadMore: () -> Unit,
    onSimulateDemoWebhook: (String, PayoutStatus) -> Unit
) {
    DriveCard {
        Text(
            text = stringResource(R.string.payout_history_heading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(DriveSpacing.sm))
        if (payouts.isEmpty()) {
            Text(
                text = stringResource(R.string.payout_history_empty),
                style = MaterialTheme.typography.bodySmall,
                color = DriveColors.current.textSecondary
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)) {
                payouts.forEach { payout ->
                    val (bg, fg) = when (payout.status) {
                        PayoutStatus.PAID -> DriveColors.current.successContainer to DriveColors.current.onSuccessContainer
                        PayoutStatus.REQUESTED,
                        PayoutStatus.PROCESSING -> DriveColors.current.brandContainer to DriveColors.current.onBrandContainer
                        PayoutStatus.FAILED,
                        PayoutStatus.CANCELLED,
                        PayoutStatus.REVERSED -> DriveColors.current.dangerContainer to DriveColors.current.onDangerContainer
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = payout.amount.formatDisplay(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(shape = DriveChipShape, color = bg) {
                                Text(
                                    text = stringResource(payout.status.labelRes()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fg,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.payout_requested_at, payout.requestedAtUtc),
                            style = MaterialTheme.typography.labelSmall,
                            color = DriveColors.current.textSecondary
                        )
                        payout.settledAtUtc?.let { settledAt ->
                            Text(
                                text = stringResource(R.string.payout_settled_at, settledAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = DriveColors.current.textSecondary
                            )
                        }
                        payout.failureReason?.let { reason ->
                            Text(
                                text = stringResource(R.string.payout_failure_reason, reason),
                                style = MaterialTheme.typography.labelSmall,
                                color = DriveColors.current.danger
                            )
                        }

                        // Sandbox Webhook Simulation ONLY in DEMO mode for non-terminal payouts
                        if (dataOrigin == DataOrigin.DEMO &&
                            (payout.status == PayoutStatus.REQUESTED || payout.status == PayoutStatus.PROCESSING)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(DriveSpacing.sm)
                            ) {
                                TextButton(
                                    onClick = { onSimulateDemoWebhook(payout.payoutId, PayoutStatus.PAID) }
                                ) {
                                    Text(text = stringResource(R.string.demo_financial_webhook_paid))
                                }
                                TextButton(
                                    onClick = { onSimulateDemoWebhook(payout.payoutId, PayoutStatus.FAILED) }
                                ) {
                                    Text(
                                        text = stringResource(R.string.demo_financial_webhook_failed),
                                        color = DriveColors.current.danger
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
                if (hasMore) {
                    DriveOutlinedButton(
                        onClick = onLoadMore,
                        enabled = !isLoadingMore
                    ) {
                        Text(text = stringResource(R.string.payout_load_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun LedgerEntryRow(
    entry: LedgerEntry,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(entry.entryType.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = DriveColors.current.textSecondary
            )
            Text(
                text = stringResource(R.string.ledger_balance_after, entry.balanceAfter.formatDisplay()),
                style = MaterialTheme.typography.labelSmall,
                color = DriveColors.current.textSecondary
            )
        }
        Spacer(modifier = Modifier.width(DriveSpacing.sm))
        Text(
            text = entry.amount.formatDisplay(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (entry.isCredit) DriveColors.current.success else DriveColors.current.danger
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = DriveColors.current.textTertiary
        )
    }
}

@Composable
private fun FinancialOutcomeBanner(
    notice: FinancialOutcomeNotice,
    onDismiss: () -> Unit
) {
    val (bg, fg, msg) = when (notice) {
        is FinancialOutcomeNotice.PayoutRequested -> Triple(
            DriveColors.current.successContainer,
            DriveColors.current.onSuccessContainer,
            stringResource(
                R.string.financial_outcome_payout_requested,
                notice.payout.amount.formatDisplay(),
                notice.payout.status.name
            )
        )
        is FinancialOutcomeNotice.PayoutAccountLinked -> Triple(
            DriveColors.current.brandContainer,
            DriveColors.current.onBrandContainer,
            stringResource(
                R.string.financial_outcome_account_linked,
                notice.account.maskedAccountNumber
            )
        )
        is FinancialOutcomeNotice.ActionRejected -> Triple(
            DriveColors.current.dangerContainer,
            DriveColors.current.onDangerContainer,
            notice.serverMessage
        )
        is FinancialOutcomeNotice.NetworkFailedNoOfflineQueue -> Triple(
            DriveColors.current.dangerContainer,
            DriveColors.current.onDangerContainer,
            stringResource(R.string.financial_outcome_network_no_queue)
        )
        is FinancialOutcomeNotice.BackendNotDeployed -> Triple(
            DriveColors.current.dangerContainer,
            DriveColors.current.onDangerContainer,
            notice.reason
        )
    }

    DriveCard(backgroundColor = bg) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.sm))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = fg,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.offer_dismiss_notice),
                    color = fg,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DemoFinancialSandboxCard(
    sandboxPayoutsEnabled: Boolean,
    onToggleSandboxPayouts: (Boolean) -> Unit,
    onSeedCompletedTrip: () -> Unit
) {
    DriveCard(
        backgroundColor = DriveColors.current.warningContainer,
        borderColor = DriveColors.current.warning.copy(alpha = 0.5f)
    ) {
        Text(
            text = stringResource(R.string.demo_financial_sandbox_title),
            style = MaterialTheme.typography.titleSmall,
            color = DriveColors.current.onWarningContainer,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(DriveSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.demo_financial_sandbox_toggle),
                style = MaterialTheme.typography.bodySmall,
                color = DriveColors.current.onWarningContainer,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.sm))
            Switch(
                checked = sandboxPayoutsEnabled,
                onCheckedChange = onToggleSandboxPayouts
            )
        }
        Spacer(modifier = Modifier.height(DriveSpacing.sm))
        DriveOutlinedButton(onClick = onSeedCompletedTrip) {
            Text(text = stringResource(R.string.demo_financial_seed_trip))
        }
    }
}
