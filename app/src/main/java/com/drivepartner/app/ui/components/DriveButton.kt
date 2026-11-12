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
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveButtonShape
import com.drivepartner.app.ui.theme.DriveError
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceWhite

@Composable
fun DrivePrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = DriveBluePrimary,
    contentColor: Color = DriveSurfaceWhite,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DriveSpacing.primaryButtonHeight),
        enabled = enabled,
        shape = DriveButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
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
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DriveSpacing.secondaryButtonHeight),
        enabled = enabled,
        shape = DriveButtonShape,
        border = BorderStroke(DriveSpacing.hairlineBorder, DriveSurfaceBorder),
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
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = DriveSpacing.primaryButtonHeight),
        enabled = enabled,
        shape = DriveButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = DriveError,
            contentColor = DriveSurfaceWhite
        ),
        content = content
    )
}
