package com.drivepartner.app.ui.screens.earnings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.money.MoneyPaise
import com.drivepartner.app.data.model.EarningStatement
import com.drivepartner.app.data.model.LedgerEntry
import com.drivepartner.app.data.model.PaiseInputParser
import com.drivepartner.app.data.model.PayoutAccount
import com.drivepartner.app.data.model.WalletSummary
import com.drivepartner.app.data.model.labelRes
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveError
import com.drivepartner.app.ui.theme.DriveOnYellowContainer
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.theme.DriveSurfaceSubtle
import com.drivepartner.app.ui.theme.DriveTextSecondary
import com.drivepartner.app.ui.theme.DriveYellowContainer

@Composable
fun TransactionDetailDialog(
    entry: LedgerEntry,
    statement: EarningStatement?,
    isLoadingStatement: Boolean,
    statementError: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.transaction_detail_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (entry.dataOrigin == DataOrigin.DEMO) {
                    Surface(shape = DriveChipShape, color = DriveYellowContainer) {
                        Text(
                            text = "DEMO",
                            style = MaterialTheme.typography.labelSmall,
                            color = DriveOnYellowContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)
            ) {
                Text(
                    text = stringResource(entry.entryType.labelRes()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.transaction_detail_entry_id, entry.entryId),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveTextSecondary
                )
                Text(
                    text = stringResource(
                        R.string.transaction_detail_source,
                        entry.sourceType,
                        entry.sourceId
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveTextSecondary
                )
                Text(
                    text = stringResource(
                        R.string.transaction_detail_amount,
                        entry.amount.formatDisplay()
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        R.string.transaction_detail_balance_after,
                        entry.balanceAfter.formatDisplay()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveTextSecondary
                )

                if (entry.statementId != null) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.transaction_detail_statement_heading),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (isLoadingStatement) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else if (statementError != null) {
                        Text(
                            text = statementError,
                            style = MaterialTheme.typography.bodySmall,
                            color = DriveError
                        )
                    } else if (statement != null) {
                        Surface(shape = DriveChipShape, color = DriveSurfaceSubtle) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(DriveSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.transaction_detail_fare_version,
                                        statement.fareConfigVersion
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DriveTextSecondary
                                )
                                Text(
                                    text = stringResource(
                                        R.string.transaction_detail_quote_id,
                                        statement.quoteSnapshotId
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DriveTextSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(
                                        R.string.transaction_detail_base_fare,
                                        statement.baseFare.formatDisplay()
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(
                                        R.string.transaction_detail_distance_fare,
                                        statement.distanceFare.formatDisplay()
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(
                                        R.string.transaction_detail_time_fare,
                                        statement.timeFare.formatDisplay()
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(
                                        R.string.transaction_detail_incentive_fare,
                                        statement.incentive.formatDisplay()
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = stringResource(
                                        R.string.transaction_detail_platform_fee,
                                        statement.platformFee.formatDisplay()
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DriveTextSecondary
                                )
                                Text(
                                    text = stringResource(
                                        R.string.transaction_detail_tax,
                                        statement.tax.formatDisplay()
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DriveTextSecondary
                                )
                                HorizontalDivider()
                                Text(
                                    text = stringResource(
                                        R.string.transaction_detail_net_earning,
                                        statement.netEarning.formatDisplay()
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()
                Text(
                    text = stringResource(R.string.transaction_detail_immutable_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = DriveTextSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.transaction_detail_close))
            }
        }
    )
}

@Composable
fun WithdrawPayoutDialog(
    wallet: WalletSummary,
    verifiedAccounts: List<PayoutAccount>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (payoutAccountToken: String, amountMinor: Long) -> Unit
) {
    var selectedAccountToken by remember(verifiedAccounts) {
        mutableStateOf(verifiedAccounts.firstOrNull()?.payoutAccountToken.orEmpty())
    }
    var amountInput by remember { mutableStateOf("") }

    val parsedPaise = PaiseInputParser.parseRupeesToPaiseOrNull(amountInput)
    val maxAllowedMinor = minOf(wallet.maximumPayout.amountMinor, wallet.available.amountMinor)
    val minAllowedMinor = wallet.minimumPayout.amountMinor
    val isAmountValid = parsedPaise != null &&
        parsedPaise >= minAllowedMinor &&
        parsedPaise <= maxAllowedMinor

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.payout_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)) {
                Text(
                    text = stringResource(R.string.payout_dialog_account_label),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                verifiedAccounts.forEach { account ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedAccountToken == account.payoutAccountToken,
                            onClick = { selectedAccountToken = account.payoutAccountToken }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.accountLabel,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${account.maskedAccountNumber} • ${account.ifscCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = DriveTextSecondary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text(stringResource(R.string.payout_dialog_amount_label)) },
                    placeholder = { Text(stringResource(R.string.payout_dialog_amount_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountInput.isNotBlank() && !isAmountValid,
                    modifier = Modifier.fillMaxWidth()
                )

                if (amountInput.isNotBlank() && parsedPaise == null) {
                    Text(
                        text = stringResource(R.string.payout_validation_invalid_amount),
                        style = MaterialTheme.typography.labelSmall,
                        color = DriveError
                    )
                } else if (parsedPaise != null && !isAmountValid) {
                    Text(
                        text = stringResource(
                            R.string.payout_validation_out_of_bounds,
                            wallet.minimumPayout.formatDisplay(),
                            MoneyPaise(maxAllowedMinor, wallet.available.currency).formatDisplay()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = DriveError
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalPaise = parsedPaise ?: return@TextButton
                    onSubmit(selectedAccountToken, finalPaise)
                },
                enabled = !isSubmitting && selectedAccountToken.isNotBlank() && isAmountValid
            ) {
                Text(
                    text = stringResource(R.string.payout_dialog_submit),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(text = stringResource(R.string.payout_dialog_cancel))
            }
        }
    )
}

@Composable
fun LinkPayoutAccountDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (holderName: String, accountNumber: String, ifscCode: String) -> Unit
) {
    var holderName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var ifscCode by remember { mutableStateOf("") }

    val digitsOnly = accountNumber.filter { it.isDigit() }
    val cleanIfsc = ifscCode.trim().uppercase()
    val validIfsc = Regex("^[A-Z]{4}0[A-Z0-9]{6}$").matches(cleanIfsc)
    val canSubmit = holderName.trim().length >= 2 && digitsOnly.length >= 9 && validIfsc

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.payout_account_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)) {
                OutlinedTextField(
                    value = holderName,
                    onValueChange = { holderName = it },
                    label = { Text(stringResource(R.string.payout_account_holder_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { updated ->
                        accountNumber = updated.filter { it.isDigit() }.take(18)
                    },
                    label = { Text(stringResource(R.string.payout_account_number_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ifscCode,
                    onValueChange = { ifscCode = it.uppercase().take(11) },
                    label = { Text(stringResource(R.string.payout_account_ifsc_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(holderName.trim(), digitsOnly, cleanIfsc) },
                enabled = !isSubmitting && canSubmit
            ) {
                Text(
                    text = stringResource(R.string.payout_account_submit),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(text = stringResource(R.string.payout_dialog_cancel))
            }
        }
    )
}
