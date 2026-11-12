package com.drivepartner.app.ui.screens.safety

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Emergency
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.model.InsuranceStatus
import com.drivepartner.app.data.model.SosAlertState
import com.drivepartner.app.data.model.labelRes
import com.drivepartner.app.data.remote.dto.SosStatus
import com.drivepartner.app.ui.components.DriveCard
import com.drivepartner.app.ui.components.DriveDangerButton
import com.drivepartner.app.ui.components.DriveOutlinedButton
import com.drivepartner.app.ui.components.DrivePrimaryButton
import com.drivepartner.app.ui.screens.rewards.RetentionOutcomeBanner
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveError
import com.drivepartner.app.ui.theme.DriveErrorContainer
import com.drivepartner.app.ui.theme.DriveGreenContainer
import com.drivepartner.app.ui.theme.DriveOnErrorContainer
import com.drivepartner.app.ui.theme.DriveOnGreenContainer
import com.drivepartner.app.ui.theme.DriveOnYellowContainer
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.theme.DriveSurfaceSubtle
import com.drivepartner.app.ui.theme.DriveTextSecondary
import com.drivepartner.app.ui.theme.DriveWarningAmber
import com.drivepartner.app.ui.theme.DriveYellowContainer
import com.drivepartner.app.ui.viewmodel.SafetyUiState
import com.drivepartner.app.ui.viewmodel.SafetyViewModel

@Composable
fun SafetyCenterRoute(
    viewModel: SafetyViewModel,
    activeTripId: String? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshSafetyData()
    }

    SafetyCenterScreen(
        uiState = uiState,
        onTriggerSos = { viewModel.triggerSos(activeTripId) },
        onAddContact = viewModel::addEmergencyContact,
        onSubmitIncident = { category, desc ->
            viewModel.submitIncidentReport(category, desc, activeTripId)
        },
        onDismissOutcomeNotice = viewModel::dismissOutcomeNotice,
        onToggleDemoSosResponder = viewModel::toggleDemoSosResponder,
        onToggleDemoInsurance = viewModel::toggleDemoInsurancePreview,
        modifier = modifier
    )
}

@Composable
fun SafetyCenterScreen(
    uiState: SafetyUiState,
    onTriggerSos: () -> Unit = {},
    onAddContact: (String, String, String) -> Unit = { _, _, _ -> },
    onSubmitIncident: (String, String) -> Unit = { _, _ -> },
    onDismissOutcomeNotice: () -> Unit = {},
    onToggleDemoSosResponder: (Boolean) -> Unit = {},
    onToggleDemoInsurance: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var showSosConfirmDialog by remember { mutableStateOf(false) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var incidentCategory by remember { mutableStateOf("VEHICLE_ISSUE") }
    var incidentDescription by remember { mutableStateOf("") }

    if (showSosConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSosConfirmDialog = false },
            title = { Text(stringResource(R.string.safety_sos_confirm_title)) },
            text = { Text(stringResource(R.string.safety_sos_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSosConfirmDialog = false
                        onTriggerSos()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.safety_sos_confirm_send),
                        color = DriveError,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSosConfirmDialog = false }) {
                    Text(stringResource(R.string.payout_dialog_cancel))
                }
            }
        )
    }

    if (showAddContactDialog) {
        AddEmergencyContactDialog(
            isSubmitting = uiState.isAddingContact,
            onDismiss = { showAddContactDialog = false },
            onSubmit = { name, phone, rel ->
                showAddContactDialog = false
                onAddContact(name, phone, rel)
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DriveSurfaceSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(DriveSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DriveSpacing.lg)
        ) {
            uiState.outcomeNotice?.let { notice ->
                RetentionOutcomeBanner(notice = notice, onDismiss = onDismissOutcomeNotice)
            }

            // SOS Emergency Workflow Card (never shows contacted unless ACKNOWLEDGED)
            SosWorkflowCard(
                latestSos = uiState.latestSosAlert,
                isSending = uiState.isSendingSos,
                onTriggerClick = { showSosConfirmDialog = true },
                onDial112Click = {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))
                    runCatching { context.startActivity(dialIntent) }
                }
            )

            // Emergency Contacts Card
            DriveCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Contacts,
                        contentDescription = null,
                        tint = DriveBluePrimary,
                        modifier = Modifier.size(DriveSpacing.iconMedium)
                    )
                    Spacer(modifier = Modifier.width(DriveSpacing.sm))
                    Text(
                        text = stringResource(R.string.safety_contacts_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showAddContactDialog = true }) {
                        Text(stringResource(R.string.safety_contacts_add))
                    }
                }
                Spacer(modifier = Modifier.height(DriveSpacing.sm))
                if (uiState.emergencyContacts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.safety_contacts_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = DriveTextSecondary
                    )
                } else {
                    uiState.emergencyContacts.forEach { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = contact.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = contact.relationship,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DriveTextSecondary
                                )
                            }
                            Text(
                                text = contact.phoneMasked,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }

            // Incident Reporting Card
            DriveCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ReportProblem,
                        contentDescription = null,
                        tint = DriveBluePrimary,
                        modifier = Modifier.size(DriveSpacing.iconMedium)
                    )
                    Spacer(modifier = Modifier.width(DriveSpacing.sm))
                    Text(
                        text = stringResource(R.string.safety_incident_heading),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.height(DriveSpacing.sm))
                OutlinedTextField(
                    value = incidentCategory,
                    onValueChange = { incidentCategory = it },
                    label = { Text(stringResource(R.string.safety_incident_cat_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(DriveSpacing.sm))
                OutlinedTextField(
                    value = incidentDescription,
                    onValueChange = { incidentDescription = it },
                    label = { Text(stringResource(R.string.safety_incident_desc_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(DriveSpacing.md))
                DrivePrimaryButton(
                    onClick = {
                        val desc = incidentDescription
                        incidentDescription = ""
                        onSubmitIncident(incidentCategory.trim(), desc.trim())
                    },
                    enabled = !uiState.isSubmittingIncident &&
                        incidentCategory.isNotBlank() &&
                        incidentDescription.trim().length >= 5
                ) {
                    Text(stringResource(R.string.safety_incident_submit))
                }
            }

            // Insurance Information Card (STRICT: never claims unverified insurance)
            InsuranceInfoCard(insurance = uiState.insuranceStatus)

            // Isolated DEMO Safety Sandbox (NEVER visible in LIVE builds)
            if (uiState.dataOrigin == DataOrigin.DEMO) {
                DriveCard(
                    backgroundColor = DriveYellowContainer,
                    borderColor = DriveWarningAmber.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = stringResource(R.string.demo_safety_sandbox_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = DriveOnYellowContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(DriveSpacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.demo_safety_sos_responder_toggle),
                            style = MaterialTheme.typography.bodySmall,
                            color = DriveOnYellowContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = uiState.demoSosResponderEnabled,
                            onCheckedChange = onToggleDemoSosResponder
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.demo_safety_insurance_toggle),
                            style = MaterialTheme.typography.bodySmall,
                            color = DriveOnYellowContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = uiState.insuranceStatus?.isVerifiedPolicyActive == true,
                            onCheckedChange = onToggleDemoInsurance
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SosWorkflowCard(
    latestSos: SosAlertState?,
    isSending: Boolean,
    onTriggerClick: () -> Unit,
    onDial112Click: () -> Unit
) {
    val currentStatus = latestSos?.status ?: SosStatus.UNCONFIRMED
    val (bg, fg) = when (currentStatus) {
        SosStatus.ACKNOWLEDGED -> DriveGreenContainer to DriveOnGreenContainer
        SosStatus.FAILED -> DriveErrorContainer to DriveOnErrorContainer
        SosStatus.SENDING -> DriveYellowContainer to DriveOnYellowContainer
        SosStatus.UNCONFIRMED -> DriveSurfaceSubtle to DriveTextSecondary
    }

    DriveCard(borderColor = DriveError.copy(alpha = 0.4f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Emergency,
                    contentDescription = null,
                    tint = DriveError,
                    modifier = Modifier.size(DriveSpacing.iconMedium)
                )
                Spacer(modifier = Modifier.width(DriveSpacing.sm))
                Text(
                    text = stringResource(R.string.safety_sos_heading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Surface(shape = DriveChipShape, color = bg) {
                Text(
                    text = stringResource(currentStatus.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(DriveSpacing.sm))
        Text(
            text = stringResource(R.string.safety_sos_no_fake_ack_note),
            style = MaterialTheme.typography.labelSmall,
            color = DriveTextSecondary
        )

        if (latestSos != null) {
            Spacer(modifier = Modifier.height(DriveSpacing.sm))
            Text(
                text = latestSos.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (latestSos.isConfirmedAcknowledged) DriveOnGreenContainer else DriveError,
                fontWeight = FontWeight.SemiBold
            )
            if (latestSos.isConfirmedAcknowledged) {
                Text(
                    text = stringResource(
                        R.string.safety_sos_ack_details,
                        latestSos.alertId.orEmpty(),
                        latestSos.responderReference.orEmpty(),
                        latestSos.acknowledgedAtUtc.orEmpty()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveOnGreenContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(DriveSpacing.md))
        DriveDangerButton(
            onClick = onTriggerClick,
            enabled = !isSending
        ) {
            Text(stringResource(R.string.safety_sos_button))
        }
        Spacer(modifier = Modifier.height(DriveSpacing.sm))
        DriveOutlinedButton(onClick = onDial112Click) {
            Icon(imageVector = Icons.Outlined.Call, contentDescription = null)
            Spacer(modifier = Modifier.width(DriveSpacing.xs))
            Text(stringResource(R.string.safety_sos_dial_112))
        }
    }
}

@Composable
private fun InsuranceInfoCard(insurance: InsuranceStatus?) {
    DriveCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.HealthAndSafety,
                contentDescription = null,
                tint = DriveBluePrimary,
                modifier = Modifier.size(DriveSpacing.iconMedium)
            )
            Spacer(modifier = Modifier.width(DriveSpacing.sm))
            Text(
                text = stringResource(R.string.safety_insurance_heading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(DriveSpacing.sm))
        if (insurance != null && insurance.isVerifiedPolicyActive) {
            Text(
                text = stringResource(R.string.safety_insurance_provider, insurance.providerName.orEmpty()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.safety_insurance_policy_ref, insurance.policyReference.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = DriveTextSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = insurance.approvedDisclaimerText.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = DriveTextSecondary
            )
        } else {
            Text(
                text = stringResource(R.string.safety_insurance_unverified),
                style = MaterialTheme.typography.bodySmall,
                color = DriveTextSecondary
            )
        }
    }
}

@Composable
private fun AddEmergencyContactDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (name: String, phone: String, relationship: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }

    val cleanDigits = phone.filter { it.isDigit() }
    val valid = name.trim().length >= 2 && cleanDigits.length >= 10 && relationship.trim().length >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.safety_contacts_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.safety_contact_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { updated -> phone = updated.filter { it.isDigit() }.take(15) },
                    label = { Text(stringResource(R.string.safety_contact_phone_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = relationship,
                    onValueChange = { relationship = it },
                    label = { Text(stringResource(R.string.safety_contact_rel_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(name.trim(), cleanDigits, relationship.trim()) },
                enabled = !isSubmitting && valid
            ) {
                Text(stringResource(R.string.safety_contacts_add), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.payout_dialog_cancel))
            }
        }
    )
}
