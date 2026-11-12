package com.drivepartner.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Drive Partner — Spacing & metric tokens
 *
 * Base unit 4 dp. Every gap in the product resolves to one of these values, which is what makes
 * screens feel composed rather than assembled. Insets grow with the window size class
 * (see [DriveWindowSizeClass.screenGutter]) so tablets do not inherit phone gutters.
 *
 * Touch targets: interactive controls are at least [minTouchTarget] (48 dp, Android guideline) and
 * primary actions use [primaryButtonHeight] (52 dp) for gloved, one-handed, in-vibration use.
 */
object DriveSpacing {
    // 4 dp base scale
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    // Aliases retained for existing call sites
    val space2 = xxs
    val space4 = xs
    val space8 = sm
    val space12 = md
    val space16 = lg
    val space20 = 20.dp
    val space24 = xl
    val space32 = xxl

    // Layout rhythm
    /** Default phone page inset; medium/expanded windows use [DriveWindowSizeClass.screenGutter]. */
    val screenPadding = 16.dp
    val cardPadding = 16.dp
    val cardPaddingCompact = 12.dp
    val cardPaddingHero = 20.dp
    val cardGap = 12.dp
    val sectionGap = 20.dp
    val listItemGap = 8.dp
    val inlineGap = 6.dp
    val iconLabelGap = 8.dp

    // Controls
    val minTouchTarget = 48.dp
    val primaryButtonHeight = 52.dp
    val secondaryButtonHeight = 48.dp
    val chipHeight = 36.dp
    val topBarHeight = 56.dp
    val bottomBarHeight = 68.dp
    val stickyActionBarMinHeight = 76.dp

    // Iconography — one consistent grid (24 dp optical box, 2 dp stroke)
    val iconXSmall = 16.dp
    val iconSmall = 18.dp
    val iconMedium = 24.dp
    val iconLarge = 28.dp
    val iconXLarge = 32.dp
    val iconWellSmall = 36.dp
    val iconWellMedium = 44.dp
    val iconWellLarge = 56.dp

    // Structure
    val hairlineBorder = 1.dp
    val dividerThickness = 1.dp
    val progressTrackHeight = 8.dp
    val progressTrackHeightSlim = 4.dp
    val statusBarScrimHeight = 24.dp

    // Illustration plates (original vector artwork, never bitmaps/screenshots)
    val illustrationSmall = 96.dp
    val illustrationMedium = 140.dp
    val illustrationLarge = 180.dp

    /** Vertical breathing room above a bottom action bar so it never crowds the last card. */
    val actionBarContentGap = 24.dp

    /**
     * Resolves the page inset for a window size class. Compact phones keep the 16 dp inset;
     * larger windows gain gutter so cards do not stretch to the physical screen edge.
     */
    fun gutterFor(windowClass: DriveWindowSizeClass): Dp = windowClass.screenGutter
}
