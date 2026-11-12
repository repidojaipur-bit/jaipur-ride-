package com.drivepartner.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.drivepartner.app.ui.theme.DriveButtonShape
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveSpacing

/**
 * Drive Partner buttons.
 *
 * Rules enforced here rather than per screen:
 * - minimum height 52 dp (primary) / 48 dp (secondary) — gloved, one-handed use,
 * - full-width by default so thumb reach is predictable at the bottom of a screen,
 * - pressed/disabled colours come from semantic tokens so both themes behave,
 * - every semantic variant pairs its container with an accessible content colour
 *   (white on `#1769AA` = 5.77:1, white on `#12875A` = 4.53:1, white on `#DC3545` = 4.53:1).
 */
@Composable
fun DrivePrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = DriveColors.current.brand,
    contentColor: Color = DriveColors.current.onBrand,
    content: @Composable RowScope.() -> Unit
) {
    val colors = DriveColors.current
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DriveSpacing.primaryButtonHeight),
        enabled = enabled,
        shape = DriveButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = colors.surfaceTonal,
            disabledContentColor = colors.textDisabled
        ),
        content = content
    )
}

/** Accept / confirm actions that mean money or a commitment. */
@Composable
fun DriveSuccessButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val colors = DriveColors.current
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DriveSpacing.primaryButtonHeight),
        enabled = enabled,
        shape = DriveButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.successStrong,
            contentColor = colors.onSuccess,
            disabledContainerColor = colors.surfaceTonal,
            disabledContentColor = colors.textDisabled
        ),
        content = content
    )
}

@Composable
fun DriveOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val colors = DriveColors.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DriveSpacing.secondaryButtonHeight),
        enabled = enabled,
        shape = DriveButtonShape,
        border = BorderStroke(DriveSpacing.hairlineBorder, colors.borderStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = colors.textPrimary,
            disabledContentColor = colors.textDisabled,
            containerColor = Color.Transparent
        ),
        content = content
    )
}

@Composable
fun DriveDangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val colors = DriveColors.current
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DriveSpacing.primaryButtonHeight),
        enabled = enabled,
        shape = DriveButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.danger,
            contentColor = colors.onDanger,
            disabledContainerColor = colors.surfaceTonal,
            disabledContentColor = colors.textDisabled
        ),
        content = content
    )
}
