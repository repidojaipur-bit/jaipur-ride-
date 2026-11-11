package com.drivepartner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveGreenContainer
import com.drivepartner.app.ui.theme.DriveGreenOnline
import com.drivepartner.app.ui.theme.DriveOfflineContainer
import com.drivepartner.app.ui.theme.DriveOfflineGrey
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextMuted
import com.drivepartner.app.ui.theme.DriveTextPrimary
import com.drivepartner.app.ui.theme.DriveTextSecondary

/**
 * TransactionCard - Reusable financial ledger transaction row.
 *
 * Implements:
 * - Clear Credit (+₹) vs Debit (-₹) differentiation.
 * - Directional arrow icons with semantic color containers.
 * - Transaction title, timestamp, and reference ID.
 * - Strict compliance with real transactional records.
 */
@Composable
fun TransactionCard(
    title: String,
    timestamp: String,
    referenceId: String,
    amountFormatted: String,
    isCredit: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clip(DriveCardShape)
            .clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }

    Card(
        modifier = cardModifier,
        shape = DriveCardShape,
        colors = CardDefaults.cardColors(containerColor = DriveSurfaceWhite),
        border = androidx.compose.foundation.BorderStroke(1.dp, DriveSurfaceBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Directional Icon
            val (icon, bg, fg) = if (isCredit) {
                Triple(Icons.Filled.ArrowDownward, DriveGreenContainer, DriveGreenOnline)
            } else {
                Triple(Icons.Filled.ArrowUpward, DriveOfflineContainer, DriveOfflineGrey)
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (isCredit) "Credit" else "Debit",
                    tint = fg,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DriveTextPrimary
                )
                Text(
                    text = "$timestamp • Ref: $referenceId",
                    fontSize = 11.sp,
                    color = DriveTextMuted
                )
            }

            // Amount
            Text(
                text = "${if (isCredit) "+" else "-"}$amountFormatted",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCredit) DriveGreenOnline else DriveTextPrimary
            )
        }
    }
}
