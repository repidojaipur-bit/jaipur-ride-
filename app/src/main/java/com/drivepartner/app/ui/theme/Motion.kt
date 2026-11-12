package com.drivepartner.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Drive Partner — Motion tokens
 *
 * Motion communicates state changes, never decoration (Design.md Section 7). A driver glancing at
 * a phone mounted on a handlebar needs transitions short enough to feel instant but long enough to
 * be tracked: 90–140 ms for feedback, up to 420 ms for surfaces entering the screen.
 *
 * Earnings are never animated as though money is accruing live, so this system deliberately ships
 * no "count-up" token. Countdown rings are driven by authoritative remaining time, not by easing.
 */
@Immutable
data class DriveMotionTokens(
    /** True when the user switched system animations off (animator duration scale = 0). */
    val reducedMotion: Boolean = false
) {
    /** Instant micro-feedback: ripple settle, icon swap, checkbox tick. */
    val instant: Int get() = scaled(INSTANT_MILLIS)

    /** Standard interaction feedback: colour/state change, chip selection. */
    val fast: Int get() = scaled(FAST_MILLIS)

    /** Default surface motion: expanding cards, progress bars. */
    val standard: Int get() = scaled(STANDARD_MILLIS)

    /** Deliberate motion: bottom sheet presentation, navigation transitions. */
    val moderate: Int get() = scaled(MODERATE_MILLIS)

    /** Emphasis motion: the dispatch offer overlay — noticed, never alarming. */
    val slow: Int get() = scaled(SLOW_MILLIS)

    /**
     * Tick interval for re-reading authoritative server countdowns. Deliberately not a motion
     * duration: an offer timer must show real remaining time.
     */
    val countdownTickMillis: Int get() = COUNTDOWN_TICK_MILLIS

    /** Duration in millis, collapsed to zero when reduced motion is requested. */
    fun scaled(baseMillis: Int): Int = if (reducedMotion) 0 else baseMillis

    fun <T> tweenOf(
        durationMillis: Int = standard,
        delayMillis: Int = 0
    ): TweenSpec<T> =
        if (reducedMotion) {
            tween(durationMillis = 0, delayMillis = delayMillis, easing = LinearEasing)
        } else {
            tween(
                durationMillis = durationMillis,
                delayMillis = delayMillis,
                easing = FastOutSlowInEasing
            )
        }

    /**
     * Springs are avoided for driver-critical feedback because they can overshoot; when used
     (sheet settling), reduced motion snaps instead of bouncing.
     */
    fun <T> springOf(
        stiffness: Float = Spring.StiffnessMediumLow,
        dampingRatio: Float = Spring.DampingRatioNoBouncy
    ): SpringSpec<T> =
        SpringSpec(
            dampingRatio = dampingRatio,
            stiffness = if (reducedMotion) Spring.StiffnessHigh else stiffness
        )

    companion object {
        const val INSTANT_MILLIS = 90
        const val FAST_MILLIS = 140
        const val STANDARD_MILLIS = 220
        const val MODERATE_MILLIS = 300
        const val SLOW_MILLIS = 420
        const val COUNTDOWN_TICK_MILLIS = 250

        /** Easing for entering surfaces. */
        val EaseEnter = EaseOut

        /** Easing for reversible/emphasis transitions. */
        val EaseEmphasis = EaseInOut
    }
}

val LocalDriveMotion = staticCompositionLocalOf { DriveMotionTokens() }

object DriveMotion {
    val current: DriveMotionTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalDriveMotion.current
}

/**
 * Detects the system "remove animations" accessibility setting so the whole design system can
 * collapse to zero-duration transitions. Recomputed when the content resolver changes, which is
 * what happens on configuration changes triggered by Developer Options or accessibility settings.
 */
@Composable
fun rememberReducedMotionEnabled(): Boolean {
    val contentResolver = LocalContext.current.contentResolver
    return remember(contentResolver) {
        runCatching {
            Settings.Global.getFloat(
                contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) <= 0f
        }.getOrDefault(false)
    }
}
