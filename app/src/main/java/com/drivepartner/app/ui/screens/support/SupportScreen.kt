package com.drivepartner.app.ui.screens.support

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.model.SupportMessage
import com.drivepartner.app.data.model.SupportTicket
import com.drivepartner.app.data.model.labelRes
import com.drivepartner.app.data.remote.dto.SupportTicketStatus
import com.drivepartner.app.ui.components.DriveCard
import com.drivepartner.app.ui.components.DriveOutlinedButton
import com.drivepartner.app.ui.components.DrivePrimaryButton
import com.drivepartner.app.ui.components.state.DriveEmptyView
import com.drivepartner.app.ui.screens.rewards.RetentionOutcomeBanner
import com.drivepartner.app.ui.theme.DriveBlueContainer
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveError
import com.drivepartner.app.ui.theme.DriveErrorContainer
import com.drivepartner.app.ui.theme.DriveGreenContainer
import com.drivepartner.app.ui.theme.DriveOnBlueContainer
import com.drivepartner.app.ui.theme.DriveOnErrorContainer
import com.drivepartner.app.ui.theme.DriveOnGreenContainer
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.theme.DriveSurfaceSubtle
import com.drivepartner.app.ui.theme.DriveTextSecondary
import com.drivepartner.app.ui.theme.DriveYellowContainer
import com.drivepartner.app.ui.theme.DriveOnYellowContainer
import com.drivepartner.app.ui.viewmodel.SupportUiState
import com.drivepartner.app.ui.viewmodel.SupportViewModel

@Composable
fun SupportRoute(
    viewModel: SupportViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshTickets()
    }

    SupportScreen(
        uiState = uiState,
        onCreateTicket = viewModel::createTicket,
        onOpenTicket = viewModel::openTicket,
        onCloseTicket = viewModel::closeTicketThread,
        onSendReply = viewModel::sendReply,
        onLoadMore = viewModel::loadMoreTickets,
        onDismissOutcomeNotice = viewModel::dismissOutcomeNotice,
        modifier = modifier
    )
}

@Composable
fun SupportScreen(
    uiState: SupportUiState,
    onCreateTicket: (category: String, subject: String, description: String, attachmentKeys: List<String>) -> Unit = { _, _, _, _ -> },
    onOpenTicket: (SupportTicket) -> Unit = {},
    onCloseTicket: () -> Unit = {},
    onSendReply: (ticketId: String, body: String) -> Unit = { _, _ -> },
    onLoadMore: () -> Unit = {},
    onDismissOutcomeNotice: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showCreateDialog by remember { mutableStateOf(false) }

    if (showCreateDialog) {
        CreateTicketDialog(
            isSubmitting = uiState.isSubmittingTicket,
            onDismiss = { showCreateDialog = false },
            onSubmit = { cat, subj, desc, keys ->
                showCreateDialog = false
                onCreateTicket(cat, subj, desc, keys)
            }
        )
    }

    uiState.selectedTicket?.let { ticket ->
        SupportThreadDialog(
            ticket = ticket,
            messages = uiState.selectedTicketMessages,
            isSendingReply = uiState.isSendingReply,
            onDismiss = onCloseTicket,
            onSendReply = { body -> onSendReply(ticket.ticketId, body) }
        )
    }

    BoxWithConstraints(
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
            if (uiState.dataOrigin == DataOrigin.LIVE && !uiState.boundaryMessage.isNullOrBlank()) {
                DriveCard(
                    backgroundColor = DriveErrorContainer,
                    borderColor = DriveError.copy(alpha = 0.35f)
                ) {
                    Text(
                        text = uiState.boundaryMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = DriveOnErrorContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            uiState.outcomeNotice?.let { notice ->
                RetentionOutcomeBanner(notice = notice, onDismiss = onDismissOutcomeNotice)
            }

            DriveCard {
                Text(
                    text = stringResource(R.string.support_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.support_thread_privacy_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveTextSecondary
                )
                Spacer(modifier = Modifier.height(DriveSpacing.md))
                DrivePrimaryButton(onClick = { showCreateDialog = true }) {
                    Text(stringResource(R.string.support_create_ticket))
                }
            }

            DriveCard {
                if (uiState.tickets.isEmpty()) {
                    DriveEmptyView(
                        modifier = Modifier.height(210.dp),
                        icon = Icons.Outlined.Headphones,
                        title = stringResource(R.string.support_tickets_empty),
                        description = stringResource(R.string.support_thread_privacy_note)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)) {
                        uiState.tickets.forEach { ticket ->
                            val (bg, fg) = when (ticket.status) {
                                SupportTicketStatus.OPEN -> DriveBlueContainer to DriveOnBlueContainer
                                SupportTicketStatus.IN_PROGRESS,
                                SupportTicketStatus.WAITING_FOR_DRIVER -> DriveYellowContainer to DriveOnYellowContainer
                                SupportTicketStatus.RESOLVED,
                                SupportTicketStatus.CLOSED -> DriveGreenContainer to DriveOnGreenContainer
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenTicket(ticket) }
                                    .padding(vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ticket.subject,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(shape = DriveChipShape, color = bg) {
                                        Text(
                                            text = stringResource(ticket.status.labelRes()),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = fg,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${ticket.category} • ${ticket.updatedAtUtc}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DriveTextSecondary
                                )
                            }
                            HorizontalDivider()
                        }
                        if (uiState.hasMoreTickets) {
                            DriveOutlinedButton(
                                onClick = onLoadMore,
                                enabled = !uiState.isLoadingMore
                            ) {
                                Text(stringResource(R.string.support_load_more))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateTicketDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (category: String, subject: String, description: String, attachmentKeys: List<String>) -> Unit
) {
    var category by remember { mutableStateOf("EARNINGS") }
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var attachmentKeys by remember { mutableStateOf(emptyList<String>()) }

    val valid = subject.trim().length >= 3 && description.trim().length >= 5

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.support_create_ticket)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)) {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.support_ticket_cat_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text(stringResource(R.string.support_ticket_subject_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.support_ticket_desc_label)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        attachmentKeys = listOf("private/support/attachment-${attachmentKeys.size + 1}.jpg")
                    }
                ) {
                    Text(stringResource(R.string.support_ticket_attach_sample))
                }
                if (attachmentKeys.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.support_ticket_attached_count, attachmentKeys.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = DriveTextSecondary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(category.trim(), subject.trim(), description.trim(), attachmentKeys) },
                enabled = !isSubmitting && valid
            ) {
                Text(stringResource(R.string.support_ticket_submit), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.payout_dialog_cancel))
            }
        }
    )
}

@Composable
private fun SupportThreadDialog(
    ticket: SupportTicket,
    messages: List<SupportMessage>,
    isSendingReply: Boolean,
    onDismiss: () -> Unit,
    onSendReply: (String) -> Unit
) {
    var replyBody by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.support_thread_title, ticket.ticketId),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)
            ) {
                Text(
                    text = ticket.subject,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.support_thread_privacy_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveTextSecondary
                )
                HorizontalDivider()
                messages.forEach { msg ->
                    Surface(shape = DriveChipShape, color = DriveSurfaceSubtle) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "${msg.senderRole} • ${msg.sentAtUtc}",
                                style = MaterialTheme.typography.labelSmall,
                                color = DriveTextSecondary
                            )
                            Text(
                                text = msg.body,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = replyBody,
                    onValueChange = { replyBody = it },
                    placeholder = { Text(stringResource(R.string.support_reply_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                DrivePrimaryButton(
                    onClick = {
                        val toSend = replyBody.trim()
                        replyBody = ""
                        onSendReply(toSend)
                    },
                    enabled = !isSendingReply && replyBody.isNotBlank()
                ) {
                    Text(stringResource(R.string.support_reply_send))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.transaction_detail_close))
            }
        }
    )
}
