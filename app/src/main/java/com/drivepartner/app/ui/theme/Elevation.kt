package com.drivepartner.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Drive Partner — Elevation & Shadow tokens
 *
 * The design direction asks for *subtle* shadows: depth must separate layers, never decorate.
 * Cards therefore combine a hairline [DriveColorTokens.border] with a low elevation instead of a
 * heavy drop shadow, which keeps the interface crisp in direct sunlight where shadows wash out.
 *
 * Levels are semantic, not numeric guesses:
 * - [level0] flat, border-only (inset wells, table rows)
 * - [level1] standard card resting on the canvas
 * - [level2] raised card / sticky summary strip
 * - [level3] app bar, bottom navigation, FAB
 * - [level4] sheets and dialogs
 * - [level5] dispatch offer overlay (the most urgent surface in the product)
 */
@Immutable
data class DriveElevationTokens(
    val level0: Dp = 0.dp,
    val level1: Dp = 1.dp,
    val level2: Dp = 2.dp,
    val level3: Dp = 4.dp,
    val level4: Dp = 8.dp,
    val level5: Dp = 16.dp,
    /** Ambient shadow colour. Tinted with the ink colour so shadows read as neutral, not grey-blue. */
    val ambientColor: Color,
    /** Key/spot shadow colour. */
    val spotColor: Color
)

val LightDriveElevation = DriveElevationTokens(
    ambientColor = Color(0x0F17202A),
    spotColor = Color(0x1F17202A)
)

/** Dark themes flatten elevation: extra glow on a dark canvas looks like a rendering bug. */
val DarkDriveElevation = DriveElevationTokens(
    level1 = 0.dp,
    level2 = 1.dp,
    level3 = 2.dp,
    level4 = 4.dp,
    level5 = 8.dp,
    ambientColor = Color(0x33000000),
    spotColor = Color(0x4D000000)
)

val LocalDriveElevation = staticCompositionLocalOf { LightDriveElevation }

object DriveElevation {
    val current: DriveElevationTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalDriveElevation.current
}

/**
 * Applies the brand-correct soft shadow for an elevation level.
 *
 * `clip = false` by default so focus rings, ripples and overflowing badges are not cropped;
 * pass `clip = true` for surfaces containing edge-to-edge imagery. Tinted shadow colours are
 * honoured on API 28+ and silently fall back to the platform shadow below that, which keeps
 * the Android 7.0 (API 24) floor supported without visual breakage.
 */
fun Modifier.driveShadow(
    level: Dp,
    shape: Shape = RectangleShape,
    tokens: DriveElevationTokens,
    clip: Boolean = false
): Modifier =
    if (level <= 0.dp) {
        this
    } else {
        shadow(
            elevation = level,
            shape = shape,
            clip = clip,
            ambientColor = tokens.ambientColor,
            spotColor = tokens.spotColor
        )
    }
