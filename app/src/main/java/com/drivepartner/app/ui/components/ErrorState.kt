package com.drivepartner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.R
import com.drivepartner.app.ui.icons.DriveIcons
import com.drivepartner.app.ui.icons.DriveIconSpec
import com.drivepartner.app.ui.theme.DriveCardShape

/**
 * ErrorState - Error and recovery view component.
 *
 * Implements:
 * - Semantic error red icon with soft error container background.
 * - Error title and explanation.
 * - Actionable "Try Again" recovery button.
 */
@Composable
fun ErrorState(
    title: String,
    message: String,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: DriveIconSpec = DriveIcons.AlertTriangle,
    retryLabel: String? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = DriveCardShape,
        colors = CardDefaults.cardColors(containerColor = DriveColors.current.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, DriveColors.current.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(DriveColors.current.dangerContainer),
                contentAlignment = Alignment.Center
            ) {
                DriveIcon(
                    icon = icon,
                    contentDescription = null,
                    tint = DriveColors.current.danger,
                    size = 28.dp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = DriveColors.current.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = message,
                fontSize = 12.sp,
                color = DriveColors.current.textTertiary,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            PrimaryButton(
                text = retryLabel ?: stringResource(R.string.state_retry),
                onClick = onRetryClick,
                leadingIcon = Icons.Outlined.Refresh,
                modifier = Modifier.fillMaxWidth(0.7f),
                containerColor = DriveColors.current.danger
            )
        }
    }
}
