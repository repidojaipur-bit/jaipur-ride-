package com.drivepartner.app.ui.screens.auth

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.drivepartner.app.ui.theme.DriveBlueContainer
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveError
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceLightGrey
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextMuted
import com.drivepartner.app.ui.theme.DriveTextPrimary
import com.drivepartner.app.ui.theme.DriveTextSecondary

/**
 * LoginScreen - First step of partner phone authentication.
 *
 * Implements:
 * - 10-digit Indian phone number entry with fixed +91 country code prefix.
 * - Input format validation (must be 10 digits).
 * - Repository pattern execution via `onRequestOtp`.
 * - Loading button state.
 * - Terms of service & privacy disclaimer.
 */
@Composable
fun LoginScreen(
    onRequestOtp: (String) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var phoneNumber by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

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

                // App Brand Badge
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(DriveBlueContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsCar,
                        contentDescription = stringResource(R.string.app_name),
                        tint = DriveBluePrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DriveBluePrimary
                )

                Text(
                    text = stringResource(R.string.partner_subtitle),
                    fontSize = 12.sp,
                    color = DriveTextMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Card Container for Phone Form
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
                            .padding(20.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.auth_enter_phone_title),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DriveTextPrimary
                        )

                        Text(
                            text = stringResource(R.string.auth_enter_phone_subtitle),
                            fontSize = 12.sp,
                            color = DriveTextSecondary,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                        )

                        // Phone Outlined Input with +91 prefix
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { input ->
                                if (input.length <= 10 && input.all { char -> char.isDigit() }) {
                                    phoneNumber = input
                                    localError = null
                                }
                            },
                            label = { Text(stringResource(R.string.auth_phone_hint)) },
                            leadingIcon = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 12.dp, end = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Phone,
                                        contentDescription = null,
                                        tint = DriveBluePrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "+91",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DriveTextPrimary
                                    )
                                }
                            },
                            isError = localError != null || errorMessage != null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
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
                                modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Submit Button
                        PrimaryButton(
                            text = stringResource(R.string.auth_get_otp),
                            onClick = {
                                if (phoneNumber.length == 10) {
                                    onRequestOtp("+91$phoneNumber")
                                } else {
                                    localError = "Please enter a valid 10-digit mobile number"
                                }
                            },
                            isLoading = isLoading,
                            enabled = phoneNumber.length == 10 && !isLoading
                        )
                    }
                }
            }

            // Disclaimer Footer
            Text(
                text = stringResource(R.string.auth_terms_disclaimer),
                fontSize = 11.sp,
                color = DriveTextMuted,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}
