package com.drivepartner.app.ui.screens.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.model.OrderDetail
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveOnYellowContainer
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.theme.DriveSurfaceSubtle
import com.drivepartner.app.ui.theme.DriveTextSecondary
import com.drivepartner.app.ui.theme.DriveYellowContainer

@Composable
fun OrderDetailDialog(
    detail: OrderDetail,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.order_detail_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (detail.dataOrigin == DataOrigin.DEMO) {
                    Surface(
                        shape = DriveChipShape,
                        color = DriveYellowContainer
                    ) {
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
                    text = stringResource(R.string.order_detail_order_id, detail.orderId),
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveTextSecondary
                )
                Text(
                    text = stringResource(R.string.order_detail_trip_id, detail.tripId),
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveTextSecondary
                )
                Text(
                    text = "${detail.pickupSummary} -> ${detail.dropSummary}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = stringResource(
                        R.string.order_detail_metrics,
                        formatKm(detail.distanceMeters),
                        formatMinutes(detail.durationSeconds)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = DriveTextSecondary
                )

                detail.cancellationReasonCode?.let { reason ->
                    Text(
                        text = stringResource(R.string.order_detail_cancel_reason, reason),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.order_detail_earning_heading),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = DriveChipShape,
                    color = DriveSurfaceSubtle
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(DriveSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (detail.finalizedAmount != null) {
                            Text(
                                text = stringResource(
                                    R.string.order_detail_finalized_amount,
                                    detail.finalizedAmount.formatDisplay()
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.orders_history_amount_pending),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        detail.earningSourceRefId?.let { srcRef ->
                            Text(
                                text = stringResource(R.string.order_detail_source_ref_id, srcRef),
                                style = MaterialTheme.typography.labelSmall,
                                color = DriveTextSecondary
                            )
                        }
                        detail.earningStatementId?.let { stmtId ->
                            Text(
                                text = stringResource(R.string.order_detail_statement_id, stmtId),
                                style = MaterialTheme.typography.labelSmall,
                                color = DriveTextSecondary
                            )
                        }
                        Text(
                            text = stringResource(R.string.trip_no_local_earnings_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = DriveTextSecondary
                        )
                    }
                }

                if (detail.timelineEvents.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.order_detail_timeline_heading),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    detail.timelineEvents.forEach { eventLine ->
                        Text(
                            text = "• $eventLine",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.order_detail_close))
            }
        }
    )
}

internal fun formatKm(distanceMeters: Int): String {
    val km = distanceMeters / 1000.0
    return String.format(java.util.Locale.US, "%.1f km", km)
}

internal fun formatMinutes(durationSeconds: Int): String {
    val mins = (durationSeconds / 60).coerceAtLeast(1)
    return "$mins min"
}
