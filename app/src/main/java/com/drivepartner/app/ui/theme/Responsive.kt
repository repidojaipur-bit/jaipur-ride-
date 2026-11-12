package com.drivepartner.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified

/**
 * Drive Partner — Responsive layout tokens
 *
 * Android partner devices range from 4.7" budget phones (360 dp) through 6.7" phones (412 dp) to
 * foldables and in-car tablets (600–1000+ dp). One layout cannot serve all of them, so the design
 * system publishes an explicit window-size class and derives every layout decision from it:
 * gutter, content columns, maximum reading measure and whether a two-pane layout is appropriate.
 *
 * Breakpoints follow the Material 3 window size classes, implemented locally so the system works
 * on API 24 (Android 7.0) without an extra dependency.
 */
enum class DriveWindowWidth { COMPACT, MEDIUM, EXPANDED }

enum class DriveWindowHeight { COMPACT, MEDIUM, EXPANDED }

object DriveBreakpoints {
    val widthMedium = 600.dp
    val widthExpanded = 840.dp
    val heightMedium = 480.dp
    val heightExpanded = 900.dp

    /** Longest comfortable text measure, in dp, per width class. */
    val measureCompact = 480.dp
    val measureMedium = 640.dp
    val measureExpanded = 840.dp
}

@Immutable
data class DriveWindowSizeClass(
    val width: DriveWindowWidth,
    val height: DriveWindowHeight,
    val screenWidth: Dp,
    val screenHeight: Dp
) {
    val isCompactWidth: Boolean get() = width == DriveWindowWidth.COMPACT

    /** True when the window can host a master/detail split (foldables, tablets, landscape cars). */
    val supportsTwoPane: Boolean get() = width != DriveWindowWidth.COMPACT

    /** Horizontal page inset. Grows with the window so cards never stretch edge-to-edge on tablets. */
    val screenGutter: Dp
        get() = when (width) {
            DriveWindowWidth.COMPACT -> 16.dp
            DriveWindowWidth.MEDIUM -> 24.dp
            DriveWindowWidth.EXPANDED -> 32.dp
        }

    /** Vertical gap between stacked cards. */
    val cardGap: Dp
        get() = when (width) {
            DriveWindowWidth.COMPACT -> 12.dp
            DriveWindowWidth.MEDIUM -> 16.dp
            DriveWindowWidth.EXPANDED -> 20.dp
        }

    /** Columns for the home shortcut grid and reward/summary tiles. */
    val gridColumns: Int
        get() = when (width) {
            DriveWindowWidth.COMPACT -> 2
            DriveWindowWidth.MEDIUM -> 3
            DriveWindowWidth.EXPANDED -> 4
        }

    /** Columns for the earnings metric strip. */
    val metricColumns: Int
        get() = when (width) {
            DriveWindowWidth.COMPACT -> 3
            DriveWindowWidth.MEDIUM -> 3
            DriveWindowWidth.EXPANDED -> 6
        }

    /** Height of the sticky primary action bar at the bottom of a screen. */
    val actionBarHeight: Dp
        get() = when (height) {
            DriveWindowHeight.COMPACT -> 56.dp
            else -> 68.dp
        }

    /** Minimum map height that still shows a usable route on short/landscape windows. */
    val mapHeight: Dp
        get() = when (height) {
            DriveWindowHeight.COMPACT -> 180.dp
            DriveWindowHeight.MEDIUM -> 240.dp
            DriveWindowHeight.EXPANDED -> 320.dp
        }

    /** Maximum reading measure; [Dp.Unspecified] on compact phones so content fills the screen. */
    val maxContentWidth: Dp
        get() = when (width) {
            DriveWindowWidth.COMPACT -> Dp.Unspecified
            DriveWindowWidth.MEDIUM -> DriveBreakpoints.measureMedium
            DriveWindowWidth.EXPANDED -> DriveBreakpoints.measureExpanded
        }

    companion object {
        fun calculate(screenWidth: Dp, screenHeight: Dp): DriveWindowSizeClass {
            val width = when {
                screenWidth >= DriveBreakpoints.widthExpanded -> DriveWindowWidth.EXPANDED
                screenWidth >= DriveBreakpoints.widthMedium -> DriveWindowWidth.MEDIUM
                else -> DriveWindowWidth.COMPACT
            }
            val height = when {
                screenHeight >= DriveBreakpoints.heightExpanded -> DriveWindowHeight.EXPANDED
                screenHeight >= DriveBreakpoints.heightMedium -> DriveWindowHeight.MEDIUM
                else -> DriveWindowHeight.COMPACT
            }
            return DriveWindowSizeClass(
                width = width,
                height = height,
                screenWidth = screenWidth,
                screenHeight = screenHeight
            )
        }

        /** Fallback for previews and tests: a 412 x 892 dp phone, the most common partner device. */
        val Compact = calculate(412.dp, 892.dp)
    }
}

val LocalDriveWindowSizeClass = staticCompositionLocalOf { DriveWindowSizeClass.Compact }

/**
 * Resolves the active window size class from the current configuration. Recomputes on rotation,
 * fold/unfold, split-screen and desktop-mode resize.
 */
@Composable
fun rememberDriveWindowSizeClass(): DriveWindowSizeClass {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val resolved = remember(screenWidthDp, screenHeightDp) {
        DriveWindowSizeClass.calculate(screenWidthDp.dp, screenHeightDp.dp)
    }
    return resolved
}

object DriveResponsive {
    val current: DriveWindowSizeClass
        @Composable
        @ReadOnlyComposable
        get() = LocalDriveWindowSizeClass.current
}

/**
 * Centres screen content and caps the reading measure on medium/expanded windows, while remaining
 * a full-bleed column on phones. Every screen shell should wrap its scrollable body in this so
 * tablets and foldables do not stretch text to unreadable line lengths.
 */
@Composable
fun DriveAdaptivePane(
    modifier: Modifier = Modifier,
    maxWidth: Dp = Dp.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    val resolvedMaxWidth =
        if (maxWidth.isUnspecified) DriveResponsive.current.maxContentWidth else maxWidth
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .widthIn(max = resolvedMaxWidth)
                .align(Alignment.TopCenter),
            content = content
        )
    }
}

/** Caps any element (dialogs, sheets, forms) to the active reading measure. */
fun Modifier.driveReadingMeasure(windowClass: DriveWindowSizeClass): Modifier =
    this.widthIn(max = windowClass.maxContentWidth)
