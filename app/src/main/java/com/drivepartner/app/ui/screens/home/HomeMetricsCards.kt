package com.drivepartner.app.ui.screens.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivepartner.app.R
import com.drivepartner.app.data.model.HomePerformanceSnapshot
import com.drivepartner.app.data.model.HomeTodayMetrics
import com.drivepartner.app.data.model.PerformanceSuggestion
import com.drivepartner.app.data.model.SuggestionAccent
import com.drivepartner.app.ui.components.DriveCard
import com.drivepartner.app.ui.components.DriveHeroCard
import com.drivepartner.app.ui.components.DriveIcon
import com.drivepartner.app.ui.components.DriveIconWell
import com.drivepartner.app.ui.components.DriveSunkenCard
import com.drivepartner.app.ui.icons.DriveIcons
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.theme.DriveTextRoles
import java.util.Locale

/**
 * Screen 5 (spec section 9): "today's earnings summary" hero card — total earnings, completed
 * rides, total distance and incentive earnings, each in its own glanceable tile.
 *
 * Missing backend figures render an explicit "not available" value instead of a made-up number.
 */
@Composable
fun HomeTodaySummaryCard(
    metrics: HomeTodayMetrics?,
    isLoading: Boolean,
    isRefreshing: Boolean,
    errorMessageRes: Int?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = DriveColors.current
    DriveHeroCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.home_today_summary_title),
                style = DriveTextRoles.overline,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f)
            )
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(DriveSpacing.iconSmall),
                    strokeWidth = 2.dp,
                    color = colors.brand
                )
            } else {
                IconButton(onClick = onRefresh) {
                    DriveIcon(
                        icon = DriveIcons.Refresh,
                        contentDescription = stringResource(R.string.home_metrics_refresh),
                        size = DriveSpacing.iconSmall,
                        tint = colors.iconSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(DriveSpacing.md))

        when {
            isLoading -> SummaryLoadingState()
            metrics == null && errorMessageRes != null -> SummaryErrorState(
                message = stringResource(errorMessageRes),
                onRefresh = onRefresh
            )
            metrics == null -> SummaryLoadingState()
            else -> SummaryMetricsGrid(metrics = metrics)
        }
    }
}

@Composable
private fun ColumnScope.SummaryLoadingState() {
    val colors = DriveColors.current
    DriveSunkenCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DriveSpacing.xs)
        ) {
            Text(
                text = stringResource(R.string.home_metric_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colors.brand,
                trackColor = colors.surfaceSunken,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ColumnScope.SummaryErrorState(message: String, onRefresh: () -> Unit) {
    val colors = DriveColors.current
    DriveSunkenCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DriveIcon(
                    icon = DriveIcons.AlertTriangle,
                    contentDescription = null,
                    size = DriveSpacing.iconSmall,
                    tint = colors.dangerStrong
                )
                Spacer(modifier = Modifier.width(DriveSpacing.sm))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.dangerStrong
                )
            }
            Text(
                text = stringResource(R.string.home_metrics_refresh),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textLink,
                modifier = Modifier
                    .heightIn(min = DriveSpacing.secondaryButtonHeight)
                    .clickable(onClick = onRefresh)
            )
        }
    }
}

@Composable
private fun ColumnScope.SummaryMetricsGrid(metrics: HomeTodayMetrics) {
    val colors = DriveColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(DriveSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DriveSpacing.md)
        ) {
            MetricTile(
                label = stringResource(R.string.home_metric_total_earnings),
                value = metrics.totalEarnings?.formatDisplay()
                    ?: stringResource(R.string.home_metric_unavailable),
                valueColor = colors.textPrimary,
                style = DriveTextRoles.moneyLarge,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = stringResource(R.string.home_metric_completed_rides),
                value = metrics.completedRides?.toString()
                    ?: stringResource(R.string.home_metric_unavailable),
                valueColor = colors.textPrimary,
                style = DriveTextRoles.moneyLarge,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DriveSpacing.md)
        ) {
            MetricTile(
                label = stringResource(R.string.home_metric_total_distance),
                value = metrics.totalDistanceMeters?.let { meters ->
                    stringResource(
                        R.string.home_distance_value,
                        formatKilometres(meters)
                    )
                } ?: stringResource(R.string.home_metric_unavailable),
                valueColor = colors.brandText,
                style = DriveTextRoles.moneyMedium,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = stringResource(R.string.home_metric_incentive_earnings),
                value = metrics.incentiveEarnings?.formatDisplay()
                    ?: stringResource(R.string.home_metric_unavailable),
                valueColor = colors.successStrong,
                style = DriveTextRoles.moneyMedium,
                modifier = Modifier.weight(1f)
            )
        }

        val covered = metrics.distanceCoversRides
        val completed = metrics.completedRides ?: 0
        if (metrics.totalDistanceMeters != null && covered in 1 until completed) {
            Text(
                text = stringResource(R.string.home_distance_partial_note, covered),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary
            )
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    val colors = DriveColors.current
    DriveSunkenCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DriveSpacing.xxs)
        ) {
            Text(
                text = label,
                style = DriveTextRoles.overline,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = style,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Screen 5 performance card: daily completed orders against a driver-owned daily target with a
 * progress bar, weekly performance from the backend, and data-driven improvement suggestions.
 */
@Composable
fun HomePerformanceCard(
    performance: HomePerformanceSnapshot,
    suggestions: List<PerformanceSuggestion>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEditTarget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = DriveColors.current
    val progress = performance.dailyTargetProgress
    DriveCard(modifier = modifier.animateContentSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DriveIconWell(
                icon = DriveIcons.Target,
                contentDescription = null,
                accent = colors.brand,
                wellSize = DriveSpacing.iconWellSmall,
                iconSize = DriveSpacing.iconSmall
            )
            Spacer(modifier = Modifier.width(DriveSpacing.md))
            Text(
                text = stringResource(R.string.home_performance_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleExpanded) {
                DriveIcon(
                    icon = if (expanded) DriveIcons.Close else DriveIcons.ChevronRight,
                    contentDescription = stringResource(
                        if (expanded) R.string.home_performance_show_less
                        else R.string.home_performance_show_more
                    ),
                    size = DriveSpacing.iconSmall,
                    tint = colors.iconSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(DriveSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_performance_daily_orders),
                    style = DriveTextRoles.overline,
                    color = colors.textTertiary
                )
                Text(
                    text = performance.dailyCompletedOrders.toString(),
                    style = DriveTextRoles.moneyLarge,
                    color = colors.textPrimary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.home_performance_target_personal),
                    style = DriveTextRoles.overline,
                    color = colors.textTertiary
                )
                Text(
                    text = performance.dailyTarget?.let { target ->
                        stringResource(R.string.home_performance_target_value, target)
                    } ?: stringResource(R.string.home_performance_no_target),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (performance.dailyTarget == null) colors.textSecondary else colors.brandText
                )
            }
        }

        Spacer(modifier = Modifier.height(DriveSpacing.sm))

        if (progress != null) {
            val progressDescription = stringResource(R.string.home_performance_progress_label) +
                " " +
                stringResource(R.string.home_performance_rate_value, (progress * 100).toInt())
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = progressDescription },
                color = colors.brand,
                trackColor = colors.surfaceSunken,
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(DriveSpacing.xs))
            Text(
                text = stringResource(R.string.home_performance_target_edit),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textLink,
                modifier = Modifier
                    .heightIn(min = DriveSpacing.secondaryButtonHeight)
                    .clickable(onClick = onEditTarget)
            )
        } else {
            Text(
                text = stringResource(R.string.home_performance_target_set),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textLink,
                modifier = Modifier
                    .heightIn(min = DriveSpacing.secondaryButtonHeight)
                    .clickable(onClick = onEditTarget)
            )
        }

        Spacer(modifier = Modifier.height(DriveSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DriveSpacing.md)
        ) {
            PerformanceFigure(
                title = stringResource(R.string.home_performance_weekly_title),
                value = performance.weeklyCompletedOrders?.let { count ->
                    stringResource(R.string.home_performance_weekly_orders, count)
                } ?: stringResource(R.string.home_metric_unavailable),
                modifier = Modifier.weight(1f)
            )
            PerformanceFigure(
                title = stringResource(R.string.home_metric_total_earnings),
                value = performance.weeklyEarnings?.formatDisplay()
                    ?: stringResource(R.string.home_metric_unavailable),
                modifier = Modifier.weight(1f)
            )
        }

        val extras = buildList {
            performance.acceptanceRatePercent?.let { rate ->
                add(
                    stringResource(R.string.home_performance_acceptance) to
                        stringResource(R.string.home_performance_rate_value, rate)
                )
            }
            performance.cancellationRatePercent?.let { rate ->
                add(
                    stringResource(R.string.home_performance_cancellation) to
                        stringResource(R.string.home_performance_rate_value, rate)
                )
            }
            performance.onlineMinutesToday?.let { minutes ->
                add(
                    stringResource(R.string.home_performance_online_time) to
                        stringResource(R.string.home_performance_online_value, minutes)
                )
            }
        }
        if (extras.isNotEmpty()) {
            Spacer(modifier = Modifier.height(DriveSpacing.sm))
            extras.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                }
            }
        }

        if (!performance.isBackendProvided) {
            Spacer(modifier = Modifier.height(DriveSpacing.sm))
            Text(
                text = stringResource(R.string.home_performance_backend_note),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary
            )
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(DriveSpacing.md))
            Text(
                text = stringResource(R.string.home_performance_suggestions_title),
                style = DriveTextRoles.overline,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(DriveSpacing.sm))
            suggestions.forEach { suggestion ->
                SuggestionRow(suggestion = suggestion)
                Spacer(modifier = Modifier.height(DriveSpacing.sm))
            }
        }
    }
}

@Composable
private fun PerformanceFigure(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val colors = DriveColors.current
    DriveSunkenCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.xxs)) {
            Text(
                text = title,
                style = DriveTextRoles.overline,
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
        }
    }
}

@Composable
private fun SuggestionRow(suggestion: PerformanceSuggestion) {
    val colors = DriveColors.current
    val accent = when (suggestion.accent) {
        SuggestionAccent.POSITIVE -> colors.successStrong
        SuggestionAccent.CAUTION -> colors.warningStrong
        SuggestionAccent.NEUTRAL -> colors.iconSecondary
    }
    val icon = when (suggestion.accent) {
        SuggestionAccent.POSITIVE -> DriveIcons.CheckCircle
        SuggestionAccent.CAUTION -> DriveIcons.AlertTriangle
        SuggestionAccent.NEUTRAL -> DriveIcons.Info
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        DriveIcon(
            icon = icon,
            contentDescription = null,
            size = DriveSpacing.iconXSmall,
            tint = accent,
            modifier = Modifier.padding(top = DriveSpacing.xxs)
        )
        Spacer(modifier = Modifier.width(DriveSpacing.sm))
        Text(
            text = suggestion.messageArg?.let { arg ->
                stringResource(suggestion.messageRes, arg)
            } ?: stringResource(suggestion.messageRes),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
    }
}

private fun formatKilometres(meters: Int): String {
    val kilometres = meters / 1000.0
    return String.format(Locale.getDefault(), "%.1f", kilometres)
}
