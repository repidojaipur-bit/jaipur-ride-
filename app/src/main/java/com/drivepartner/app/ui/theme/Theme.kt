package com.drivepartner.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.drivepartner.app.data.model.ThemePreference

/**
 * Drive Partner — Theme entry point
 *
 * One composable wires the whole design system: semantic colour tokens, elevation, motion
 * (including the reduced-motion accessibility setting), the responsive window size class, the
 * Material 3 type scale/shapes and edge-to-edge system bar contrast. Feature code reads tokens
 * through `DriveColors.current` / `MaterialTheme` and therefore supports light mode, dark mode and
 * any future brand re-skin without conditional colour logic.
 *
 * Canvas model (design direction):
 * - [DriveCanvasStyle.PRIMARY] — pure white `#FFFFFF`, used for content and detail screens.
 * - [DriveCanvasStyle.SECONDARY] — `#F5F7FA`, used behind card stacks so white cards separate
 *   through surface contrast plus a hairline border and a subtle shadow.
 */
enum class DriveCanvasStyle { PRIMARY, SECONDARY }

/** Paints the screen canvas. Always the outermost modifier of a screen root. */
@Composable
fun Modifier.driveCanvas(style: DriveCanvasStyle = DriveCanvasStyle.PRIMARY): Modifier {
    val colors = DriveColors.current
    return this.background(if (style == DriveCanvasStyle.SECONDARY) colors.backgroundSubtle else colors.background)
}

/** Full-screen canvas box: the standard root for every screen in the product. */
@Composable
fun DriveCanvas(
    modifier: Modifier = Modifier,
    style: DriveCanvasStyle = DriveCanvasStyle.PRIMARY,
    content: @Composable () -> Unit
) {
    val colors = DriveColors.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (style == DriveCanvasStyle.SECONDARY) colors.backgroundSubtle else colors.background)
    ) {
        content()
    }
}

/**
 * Resolves the theme preference (including SYSTEM) and installs the design system.
 *
 * @param themePreference persisted user choice; SYSTEM follows the platform dark setting.
 */
@Composable
fun DrivePartnerTheme(
    themePreference: ThemePreference = ThemePreference.LIGHT,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    DriveDesignSystem(darkTheme = darkTheme, content = content)
}

/**
 * Installs the design system for an explicit dark/light choice. Use this in previews and in the
 * in-app Design System gallery to render both themes side by side.
 */
@Composable
fun DriveDesignSystem(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkDriveColors else LightDriveColors
    val elevation = if (darkTheme) DarkDriveElevation else LightDriveElevation
    val reducedMotion = rememberReducedMotionEnabled()
    val motion = remember(reducedMotion) { DriveMotionTokens(reducedMotion = reducedMotion) }
    val windowSizeClass = rememberDriveWindowSizeClass()
    val colorScheme = remember(colors) { driveColorSchemeFrom(colors) }

    CompositionLocalProvider(
        LocalDriveColors provides colors,
        LocalDriveElevation provides elevation,
        LocalDriveMotion provides motion,
        LocalDriveWindowSizeClass provides windowSizeClass
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DriveTypography,
            shapes = DriveShapes
        ) {
            // Authenticated shell: light status icons over the brand-blue app bar.
            // Navigation bar icons follow the canvas: dark on the light bottom bar, light in dark mode.
            DriveSystemBarAppearance(
                statusBarLightIcons = true,
                navigationBarLightIcons = darkTheme
            )
            content()
        }
    }
}

/** Maps semantic tokens onto the Material 3 colour roles used by built-in components. */
private fun driveColorSchemeFrom(tokens: DriveColorTokens): ColorScheme =
    if (tokens.isDark) {
        darkColorScheme(
            primary = tokens.brand,
            onPrimary = tokens.onBrand,
            primaryContainer = tokens.brandContainer,
            onPrimaryContainer = tokens.onBrandContainer,
            inversePrimary = DriveBluePrimary,
            secondary = tokens.info,
            onSecondary = tokens.onBrand,
            secondaryContainer = tokens.surfaceTonal,
            onSecondaryContainer = tokens.textPrimary,
            tertiary = tokens.warning,
            onTertiary = tokens.onWarning,
            tertiaryContainer = tokens.warningContainer,
            onTertiaryContainer = tokens.onWarningContainer,
            background = tokens.background,
            onBackground = tokens.textPrimary,
            surface = tokens.surface,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surfaceTonal,
            onSurfaceVariant = tokens.textSecondary,
            surfaceTint = tokens.brand,
            inverseSurface = tokens.surfaceInverse,
            inverseOnSurface = tokens.onSurfaceInverse,
            error = tokens.danger,
            onError = tokens.onDanger,
            errorContainer = tokens.dangerContainer,
            onErrorContainer = tokens.onDangerContainer,
            outline = tokens.borderStrong,
            outlineVariant = tokens.border,
            scrim = DriveScrimOpaque,
            surfaceContainerLowest = tokens.backgroundSubtle,
            surfaceContainerLow = tokens.surfaceSunken,
            surfaceContainer = tokens.surface,
            surfaceContainerHigh = tokens.surfaceRaised,
            surfaceContainerHighest = tokens.surfaceTonal
        )
    } else {
        lightColorScheme(
            primary = tokens.brand,
            onPrimary = tokens.onBrand,
            primaryContainer = tokens.brandContainer,
            onPrimaryContainer = tokens.onBrandContainer,
            inversePrimary = DriveDarkBluePrimary,
            secondary = tokens.info,
            onSecondary = tokens.onBrand,
            secondaryContainer = tokens.surfaceSunken,
            onSecondaryContainer = tokens.textPrimary,
            tertiary = tokens.warning,
            onTertiary = tokens.onWarning,
            tertiaryContainer = tokens.warningContainer,
            onTertiaryContainer = tokens.onWarningContainer,
            background = tokens.background,
            onBackground = tokens.textPrimary,
            surface = tokens.surface,
            onSurface = tokens.textPrimary,
            surfaceVariant = tokens.surfaceSunken,
            onSurfaceVariant = tokens.textSecondary,
            surfaceTint = tokens.brand,
            inverseSurface = tokens.surfaceInverse,
            inverseOnSurface = tokens.onSurfaceInverse,
            error = tokens.danger,
            onError = tokens.onDanger,
            errorContainer = tokens.dangerContainer,
            onErrorContainer = tokens.onDangerContainer,
            outline = tokens.borderStrong,
            outlineVariant = tokens.border,
            scrim = DriveScrimOpaque,
            surfaceContainerLowest = tokens.surface,
            surfaceContainerLow = tokens.surfaceSunken,
            surfaceContainer = tokens.surfaceSunken,
            surfaceContainerHigh = tokens.surfaceTonal,
            surfaceContainerHighest = tokens.surfaceTonal
        )
    }

/** Opaque ink used by platform components that apply their own alpha (drawer/dialog scrims). */
val DriveScrimOpaque = Color(0xFF0B1119)
