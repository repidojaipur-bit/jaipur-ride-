package com.drivepartner.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.ui.components.EnvironmentBanner
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.viewmodel.OnboardingAuthUiState

@Composable
fun VehicleDetailsScreen(
    uiState: OnboardingAuthUiState,
    onSaveVehicle: (
        serviceType: ServiceType,
        regNumber: String,
        brand: String,
        model: String,
        color: String,
        yearText: String
    ) -> Unit,
    onNavigateDocuments: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = uiState.vehicles.firstOrNull()
    var serviceType by rememberSaveable(primary?.serviceType) { mutableStateOf(primary?.serviceType ?: ServiceType.BIKE_TAXI) }
    var regNumber by rememberSaveable(primary?.registrationNumber) { mutableStateOf(primary?.registrationNumber.orEmpty()) }
    var brand by rememberSaveable(primary?.brand) { mutableStateOf(primary?.brand.orEmpty()) }
    var model by rememberSaveable(primary?.vehicleModel) { mutableStateOf(primary?.vehicleModel.orEmpty()) }
    var color by rememberSaveable(primary?.color) { mutableStateOf(primary?.color.orEmpty()) }
    var yearText by rememberSaveable(primary?.year) { mutableStateOf(primary?.year?.toString().orEmpty()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DriveSpacing.space16),
        verticalArrangement = Arrangement.spacedBy(DriveSpacing.space12)
    ) {
        if (uiState.dataOrigin == DataOrigin.DEMO) {
            EnvironmentBanner(dataOrigin = uiState.dataOrigin)
        }

        Text(
            text = stringResource(R.string.vehicle_details_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.vehicle_details_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!uiState.errorMessage.isNullOrBlank()) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = DriveCardShape) {
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(DriveSpacing.space12)
                )
            }
        }
        if (!uiState.successMessage.isNullOrBlank()) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = DriveCardShape) {
                Text(
                    text = uiState.successMessage,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(DriveSpacing.space12)
                )
            }
        }

        if (primary != null) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = DriveCardShape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(DriveSpacing.space12),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = primary.registrationNumber,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (primary.isApproved) {
                            stringResource(R.string.vehicle_status_approved)
                        } else {
                            primary.status
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Card(
            shape = DriveCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DriveSpacing.space16),
                verticalArrangement = Arrangement.spacedBy(DriveSpacing.space12)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(DriveSpacing.space8)) {
                    ServiceType.entries.forEach { st ->
                        FilterChip(
                            selected = serviceType == st,
                            onClick = { serviceType = st },
                            label = { Text(stringResource(st.titleRes)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = regNumber,
                    onValueChange = { regNumber = it.uppercase() },
                    label = { Text(stringResource(R.string.field_vehicle_reg)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("vehicle_reg_input")
                )
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text(stringResource(R.string.field_vehicle_brand)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(R.string.field_vehicle_model)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DriveSpacing.space8)
                ) {
                    OutlinedTextField(
                        value = color,
                        onValueChange = { color = it },
                        label = { Text(stringResource(R.string.field_vehicle_color)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = { yearText = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text(stringResource(R.string.field_vehicle_year)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Button(
                    onClick = { onSaveVehicle(serviceType, regNumber, brand, model, color, yearText) },
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                        .testTag("vehicle_save_button")
                ) {
                    Text(text = stringResource(R.string.vehicle_save_cta))
                }
                OutlinedButton(
                    onClick = onNavigateDocuments,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                ) {
                    Text(text = stringResource(R.string.vehicle_go_to_kyc))
                }
            }
        }
    }
}
