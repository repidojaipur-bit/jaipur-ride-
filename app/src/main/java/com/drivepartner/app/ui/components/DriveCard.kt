package com.drivepartner.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.drivepartner.app.ui.theme.DriveCardLargeShape
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation
import com.drivepartner.app.ui.theme.DriveSpacing

/**
 * The standard Drive Partner card: white surface in light mode, `#16202B` in dark mode, a hairline
 * border plus a *subtle* shadow (Design.md Section 4 — "use subtle elevation sparingly").
 *
 * In dark mode the elevation collapses towards flat because shadows are invisible on a dark
 * canvas; the border carries the separation instead. That behaviour lives in the elevation tokens,
 * so no card in the product needs a theme branch.
 */
@Composable
fun DriveCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = DriveColors.current.surface,
    elevation: Dp = DriveElevation.current.level1,
    borderColor: Color = DriveColors.current.border,
    shape: Shape = DriveCardShape,
    contentPadding: Dp = DriveSpacing.cardPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(DriveSpacing.hairlineBorder, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/** Hero variant for the cards a driver reads at arm's length: duty, earnings, dispatch offer. */
@Composable
fun DriveHeroCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = DriveColors.current.surface,
    borderColor: Color = DriveColors.current.border,
    content: @Composable ColumnScope.() -> Unit
) {
    DriveCard(
        modifier = modifier,
        backgroundColor = backgroundColor,
        elevation = DriveElevation.current.level2,
        borderColor = borderColor,
        shape = DriveCardLargeShape,
        contentPadding = DriveSpacing.cardPaddingHero,
        content = content
    )
}

/** Sunken variant: input wells, empty plates, inset summary strips. */
@Composable
fun DriveSunkenCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = DriveColors.current
    DriveCard(
        modifier = modifier,
        backgroundColor = colors.surfaceSunken,
        elevation = DriveElevation.current.level0,
        borderColor = colors.border,
        content = content
    )
}
