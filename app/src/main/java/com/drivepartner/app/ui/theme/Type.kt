package com.drivepartner.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Drive Partner — Typography tokens
 *
 * Clean, high-legibility type tuned for daylight reading and for Devanagari. Two rules matter more
 * than any single size:
 *
 * 1. **No colour is baked into a text style.** Every style inherits `LocalContentColor`, which is
 *    what makes the same `Text(style = titleLarge)` correct on a white card in light mode and on a
 *    `#16202B` card in dark mode. Hardcoding ink here was a real dark-mode defect.
 * 2. **Sizes are `sp`, never px**, so the system font scale is respected end-to-end
 *    (Design.md Section 3). Layouts reflow at large scales via [rememberDriveTypeScaleInfo].
 *
 * Font family: the platform sans-serif stack. Android ships Noto Sans Devanagari as a system font
 * from API 21, so Hindi (हिंदी) renders with correct shaping and matras on the Android 7.0 floor
 * without bundling a font asset. The family is centralized here so a future brand face is a
 * one-line change (Design.md Section 12).
 */
val DriveFontFamily: FontFamily = FontFamily.SansSerif

/** Optional brand override hook: set to a bundled [FontFamily] to re-skin type without touching screens. */
val DriveBrandFontFamily: FontFamily = DriveFontFamily

/** Vertical alignment strategy so Devanagari and Latin share a consistent optical baseline. */
private val DriveLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Proportional,
    trim = LineHeightStyle.Trim.None
)

private fun driveTextStyle(
    fontSizeSp: Int,
    lineHeightSp: Int,
    fontWeight: FontWeight,
    letterSpacingSp: Double = 0.0
) = TextStyle(
    fontFamily = DriveBrandFontFamily,
    fontWeight = fontWeight,
    fontSize = fontSizeSp.sp,
    lineHeight = lineHeightSp.sp,
    letterSpacing = letterSpacingSp.sp,
    lineHeightStyle = DriveLineHeightStyle
)

/**
 * Material 3 type scale. Roles are used semantically across the product:
 * `displayLarge` = hero money, `headlineMedium` = screen titles, `titleMedium` = card titles,
 * `bodyLarge` = primary reading text, `labelSmall` = chips and tab labels.
 */
val DriveTypography = Typography(
    displayLarge = driveTextStyle(fontSizeSp = 32, lineHeightSp = 40, fontWeight = FontWeight.Black, letterSpacingSp = -0.5),
    displayMedium = driveTextStyle(fontSizeSp = 28, lineHeightSp = 34, fontWeight = FontWeight.Bold, letterSpacingSp = -0.25),
    displaySmall = driveTextStyle(fontSizeSp = 24, lineHeightSp = 30, fontWeight = FontWeight.Bold, letterSpacingSp = -0.2),

    headlineLarge = driveTextStyle(fontSizeSp = 22, lineHeightSp = 28, fontWeight = FontWeight.Bold),
    headlineMedium = driveTextStyle(fontSizeSp = 20, lineHeightSp = 26, fontWeight = FontWeight.Bold),
    headlineSmall = driveTextStyle(fontSizeSp = 18, lineHeightSp = 24, fontWeight = FontWeight.SemiBold),

    titleLarge = driveTextStyle(fontSizeSp = 18, lineHeightSp = 24, fontWeight = FontWeight.SemiBold),
    titleMedium = driveTextStyle(fontSizeSp = 16, lineHeightSp = 22, fontWeight = FontWeight.SemiBold),
    titleSmall = driveTextStyle(fontSizeSp = 14, lineHeightSp = 20, fontWeight = FontWeight.Medium),

    bodyLarge = driveTextStyle(fontSizeSp = 15, lineHeightSp = 22, fontWeight = FontWeight.Normal),
    bodyMedium = driveTextStyle(fontSizeSp = 14, lineHeightSp = 20, fontWeight = FontWeight.Normal),
    bodySmall = driveTextStyle(fontSizeSp = 12, lineHeightSp = 17, fontWeight = FontWeight.Normal),

    labelLarge = driveTextStyle(fontSizeSp = 15, lineHeightSp = 20, fontWeight = FontWeight.SemiBold, letterSpacingSp = 0.1),
    labelMedium = driveTextStyle(fontSizeSp = 12, lineHeightSp = 16, fontWeight = FontWeight.Medium, letterSpacingSp = 0.3),
    labelSmall = driveTextStyle(fontSizeSp = 11, lineHeightSp = 14, fontWeight = FontWeight.SemiBold, letterSpacingSp = 0.4)
)

/**
 * Role-based styles that are not part of the Material scale. Monetary values get their own roles so
 * every rupee total in the product has identical weight, tabular rhythm and emphasis.
 */
@Immutable
object DriveTextRoles {
    /** Hero earnings figure (e.g. ₹1,450.00 on the wallet card). */
    val moneyHero: TextStyle = driveTextStyle(fontSizeSp = 34, lineHeightSp = 40, fontWeight = FontWeight.Black, letterSpacingSp = -0.75)

    /** Section-level money total (today's earnings, payout amount). */
    val moneyLarge: TextStyle = driveTextStyle(fontSizeSp = 26, lineHeightSp = 32, fontWeight = FontWeight.Bold, letterSpacingSp = -0.4)

    /** Money inside list rows and ledger entries. */
    val moneyMedium: TextStyle = driveTextStyle(fontSizeSp = 17, lineHeightSp = 22, fontWeight = FontWeight.Bold)

    /** Offer countdown seconds — large so it is readable at arm's length on a mount. */
    val countdown: TextStyle = driveTextStyle(fontSizeSp = 28, lineHeightSp = 32, fontWeight = FontWeight.Bold, letterSpacingSp = -0.2)

    /** Overline used for section eyebrows ("TODAY", "PICKUP"). */
    val overline: TextStyle = driveTextStyle(fontSizeSp = 11, lineHeightSp = 14, fontWeight = FontWeight.Bold, letterSpacingSp = 0.8)

    /** Address lines: never shrunk, always allowed to wrap to two lines (Design.md Section 3). */
    val address: TextStyle = driveTextStyle(fontSizeSp = 14, lineHeightSp = 20, fontWeight = FontWeight.Medium)

    /** Supporting address/meta line under an address. */
    val addressMeta: TextStyle = driveTextStyle(fontSizeSp = 12, lineHeightSp = 17, fontWeight = FontWeight.Normal)

    /** Legal/consent copy. */
    val caption: TextStyle = driveTextStyle(fontSizeSp = 12, lineHeightSp = 17, fontWeight = FontWeight.Normal)
}
