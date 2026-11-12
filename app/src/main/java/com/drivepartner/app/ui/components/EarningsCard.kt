package com.drivepartner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CurrencyRupee
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
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveCardShape

/**
 * EarningsCard - Dedicated high-visibility earnings highlight card.
 *
 * Implements:
 * - Drive Yellow Container highlight (#FEF08A / #FEF9C3).
 * - Bold, clear currency typography (INR).
 * - Subtext/period explanation.
 * - Optional tap interaction to view wallet/ledger.
 */
@Composable
fun EarningsCard(
    amountFormatted: String,
    periodLabel: String,
    modifier: Modifier = Modifier,
    subtext: String? = null,
    isProminentHighlight: Boolean = true,
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

    if (isProminentHighlight) {
        // High emphasis Drive Yellow Container
        Card(
            modifier = cardModifier,
            shape = DriveCardShape,
            colors = CardDefaults.cardColors(containerColor = DriveColors.current.warningContainer),
            border = androidx.compose.foundation.BorderStroke(1.dp, DriveColors.current.warning.copy(alpha = 0.6f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = periodLabel.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DriveColors.current.onWarningContainer,
                        letterSpacing = 0.8.sp
                    )
                    if (onClick != null) {
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "View details",
                            tint = DriveColors.current.onWarningContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = amountFormatted,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = DriveColors.current.onWarningContainer,
                    letterSpacing = (-0.5).sp
                )
                if (!subtext.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtext,
                        fontSize = 11.sp,
                        color = DriveColors.current.onWarningContainer.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    } else {
        // Neutral clean white card
        Card(
            modifier = cardModifier,
            shape = DriveCardShape,
            colors = CardDefaults.cardColors(containerColor = DriveColors.current.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DriveColors.current.border),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = periodLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = DriveColors.current.textSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = amountFormatted,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = DriveColors.current.textPrimary
                )
                if (!subtext.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtext,
                        fontSize = 11.sp,
                        color = DriveColors.current.textTertiary
                    )
                }
            }
        }
    }
}
