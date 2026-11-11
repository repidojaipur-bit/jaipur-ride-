package com.drivepartner.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = DriveBluePrimary,
    onPrimary = DriveSurfaceWhite,
    primaryContainer = DriveBlueContainer,
    onPrimaryContainer = DriveOnBlueContainer,

    secondary = DriveBlueLight,
    onSecondary = DriveSurfaceWhite,
    secondaryContainer = DriveSurfaceLightGrey,
    onSecondaryContainer = DriveTextPrimary,

    tertiary = DriveYellowEarning,
    onTertiary = DriveSurfaceWhite,
    tertiaryContainer = DriveYellowContainer,
    onTertiaryContainer = DriveOnYellowContainer,

    background = DriveSurfaceWhite,
    onBackground = DriveTextPrimary,

    surface = DriveSurfaceWhite,
    onSurface = DriveTextPrimary,
    surfaceVariant = DriveSurfaceLightGrey,
    onSurfaceVariant = DriveTextSecondary,

    error = DriveError,
    onError = DriveSurfaceWhite,
    errorContainer = DriveErrorContainer,
    onErrorContainer = DriveOnErrorContainer,

    outline = DriveSurfaceBorder,
    outlineVariant = DriveSurfaceLightGrey
)

/**
 * DrivePartnerTheme - Professional Material 3 theme tailored for driver partner application.
 * Retains high-contrast readability under bright daylight and dashboard mounted conditions.
 */
@Composable
fun DrivePartnerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Driver applications prioritize high daytime visibility with a crisp white/light-grey palette
    val colorScheme = LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = DriveBluePrimary.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
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
