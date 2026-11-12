package com.drivepartner.app.ui.screens.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.local.AppLanguage
import com.drivepartner.app.data.model.RegistrationStep
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.labelRes
import com.drivepartner.app.data.remote.dto.KycDocumentType
import com.drivepartner.app.ui.components.EnvironmentBanner
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.viewmodel.OnboardingAuthUiState

/**
 * S-03 (`Personal`), S-04 (`Vehicle`), Step 3 (`Bank`), S-05 (`Documents`) 4-Step Wizard.
 */
@Composable
fun RegistrationWizardScreen(
    uiState: OnboardingAuthUiState,
    selectedLanguage: AppLanguage,
    onSelectStep: (RegistrationStep) -> Unit,
    onSavePersonal: (
        fullName: String,
        cityCode: String,
        language: AppLanguage,
        dob: String,
        gender: String?,
        address: String,
        stateCode: String,
        pinCode: String
    ) -> Unit,
    onSaveVehicle: (
        serviceType: ServiceType,
        regNumber: String,
        brand: String,
        model: String,
        color: String,
        yearText: String
    ) -> Unit,
    onSaveBank: (accountHolder: String, accountNumber: String, ifscCode: String) -> Unit,
    onUploadDocument: (
        type: KycDocumentType,
        mimeType: String,
        sizeBytes: Long,
        maskedReference: String?,
        expiresOnUtc: String?
    ) -> Unit,
    onSubmitForReview: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val profile = uiState.profile
    val vehicle = uiState.vehicles.firstOrNull()
    val bank = uiState.linkedPayoutAccounts.firstOrNull()

    var fullName by rememberSaveable(profile?.fullName) { mutableStateOf(profile?.fullName.orEmpty()) }
    var cityCode by rememberSaveable(profile?.cityCode) { mutableStateOf(profile?.cityCode.orEmpty().ifBlank { "JAI" }) }
    var dob by rememberSaveable(profile?.dateOfBirth) { mutableStateOf(profile?.dateOfBirth.orEmpty()) }
    var gender by rememberSaveable(profile?.gender) { mutableStateOf(profile?.gender ?: "MALE") }
    var address by rememberSaveable(profile?.addressLine) { mutableStateOf(profile?.addressLine.orEmpty()) }
    var stateCode by rememberSaveable(profile?.stateCode) { mutableStateOf(profile?.stateCode.orEmpty().ifBlank { "RJ" }) }
    var pinCode by rememberSaveable(profile?.pinCode) { mutableStateOf(profile?.pinCode.orEmpty()) }

    var serviceType by rememberSaveable(vehicle?.serviceType) { mutableStateOf(vehicle?.serviceType ?: ServiceType.BIKE_TAXI) }
    var regNumber by rememberSaveable(vehicle?.registrationNumber) { mutableStateOf(vehicle?.registrationNumber.orEmpty()) }
    var brand by rememberSaveable(vehicle?.brand) { mutableStateOf(vehicle?.brand.orEmpty()) }
    var model by rememberSaveable(vehicle?.vehicleModel) { mutableStateOf(vehicle?.vehicleModel.orEmpty()) }
    var color by rememberSaveable(vehicle?.color) { mutableStateOf(vehicle?.color.orEmpty()) }
    var yearText by rememberSaveable(vehicle?.year) { mutableStateOf(vehicle?.year?.toString().orEmpty()) }

    var bankHolder by rememberSaveable(bank?.accountLabel) { mutableStateOf(bank?.accountLabel ?: fullName) }
    var bankAccount by rememberSaveable { mutableStateOf("") }
    var bankIfsc by rememberSaveable(bank?.ifscCode) { mutableStateOf(bank?.ifscCode.orEmpty()) }

    var activeUploadDocType by remember { mutableStateOf<KycDocumentType?>(null) }
    var docMaskedRef by rememberSaveable { mutableStateOf("") }
    var docExpiry by rememberSaveable { mutableStateOf("") }
    var selectedUriPreview by remember { mutableStateOf<Map<KycDocumentType, Uri>>(emptyMap()) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val docType = activeUploadDocType
        if (uri != null && docType != null) {
            selectedUriPreview = selectedUriPreview + (docType to uri)
            onUploadDocument(
                docType,
                "image/jpeg",
                310_000L,
                docMaskedRef.takeIf { it.isNotBlank() },
                docExpiry.takeIf { it.isNotBlank() }
            )
        }
    }

    val currentStep = uiState.registrationStep
    val progress = (currentStep.stepIndex + 1) / 4f

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.wizard_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.wizard_step_counter, currentStep.stepIndex + 1, 4),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
            ) {
                Text(text = stringResource(R.string.action_sign_out))
            }
        }

        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DriveSpacing.space8)
        ) {
            RegistrationStep.entries.forEach { step ->
                FilterChip(
                    selected = currentStep == step,
                    onClick = { onSelectStep(step) },
                    label = {
                        Text(
                            text = when (step) {
                                RegistrationStep.PERSONAL -> stringResource(R.string.wizard_step_personal)
                                RegistrationStep.VEHICLE -> stringResource(R.string.wizard_step_vehicle)
                                RegistrationStep.BANK -> stringResource(R.string.wizard_step_bank)
                                RegistrationStep.DOCUMENTS -> stringResource(R.string.wizard_step_docs)
                            }
                        )
                    }
                )
            }
        }

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

        when (currentStep) {
            RegistrationStep.PERSONAL -> {
                Card(shape = DriveCardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(DriveSpacing.space16),
                        verticalArrangement = Arrangement.spacedBy(DriveSpacing.space12)
                    ) {
                        Text(
                            text = stringResource(R.string.wizard_personal_heading),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text(stringResource(R.string.field_full_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("wizard_full_name")
                        )
                        OutlinedTextField(
                            value = cityCode,
                            onValueChange = { cityCode = it },
                            label = { Text(stringResource(R.string.field_city_code)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = dob,
                            onValueChange = { dob = it },
                            label = { Text(stringResource(R.string.field_dob_optional)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(DriveSpacing.space8)) {
                            listOf("MALE", "FEMALE", "OTHER").forEach { option ->
                                FilterChip(
                                    selected = gender == option,
                                    onClick = { gender = option },
                                    label = { Text(option) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text(stringResource(R.string.field_address_optional)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(DriveSpacing.space8)
                        ) {
                            OutlinedTextField(
                                value = stateCode,
                                onValueChange = { stateCode = it },
                                label = { Text(stringResource(R.string.field_state_code)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = pinCode,
                                onValueChange = { pinCode = it.filter { c -> c.isDigit() }.take(6) },
                                label = { Text(stringResource(R.string.field_pincode)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Button(
                            onClick = {
                                onSavePersonal(
                                    fullName,
                                    cityCode,
                                    selectedLanguage,
                                    dob,
                                    gender,
                                    address,
                                    stateCode,
                                    pinCode
                                )
                            },
                            enabled = !uiState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                                .testTag("wizard_save_personal")
                        ) {
                            Text(text = stringResource(R.string.wizard_save_continue))
                        }
                    }
                }
            }

            RegistrationStep.VEHICLE -> {
                Card(shape = DriveCardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(DriveSpacing.space16),
                        verticalArrangement = Arrangement.spacedBy(DriveSpacing.space12)
                    ) {
                        Text(
                            text = stringResource(R.string.wizard_vehicle_heading),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
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
                            modifier = Modifier.fillMaxWidth().testTag("wizard_vehicle_reg")
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
                            modifier = Modifier.fillMaxWidth().testTag("wizard_vehicle_model")
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
                                .testTag("wizard_save_vehicle")
                        ) {
                            Text(text = stringResource(R.string.wizard_save_continue))
                        }
                    }
                }
            }

            RegistrationStep.BANK -> {
                Card(shape = DriveCardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(DriveSpacing.space16),
                        verticalArrangement = Arrangement.spacedBy(DriveSpacing.space12)
                    ) {
                        Text(
                            text = stringResource(R.string.wizard_bank_heading),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (bank != null) {
                            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = DriveCardShape) {
                                Text(
                                    text = stringResource(R.string.bank_linked_summary, bank.accountLabel, bank.maskedAccountNumber),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(DriveSpacing.space12)
                                )
                            }
                        }
                        OutlinedTextField(
                            value = bankHolder,
                            onValueChange = { bankHolder = it },
                            label = { Text(stringResource(R.string.field_bank_holder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = bankAccount,
                            onValueChange = { bankAccount = it.filter { c -> c.isDigit() }.take(18) },
                            label = { Text(stringResource(R.string.field_bank_account)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = bankIfsc,
                            onValueChange = { bankIfsc = it.uppercase().take(11) },
                            label = { Text(stringResource(R.string.field_bank_ifsc)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(DriveSpacing.space8)
                        ) {
                            OutlinedButton(
                                onClick = { onSelectStep(RegistrationStep.DOCUMENTS) },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                            ) {
                                Text(text = stringResource(R.string.wizard_skip_for_now))
                            }
                            Button(
                                onClick = { onSaveBank(bankHolder, bankAccount, bankIfsc) },
                                enabled = !uiState.isLoading,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                            ) {
                                Text(text = stringResource(R.string.wizard_save_continue))
                            }
                        }
                    }
                }
            }

            RegistrationStep.DOCUMENTS -> {
                Card(shape = DriveCardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(DriveSpacing.space16),
                        verticalArrangement = Arrangement.spacedBy(DriveSpacing.space12)
                    ) {
                        Text(
                            text = stringResource(R.string.wizard_docs_heading),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.kyc_aadhaar_compliance_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = docMaskedRef,
                            onValueChange = { docMaskedRef = it },
                            label = { Text(stringResource(R.string.kyc_optional_reference_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = docExpiry,
                            onValueChange = { docExpiry = it },
                            label = { Text(stringResource(R.string.kyc_optional_expiry_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        KycDocumentType.entries.forEach { docType ->
                            val existing = uiState.documents.find { it.documentType == docType }
                            val previewUri = selectedUriPreview[docType]
                            Surface(
                                tonalElevation = 1.dp,
                                shape = DriveCardShape,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(DriveSpacing.space12),
                                    verticalArrangement = Arrangement.spacedBy(DriveSpacing.space8)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = stringResource(docType.labelRes()),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = existing?.let { stringResource(it.status.labelRes()) }
                                                ?: stringResource(R.string.kyc_status_not_submitted),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (!existing?.rejectionReason.isNullOrBlank()) {
                                        Text(
                                            text = existing?.rejectionReason.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    if (previewUri != null) {
                                        Text(
                                            text = stringResource(R.string.kyc_selected_preview_uri, previewUri.lastPathSegment ?: "document"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(DriveSpacing.space8)) {
                                        OutlinedButton(
                                            onClick = {
                                                activeUploadDocType = docType
                                                filePickerLauncher.launch("image/*")
                                            },
                                            modifier = Modifier.defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                                        ) {
                                            Text(text = stringResource(R.string.kyc_pick_gallery))
                                        }
                                        if (uiState.dataOrigin == DataOrigin.DEMO) {
                                            Button(
                                                onClick = {
                                                    onUploadDocument(
                                                        docType,
                                                        "image/jpeg",
                                                        240_000L,
                                                        docMaskedRef.takeIf { it.isNotBlank() },
                                                        docExpiry.takeIf { it.isNotBlank() }
                                                    )
                                                },
                                                modifier = Modifier.defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                                            ) {
                                                Text(text = stringResource(R.string.kyc_quick_upload_demo))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(DriveSpacing.space4))
                        Button(
                            onClick = onSubmitForReview,
                            enabled = !uiState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                                .testTag("wizard_submit_review")
                        ) {
                            Text(text = stringResource(R.string.wizard_submit_review))
                        }
                    }
                }
            }
        }
    }
}
