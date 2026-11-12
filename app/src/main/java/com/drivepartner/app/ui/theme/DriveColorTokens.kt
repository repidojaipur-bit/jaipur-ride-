package com.drivepartner.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Drive Partner — Semantic Colour Token Layer
 *
 * The primitives in `Color.kt` are raw brand values. Components must consume the *semantic*
 * tokens below, which resolve automatically for light and dark themes. This is what makes
 * "Dark mode support" real rather than aspirational: a card that paints `DriveColors.current.surface`
 * is correct in both themes without a single `if (isDark)` branch in feature code.
 *
 * Naming rule: tokens describe a *role* (`textSecondary`, `successContainer`), never a colour
 * (`greyText`, `greenBox`), so the brand can be re-skinned without touching feature modules
 * (Design.md Section 12 — brand configuration is centralized).
 *
 * Accessibility contract (verified by `DesignTokensTest`):
 * - `textPrimary` / `textSecondary` / `textOnSubtle` reach WCAG AA (4.5:1) on their intended canvas.
 * - Semantic accent colours used as *text* always have a `*Strong` or `on*Container` partner.
 * - Status is never carried by colour alone — every status token pairs with text + icon.
 */
@Immutable
data class DriveColorTokens(
    /** True when these tokens describe the night/dark theme. */
    val isDark: Boolean,

    // -- Brand ------------------------------------------------------------
    val brand: Color,
    val brandPressed: Color,
    val brandSubtle: Color,
    val onBrand: Color,
    val brandContainer: Color,
    val onBrandContainer: Color,
    val brandText: Color,

    // -- Canvas & surfaces ------------------------------------------------
    /** Primary background of the application canvas. */
    val background: Color,
    /** Secondary background used for grouped/sunken regions and screen scaffolds. */
    val backgroundSubtle: Color,
    /** Card and sheet surface. */
    val surface: Color,
    /** Surface floating above content (sticky bars, FAB wells, dialog bodies). */
    val surfaceRaised: Color,
    /** Recessed surface: input wells, empty-state plates, inset strips. */
    val surfaceSunken: Color,
    /** Tonal step used for hover/pressed rows and segmented controls. */
    val surfaceTonal: Color,
    val onSurface: Color,
    val surfaceInverse: Color,
    val onSurfaceInverse: Color,

    // -- Structure --------------------------------------------------------
    val border: Color,
    val borderStrong: Color,
    val divider: Color,

    // -- Ink --------------------------------------------------------------
    val textPrimary: Color,
    val textSecondary: Color,
    /** Tertiary ink for meta text and unselected controls; AA on both canvases. */
    val textTertiary: Color,
    /** Secondary ink guaranteed AA on [backgroundSubtle]. */
    val textOnSubtle: Color,
    val textLink: Color,
    /** Decorative/disabled ink. Never used for information a driver must read. */
    val textDisabled: Color,
    val iconPrimary: Color,
    val iconSecondary: Color,
    val iconOnBrand: Color,

    // -- Success / Online -------------------------------------------------
    val success: Color,
    val successStrong: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,

    // -- Warning ----------------------------------------------------------
    val warning: Color,
    val warningStrong: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,

    // -- Danger / SOS -----------------------------------------------------
    val danger: Color,
    val dangerStrong: Color,
    val onDanger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,

    // -- Info -------------------------------------------------------------
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,

    // -- Neutral / Offline ------------------------------------------------
    val neutral: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,

    // -- Overlays ---------------------------------------------------------
    val scrim: Color,
    val shadow: Color,
    val shimmerBase: Color,
    val shimmerHighlight: Color,

    // -- Map cartography --------------------------------------------------
    val mapLand: Color,
    val mapBlock: Color,
    val mapPark: Color,
    val mapWater: Color,
    val mapRoad: Color,
    val mapRoadMajor: Color,
    val mapLabel: Color,
    val mapRoute: Color,
    val mapPickup: Color,
    val mapDrop: Color,
    val mapDriverHalo: Color
) {
    /**
     * Pressed/ripple overlay for a given container. Keeps feedback visible on white cards
     * without introducing a second hardcoded alpha per component.
     */
    fun pressedOverlay(on: Color): Color = on.copy(alpha = if (isDark) 0.12f else 0.08f)

    /** Subtle tint of [accent] suitable for icon wells and selected rows. */
    fun tintedWell(accent: Color): Color =
        if (isDark) lerp(surfaceRaised, accent, 0.18f) else lerp(surface, accent, 0.10f)
}

/** Approved light tokens — the design direction specified for Drive Partner. */
val LightDriveColors = DriveColorTokens(
    isDark = false,

    brand = DriveBluePrimary,
    brandPressed = DriveBlueDark,
    brandSubtle = DriveBlueSurface,
    onBrand = DriveSurfaceWhite,
    brandContainer = DriveBlueContainer,
    onBrandContainer = DriveOnBlueContainer,
    brandText = DriveBluePrimary,

    background = DriveSurfaceWhite,
    backgroundSubtle = DriveSurfaceSubtle,
    surface = DriveSurfaceWhite,
    surfaceRaised = DriveSurfaceWhite,
    surfaceSunken = DriveSurfaceSubtle,
    surfaceTonal = DriveSurfaceRaised,
    onSurface = DriveTextPrimary,
    surfaceInverse = DriveTextPrimary,
    onSurfaceInverse = DriveSurfaceWhite,

    border = DriveSurfaceBorder,
    borderStrong = DriveSurfaceBorderStrong,
    divider = DriveSurfaceBorder,

    textPrimary = DriveTextPrimary,
    textSecondary = DriveTextSecondary,
    textTertiary = Color(0xFF656F80),
    textOnSubtle = DriveTextOnSubtle,
    textLink = DriveBlueDark,
    textDisabled = DriveTextDisabled,
    iconPrimary = DriveTextPrimary,
    iconSecondary = DriveTextSecondary,
    iconOnBrand = DriveSurfaceWhite,

    success = DriveGreenOnline,
    successStrong = DriveGreenAction,
    onSuccess = DriveSurfaceWhite,
    successContainer = DriveGreenContainer,
    onSuccessContainer = DriveOnGreenContainer,

    warning = DriveWarningAmber,
    warningStrong = DriveWarningText,
    onWarning = DriveOnWarning,
    warningContainer = DriveYellowContainer,
    onWarningContainer = DriveOnYellowContainer,

    danger = DriveError,
    dangerStrong = DriveError,
    onDanger = DriveSurfaceWhite,
    dangerContainer = DriveErrorContainer,
    onDangerContainer = DriveOnErrorContainer,

    info = DriveInfoBlue,
    infoContainer = DriveBlueContainer,
    onInfoContainer = DriveOnBlueContainer,

    neutral = DriveOfflineGrey,
    neutralContainer = DriveOfflineContainer,
    onNeutralContainer = DriveOnOfflineContainer,

    scrim = Color(0x7317202A),
    shadow = Color(0xFF17202A),
    shimmerBase = DriveSurfaceSubtle,
    shimmerHighlight = Color(0xFFFFFFFF),

    mapLand = DriveMapLightLand,
    mapBlock = DriveMapLightBlock,
    mapPark = DriveMapLightPark,
    mapWater = DriveMapLightWater,
    mapRoad = DriveMapLightRoad,
    mapRoadMajor = DriveMapLightRoadMajor,
    mapLabel = DriveMapLightLabel,
    mapRoute = DriveMapRoute,
    mapPickup = DriveMapPickup,
    mapDrop = DriveMapDrop,
    mapDriverHalo = DriveMapDriverHalo
)

/** Night tokens — tuned for low-glare cabin/night driving, all text pairs ≥ 4.5:1. */
val DarkDriveColors = DriveColorTokens(
    isDark = true,

    brand = DriveDarkBluePrimary,
    brandPressed = DriveDarkBluePressed,
    brandSubtle = Color(0xFF13283C),
    onBrand = DriveDarkOnBlue,
    brandContainer = DriveDarkBlueContainer,
    onBrandContainer = DriveDarkOnBlueContainer,
    brandText = DriveDarkBluePrimary,

    background = DriveDarkBackground,
    backgroundSubtle = DriveDarkBackgroundDeep,
    surface = DriveDarkSurface,
    surfaceRaised = DriveDarkSurfaceRaised,
    surfaceSunken = Color(0xFF101820),
    surfaceTonal = DriveDarkSurfaceVariant,
    onSurface = DriveDarkTextPrimary,
    surfaceInverse = DriveDarkTextPrimary,
    onSurfaceInverse = DriveDarkBackground,

    border = DriveDarkBorder,
    borderStrong = DriveDarkBorderStrong,
    divider = DriveDarkDivider,

    textPrimary = DriveDarkTextPrimary,
    textSecondary = DriveDarkTextSecondary,
    textTertiary = DriveDarkTextMuted,
    textOnSubtle = DriveDarkTextOnSubtle,
    textLink = DriveDarkBluePrimary,
    textDisabled = DriveDarkTextDisabled,
    iconPrimary = DriveDarkTextPrimary,
    iconSecondary = DriveDarkTextSecondary,
    iconOnBrand = DriveDarkOnBlue,

    success = DriveDarkGreenOnline,
    successStrong = DriveDarkGreenOnline,
    onSuccess = DriveDarkOnGreen,
    successContainer = DriveDarkGreenContainer,
    onSuccessContainer = DriveDarkOnGreenContainer,

    warning = DriveDarkWarning,
    warningStrong = DriveDarkWarning,
    onWarning = DriveDarkOnWarning,
    warningContainer = DriveDarkWarningContainer,
    onWarningContainer = DriveDarkOnWarningContainer,

    danger = DriveDarkError,
    dangerStrong = DriveDarkError,
    onDanger = DriveDarkOnError,
    dangerContainer = DriveDarkErrorContainer,
    onDangerContainer = DriveDarkOnErrorContainer,

    info = DriveDarkBluePrimary,
    infoContainer = DriveDarkBlueContainer,
    onInfoContainer = DriveDarkOnBlueContainer,

    neutral = DriveDarkNeutral,
    neutralContainer = DriveDarkNeutralContainer,
    onNeutralContainer = DriveDarkOnNeutralContainer,

    scrim = Color(0x99050A10),
    shadow = Color(0xFF000000),
    shimmerBase = DriveDarkSurfaceVariant,
    shimmerHighlight = Color(0xFF2D3C4E),

    mapLand = DriveMapDarkLand,
    mapBlock = DriveMapDarkBlock,
    mapPark = DriveMapDarkPark,
    mapWater = DriveMapDarkWater,
    mapRoad = DriveMapDarkRoad,
    mapRoadMajor = DriveMapDarkRoadMajor,
    mapLabel = DriveMapDarkLabel,
    mapRoute = DriveMapRouteDark,
    mapPickup = DriveDarkGreenOnline,
    mapDrop = DriveDarkError,
    mapDriverHalo = Color(0x405CA8E6)
)

/** CompositionLocal carrying the resolved semantic palette. */
val LocalDriveColors = staticCompositionLocalOf { LightDriveColors }

/**
 * Convenient read access to the active semantic palette:
 * `val colors = DriveColors.current`
 */
object DriveColors {
    val current: DriveColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalDriveColors.current
}
