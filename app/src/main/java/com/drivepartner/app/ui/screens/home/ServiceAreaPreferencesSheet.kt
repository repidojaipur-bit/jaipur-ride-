package com.drivepartner.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivepartner.app.R
import com.drivepartner.app.data.model.DriverPreferences
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.ServiceZone
import com.drivepartner.app.ui.components.DriveOutlinedButton
import com.drivepartner.app.ui.components.DrivePrimaryButton
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveGreenOnline
import com.drivepartner.app.ui.theme.DriveSheetShape
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceAreaPreferencesSheet(
    availableZones: List<ServiceZone>,
    currentPreferences: DriverPreferences?,
    fallbackServiceType: ServiceType,
    onDismiss: () -> Unit,
    onSave: (preferredZoneIds: List<String>, autoBoostEnabled: Boolean, activeServiceType: ServiceType) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedZoneIds by remember(currentPreferences) {
        mutableStateOf(currentPreferences?.preferredZoneIds.orEmpty().toSet())
    }
    var autoBoostEnabled by remember(currentPreferences) {
        mutableStateOf(currentPreferences?.autoBoostEnabled == true)
    }
    var selectedService by remember(currentPreferences, fallbackServiceType) {
        mutableStateOf(currentPreferences?.activeServiceType ?: fallbackServiceType)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = DriveSheetShape,
        containerColor = DriveSurfaceWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = DriveSpacing.lg, vertical = DriveSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DriveSpacing.md)
        ) {
            Text(
                text = stringResource(R.string.prefs_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.service_category_heading),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            ServiceType.entries.forEach { service ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = DriveSpacing.minTouchTarget)
                        .clickable(role = Role.RadioButton) { selectedService = service },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedService == service,
                        onClick = { selectedService = service },
                        colors = RadioButtonDefaults.colors(selectedColor = DriveBluePrimary)
                    )
                    Spacer(modifier = Modifier.width(DriveSpacing.sm))
                    Text(
                        text = stringResource(service.titleRes),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            HorizontalDivider(color = DriveSurfaceBorder)

            Text(
                text = stringResource(R.string.prefs_zones_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            if (availableZones.isEmpty()) {
                Text(
                    text = stringResource(R.string.map_no_zones_configured),
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveTextSecondary
                )
            } else {
                availableZones.forEach { zone ->
                    val checked = zone.zoneId in selectedZoneIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = DriveSpacing.minTouchTarget)
                            .clickable(enabled = zone.isEnabled, role = Role.Checkbox) {
                                selectedZoneIds = if (checked) {
                                    selectedZoneIds - zone.zoneId
                                } else {
                                    selectedZoneIds + zone.zoneId
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                if (zone.isEnabled) {
                                    selectedZoneIds = if (isChecked) {
                                        selectedZoneIds + zone.zoneId
                                    } else {
                                        selectedZoneIds - zone.zoneId
                                    }
                                }
                            },
                            enabled = zone.isEnabled,
                            colors = CheckboxDefaults.colors(checkedColor = DriveBluePrimary)
                        )
                        Spacer(modifier = Modifier.width(DriveSpacing.sm))
                        Text(
                            text = if (zone.isDemoZone) "${zone.name} (DEMO)" else zone.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            HorizontalDivider(color = DriveSurfaceBorder)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = DriveSpacing.minTouchTarget)
                    .clickable(role = Role.Switch) { autoBoostEnabled = !autoBoostEnabled },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.prefs_autoboost_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.prefs_autoboost_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = DriveTextSecondary
                    )
                }
                Spacer(modifier = Modifier.width(DriveSpacing.md))
                Switch(
                    checked = autoBoostEnabled,
                    onCheckedChange = { autoBoostEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DriveSurfaceWhite,
                        checkedTrackColor = DriveGreenOnline
                    )
                )
            }

            Spacer(modifier = Modifier.height(DriveSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(DriveSpacing.md)
            ) {
                DriveOutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.prefs_cancel_action))
                }
                DrivePrimaryButton(
                    onClick = {
                        onSave(
                            selectedZoneIds.toList(),
                            autoBoostEnabled,
                            selectedService
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.prefs_save_action))
                }
            }

            Spacer(modifier = Modifier.height(DriveSpacing.xl))
        }
    }
}
