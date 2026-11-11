package com.drivepartner.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.R
import com.drivepartner.app.ui.components.PrimaryButton
import com.drivepartner.app.ui.components.SecondaryButton
import com.drivepartner.app.ui.theme.DriveBlueContainer
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveError
import com.drivepartner.app.ui.theme.DriveGreenOnline
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceLightGrey
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextMuted
import com.drivepartner.app.ui.theme.DriveTextPrimary
import com.drivepartner.app.ui.theme.DriveTextSecondary
import kotlinx.coroutines.delay

/**
 * OtpScreen - Second step of partner authentication (OTP Verification).
 *
 * Implements:
 * - Masked phone number header display with "Edit Number" action.
 * - 6-digit OTP code input with validation.
 * - 60-second countdown timer for Resend code.
 * - Repository verification execution via `onVerifyOtp`.
 * - Loading indicator during verification API dispatch.
 */
@Composable
fun OtpScreen(
    phoneNumber: String,
    onVerifyOtp: (String) -> Unit,
    onEditPhoneClick: () -> Unit,
    onResendOtpClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var otpCode by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var countdownSeconds by remember { mutableStateOf(60) }
    var isTimerActive by remember { mutableStateOf(true) }

    // 60-second countdown timer effect
    LaunchedEffect(isTimerActive) {
        if (isTimerActive) {
            while (countdownSeconds > 0) {
                delay(1000L)
                countdownSeconds--
            }
            isTimerActive = false
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = DriveSurfaceWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // OTP Lock Icon Badge
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(DriveBlueContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = DriveBluePrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.auth_verify_otp_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = DriveTextPrimary
                )

                // Phone number header with edit link
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = "Sent to $phoneNumber",
                        fontSize = 13.sp,
                        color = DriveTextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onEditPhoneClick)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.auth_edit_phone),
                            tint = DriveBluePrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.auth_edit_phone),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DriveBluePrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Card Container for OTP Form
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DriveCardShape,
                    colors = CardDefaults.cardColors(containerColor = DriveSurfaceWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DriveSurfaceBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Enter 6-Digit Code",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = DriveTextPrimary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 6-digit Outlined OTP Input Field
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { input ->
                                if (input.length <= 6 && input.all { char -> char.isDigit() }) {
                                    otpCode = input
                                    localError = null
                                }
                            },
                            singleLine = true,
                            isError = localError != null || errorMessage != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            placeholder = {
                                Text(
                                    text = "123456",
                                    letterSpacing = 8.sp,
                                    fontSize = 20.sp,
                                    color = DriveTextMuted,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DriveBluePrimary,
                                unfocusedBorderColor = DriveSurfaceBorder,
                                errorBorderColor = DriveError
                            )
                        )

                        val activeError = localError ?: errorMessage
                        if (activeError != null) {
                            Text(
                                text = activeError,
                                fontSize = 11.sp,
                                color = DriveError,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            Text(
                                text = "Demo Code: 123456",
                                fontSize = 11.sp,
                                color = DriveGreenOnline,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Verify Primary Button
                        PrimaryButton(
                            text = stringResource(R.string.auth_verify_proceed),
                            onClick = {
                                if (otpCode.length == 6) {
                                    onVerifyOtp(otpCode)
                                } else {
                                    localError = "Please enter complete 6-digit OTP code"
                                }
                            },
                            isLoading = isLoading,
                            enabled = otpCode.length == 6 && !isLoading
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Resend OTP Action / Timer
                        if (isTimerActive && countdownSeconds > 0) {
                            Text(
                                text = stringResource(R.string.auth_resend_timer, countdownSeconds),
                                fontSize = 12.sp,
                                color = DriveTextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.auth_resend_code),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = DriveBluePrimary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        countdownSeconds = 60
                                        isTimerActive = true
                                        onResendOtpClick()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Edit phone secondary option
            SecondaryButton(
                text = "Back to Phone Entry",
                onClick = onEditPhoneClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
