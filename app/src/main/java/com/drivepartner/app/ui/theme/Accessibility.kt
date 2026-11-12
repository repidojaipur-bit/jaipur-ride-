package com.drivepartner.app.ui.theme

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Drive Partner — Accessibility contract
 *
 * The design system enforces accessibility structurally instead of leaving it to each screen:
 *
 * 1. **Touch targets** — every interactive control reaches 48 dp (Android guideline) and the
 *    primary duty/accept controls use 52 dp, because drivers operate the phone with gloves,
 *    in vibration and often one-handed.
 * 2. **Contrast** — text tokens are published with their measured WCAG 2.1 ratios, and
 *    [contrastRatio] lets tests assert them instead of eyeballing screenshots.
 * 3. **Never colour alone** — status always ships as text + icon + semantic tint. This file
 *    exposes the semantics helpers that make the non-colour cue readable by TalkBack.
 * 4. **Font scaling** — layouts adapt at large font scales rather than clipping. Values are in
 *    `sp` everywhere; no token in this system is expressed in raw pixels.
 */
object DriveAccessibility {
    /** Android minimum interactive size. */
    val minTouchTarget: Dp = 48.dp

    /** Drive Partner target for primary actions (duty toggle, accept, withdraw). */
    val recommendedTouchTarget: Dp = 52.dp

    /** Minimum icon size that stays legible in daylight. */
    val minIconSize: Dp = 20.dp

    /** Minimum text size for information a driver must read. */
    val minTextSize: TextUnit = 12.sp

    val wcagAaNormalText: Double = 4.5
    val wcagAaLargeText: Double = 3.0
    val wcagAaaNormalText: Double = 7.0
    val wcagUiComponent: Double = 3.0

    /** WCAG "large text": 24 sp, or 18.5 sp and bold. */
    val largeTextThreshold: TextUnit = 24.sp
    val largeBoldTextThreshold: TextUnit = 18.5.sp

    /** Spacing between an icon and its label so the pair reads as one unit. */
    val iconLabelGap: Dp = 8.dp
}

/** Font-scale buckets used to reflow layouts instead of truncating text. */
enum class DriveFontScaleBucket {
    /** System scale below 1.2 — the default phone layout. */
    DEFAULT,

    /** 1.2–1.44 — metric strips wrap to two lines, chips keep single-line labels. */
    LARGE,

    /** ≥ 1.45 — horizontal rows collapse into vertical stacks to preserve readability. */
    LARGEST
}

@Immutable
data class DriveTypeScaleInfo(
    val fontScale: Float,
    val bucket: DriveFontScaleBucket
) {
    val isEnlarged: Boolean get() = bucket != DriveFontScaleBucket.DEFAULT
    val collapsesRows: Boolean get() = bucket == DriveFontScaleBucket.LARGEST
}

/** Reads the system font scale and maps it to a layout bucket. */
@Composable
fun rememberDriveTypeScaleInfo(): DriveTypeScaleInfo {
    val fontScale = LocalDensity.current.fontScale
    return remember(fontScale) {
        val bucket = when {
            fontScale >= 1.45f -> DriveFontScaleBucket.LARGEST
            fontScale >= 1.2f -> DriveFontScaleBucket.LARGE
            else -> DriveFontScaleBucket.DEFAULT
        }
        DriveTypeScaleInfo(fontScale = fontScale, bucket = bucket)
    }
}

// ---------------------------------------------------------------------------
// WCAG 2.1 contrast mathematics (pure JVM — asserted by DesignTokensTest)
// ---------------------------------------------------------------------------

/** Relative luminance of an sRGB colour per WCAG 2.1. */
fun relativeLuminance(color: Color): Double {
    val argb = color.toArgb()
    val red = ((argb shr 16) and 0xFF) / 255.0
    val green = ((argb shr 8) and 0xFF) / 255.0
    val blue = (argb and 0xFF) / 255.0
    val r = linearize(red)
    val g = linearize(green)
    val b = linearize(blue)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

private fun linearize(channel: Double): Double =
    if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

/**
 * Contrast ratio between a foreground and background colour. Semi-transparent foregrounds are
 * composited over the background first, which is what a driver's eye actually perceives.
 * Returns a value in 1.0..21.0.
 */
fun contrastRatio(foreground: Color, background: Color): Double {
    val opaqueForeground = foreground.compositeOver(background)
    val foregroundLuminance = relativeLuminance(opaqueForeground)
    val backgroundLuminance = relativeLuminance(background)
    val lighter = max(foregroundLuminance, backgroundLuminance)
    val darker = min(foregroundLuminance, backgroundLuminance)
    return (lighter + 0.05) / (darker + 0.05)
}

/** True when the pair satisfies WCAG AA for the given text size. */
fun meetsWcagAa(foreground: Color, background: Color, isLargeText: Boolean = false): Boolean {
    val ratio = contrastRatio(foreground, background)
    val threshold = if (isLargeText) DriveAccessibility.wcagAaLargeText else DriveAccessibility.wcagAaNormalText
    return ratio >= threshold
}

/** True when the pair satisfies WCAG 3:1 for non-text UI components (icons, borders, controls). */
fun meetsUiComponentContrast(foreground: Color, background: Color): Boolean =
    contrastRatio(foreground, background) >= DriveAccessibility.wcagUiComponent

// ---------------------------------------------------------------------------
// Semantics & sizing modifiers
// ---------------------------------------------------------------------------

/**
 * Grows an element to the accessible minimum without changing its visual size: a 24 dp icon in a
 * 48 dp hit box still looks like a 24 dp icon, but is comfortably tappable with gloves on.
 */
fun Modifier.driveMinTouchTarget(minSize: Dp = DriveAccessibility.minTouchTarget): Modifier =
    this.defaultMinSize(minWidth = minSize, minHeight = minSize)

/** Marks a text node as a heading so TalkBack users can jump between screen sections. */
fun Modifier.driveHeading(): Modifier = this.semantics { heading() }

/**
 * Announces changing values (offer countdown, duty transition, payout progress) politely.
 * Countdowns use this so a driver with the screen off still hears the remaining time.
 */
fun Modifier.driveLiveRegion(mode: LiveRegionMode = LiveRegionMode.Polite): Modifier =
    this.semantics { liveRegion = mode }

/** Adds the state half of a toggle's description ("Online", "Selected"), required for switches. */
fun Modifier.driveStateDescription(description: String): Modifier =
    this.semantics { stateDescription = description }

/**
 * Merges descendants into a single accessibility node — used for cards so TalkBack reads
 * "Bike taxi, pickup Malviya Nagar, 4 kilometres, 96 rupees" as one swipe instead of seven.
 */
fun Modifier.driveMergedGroup(): Modifier = this.semantics(mergeDescendants = true) {}

/** Hides purely decorative artwork from the accessibility tree. */
fun Modifier.driveDecorative(): Modifier = this.clearAndSetSemantics {}
