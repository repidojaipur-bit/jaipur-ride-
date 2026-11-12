package com.drivepartner.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/**
 * Drive Partner — Shape tokens
 *
 * Design direction: rounded cards with a 14–20 dp radius. The scale is deliberately narrow so the
 * product reads as one system instead of a bag of unrelated corner values:
 *
 * | Token | Radius | Use |
 * |---|---|---|
 * | [DriveShapeRadius.control] | 12 dp | buttons, text fields, small tiles |
 * | [DriveShapeRadius.cardSmall] | 14 dp | list rows, chips wells, document rows |
 * | [DriveShapeRadius.card] | 16 dp | default card baseline |
 * | [DriveShapeRadius.cardLarge] | 20 dp | hero cards: earnings, offer, duty |
 * | [DriveShapeRadius.sheet] | 20 dp top | bottom sheets and drawers |
 * | [DriveShapeRadius.dialog] | 20 dp | dialogs and modals |
 * | [DriveShapeRadius.pill] | fully round | status chips, toggle pills, badges |
 */
object DriveShapeRadius {
    val control = 12.dp
    val controlLarge = 14.dp
    val cardSmall = 14.dp
    val card = 16.dp
    val cardLarge = 20.dp
    val sheet = 20.dp
    val dialog = 20.dp
    val iconWell = 12.dp
    val pill = 999.dp

    /** Radii permitted for cards by the design direction — asserted by `DesignTokensTest`. */
    val cardRadiusRange = 14.dp..20.dp
}

/** Material 3 shape scale mapped onto the Drive Partner radius band. */
val DriveShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(DriveShapeRadius.cardSmall),
    large = RoundedCornerShape(DriveShapeRadius.card),
    extraLarge = RoundedCornerShape(DriveShapeRadius.cardLarge)
)

// Reusable shape tokens (names retained for existing call sites)
val DriveCardShape = RoundedCornerShape(DriveShapeRadius.card)
val DriveCardSmallShape = RoundedCornerShape(DriveShapeRadius.cardSmall)
val DriveCardLargeShape = RoundedCornerShape(DriveShapeRadius.cardLarge)
val DriveChipShape = RoundedCornerShape(DriveShapeRadius.pill)
val DrivePillShape = RoundedCornerShape(DriveShapeRadius.pill)
val DriveButtonShape = RoundedCornerShape(DriveShapeRadius.control)
val DriveInputShape = RoundedCornerShape(DriveShapeRadius.control)
val DriveIconWellShape = RoundedCornerShape(DriveShapeRadius.iconWell)
val DriveDialogShape = RoundedCornerShape(DriveShapeRadius.dialog)
val DriveSheetShape = RoundedCornerShape(
    topStart = DriveShapeRadius.sheet,
    topEnd = DriveShapeRadius.sheet
)

/** Full-bleed surfaces (maps, dividers) intentionally opt out of rounding. */
val DriveSquareShape = RectangleShape
