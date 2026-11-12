package com.drivepartner.app.core.maps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.LocationDisabled
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.DriverLocationSnapshot
import com.drivepartner.app.core.location.GpsReadinessStatus
import com.drivepartner.app.core.location.LocationPermissionStatus
import com.drivepartner.app.data.model.ServiceZone
import com.drivepartner.app.ui.theme.DriveBlueContainer
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveError
import com.drivepartner.app.ui.theme.DriveErrorContainer
import com.drivepartner.app.ui.theme.DriveGreenContainer
import com.drivepartner.app.ui.theme.DriveGreenOnline
import com.drivepartner.app.ui.theme.DriveOnBlueContainer
import com.drivepartner.app.ui.theme.DriveOnErrorContainer
import com.drivepartner.app.ui.theme.DriveOnGreenContainer
import com.drivepartner.app.ui.theme.DriveOnYellowContainer
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceSubtle
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextSecondary
import com.drivepartner.app.ui.theme.DriveWarningAmber
import com.drivepartner.app.ui.theme.DriveYellowContainer
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale

/**
 * Immutable input contract for the Google Maps integration boundary.
 * Demand/surge layers are intentionally omitted unless backed by verified live backend data.
 */
data class MapSurfaceConfig(
    val isMapsKeyConfigured: Boolean,
    val currentLocation: DriverLocationSnapshot?,
    val configuredZones: List<ServiceZone>,
    val preferredZoneIds: List<String>,
    val permissionStatus: LocationPermissionStatus,
    val gpsStatus: GpsReadinessStatus,
    val isStaleLocation: Boolean,
    val dataOrigin: DataOrigin
)

/**
 * Google Maps boundary isolating vendor SDK usage from business logic.
 * - Renders Google Maps Compose ONLY when [MapSurfaceConfig.isMapsKeyConfigured] is true.
 * - Falls back to a clean, accessible zone/location surface when unconfigured so dev/demo builds
 *   never crash or fail blank.
 * - Never fabricates demand heatmaps or surge pricing indicators.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrivePartnerMapBoundary(
    config: MapSurfaceConfig,
    onRecenterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DriveCardShape,
        color = DriveSurfaceWhite,
        border = BorderStroke(DriveSpacing.hairlineBorder, DriveSurfaceBorder),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(DriveSurfaceSubtle)
            ) {
                if (config.isMapsKeyConfigured) {
                    ConfiguredGoogleMapView(
                        config = config,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    UnconfiguredMapPlaceholderSurface(
                        config = config,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Location freshness / status pill (top-start)
                LocationFreshnessBadge(
                    config = config,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(DriveSpacing.md)
                )

                // Reachable Recenter control (top-end)
                FilledTonalIconButton(
                    onClick = onRecenterClick,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = DriveSurfaceWhite,
                        contentColor = DriveBluePrimary
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(DriveSpacing.md)
                        .size(DriveSpacing.minTouchTarget)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MyLocation,
                        contentDescription = stringResource(R.string.cd_recenter_map)
                    )
                }
            }

            // Configured zones legend & non-map textual accessibility alternative
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DriveSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)
            ) {
                Text(
                    text = stringResource(R.string.map_configured_zones_heading),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                if (config.configuredZones.isEmpty()) {
                    Text(
                        text = stringResource(R.string.map_no_zones_configured),
                        style = MaterialTheme.typography.bodySmall,
                        color = DriveTextSecondary
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(DriveSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)
                    ) {
                        config.configuredZones.forEach { zone ->
                            val isPreferred = zone.zoneId in config.preferredZoneIds
                            Surface(
                                shape = DriveChipShape,
                                color = if (isPreferred) DriveBlueContainer else DriveSurfaceSubtle,
                                border = BorderStroke(
                                    1.dp,
                                    if (isPreferred) DriveBluePrimary else DriveSurfaceBorder
                                )
                            ) {
                                Text(
                                    text = if (zone.isDemoZone) "${zone.name} (DEMO)" else zone.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isPreferred) DriveOnBlueContainer else DriveTextSecondary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.map_no_surge_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveTextSecondary
                )
            }
        }
    }
}

@Composable
private fun ConfiguredGoogleMapView(
    config: MapSurfaceConfig,
    modifier: Modifier = Modifier
) {
    val defaultCityViewport = LatLng(26.9124, 75.7873) // Jaipur neutral viewport center only
    val driverLatLng = config.currentLocation?.let { LatLng(it.latitude, it.longitude) }
    val targetLatLng = driverLatLng ?: defaultCityViewport

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(targetLatLng, if (driverLatLng != null) 15f else 12f)
    }

    LaunchedEffect(driverLatLng) {
        if (driverLatLng != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(driverLatLng, 15f)
            )
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(
            isMyLocationEnabled = config.permissionStatus.hasLocationAccess &&
                config.gpsStatus == GpsReadinessStatus.READY
        ),
        uiSettings = MapUiSettings(
            myLocationButtonEnabled = false,
            zoomControlsEnabled = true,
            compassEnabled = true
        )
    ) {
        if (driverLatLng != null) {
            val markerState = remember(driverLatLng) { MarkerState(position = driverLatLng) }
            Marker(
                state = markerState,
                title = stringResource(R.string.map_own_location_marker)
            )
        }
    }
}

@Composable
private fun UnconfiguredMapPlaceholderSurface(
    config: MapSurfaceConfig,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(DriveSurfaceSubtle)
            .padding(DriveSpacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(DriveBlueContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Map,
                    contentDescription = null,
                    tint = DriveBluePrimary
                )
            }
            Text(
                text = stringResource(R.string.map_sdk_boundary_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = when {
                    !config.permissionStatus.hasLocationAccess ->
                        stringResource(R.string.map_location_permission_needed)
                    config.gpsStatus == GpsReadinessStatus.DISABLED ->
                        stringResource(R.string.map_gps_disabled_notice)
                    config.currentLocation != null ->
                        stringResource(
                            R.string.map_coordinates_preview,
                            String.format(Locale.US, "%.4f", config.currentLocation.latitude),
                            String.format(Locale.US, "%.4f", config.currentLocation.longitude),
                            config.currentLocation.accuracyMeters.toInt()
                        )
                    else -> stringResource(R.string.map_key_unconfigured_notice)
                },
                style = MaterialTheme.typography.bodySmall,
                color = DriveTextSecondary
            )
        }
    }
}

@Composable
private fun LocationFreshnessBadge(
    config: MapSurfaceConfig,
    modifier: Modifier = Modifier
) {
    val (bg, fg, icon, labelRes) = when {
        !config.permissionStatus.hasLocationAccess -> Quad(
            DriveErrorContainer,
            DriveOnErrorContainer,
            Icons.Outlined.LocationDisabled,
            R.string.location_badge_permission_denied
        )
        config.gpsStatus == GpsReadinessStatus.DISABLED -> Quad(
            DriveErrorContainer,
            DriveOnErrorContainer,
            Icons.Outlined.GpsOff,
            R.string.location_badge_gps_off
        )
        config.currentLocation == null -> Quad(
            DriveYellowContainer,
            DriveOnYellowContainer,
            Icons.Outlined.WarningAmber,
            R.string.location_badge_unavailable
        )
        config.isStaleLocation -> Quad(
            DriveYellowContainer,
            DriveOnYellowContainer,
            Icons.Outlined.WarningAmber,
            R.string.location_badge_stale
        )
        config.permissionStatus == LocationPermissionStatus.GRANTED_APPROXIMATE -> Quad(
            DriveYellowContainer,
            DriveOnYellowContainer,
            Icons.Outlined.WarningAmber,
            R.string.location_badge_approximate
        )
        else -> Quad(
            DriveGreenContainer,
            DriveOnGreenContainer,
            Icons.Outlined.MyLocation,
            R.string.location_badge_live_gps
        )
    }

    Surface(
        modifier = modifier,
        shape = DriveChipShape,
        color = bg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
