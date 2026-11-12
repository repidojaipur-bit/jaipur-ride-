package com.drivepartner.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.drivepartner.app.data.model.ThemePreference

private val LightColorScheme = lightColorScheme(
    primary = DriveBluePrimary,
    onPrimary = DriveSurfaceWhite,
    primaryContainer = DriveBlueContainer,
    onPrimaryContainer = DriveOnBlueContainer,

    secondary = DriveInfoBlue,
    onSecondary = DriveSurfaceWhite,
    secondaryContainer = DriveSurfaceSubtle,
    onSecondaryContainer = DriveTextPrimary,

    tertiary = DriveWarningAmber,
    onTertiary = DriveTextPrimary,
    tertiaryContainer = DriveYellowContainer,
    onTertiaryContainer = DriveOnYellowContainer,

    background = DriveSurfaceSubtle,
    onBackground = DriveTextPrimary,

    surface = DriveSurfaceWhite,
    onSurface = DriveTextPrimary,
    surfaceVariant = DriveSurfaceSubtle,
    onSurfaceVariant = DriveTextSecondary,

    error = DriveError,
    onError = DriveSurfaceWhite,
    errorContainer = DriveErrorContainer,
    onErrorContainer = DriveOnErrorContainer,

    outline = DriveSurfaceBorder,
    outlineVariant = DriveSurfaceSubtle
)

private val DarkColorScheme = darkColorScheme(
    primary = DriveDarkBluePrimary,
    onPrimary = DriveDarkBackground,
    primaryContainer = DriveDarkBlueContainer,
    onPrimaryContainer = DriveDarkOnBlueContainer,

    secondary = DriveBlueLight,
    onSecondary = DriveDarkTextPrimary,
    secondaryContainer = DriveDarkSurfaceVariant,
    onSecondaryContainer = DriveDarkTextPrimary,

    tertiary = DriveWarningAmber,
    onTertiary = DriveDarkBackground,
    tertiaryContainer = DriveOnYellowContainer,
    onTertiaryContainer = DriveYellowContainer,

    background = DriveDarkBackground,
    onBackground = DriveDarkTextPrimary,

    surface = DriveDarkSurface,
    onSurface = DriveDarkTextPrimary,
    surfaceVariant = DriveDarkSurfaceVariant,
    onSurfaceVariant = DriveDarkTextSecondary,

    error = DriveError,
    onError = DriveSurfaceWhite,
    errorContainer = DriveDarkErrorContainer,
    onErrorContainer = DriveDarkOnErrorContainer,

    outline = DriveDarkBorder,
    outlineVariant = DriveDarkSurfaceVariant
)

/**
 * DrivePartnerTheme - Material 3 theme aligned with Design.md tokens.
 * Supports high-contrast daylight mode and optional/system night dark mode.
 */
@Composable
fun DrivePartnerTheme(
    themePreference: ThemePreference = ThemePreference.LIGHT,
    content: @Composable () -> Unit
) {
    val useDark = when (themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = !useDark
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DriveTypography,
        shapes = DriveShapes,
        content = content
    )
}
