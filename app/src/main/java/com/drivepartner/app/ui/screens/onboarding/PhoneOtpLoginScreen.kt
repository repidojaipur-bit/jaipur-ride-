package com.drivepartner.app.ui.screens.onboarding

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.ui.components.EnvironmentBanner
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.viewmodel.OnboardingAuthUiState

/**
 * S-02: Phone OTP Authentication screen (`11_SCREENS.md`, `03_APPFLOW.md`).
 */
private data class CountryCodeOption(
    val dialCode: String,
    val isoCode: String,
    val otpSupported: Boolean
)

private val countryCodes = listOf(
    CountryCodeOption("+91", "IN", otpSupported = true),
    CountryCodeOption("+977", "NP", otpSupported = false),
    CountryCodeOption("+880", "BD", otpSupported = false),
    CountryCodeOption("+94", "LK", otpSupported = false),
    CountryCodeOption("+971", "AE", otpSupported = false),
    CountryCodeOption("+44", "GB", otpSupported = false),
    CountryCodeOption("+1", "US", otpSupported = false)
)

@Composable
fun PhoneOtpLoginScreen(
    uiState: OnboardingAuthUiState,
    onPhoneChanged: (String) -> Unit,
    onOtpChanged: (String) -> Unit,
    onRequestOtp: () -> Unit,
    onVerifyOtp: () -> Unit,
    onEditPhone: () -> Unit,
    onOpenSettingsLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
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

        Text(
            text = stringResource(R.string.auth_phone_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.auth_phone_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            shape = DriveCardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DriveSpacing.space16),
                verticalArrangement = Arrangement.spacedBy(DriveSpacing.space12)
            ) {
                var showCountryDialog by remember { mutableStateOf(false) }
                var selectedDialCode by rememberSaveable { mutableStateOf("+91") }
                val selectedOption = countryCodes.first { it.dialCode == selectedDialCode }

                if (showCountryDialog) {
                    CountryCodeDialog(
                        selectedDialCode = selectedDialCode,
                        onSelect = { code ->
                            selectedDialCode = code
                            showCountryDialog = false
                        },
                        onDismiss = { showCountryDialog = false }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DriveSpacing.sm)
                ) {
                    OutlinedButton(
                        onClick = { showCountryDialog = true },
                        modifier = Modifier.defaultMinSize(minHeight = DriveSpacing.secondaryButtonHeight)
                    ) {
                        Text(text = selectedDialCode)
                        Icon(
                            imageVector = Icons.Outlined.ArrowDropDown,
                            contentDescription = stringResource(
                                R.string.auth_country_code_value,
                                selectedDialCode
                            )
                        )
                    }
                    OutlinedTextField(
                        value = uiState.phoneInput,
                        onValueChange = onPhoneChanged,
                        enabled = !uiState.isOtpSent && !uiState.isLoading,
                        label = { Text(stringResource(R.string.auth_phone_label)) },
                        supportingText = { Text(stringResource(R.string.auth_phone_helper)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.PhoneAndroid, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("auth_phone_input")
                    )
                }

                if (!selectedOption.otpSupported) {
                    Text(
                        text = stringResource(R.string.auth_country_code_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (uiState.phoneInput.isNotEmpty() && uiState.phoneInput.length < 10) {
                    Text(
                        text = stringResource(R.string.auth_phone_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (!uiState.isOtpSent) {
                    Button(
                        onClick = onRequestOtp,
                        enabled = !uiState.isLoading && uiState.phoneInput.length == 10 &&
                            selectedOption.otpSupported,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                            .testTag("auth_send_otp_button")
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                        }
                        Text(text = stringResource(R.string.auth_send_otp))
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.auth_otp_sent_to, "+91 ${uiState.phoneInput}"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onEditPhone) {
                            Text(text = stringResource(R.string.auth_edit_number))
                        }
                    }

                    OutlinedTextField(
                        value = uiState.otpInput,
                        onValueChange = onOtpChanged,
                        enabled = !uiState.isLoading,
                        label = { Text(stringResource(R.string.auth_otp_label)) },
                        leadingIcon = {
                            Icon(Icons.Outlined.Lock, contentDescription = null)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_otp_input")
                    )

                    if (uiState.dataOrigin == DataOrigin.DEMO && !uiState.demoOtpHint.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = DriveCardShape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.auth_demo_otp_hint, uiState.demoOtpHint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(DriveSpacing.space12)
                            )
                        }
                    }

                    if (uiState.otpInput.isNotEmpty() && uiState.otpInput.length < 6) {
                        Text(
                            text = stringResource(R.string.auth_otp_invalid_length),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = onVerifyOtp,
                        enabled = !uiState.isLoading && uiState.otpInput.length == 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                            .testTag("auth_verify_otp_button")
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(end = 8.dp))
                        }
                        Text(text = stringResource(R.string.auth_verify_otp))
                    }

                    OutlinedButton(
                        onClick = onRequestOtp,
                        enabled = uiState.resendSecondsRemaining == 0 && !uiState.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                    ) {
                        Text(
                            text = if (uiState.resendSecondsRemaining > 0) {
                                stringResource(R.string.auth_resend_otp_wait, uiState.resendSecondsRemaining)
                            } else {
                                stringResource(R.string.auth_resend_otp)
                            }
                        )
                    }
                }
            }
        }

        if (!uiState.errorMessage.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = DriveCardShape,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(DriveSpacing.space12)
                )
            }
        }

        Spacer(modifier = Modifier.height(DriveSpacing.space8))

        TextButton(
            onClick = onOpenSettingsLanguage,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(text = stringResource(R.string.auth_change_language))
        }
    }
}

@Composable
private fun CountryCodeDialog(
    selectedDialCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auth_country_code_dialog_title)) },
        text = {
            Column {
                countryCodes.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                            .clickable(enabled = option.otpSupported) { onSelect(option.dialCode) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option.dialCode == selectedDialCode,
                            onClick = { if (option.otpSupported) onSelect(option.dialCode) },
                            enabled = option.otpSupported
                        )
                        Text(
                            text = "${option.dialCode} · ${option.isoCode}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (option.otpSupported) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            }
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.auth_country_code_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.payout_dialog_cancel))
            }
        }
    )
}
