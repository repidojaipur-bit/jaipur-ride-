package com.drivepartner.app.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.drivepartner.app.R
import com.drivepartner.app.data.local.SavedLocation
import com.drivepartner.app.data.local.SavedLocationsRepository
import com.drivepartner.app.ui.components.DriveIcon
import com.drivepartner.app.ui.components.DriveIconWell
import com.drivepartner.app.ui.components.DriveOutlinedButton
import com.drivepartner.app.ui.components.DrivePrimaryButton
import com.drivepartner.app.ui.components.DriveSunkenCard
import com.drivepartner.app.ui.icons.DriveIcons
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveSheetShape
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.viewmodel.SavedLocationsUiState

/**
 * Favourite/saved-locations shortcut (spec section 4: "shortcut for favourite or saved locations").
 *
 * Locations are personal and stay on-device; the sheet offers add, remove and "return home" so the
 * top-bar bookmark always has something real behind it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedLocationsSheet(
    uiState: SavedLocationsUiState,
    onAdd: (label: String, locality: String, cityCode: String) -> Unit,
    onRemove: (String) -> Unit,
    onSetReturnHome: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember { mutableStateOf("") }
    var locality by remember { mutableStateOf("") }
    var cityCode by remember { mutableStateOf("") }
    var showValidation by remember { mutableStateOf(false) }
    val colors = DriveColors.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = DriveSheetShape,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DriveSpacing.lg, vertical = DriveSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DriveSpacing.md)
        ) {
            Text(
                text = stringResource(R.string.saved_locations_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = stringResource(R.string.saved_locations_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )

            if (uiState.locations.isEmpty()) {
                DriveSunkenCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(DriveSpacing.xs)
                    ) {
                        Text(
                            text = stringResource(R.string.saved_locations_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.saved_locations_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            } else {
                uiState.locations.forEach { location ->
                    SavedLocationRow(
                        location = location,
                        busy = uiState.isBusy,
                        onRemove = { onRemove(location.id) },
                        onSetReturnHome = { onSetReturnHome(location.id) }
                    )
                }
            }

            HorizontalDivider(color = colors.border)

            Text(
                text = stringResource(R.string.saved_locations_add),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.saved_locations_field_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = locality,
                onValueChange = { locality = it },
                label = { Text(stringResource(R.string.saved_locations_field_locality)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = cityCode,
                onValueChange = { cityCode = it.take(4).uppercase() },
                label = { Text(stringResource(R.string.saved_locations_field_city)) },
                supportingText = { Text(stringResource(R.string.saved_locations_city_helper)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (showValidation) {
                Text(
                    text = stringResource(R.string.saved_locations_validation),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dangerStrong
                )
            }
            if (uiState.locations.size >= SavedLocationsRepository.MAX_SAVED_LOCATIONS) {
                Text(
                    text = stringResource(
                        R.string.saved_locations_limit,
                        SavedLocationsRepository.MAX_SAVED_LOCATIONS
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warningStrong
                )
            }

            DrivePrimaryButton(
                onClick = {
                    val trimmedLabel = label.trim()
                    val trimmedLocality = locality.trim()
                    val trimmedCity = cityCode.trim()
                    if (trimmedLabel.isBlank() || trimmedLocality.isBlank() || trimmedCity.isBlank()) {
                        showValidation = true
                        return@DrivePrimaryButton
                    }
                    showValidation = false
                    onAdd(trimmedLabel, trimmedLocality, trimmedCity)
                    label = ""
                    locality = ""
                    cityCode = ""
                },
                enabled = !uiState.isBusy &&
                    uiState.locations.size < SavedLocationsRepository.MAX_SAVED_LOCATIONS
            ) {
                DriveIcon(
                    icon = DriveIcons.Bookmark,
                    contentDescription = null,
                    size = DriveSpacing.iconSmall,
                    tint = colors.onBrand
                )
                Spacer(modifier = Modifier.width(DriveSpacing.sm))
                Text(text = stringResource(R.string.saved_locations_add))
            }

            Spacer(modifier = Modifier.height(DriveSpacing.sm))
        }
    }
}

@Composable
private fun SavedLocationRow(
    location: SavedLocation,
    busy: Boolean,
    onRemove: () -> Unit,
    onSetReturnHome: () -> Unit
) {
    val colors = DriveColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DriveSpacing.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DriveIconWell(
            icon = if (location.isReturnHome) DriveIcons.Home else DriveIcons.MapPin,
            contentDescription = null,
            accent = if (location.isReturnHome) colors.success else colors.brand,
            wellSize = DriveSpacing.iconWellSmall,
            iconSize = DriveSpacing.iconSmall
        )
        Spacer(modifier = Modifier.width(DriveSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = location.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (location.isReturnHome) {
                    stringResource(R.string.saved_locations_home_chip)
                } else {
                    location.locality
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onSetReturnHome,
            enabled = !busy && !location.isReturnHome
        ) {
            DriveIcon(
                icon = DriveIcons.Home,
                contentDescription = stringResource(R.string.saved_locations_set_home, location.label),
                size = DriveSpacing.iconSmall,
                tint = if (location.isReturnHome) colors.success else colors.iconSecondary
            )
        }
        IconButton(onClick = onRemove, enabled = !busy) {
            DriveIcon(
                icon = DriveIcons.Trash,
                contentDescription = stringResource(R.string.saved_locations_remove, location.label),
                size = DriveSpacing.iconSmall,
                tint = colors.dangerStrong
            )
        }
    }
}
