package com.drivepartner.app.ui.screens.home

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.LocationDisabled
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.GpsReadinessStatus
import com.drivepartner.app.core.location.LocationPermissionStatus
import com.drivepartner.app.core.maps.DrivePartnerMapBoundary
import com.drivepartner.app.core.maps.MapSurfaceConfig
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.data.model.ActiveTrip
import com.drivepartner.app.data.model.DriverEligibility
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.PartnerProfile
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.WorkOffer
import com.drivepartner.app.data.remote.dto.EligibilityBlockingReason
import com.drivepartner.app.ui.components.DriveCard
import com.drivepartner.app.ui.components.DriveOutlinedButton
import com.drivepartner.app.ui.components.state.DriveLoadingView
import com.drivepartner.app.ui.components.state.DriveUiState
import com.drivepartner.app.ui.theme.DriveButtonShape
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.viewmodel.HomeUiEvent
import com.drivepartner.app.ui.viewmodel.HomeUiState
import com.drivepartner.app.ui.viewmodel.HomeViewModel

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onOpenOrders: () -> Unit,
    onOpenSupport: () -> Unit,
    activeTrip: ActiveTrip? = null,
    incomingOffer: WorkOffer? = null,
    incomingOfferRemainingSeconds: Int = 0,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMap ->
        val fineGranted = grantMap[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = grantMap[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val activity = context.findActivity()
        val canAskAgain = activity != null && (
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) || ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        viewModel.onLocationPermissionResult(
            fineGranted = fineGranted,
            coarseGranted = coarseGranted,
            canShowRationaleAgain = canAskAgain
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Foreground location notification proceeds gracefully even if denied, while granted enables visible duty alert
    }

    fun requestNotificationPermissionIfNeededAndToggleDuty() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !uiState.dutyControlState.isOnline &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.onToggleDutyStatus()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshDashboard()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeUiEvent.RequestLocationPermissions -> {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
                HomeUiEvent.OpenAppSettings -> {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }
                HomeUiEvent.OpenLocationSettings -> {
                    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }
            }
        }
    }

    HomeScreen(
        uiState = uiState,
        onToggleDutyStatus = ::requestNotificationPermissionIfNeededAndToggleDuty,
        onRecenterMap = viewModel::onRefreshLocationClick,
        onGrantPermissionClick = viewModel::onRequestLocationFromBanner,
        onProceedPermissionEducation = viewModel::onProceedFromPermissionEducation,
        onDismissPermissionEducation = viewModel::onDismissPermissionEducation,
        onOpenLocationSettings = viewModel::onOpenLocationSettingsClick,
        onSavePreferences = viewModel::onSaveServicePreferences,
        onToggleDemoEligibility = viewModel::onToggleDemoEligibility,
        onOpenOrders = onOpenOrders,
        onOpenSupport = onOpenSupport,
        activeTrip = activeTrip,
        incomingOffer = incomingOffer,
        incomingOfferRemainingSeconds = incomingOfferRemainingSeconds,
        modifier = modifier
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onToggleDutyStatus: () -> Unit,
    onRecenterMap: () -> Unit = {},
    onGrantPermissionClick: () -> Unit = {},
    onProceedPermissionEducation: () -> Unit = {},
    onDismissPermissionEducation: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {},
    onSavePreferences: (List<String>, Boolean, ServiceType) -> Unit = { _, _, _ -> },
    onToggleDemoEligibility: (Boolean) -> Unit = {},
    onOpenOrders: () -> Unit = {},
    onOpenSupport: () -> Unit = {},
    activeTrip: ActiveTrip? = null,
    incomingOffer: WorkOffer? = null,
    incomingOfferRemainingSeconds: Int = 0,
    modifier: Modifier = Modifier
) {
    var showPreferencesSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    if (uiState.showPermissionEducation) {
        AlertDialog(
            onDismissRequest = onDismissPermissionEducation,
            title = { Text(text = stringResource(R.string.permission_edu_title)) },
            text = { Text(text = stringResource(R.string.permission_edu_body)) },
            confirmButton = {
                TextButton(onClick = onProceedPermissionEducation) {
                    Text(text = stringResource(R.string.permission_edu_allow))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPermissionEducation) {
                    Text(text = stringResource(R.string.permission_edu_not_now))
                }
            }
        )
    }

    if (showPreferencesSheet) {
        ServiceAreaPreferencesSheet(
            availableZones = uiState.availableZones,
            currentPreferences = uiState.preferences,
            fallbackServiceType = uiState.partnerProfile?.serviceType ?: ServiceType.BIKE_TAXI,
            onDismiss = { showPreferencesSheet = false },
            onSave = { zoneIds, autoBoost, service ->
                showPreferencesSheet = false
                onSavePreferences(zoneIds, autoBoost, service)
            }
        )
    }

    if (uiState.screenState is DriveUiState.Loading && uiState.dashboardSummary == null && uiState.dataOrigin == DataOrigin.LIVE && uiState.boundaryMessage == null) {
        DriveLoadingView(modifier = modifier)
        return
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
            if (activeTrip != null && activeTrip.isExecutionActive) {
                DriveCard(
                    backgroundColor = DriveColors.current.brandContainer,
                    borderColor = DriveColors.current.brand.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(
                                    R.string.home_active_trip_banner,
                                    activeTrip.status.name
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = DriveColors.current.onBrandContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${activeTrip.pickup.addressSummary} -> ${activeTrip.drop.addressSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = DriveColors.current.onBrandContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(DriveSpacing.sm))
                        TextButton(onClick = onOpenOrders) {
                            Text(
                                text = stringResource(R.string.home_active_trip_action),
                                color = DriveColors.current.brand,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (incomingOffer != null && incomingOfferRemainingSeconds > 0) {
                DriveCard(
                    backgroundColor = DriveColors.current.successContainer,
                    borderColor = DriveColors.current.success.copy(alpha = 0.55f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(
                                    R.string.home_incoming_offer_banner,
                                    "${incomingOfferRemainingSeconds}s"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = DriveColors.current.onSuccessContainer,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${incomingOffer.pickupSummary} → ${incomingOffer.dropSummary}",
                                style = MaterialTheme.typography.labelSmall,
                                color = DriveColors.current.onSuccessContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(DriveSpacing.sm))
                        TextButton(onClick = onOpenOrders) {
                            Text(
                                text = stringResource(R.string.home_incoming_offer_action),
                                color = DriveColors.current.success,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

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

            if (uiState.actionFeedback != null) {
                DriveCard(
                    backgroundColor = DriveColors.current.warningContainer,
                    borderColor = DriveColors.current.warning.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = uiState.actionFeedback.asString(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = DriveColors.current.onWarningContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Permission Denied / GPS Off / Stale Location Actionable Banners
            if (!uiState.permissionStatus.hasLocationAccess) {
                LocationReadinessBanner(
                    icon = Icons.Outlined.LocationDisabled,
                    message = stringResource(
                        if (uiState.permissionStatus == LocationPermissionStatus.PERMANENTLY_DENIED) {
                            R.string.permission_permanently_denied_banner
                        } else {
                            R.string.permission_denied_banner
                        }
                    ),
                    actionLabel = stringResource(
                        if (uiState.permissionStatus == LocationPermissionStatus.PERMANENTLY_DENIED) {
                            R.string.state_permission_action
                        } else {
                            R.string.action_grant_permission
                        }
                    ),
                    onActionClick = onGrantPermissionClick
                )
            } else if (uiState.gpsStatus == GpsReadinessStatus.DISABLED) {
                LocationReadinessBanner(
                    icon = Icons.Outlined.GpsOff,
                    message = stringResource(R.string.gps_disabled_banner),
                    actionLabel = stringResource(R.string.action_enable_gps),
                    onActionClick = onOpenLocationSettings
                )
            } else if (uiState.isLocationStale) {
                LocationReadinessBanner(
                    icon = Icons.Outlined.WarningAmber,
                    message = stringResource(R.string.location_stale_banner),
                    actionLabel = stringResource(R.string.action_refresh),
                    onActionClick = onRecenterMap
                )
            } else if (uiState.permissionStatus == LocationPermissionStatus.GRANTED_APPROXIMATE) {
                DriveCard(
                    backgroundColor = DriveColors.current.warningContainer,
                    borderColor = DriveColors.current.warning.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = stringResource(R.string.location_approximate_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = DriveColors.current.onWarningContainer
                    )
                }
            }

            if (isWideViewport) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DriveSpacing.md)
                ) {
                    PartnerIdentityCard(
                        partnerProfile = uiState.partnerProfile,
                        isVerified = uiState.partnerProfile?.isVerified == true,
                        modifier = Modifier.weight(1f)
                    )
                    DutyControlCard(
                        dutyControlState = uiState.dutyControlState,
                        isActionInFlight = uiState.isActionInFlight,
                        onToggleDutyStatus = onToggleDutyStatus,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                PartnerIdentityCard(
                    partnerProfile = uiState.partnerProfile,
                    isVerified = uiState.partnerProfile?.isVerified == true
                )
                DutyControlCard(
                    dutyControlState = uiState.dutyControlState,
                    isActionInFlight = uiState.isActionInFlight,
                    onToggleDutyStatus = onToggleDutyStatus
                )
            }

            // Eligibility Card
            EligibilityStatusCard(eligibility = uiState.eligibility)

            // Map & Configured Zones Boundary (NO fake demand/surge!)
            DrivePartnerMapBoundary(
                config = MapSurfaceConfig(
                    isMapsKeyConfigured = uiState.isMapsKeyConfigured,
                    currentLocation = uiState.currentLocation,
                    configuredZones = uiState.availableZones,
                    preferredZoneIds = uiState.preferences?.preferredZoneIds.orEmpty(),
                    permissionStatus = uiState.permissionStatus,
                    gpsStatus = uiState.gpsStatus,
                    isStaleLocation = uiState.isLocationStale,
                    dataOrigin = uiState.dataOrigin
                ),
                onRecenterClick = onRecenterMap
            )

            // Today's Overview & Service Area / Auto Boost Preferences
            if (isWideViewport) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DriveSpacing.md)
                ) {
                    TodayOverviewCard(uiState = uiState, modifier = Modifier.weight(1f))
                    ServiceAreaCard(
                        uiState = uiState,
                        onOpenPreferences = { showPreferencesSheet = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                TodayOverviewCard(uiState = uiState)
                ServiceAreaCard(
                    uiState = uiState,
                    onOpenPreferences = { showPreferencesSheet = true }
                )
            }

            // Quick Actions
            DriveCard {
                Text(
                    text = stringResource(R.string.home_quick_actions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(DriveSpacing.md))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DriveSpacing.md)
                ) {
                    DriveOutlinedButton(
                        onClick = onOpenOrders,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ListAlt,
                            contentDescription = null,
                            modifier = Modifier.size(DriveSpacing.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.nav_orders))
                    }
                    DriveOutlinedButton(
                        onClick = onOpenSupport,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Headphones,
                            contentDescription = null,
                            modifier = Modifier.size(DriveSpacing.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.action_help))
                    }
                }
            }

            // Isolated DEMO testing switch so reviewers can test eligibility blocking in demo builds only
            if (uiState.dataOrigin == DataOrigin.DEMO) {
                DriveCard(
                    backgroundColor = DriveColors.current.warningContainer,
                    borderColor = DriveColors.current.warning.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = stringResource(R.string.demo_controls_title),
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
                            text = stringResource(R.string.demo_controls_eligible_toggle),
                            style = MaterialTheme.typography.bodySmall,
                            color = DriveColors.current.onWarningContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(DriveSpacing.sm))
                        Switch(
                            checked = uiState.eligibility?.isEligibleForOnline != false,
                            onCheckedChange = onToggleDemoEligibility
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationReadinessBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    actionLabel: String,
    onActionClick: () -> Unit
) {
    DriveCard(
        backgroundColor = DriveColors.current.dangerContainer,
        borderColor = DriveColors.current.danger.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DriveColors.current.danger,
                modifier = Modifier.size(DriveSpacing.iconMedium)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.md))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = DriveColors.current.onDangerContainer,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.sm))
            TextButton(onClick = onActionClick) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = DriveColors.current.danger,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PartnerIdentityCard(
    partnerProfile: PartnerProfile?,
    isVerified: Boolean,
    modifier: Modifier = Modifier
) {
    DriveCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(DriveColors.current.brand.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (partnerProfile?.serviceType) {
                        ServiceType.BIKE_TAXI -> Icons.Filled.DirectionsBike
                        ServiceType.AUTO -> Icons.Filled.LocalTaxi
                        ServiceType.CAB -> Icons.Filled.DirectionsCar
                        ServiceType.DELIVERY -> Icons.Filled.LocalShipping
                        null -> Icons.Filled.DirectionsBike
                    },
                    contentDescription = null,
                    tint = DriveColors.current.brand
                )
            }
            Spacer(modifier = Modifier.width(DriveSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = partnerProfile?.name ?: stringResource(R.string.profile_not_signed_in),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = partnerProfile?.let { "${it.vehicleBadge} • ID: ${it.id}" }
                        ?: stringResource(R.string.backend_not_deployed_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveColors.current.textSecondary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (isVerified) DriveColors.current.success else DriveColors.current.neutral,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isVerified) {
                        stringResource(R.string.status_verified)
                    } else {
                        stringResource(R.string.status_unverified)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isVerified) DriveColors.current.success else DriveColors.current.neutral,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun DutyControlCard(
    dutyControlState: DutyControlState,
    isActionInFlight: Boolean,
    onToggleDutyStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOnline = dutyControlState == DutyControlState.ONLINE
    val cardBackground = when (dutyControlState) {
        DutyControlState.ONLINE -> DriveColors.current.successContainer
        DutyControlState.BLOCKED -> DriveColors.current.dangerContainer
        DutyControlState.CONNECTION_UNKNOWN,
        DutyControlState.TURNING_ONLINE,
        DutyControlState.TURNING_OFFLINE -> DriveColors.current.warningContainer
        DutyControlState.OFFLINE -> DriveColors.current.surface
    }
    val subtitleRes = when (dutyControlState) {
        DutyControlState.ONLINE -> R.string.ready_to_receive_orders
        DutyControlState.TURNING_ONLINE -> R.string.duty_turning_online_msg
        DutyControlState.TURNING_OFFLINE -> R.string.duty_turning_offline_msg
        DutyControlState.BLOCKED -> R.string.duty_blocked_msg
        DutyControlState.CONNECTION_UNKNOWN -> R.string.duty_connection_unknown_msg
        DutyControlState.OFFLINE -> R.string.currently_offline_message
    }
    val headingColor = when (dutyControlState) {
        DutyControlState.ONLINE -> DriveColors.current.onSuccessContainer
        DutyControlState.BLOCKED -> DriveColors.current.onDangerContainer
        DutyControlState.CONNECTION_UNKNOWN,
        DutyControlState.TURNING_ONLINE,
        DutyControlState.TURNING_OFFLINE -> DriveColors.current.onWarningContainer
        DutyControlState.OFFLINE -> DriveColors.current.textPrimary
    }

    DriveCard(
        modifier = modifier,
        backgroundColor = cardBackground
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_duty_status),
                    style = MaterialTheme.typography.labelMedium,
                    color = headingColor.copy(alpha = 0.8f)
                )
                Text(
                    text = stringResource(dutyControlState.labelRes),
                    style = MaterialTheme.typography.titleLarge,
                    color = headingColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(DriveSpacing.xs))
                Text(
                    text = stringResource(subtitleRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = headingColor.copy(alpha = 0.85f)
                )
            }

            Spacer(modifier = Modifier.width(DriveSpacing.md))

            Button(
                onClick = onToggleDutyStatus,
                enabled = !isActionInFlight && dutyControlState != DutyControlState.BLOCKED,
                shape = DriveButtonShape,
                modifier = Modifier.heightIn(min = DriveSpacing.secondaryButtonHeight),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOnline) DriveColors.current.neutral else DriveColors.current.success
                )
            ) {
                if (isActionInFlight) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = DriveColors.current.onBrand,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(DriveSpacing.iconSmall)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isOnline) stringResource(R.string.go_offline) else stringResource(R.string.go_online),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun EligibilityStatusCard(
    eligibility: DriverEligibility?,
    modifier: Modifier = Modifier
) {
    val isBlocked = eligibility != null && !eligibility.isEligibleForOnline
    DriveCard(
        modifier = modifier,
        backgroundColor = if (isBlocked) DriveColors.current.dangerContainer else DriveColors.current.surface,
        borderColor = if (isBlocked) DriveColors.current.danger.copy(alpha = 0.35f) else DriveColors.current.border
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isBlocked) Icons.Outlined.Block else Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = when {
                    eligibility == null -> DriveColors.current.neutral
                    eligibility.isEligibleForOnline -> DriveColors.current.success
                    else -> DriveColors.current.danger
                },
                modifier = Modifier.size(DriveSpacing.iconMedium)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.eligibility_heading),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        eligibility == null -> stringResource(R.string.eligibility_not_checked)
                        eligibility.isEligibleForOnline -> stringResource(R.string.eligibility_verified_ready)
                        else -> stringResource(R.string.eligibility_blocked_title)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBlocked) DriveColors.current.onDangerContainer else DriveColors.current.textSecondary
                )
            }
        }

        if (isBlocked && eligibility != null && eligibility.blockingReasons.isNotEmpty()) {
            Spacer(modifier = Modifier.height(DriveSpacing.sm))
            eligibility.blockingReasons.forEach { reason ->
                val reasonRes = when (reason) {
                    EligibilityBlockingReason.ACCOUNT_NOT_APPROVED -> R.string.elig_reason_account_not_approved
                    EligibilityBlockingReason.KYC_INCOMPLETE -> R.string.elig_reason_kyc_incomplete
                    EligibilityBlockingReason.DOCUMENT_REJECTED -> R.string.elig_reason_document_rejected
                    EligibilityBlockingReason.DOCUMENT_EXPIRED -> R.string.elig_reason_document_expired
                    EligibilityBlockingReason.VEHICLE_NOT_APPROVED -> R.string.elig_reason_vehicle_not_approved
                    EligibilityBlockingReason.CITY_NOT_ENABLED -> R.string.elig_reason_city_not_enabled
                    EligibilityBlockingReason.ACTIVE_SUSPENSION -> R.string.elig_reason_active_suspension
                    EligibilityBlockingReason.LOCATION_UNAVAILABLE -> R.string.elig_reason_location_unavailable
                }
                Text(
                    text = "• ${stringResource(reasonRes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveColors.current.onDangerContainer
                )
            }
        }
    }
}

@Composable
private fun TodayOverviewCard(
    uiState: HomeUiState,
    modifier: Modifier = Modifier
) {
    val summary = uiState.dashboardSummary
    val displayEarnings = (summary?.todayEstimatedEarnings ?: MoneyPaise.ZERO_INR).formatDisplay()
    val completedCount = summary?.todayCompletedCount ?: 0

    DriveCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.home_today_summary),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(DriveSpacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DriveSpacing.md)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_completed_trips_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveColors.current.textSecondary
                )
                Text(
                    text = completedCount.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (uiState.dataOrigin == DataOrigin.DEMO) {
                        "${stringResource(R.string.home_estimated_earnings_label)} (DEMO)"
                    } else {
                        stringResource(R.string.home_estimated_earnings_label)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveColors.current.textSecondary
                )
                Text(
                    text = displayEarnings,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(DriveSpacing.sm))
        Text(
            text = stringResource(R.string.home_estimate_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = DriveColors.current.textSecondary
        )
        Spacer(modifier = Modifier.height(DriveSpacing.xs))
        Text(
            text = if (!summary?.lastSyncedUtc.isNullOrBlank()) {
                stringResource(R.string.home_last_synced, summary?.lastSyncedUtc.orEmpty())
            } else {
                stringResource(R.string.home_not_synced)
            },
            style = MaterialTheme.typography.labelSmall,
            color = DriveColors.current.textSecondary
        )
        Spacer(modifier = Modifier.height(DriveSpacing.xs))
        Text(
            text = stringResource(R.string.home_empty_message),
            style = MaterialTheme.typography.bodySmall,
            color = DriveColors.current.textSecondary
        )
    }
}

@Composable
private fun ServiceAreaCard(
    uiState: HomeUiState,
    onOpenPreferences: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs = uiState.preferences
    val selectedZoneCount = prefs?.preferredZoneIds?.size ?: 0
    val autoBoostEnabled = prefs?.autoBoostEnabled == true

    DriveCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = DriveColors.current.brand,
                modifier = Modifier.size(DriveSpacing.iconMedium)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_zones_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.home_zones_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveColors.current.textSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(DriveSpacing.sm))
        Text(
            text = stringResource(R.string.home_selected_zones_count, selectedZoneCount),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(
                if (autoBoostEnabled) R.string.home_autoboost_on else R.string.home_autoboost_off
            ),
            style = MaterialTheme.typography.bodySmall,
            color = DriveColors.current.textSecondary
        )
        Spacer(modifier = Modifier.height(DriveSpacing.md))
        DriveOutlinedButton(onClick = onOpenPreferences) {
            Text(text = stringResource(R.string.home_manage_preferences))
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
