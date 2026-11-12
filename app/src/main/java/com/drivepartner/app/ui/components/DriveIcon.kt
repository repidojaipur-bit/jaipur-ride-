package com.drivepartner.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.drivepartner.app.ui.icons.DriveIconSpec
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveIconWellShape
import com.drivepartner.app.ui.theme.DriveSpacing

/**
 * The single way to render a [DriveIconSpec].
 *
 * Guarantees the design system's iconography rules:
 * - size comes from the spacing scale (never ad-hoc dp),
 * - tint comes from semantic tokens, so light/dark correctness is automatic,
 * - icon-only controls get a `contentDescription`; purely decorative artwork passes `null`,
 *   which also strips it from the accessibility tree.
 */
@Composable
fun DriveIcon(
    icon: DriveIconSpec,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = DriveSpacing.iconMedium,
    tint: Color? = null
) {
    val colors = DriveColors.current
    Icon(
        painter = painterResource(id = icon.resource),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        tint = tint ?: colors.iconPrimary
    )
}

/**
 * A rounded tinted plate behind an icon — the standard treatment for card headers, list rows and
 * shortcut tiles. The plate colour is derived from the accent so it stays accessible in both
 * themes (a 10–18% tint, never a saturated fill under dark text).
 */
@Composable
fun DriveIconWell(
    icon: DriveIconSpec,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    accent: Color = DriveColors.current.brand,
    wellSize: Dp = DriveSpacing.iconWellMedium,
    iconSize: Dp = DriveSpacing.iconMedium
) {
    val colors = DriveColors.current
    Box(
        modifier = modifier
            .size(wellSize)
            .background(colors.tintedWell(accent), DriveIconWellShape),
        contentAlignment = Alignment.Center
    ) {
        DriveIcon(
            icon = icon,
            contentDescription = contentDescription,
            size = iconSize,
            tint = accent
        )
    }
}

/** Small inline icon with a fixed gap to its label, e.g. "• 2.4 km". */
@Composable
fun DriveInlineIcon(
    icon: DriveIconSpec,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = DriveSpacing.iconSmall,
    tint: Color? = null
) {
    val colors = DriveColors.current
    Icon(
        painter = painterResource(id = icon.resource),
        contentDescription = contentDescription,
        modifier = modifier
            .padding(end = DriveSpacing.inlineGap)
            .size(size),
        tint = tint ?: colors.iconSecondary
    )
}
