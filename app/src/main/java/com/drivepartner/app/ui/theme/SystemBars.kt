package com.drivepartner.app.ui.theme

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Drive Partner — Edge-to-edge & system bar handling
 *
 * The app draws under the status and navigation bars on every supported API level (24–35) and
 * takes responsibility for two things the platform cannot infer:
 *
 * 1. **Icon contrast.** The authenticated shell always shows a brand-blue app bar under the status
 *    bar, so its icons are light. Screens with a light canvas (onboarding, language selection,
 *    legal sheets) opt into dark icons through [DriveStatusBarIcons] — never by guessing.
 * 2. **Scrims.** Android 7.0/7.1 (API 24–25) cannot render a transparent navigation bar, so the
 *    platform adds a translucent scrim there automatically. Nothing in the app hardcodes a bar
 *    colour that could fight that behaviour.
 *
 * Insets themselves are consumed by Compose (`statusBarsPadding`, `navigationBarsPadding`,
 * `imePadding`, `safeDrawingPadding`) so display cutouts and gesture navigation never clip content.
 */
object DriveSystemBarScrims {
    /** Transparent scrim: content shows through, Compose controls icon contrast. */
    val transparent: Int = AndroidColor.TRANSPARENT

    /** Fallback scrim used by the platform on API 24–26 where transparency is unavailable. */
    val navigationFallbackLight: Int = 0xFF16202B.toInt()
    val navigationFallbackDark: Int = 0xFF05090E.toInt()
}

/**
 * Applies icon contrast for the system bars. Called by [DrivePartnerTheme] with the theme-derived
 * defaults; screens with a light canvas call [DriveStatusBarIcons] to override the status bar.
 */
@Composable
fun DriveSystemBarAppearance(
    statusBarLightIcons: Boolean,
    navigationBarLightIcons: Boolean
) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !statusBarLightIcons
            isAppearanceLightNavigationBars = !navigationBarLightIcons
        }
    }
}

/**
 * Opts a screen into dark status bar icons (for light canvases such as onboarding and legal
 * sheets). Pass `darkIcons = false` to restore the light icons used by the app bar shell.
 */
@Composable
fun DriveStatusBarIcons(darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons
    }
}
