package com.drivepartner.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Drive Partner — Brand Colour Primitives
 *
 * Single source of truth for raw hex values. Feature modules must never reference these
 * directly; they consume the semantic tokens in [DriveColorTokens] (via `DriveColors.current`)
 * so that light/dark theming stays centralized and brandable (Design.md Section 12).
 *
 * Every value below is an *original* brand decision for Drive Partner — no competitor palette
 * is copied. Contrast ratios quoted in comments were measured with the WCAG 2.1 relative
 * luminance formula and are re-asserted by `DesignTokensTest`.
 */

// ---------------------------------------------------------------------------
// Brand Primary — #1769AA (approved design direction)
// ---------------------------------------------------------------------------
val DriveBluePrimary = Color(0xFF1769AA) // 5.77:1 with white text
val DriveBlueDark = Color(0xFF124B7A) // pressed / strong emphasis, 9.08:1 with white
val DriveBlueLight = Color(0xFF2878C7) // graphic accent only
val DriveInfoBlue = Color(0xFF2878C7)
val DriveBlueContainer = Color(0xFFE3F0FB)
val DriveOnBlueContainer = Color(0xFF0D3A61) // 10.10:1 on container
val DriveBlueSurface = Color(0xFFF3F9FD) // faint brand wash for selected rows

// ---------------------------------------------------------------------------
// Success / Online — #16A36A (reserved exclusively for online & confirmed states)
// ---------------------------------------------------------------------------
val DriveGreenOnline = Color(0xFF16A36A) // 3.24:1 with white → graphics + large text only
val DriveGreenDark = Color(0xFF0F7A4F) // 5.36:1 with white → text & filled actions
val DriveGreenAction = Color(0xFF12875A) // 4.53:1 with white → WCAG AA filled Accept button
val DriveGreenContainer = Color(0xFFE6F6EF)
val DriveOnGreenContainer = Color(0xFF0B5C3B) // 7.21:1 on container

// ---------------------------------------------------------------------------
// Warning / Payout highlight — #F5A623 (never used as body text on light surfaces)
// ---------------------------------------------------------------------------
val DriveWarningAmber = Color(0xFFF5A623) // 2.03:1 with white → graphic fills only
val DriveWarningText = Color(0xFF8A5A06) // 5.92:1 on white → warning text
val DriveOnWarning = Color(0xFF17202A) // 8.12:1 on amber fill
val DriveYellowEarning = Color(0xFFF5A623)
val DriveYellowGold = Color(0xFFF5A623)
val DriveYellowContainer = Color(0xFFFEF3DC)
val DriveOnYellowContainer = Color(0xFF7A4F04) // 6.47:1 on container

// ---------------------------------------------------------------------------
// Neutrals & Surfaces — pure white canvas, #F5F7FA secondary canvas
// ---------------------------------------------------------------------------
val DriveSurfaceWhite = Color(0xFFFFFFFF)
val DriveSurfaceSubtle = Color(0xFFF5F7FA)
val DriveSurfaceLightGrey = Color(0xFFF5F7FA)
val DriveSurfaceRaised = Color(0xFFF0F3F8) // tonal step above the subtle canvas
val DriveSurfaceCard = Color(0xFFFFFFFF)
val DriveSurfaceVariant = Color(0xFFF5F7FA)
val DriveSurfaceBorder = Color(0xFFE7EAF0) // hairline card border / divider
val DriveSurfaceBorderStrong = Color(0xFF7C8798) // 3.64:1 → outlines that carry meaning alone

// ---------------------------------------------------------------------------
// Typography & Ink
// ---------------------------------------------------------------------------
val DriveTextPrimary = Color(0xFF17202A) // 16.45:1 on white (WCAG AAA)
val DriveTextSecondary = Color(0xFF697586) // 4.68:1 on white (WCAG AA)
val DriveTextOnSubtle = Color(0xFF616B7C) // 5.01:1 on #F5F7FA (WCAG AA)
val DriveTextMuted = Color(0xFF94A3B8) // 2.56:1 → decorative / disabled only
val DriveTextDisabled = Color(0xFFB4BDCB) // non-informative disabled ink

// ---------------------------------------------------------------------------
// Danger / SOS / Offline — #DC3545
// ---------------------------------------------------------------------------
val DriveError = Color(0xFFDC3545) // 4.53:1 with white (WCAG AA)
val DriveErrorContainer = Color(0xFFFDECEC)
val DriveOnErrorContainer = Color(0xFF7D1924) // 9.03:1 on container
val DriveOfflineGrey = Color(0xFF697586)
val DriveOfflineContainer = Color(0xFFE7EAF0)
val DriveOnOfflineContainer = Color(0xFF3F4A5A) // 7.45:1 on container

// ---------------------------------------------------------------------------
// Dark Mode ramp — high contrast for night driving (Design.md Section 10)
// ---------------------------------------------------------------------------
val DriveDarkBackground = Color(0xFF0E141B)
val DriveDarkBackgroundDeep = Color(0xFF0A0F14)
val DriveDarkSurface = Color(0xFF16202B)
val DriveDarkSurfaceRaised = Color(0xFF1B2735)
val DriveDarkSurfaceVariant = Color(0xFF1F2B38)
val DriveDarkBorder = Color(0xFF2D3C4E)
val DriveDarkBorderStrong = Color(0xFF5E7186) // 3.28:1 on dark surface
val DriveDarkDivider = Color(0xFF263443)
val DriveDarkTextPrimary = Color(0xFFF1F5F9) // 15.03:1 on dark surface (AAA)
val DriveDarkTextSecondary = Color(0xFFA8B6C6) // 7.97:1 on dark surface (AAA)
val DriveDarkTextOnSubtle = Color(0xFFB6C2D1)
val DriveDarkTextMuted = Color(0xFF8895A8) // 5.42:1 on dark surface
val DriveDarkTextDisabled = Color(0xFF4A5A6C)
val DriveDarkBluePrimary = Color(0xFF5CA8E6)
val DriveDarkBluePressed = Color(0xFF7CBDF0)
val DriveDarkOnBlue = Color(0xFF04121E) // 7.38:1 on dark brand
val DriveDarkBlueContainer = Color(0xFF15385A)
val DriveDarkOnBlueContainer = Color(0xFFD6EAFC)
val DriveDarkGreenOnline = Color(0xFF3ECF8E) // 8.25:1 on dark surface
val DriveDarkOnGreen = Color(0xFF04121E)
val DriveDarkGreenContainer = Color(0xFF12402F)
val DriveDarkOnGreenContainer = Color(0xFFC9F5E1)
val DriveDarkWarning = Color(0xFFFBC65F) // 10.46:1 on dark surface
val DriveDarkOnWarning = Color(0xFF2A1B04)
val DriveDarkWarningContainer = Color(0xFF4A3208)
val DriveDarkOnWarningContainer = Color(0xFFFBE7C2)
val DriveDarkError = Color(0xFFFF7C89) // 6.66:1 on dark surface
val DriveDarkOnError = Color(0xFF2A0A0E)
val DriveDarkErrorContainer = Color(0xFF4E1A22)
val DriveDarkOnErrorContainer = Color(0xFFFFDADF)
val DriveDarkNeutral = Color(0xFF93A0B2)
val DriveDarkNeutralContainer = Color(0xFF1F2B38)
val DriveDarkOnNeutralContainer = Color(0xFFD3DCE8)

// ---------------------------------------------------------------------------
// Map cartography — original neutral "daylight" and "night" map styling.
// Used by the professional map surface so overlays never fight the UI chrome.
// ---------------------------------------------------------------------------
val DriveMapLightLand = Color(0xFFEFF3F8)
val DriveMapLightBlock = Color(0xFFE5EBF3)
val DriveMapLightPark = Color(0xFFDCEFDF)
val DriveMapLightWater = Color(0xFFCDE3F6)
val DriveMapLightRoad = Color(0xFFFFFFFF)
val DriveMapLightRoadMajor = Color(0xFFFDF1D8)
val DriveMapLightLabel = Color(0xFF697586)

val DriveMapDarkLand = Color(0xFF111A24)
val DriveMapDarkBlock = Color(0xFF17222E)
val DriveMapDarkPark = Color(0xFF14291F)
val DriveMapDarkWater = Color(0xFF14324A)
val DriveMapDarkRoad = Color(0xFF2C3B4C)
val DriveMapDarkRoadMajor = Color(0xFF3D4A57)
val DriveMapDarkLabel = Color(0xFFA8B6C6)

// Shared map semantics (theme independent)
val DriveMapRoute = Color(0xFF1769AA)
val DriveMapRouteDark = Color(0xFF5CA8E6)
val DriveMapPickup = Color(0xFF16A36A)
val DriveMapDrop = Color(0xFFDC3545)
val DriveMapDriverHalo = Color(0x331769AA)
