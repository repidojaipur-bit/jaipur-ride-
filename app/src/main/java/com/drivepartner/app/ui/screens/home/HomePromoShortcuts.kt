package com.drivepartner.app.ui.screens.home

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drivepartner.app.R
import com.drivepartner.app.ui.components.DriveIconWell
import com.drivepartner.app.ui.icons.DriveIconSpec
import com.drivepartner.app.ui.theme.DriveCardSmallShape
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.theme.DriveTextRoles

/**
 * Screen 5 (spec section 9): horizontally scrollable promotional shortcuts along the bottom of the
 * home dashboard — Daily Rewards, Refer & Earn, Bonus Challenges, Safety & Insurance, Auto Boost,
 * Priority Orders and My Performance. Each entry is a real destination or a real in-screen control;
 * nothing here is a decorative stub.
 */
data class HomeShortcut(
    val id: String,
    @StringRes val labelRes: Int,
    @StringRes val noteRes: Int,
    val icon: DriveIconSpec,
    val enabled: Boolean = true,
    @StringRes val statusRes: Int? = null,
    val onClick: () -> Unit
)

@Composable
fun HomePromoShortcutsRow(
    shortcuts: List<HomeShortcut>,
    modifier: Modifier = Modifier
) {
    val colors = DriveColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_shortcuts_title),
            style = DriveTextRoles.overline,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = DriveSpacing.screenPadding)
        )
        Spacer(modifier = Modifier.height(DriveSpacing.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(
                DriveSpacing.md,
                alignment = Alignment.Start
            )
        ) {
            Spacer(modifier = Modifier.width(DriveSpacing.screenPadding))
            shortcuts.forEach { shortcut ->
                HomeShortcutTile(shortcut = shortcut)
            }
            Spacer(modifier = Modifier.width(DriveSpacing.screenPadding))
        }
    }
}

@Composable
private fun HomeShortcutTile(shortcut: HomeShortcut) {
    val colors = DriveColors.current
    Surface(
        shape = DriveCardSmallShape,
        color = colors.surface,
        border = androidx.compose.foundation.BorderStroke(
            DriveSpacing.hairlineBorder,
            colors.border
        ),
        shadowElevation = DriveElevation.current.level1,
        modifier = Modifier
            .width(SHORTCUT_TILE_WIDTH)
            .heightIn(min = SHORTCUT_TILE_HEIGHT)
            .clickable(
                enabled = shortcut.enabled,
                role = Role.Button,
                onClick = shortcut.onClick
            )
    ) {
        Column(
            modifier = Modifier.padding(DriveSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)
        ) {
            DriveIconWell(
                icon = shortcut.icon,
                contentDescription = null,
                accent = colors.brand,
                wellSize = DriveSpacing.iconWellSmall,
                iconSize = DriveSpacing.iconSmall
            )
            Text(
                text = stringResource(shortcut.labelRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (shortcut.enabled) colors.textPrimary else colors.textDisabled,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = shortcut.statusRes?.let { stringResource(it) }
                    ?: stringResource(shortcut.noteRes),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val SHORTCUT_TILE_WIDTH = 148.dp
private val SHORTCUT_TILE_HEIGHT = 116.dp
