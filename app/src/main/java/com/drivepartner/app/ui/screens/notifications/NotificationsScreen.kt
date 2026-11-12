package com.drivepartner.app.ui.screens.notifications

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.drivepartner.app.data.model.AppNotificationItem
import com.drivepartner.app.data.model.NotificationDeepLinkResolver
import com.drivepartner.app.navigation.DriveDestination
import com.drivepartner.app.ui.components.DriveCard
import com.drivepartner.app.ui.components.DriveOutlinedButton
import com.drivepartner.app.ui.components.state.DriveEmptyView
import com.drivepartner.app.ui.screens.rewards.RetentionOutcomeBanner
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.viewmodel.NotificationsUiState
import com.drivepartner.app.ui.viewmodel.NotificationsViewModel

@Composable
fun NotificationsRoute(
    viewModel: NotificationsViewModel,
    onNavigateDeepLink: (DriveDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshNotificationsAndPreferences()
    }

    NotificationsScreen(
        uiState = uiState,
        onRefresh = viewModel::refreshNotificationsAndPreferences,
        onNotificationClick = { item ->
            viewModel.markReadAndResolveDeepLink(item, onNavigateDeepLink)
        },
        onLoadMore = viewModel::loadMoreNotifications,
        onUpdatePreferences = viewModel::updatePreferences,
        onDismissOutcomeNotice = viewModel::dismissOutcomeNotice,
        onSimulateDemoPush = viewModel::simulateDemoPushNotification,
        modifier = modifier
    )
}

@Composable
fun NotificationsScreen(
    uiState: NotificationsUiState,
    onRefresh: () -> Unit = {},
    onNotificationClick: (AppNotificationItem) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onUpdatePreferences: (Boolean, Boolean, Boolean) -> Unit = { _, _, _ -> },
    onDismissOutcomeNotice: () -> Unit = {},
    onSimulateDemoPush: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

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

            // Notification Inbox Header + List
            DriveCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.notifications_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.notifications_unread_summary, uiState.unreadCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = DriveColors.current.textSecondary
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !uiState.isRefreshing) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = stringResource(R.string.action_refresh)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(DriveSpacing.sm))

                if (uiState.notifications.isEmpty()) {
                    DriveEmptyView(
                        modifier = Modifier.height(210.dp),
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.notifications_empty_title),
                        description = stringResource(R.string.notifications_empty_desc)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)) {
                        uiState.notifications.forEach { item ->
                            val resolvedDest = NotificationDeepLinkResolver.resolveDestination(item.deepLinkRoute)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNotificationClick(item) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!item.isRead) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(DriveColors.current.brand, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(shape = DriveChipShape, color = DriveColors.current.brandContainer) {
                                        Text(
                                            text = item.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DriveColors.current.onBrandContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DriveColors.current.textSecondary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.createdAtUtc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DriveColors.current.textSecondary
                                    )
                                    if (resolvedDest != null) {
                                        Text(
                                            text = stringResource(
                                                R.string.notifications_deep_link_chip,
                                                stringResource(resolvedDest.titleRes)
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DriveColors.current.brand,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                        if (uiState.hasMore) {
                            DriveOutlinedButton(
                                onClick = onLoadMore,
                                enabled = !uiState.isLoadingMore
                            ) {
                                Text(stringResource(R.string.notifications_load_more))
                            }
                        }
                    }
                }
            }

            // Notification Preferences Card
            uiState.preferences?.let { prefs ->
                DriveCard {
                    Text(
                        text = stringResource(R.string.notifications_prefs_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(DriveSpacing.sm))
                    PreferenceToggleRow(
                        label = stringResource(R.string.notifications_pref_orders),
                        checked = prefs.orderAlertsEnabled,
                        enabled = !uiState.isSavingPreferences,
                        onCheckedChange = {
                            onUpdatePreferences(it, prefs.earningsAlertsEnabled, prefs.campaignAlertsEnabled)
                        }
                    )
                    PreferenceToggleRow(
                        label = stringResource(R.string.notifications_pref_earnings),
                        checked = prefs.earningsAlertsEnabled,
                        enabled = !uiState.isSavingPreferences,
                        onCheckedChange = {
                            onUpdatePreferences(prefs.orderAlertsEnabled, it, prefs.campaignAlertsEnabled)
                        }
                    )
                    PreferenceToggleRow(
                        label = stringResource(R.string.notifications_pref_campaigns),
                        checked = prefs.campaignAlertsEnabled,
                        enabled = !uiState.isSavingPreferences,
                        onCheckedChange = {
                            onUpdatePreferences(prefs.orderAlertsEnabled, prefs.earningsAlertsEnabled, it)
                        }
                    )
                }
            }

            // Isolated DEMO Push Notification Simulator
            if (uiState.dataOrigin == DataOrigin.DEMO) {
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
                    DriveOutlinedButton(onClick = { onSimulateDemoPush("earnings") }) {
                        Text(stringResource(R.string.demo_notifications_simulate_push))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}
