package com.drivepartner.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveButtonShape
import com.drivepartner.app.ui.theme.DriveSurfaceBorder
import com.drivepartner.app.ui.theme.DriveSurfaceWhite

/**
 * SecondaryButton - Material 3 OutlinedButton for Drive Partner.
 *
 * Implements:
 * - Light grey border (DriveSurfaceBorder).
 * - Deep blue text (DriveBluePrimary).
 * - Rounded corners (DriveButtonShape, 12dp) consistent with PrimaryButton.
 * - Accessible 52dp touch target height.
 * - Content padding matching PrimaryButton (horizontal = 16.dp, vertical = 12.dp).
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
    borderColor: Color = DriveSurfaceBorder,
    contentColor: Color = DriveBluePrimary,
    containerColor: Color = DriveSurfaceWhite,
    shape: Shape = DriveButtonShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled && !isLoading,
        shape = shape,
        border = BorderStroke(1.5.dp, if (enabled) borderColor else borderColor.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContentColor = contentColor.copy(alpha = 0.4f)
        ),
        contentPadding = contentPadding
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = contentColor,
                strokeWidth = 2.5.dp
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = contentColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.1.sp
                )
            }
        }
    }
}

/**
 * Slot-based SecondaryButton overload allowing custom Composable content.
 */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = DriveSurfaceBorder,
    contentColor: Color = DriveBluePrimary,
    containerColor: Color = DriveSurfaceWhite,
    shape: Shape = DriveButtonShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        enabled = enabled,
        shape = shape,
        border = BorderStroke(1.5.dp, if (enabled) borderColor else borderColor.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContentColor = contentColor.copy(alpha = 0.4f)
        ),
        contentPadding = contentPadding,
        content = content
    )
}
