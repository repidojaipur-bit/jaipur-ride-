package com.drivepartner.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.model.labelRes
import com.drivepartner.app.data.remote.dto.DriverAccountStatus
import com.drivepartner.app.data.remote.dto.KycDocumentStatus
import com.drivepartner.app.data.remote.dto.KycDocumentType
import com.drivepartner.app.ui.components.EnvironmentBanner
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.viewmodel.OnboardingAuthUiState

/**
 * S-06: Verification Pending / Rejected / Blocked state (`11_SCREENS.md`, `03_APPFLOW.md`).
 * Never displays invented turnaround times.
 */
@Composable
fun VerificationStatusScreen(
    uiState: OnboardingAuthUiState,
    onRefreshStatus: () -> Unit,
    onFixDocuments: () -> Unit,
    onContactSupport: () -> Unit,
    onSimulateDemoApproval: (Boolean) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accountStatus = uiState.session?.accountStatus ?: DriverAccountStatus.PENDING_REVIEW
    val isRejectedOrBlocked = accountStatus == DriverAccountStatus.REJECTED ||
        accountStatus == DriverAccountStatus.SUSPENDED ||
        accountStatus == DriverAccountStatus.CLOSED

    val actionNeededDocs = uiState.documents.filter {
        it.status == KycDocumentStatus.REJECTED ||
            it.status == KycDocumentStatus.EXPIRED ||
            it.status == KycDocumentStatus.NOT_SUBMITTED
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DriveSpacing.space24),
        verticalArrangement = Arrangement.spacedBy(DriveSpacing.space16)
    ) {
        if (uiState.dataOrigin == DataOrigin.DEMO) {
            EnvironmentBanner(dataOrigin = uiState.dataOrigin)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DriveSpacing.space12)
                ) {
                    Icon(
                        imageVector = if (isRejectedOrBlocked) {
                            Icons.Outlined.ReportProblem
                        } else {
                            Icons.Outlined.HourglassTop
                        },
                        contentDescription = null,
                        tint = if (isRejectedOrBlocked) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Column {
                        Text(
                            text = stringResource(accountStatus.labelRes()),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isRejectedOrBlocked) {
                                stringResource(R.string.verification_blocked_subtitle)
                            } else {
                                stringResource(R.string.verification_pending_subtitle)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (actionNeededDocs.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = DriveCardShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(DriveSpacing.space12),
                            verticalArrangement = Arrangement.spacedBy(DriveSpacing.space4)
                        ) {
                            Text(
                                text = stringResource(R.string.verification_docs_action_required),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                            actionNeededDocs.forEach { doc ->
                                Text(
                                    text = "${stringResource(doc.documentType.labelRes())}: ${
                                        doc.rejectionReason ?: stringResource(doc.status.labelRes())
                                    }",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onRefreshStatus,
                    enabled = !uiState.isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                ) {
                    Text(text = stringResource(R.string.verification_refresh_status))
                }

                if (actionNeededDocs.isNotEmpty() || accountStatus != DriverAccountStatus.SUSPENDED) {
                    OutlinedButton(
                        onClick = onFixDocuments,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                    ) {
                        Text(text = stringResource(R.string.verification_fix_documents))
                    }
                }

                OutlinedButton(
                    onClick = onContactSupport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                ) {
                    Text(text = stringResource(R.string.verification_contact_support))
                }
            }
        }

        if (uiState.dataOrigin == DataOrigin.DEMO) {
            Card(
                shape = DriveCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(DriveSpacing.space16),
                    verticalArrangement = Arrangement.spacedBy(DriveSpacing.space8)
                ) {
                    Text(
                        text = stringResource(R.string.kyc_demo_reviewer_controls),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(DriveSpacing.space8)) {
                        Button(
                            onClick = { onSimulateDemoApproval(true) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                        ) {
                            Text(text = stringResource(R.string.kyc_demo_approve_all))
                        }
                        OutlinedButton(
                            onClick = { onSimulateDemoApproval(false) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                        ) {
                            Text(text = stringResource(R.string.kyc_demo_reject_dl))
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
        ) {
            Text(text = stringResource(R.string.action_sign_out))
        }
    }
}
