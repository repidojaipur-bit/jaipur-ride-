package com.drivepartner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.R
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextMuted
import com.drivepartner.app.ui.theme.DriveTextPrimary

/**
 * LoadingIndicator - Consistent loading indicator component.
 *
 * Implements:
 * - Brand Deep Blue circular progress spinner.
 * - Localized message support with fallback.
 * - Clean centered viewport presence.
 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    message: String? = null,
    subMessage: String? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DriveSurfaceWhite)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                color = DriveBluePrimary,
                strokeWidth = 3.5.dp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = message ?: stringResource(R.string.state_loading),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = DriveTextPrimary
            )

            if (!subMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = subMessage,
                    fontSize = 12.sp,
                    color = DriveTextMuted
                )
            }
        }
    }
}
