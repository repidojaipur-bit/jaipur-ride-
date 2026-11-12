package com.drivepartner.app.ui.screens.rewards

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.model.CampaignItem
import com.drivepartner.app.data.model.ReferralSummary
import com.drivepartner.app.data.model.RetentionSafetyOutcomeNotice
import com.drivepartner.app.data.model.labelRes
import com.drivepartner.app.data.remote.dto.CampaignCategory
import com.drivepartner.app.data.remote.dto.ReferralStatus
import com.drivepartner.app.ui.components.DriveCard
import com.drivepartner.app.ui.components.DriveOutlinedButton
import com.drivepartner.app.ui.components.DrivePrimaryButton
import com.drivepartner.app.ui.components.state.DriveEmptyView
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.viewmodel.RewardsUiState
import com.drivepartner.app.ui.viewmodel.RewardsViewModel

@Composable
fun RewardsRoute(
    viewModel: RewardsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshRewards()
    }

    RewardsScreen(
        uiState = uiState,
        onSelectCategory = viewModel::selectCategory,
        onRefresh = viewModel::refreshRewards,
        onApplyReferralCode = viewModel::applyReferralCode,
        onDismissOutcomeNotice = viewModel::dismissOutcomeNotice,
        onToggleDemoEligibility = viewModel::toggleDemoEligibility,
        onSimulateDemoProgress = viewModel::simulateDemoCampaignProgress,
        onSimulateDemoQualifyReferral = viewModel::simulateDemoQualifyReferral,
        modifier = modifier
    )
}

@Composable
fun RewardsScreen(
    dataOrigin: DataOrigin = DataOrigin.LIVE,
    assignedTierLabel: String? = null,
    modifier: Modifier = Modifier
) {
    RewardsScreen(
        uiState = RewardsUiState(
            dataOrigin = dataOrigin,
            assignedTierLabel = assignedTierLabel
        ),
        modifier = modifier
    )
}

@Composable
fun RewardsScreen(
    uiState: RewardsUiState,
    onSelectCategory: (CampaignCategory?) -> Unit = {},
    onRefresh: () -> Unit = {},
    onApplyReferralCode: (String) -> Unit = {},
    onDismissOutcomeNotice: () -> Unit = {},
    onToggleDemoEligibility: (Boolean) -> Unit = {},
    onSimulateDemoProgress: (String) -> Unit = {},
    onSimulateDemoQualifyReferral: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DriveColors.current.backgroundSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(DriveSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DriveSpacing.lg)
        ) {
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
                RetentionOutcomeBanner(notice = notice, onDismiss = onDismissOutcomeNotice)
            }

            // Partner Tier Summary Card
            DriveCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(DriveColors.current.brandContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.WorkspacePremium,
                            contentDescription = null,
                            tint = DriveColors.current.brand,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.rewards_current_tier),
                            style = MaterialTheme.typography.titleSmall,
                            color = DriveColors.current.textSecondary
                        )
                        Text(
                            text = uiState.assignedTierLabel
                                ?: if (uiState.dataOrigin == DataOrigin.DEMO) {
                                    "${stringResource(R.string.rewards_tier_unassigned)} (DEMO)"
                                } else {
                                    stringResource(R.string.rewards_tier_unassigned)
                                },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !uiState.isRefreshing) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.action_refresh)
                        )
                    }
                }
            }

            // Campaigns Section (Daily Incentives, Weekly Challenges, Priority Rewards)
            DriveCard {
                Text(
                    text = stringResource(R.string.rewards_campaigns_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.rewards_server_eligibility_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveColors.current.textSecondary
                )
                Spacer(modifier = Modifier.height(DriveSpacing.sm))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(DriveSpacing.sm)
                ) {
                    val categories = listOf(
                        null to R.string.rewards_filter_all,
                        CampaignCategory.DAILY_INCENTIVE to R.string.rewards_cat_daily_incentive,
                        CampaignCategory.WEEKLY_CHALLENGE to R.string.rewards_cat_weekly_challenge,
                        CampaignCategory.PRIORITY_REWARD to R.string.rewards_cat_priority_reward
                    )
                    categories.forEach { (cat, labelRes) ->
                        FilterChip(
                            selected = uiState.selectedCategory == cat,
                            onClick = { onSelectCategory(cat) },
                            label = { Text(stringResource(labelRes)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DriveColors.current.brandContainer,
                                selectedLabelColor = DriveColors.current.brand
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(DriveSpacing.md))
                if (uiState.campaigns.isEmpty()) {
                    DriveEmptyView(
                        modifier = Modifier.height(210.dp),
                        icon = Icons.Outlined.EmojiEvents,
                        title = stringResource(R.string.rewards_empty_title),
                        description = stringResource(R.string.rewards_empty_description)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.md)) {
                        uiState.campaigns.forEach { campaign ->
                            CampaignCard(
                                campaign = campaign,
                                onSimulateDemoProgress = { onSimulateDemoProgress(campaign.campaignId) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }

            // Refer & Earn + Referral Tracking Card
            ReferralSectionCard(
                summary = uiState.referralSummary,
                dataOrigin = uiState.dataOrigin,
                isApplyingReferral = uiState.isApplyingReferral,
                onShareLink = { link, code ->
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "$code — $link")
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(shareIntent, null))
                    }
                },
                onApplyReferralCode = onApplyReferralCode,
                onSimulateDemoQualifyReferral = onSimulateDemoQualifyReferral
            )

            // Isolated DEMO Sandbox Controls (NEVER visible in LIVE builds)
            if (uiState.dataOrigin == DataOrigin.DEMO) {
                val eligibleNow = uiState.referralSummary?.isEligibleForReferral ?: true
                DriveCard(
                    backgroundColor = DriveColors.current.warningContainer,
                    borderColor = DriveColors.current.warning.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = stringResource(R.string.demo_rewards_sandbox_title),
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
                            text = stringResource(R.string.demo_rewards_eligibility_toggle),
                            style = MaterialTheme.typography.bodySmall,
                            color = DriveColors.current.onWarningContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = eligibleNow,
                            onCheckedChange = onToggleDemoEligibility
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CampaignCard(
    campaign: CampaignItem,
    onSimulateDemoProgress: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = DriveChipShape, color = DriveColors.current.brandContainer) {
                Text(
                    text = stringResource(campaign.category.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveColors.current.onBrandContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Text(
                text = campaign.reward.formatDisplay(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DriveColors.current.brand
            )
        }

        Text(
            text = campaign.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = campaign.termsSummary,
            style = MaterialTheme.typography.bodySmall,
            color = DriveColors.current.textSecondary
        )
        Text(
            text = stringResource(
                R.string.rewards_terms_version,
                campaign.termsVersion,
                campaign.expiresAtUtc
            ),
            style = MaterialTheme.typography.labelSmall,
            color = DriveColors.current.textSecondary
        )

        LinearProgressIndicator(
            progress = { campaign.progressFraction },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(
                R.string.rewards_progress_label,
                campaign.currentCount,
                campaign.targetCount
            ),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )

        if (!campaign.isDriverEligible) {
            Surface(shape = DriveChipShape, color = DriveColors.current.dangerContainer) {
                Text(
                    text = campaign.ineligibilityReason ?: stringResource(R.string.rewards_ineligible_badge),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveColors.current.onDangerContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else if (campaign.isQualified) {
            Surface(shape = DriveChipShape, color = DriveColors.current.successContainer) {
                Text(
                    text = stringResource(
                        R.string.rewards_qualified_badge,
                        campaign.rewardCreditedSourceRefId ?: campaign.campaignId
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveColors.current.onSuccessContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        if (campaign.dataOrigin == DataOrigin.DEMO && !campaign.isQualified && campaign.isDriverEligible) {
            DriveOutlinedButton(onClick = onSimulateDemoProgress) {
                Text(text = stringResource(R.string.demo_rewards_simulate_progress))
            }
        }
    }
}

@Composable
private fun ReferralSectionCard(
    summary: ReferralSummary?,
    dataOrigin: DataOrigin,
    isApplyingReferral: Boolean,
    onShareLink: (link: String, code: String) -> Unit,
    onApplyReferralCode: (String) -> Unit,
    onSimulateDemoQualifyReferral: (String) -> Unit
) {
    var codeInput by remember { mutableStateOf("") }

    DriveCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.CardGiftcard,
                contentDescription = null,
                tint = DriveColors.current.brand,
                modifier = Modifier.size(DriveSpacing.iconMedium)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.sm))
            Text(
                text = stringResource(R.string.referral_heading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(DriveSpacing.sm))

        if (summary == null) {
            Text(
                text = stringResource(R.string.referral_tracking_empty),
                style = MaterialTheme.typography.bodySmall,
                color = DriveColors.current.textSecondary
            )
        } else {
            Text(
                text = stringResource(R.string.referral_code_label, summary.referralCode),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(
                    R.string.referral_stats_row,
                    summary.qualifiedCount,
                    summary.pendingCount,
                    summary.totalEarned.formatDisplay()
                ),
                style = MaterialTheme.typography.bodySmall,
                color = DriveColors.current.textSecondary
            )
            if (!summary.isEligibleForReferral) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(shape = DriveChipShape, color = DriveColors.current.dangerContainer) {
                    Text(
                        text = summary.ineligibilityReason ?: stringResource(R.string.rewards_ineligible_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = DriveColors.current.onDangerContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(DriveSpacing.sm))
            DriveOutlinedButton(
                onClick = { onShareLink(summary.shareLink, summary.referralCode) }
            ) {
                Icon(imageVector = Icons.Outlined.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(DriveSpacing.xs))
                Text(text = stringResource(R.string.referral_share_action))
            }

            Spacer(modifier = Modifier.height(DriveSpacing.md))
            Text(
                text = stringResource(R.string.referral_apply_heading),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = codeInput,
                onValueChange = { codeInput = it },
                placeholder = { Text(stringResource(R.string.referral_apply_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(DriveSpacing.sm))
            DrivePrimaryButton(
                onClick = {
                    val toSend = codeInput.trim()
                    codeInput = ""
                    onApplyReferralCode(toSend)
                },
                enabled = !isApplyingReferral && codeInput.isNotBlank()
            ) {
                Text(text = stringResource(R.string.referral_apply_action))
            }

            Spacer(modifier = Modifier.height(DriveSpacing.md))
            Text(
                text = stringResource(R.string.referral_tracking_heading),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(DriveSpacing.xs))
            if (summary.referrals.isEmpty()) {
                Text(
                    text = stringResource(R.string.referral_tracking_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveColors.current.textSecondary
                )
            } else {
                summary.referrals.forEach { ref ->
                    val (bg, fg) = when (ref.status) {
                        ReferralStatus.QUALIFIED,
                        ReferralStatus.REWARDED -> DriveColors.current.successContainer to DriveColors.current.onSuccessContainer
                        ReferralStatus.PENDING -> DriveColors.current.warningContainer to DriveColors.current.onWarningContainer
                        ReferralStatus.REJECTED_SELF_REFERRAL,
                        ReferralStatus.REJECTED_FRAUD -> DriveColors.current.dangerContainer to DriveColors.current.onDangerContainer
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ref.refereeDisplayLabel,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(shape = DriveChipShape, color = bg) {
                                Text(
                                    text = stringResource(ref.status.labelRes()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fg,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = ref.qualificationSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = DriveColors.current.textSecondary
                        )
                        if (dataOrigin == DataOrigin.DEMO &&
                            ref.status == ReferralStatus.PENDING &&
                            summary.isEligibleForReferral
                        ) {
                            TextButton(onClick = { onSimulateDemoQualifyReferral(ref.referralId) }) {
                                Text(text = stringResource(R.string.demo_rewards_qualify_referral))
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
internal fun RetentionOutcomeBanner(
    notice: RetentionSafetyOutcomeNotice,
    onDismiss: () -> Unit
) {
    val (bg, fg, msg) = when (notice) {
        is RetentionSafetyOutcomeNotice.SuccessMessage ->
            Triple(DriveColors.current.successContainer, DriveColors.current.onSuccessContainer, notice.message)
        is RetentionSafetyOutcomeNotice.Rejected ->
            Triple(DriveColors.current.dangerContainer, DriveColors.current.onDangerContainer, notice.message)
        is RetentionSafetyOutcomeNotice.NetworkFailure ->
            Triple(DriveColors.current.dangerContainer, DriveColors.current.onDangerContainer, notice.message)
        is RetentionSafetyOutcomeNotice.BackendUnavailable ->
            Triple(DriveColors.current.dangerContainer, DriveColors.current.onDangerContainer, notice.reason)
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
