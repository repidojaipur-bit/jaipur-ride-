package com.drivepartner.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val DriveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// Reusable Design.md shape tokens
val DriveCardShape = RoundedCornerShape(16.dp)
val DriveChipShape = RoundedCornerShape(999.dp)
val DriveButtonShape = RoundedCornerShape(12.dp)
val DriveSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
