package com.drivepartner.app.ui.illustrations

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.drivepartner.app.ui.theme.DriveCardLargeShape
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveSpacing

/**
 * Drive Partner — Original vector illustrations.
 *
 * Every illustration is drawn as code on a 100 x 100 unit grid with the same optical language as
 * the icon set (round caps, round joins, ~2.2 unit stroke), so empty states, onboarding and error
 * screens read as one product. Nothing here is a bitmap, a screenshot or stock art
 * (Design.md Section 8): the artwork scales losslessly from a 3" phone to a tablet and recolors
 * itself from semantic tokens in light and dark mode.
 *
 * The grid is centred inside the canvas and scaled by the smallest dimension, so a wide canvas
 * simply adds breathing room instead of stretching the artwork. Illustrations are decorative:
 * the title/subtitle beside them carries the meaning for TalkBack.
 */

/** Maps the 100-unit artwork grid onto the draw scope, centred on both axes. */
internal class DriveIllustrationGrid(private val scope: DrawScope) {
    val unit: Float = scope.size.minDimension / 100f
    private val offsetX: Float = (scope.size.width - 100f * unit) / 2f
    private val offsetY: Float = (scope.size.height - 100f * unit) / 2f

    fun pt(x: Float, y: Float): Offset = Offset(offsetX + x * unit, offsetY + y * unit)

    fun path(block: GridPath.() -> Unit): Path = GridPath(this).apply(block).path
}

internal class GridPath(private val grid: DriveIllustrationGrid) {
    val path = Path()
    fun moveTo(x: Float, y: Float) {
        val p = grid.pt(x, y)
        path.moveTo(p.x, p.y)
    }

    fun lineTo(x: Float, y: Float) {
        val p = grid.pt(x, y)
        path.lineTo(p.x, p.y)
    }

    fun quadTo(cx: Float, cy: Float, x: Float, y: Float) {
        val c = grid.pt(cx, cy)
        val e = grid.pt(x, y)
        path.quadraticTo(c.x, c.y, e.x, e.y)
    }

    fun close() = path.close()
}

private fun DrawScope.grid(): DriveIllustrationGrid = DriveIllustrationGrid(this)

private fun DrawScope.dashEffect(): PathEffect =
    PathEffect.dashPathEffect(floatArrayOf(4.5f * grid().unit, 4.5f * grid().unit), 0f)

private fun DrawScope.strokeStyle(color: Color, widthUnits: Float = 2.2f, dashed: Boolean = false): DrawStyle =
    Stroke(
        width = widthUnits * grid().unit,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
        pathEffect = if (dashed) dashEffect() else null
    )

private fun DrawScope.seg(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    color: Color,
    width: Float = 2.2f,
    dashed: Boolean = false
) {
    val g = grid()
    drawLine(
        color = color,
        start = g.pt(x1, y1),
        end = g.pt(x2, y2),
        strokeWidth = width * g.unit,
        cap = StrokeCap.Round,
        pathEffect = if (dashed) dashEffect() else null
    )
}

private fun DrawScope.ring(x: Float, y: Float, r: Float, color: Color, width: Float = 2.2f) {
    val g = grid()
    drawCircle(
        color = color,
        radius = r * g.unit,
        center = g.pt(x, y),
        style = strokeStyle(color, width)
    )
}

private fun DrawScope.dot(x: Float, y: Float, r: Float, color: Color) {
    val g = grid()
    drawCircle(color = color, radius = r * g.unit, center = g.pt(x, y), style = Fill)
}

/** Side-view two-wheeler silhouette shared across the "no work" family of illustrations. */
private fun DrawScope.scooter(color: Color, width: Float = 2.4f) {
    ring(30f, 72f, 10f, color, width)
    ring(74f, 72f, 10f, color, width)
    seg(36f, 63f, 58f, 63f, color, width)
    seg(36f, 63f, 30f, 72f, color, width)
    seg(58f, 63f, 74f, 72f, color, width)
    seg(40f, 63f, 36f, 48f, color, width)
    seg(29f, 48f, 43f, 48f, color, width)
    seg(62f, 63f, 70f, 42f, color, width)
    seg(64f, 40f, 77f, 40f, color, width)
}

/** Rounded shield outline used by safety & verification artwork. */
private fun DriveIllustrationGrid.shield(): Path = path {
    moveTo(50f, 7f)
    lineTo(83f, 19f)
    lineTo(83f, 47f)
    quadTo(83f, 78f, 50f, 93f)
    quadTo(17f, 78f, 17f, 47f)
    lineTo(17f, 19f)
    close()
}

// ---------------------------------------------------------------------------
// Illustrations
// ---------------------------------------------------------------------------

/**
 * "No orders yet": a parked scooter beside a dashed route ending in an empty pickup pin.
 * Used by the dispatch queue and order history empty states.
 */
@Composable
fun DriveEmptyOrdersIllustration(modifier: Modifier = Modifier) {
    val colors = DriveColors.current
    Canvas(modifier = modifier.aspectRatio(1.25f)) {
        val g = grid()
        dot(50f, 52f, 40f, colors.brand.copy(alpha = if (colors.isDark) 0.10f else 0.06f))
        dot(50f, 52f, 29f, colors.brand.copy(alpha = if (colors.isDark) 0.14f else 0.08f))
        seg(8f, 86f, 92f, 86f, colors.border, 2.0f)
        val route = g.path {
            moveTo(58f, 78f)
            quadTo(78f, 74f, 84f, 56f)
        }
        drawPath(route, color = colors.brand, style = strokeStyle(colors.brand, 2.0f, dashed = true))
        ring(84f, 46f, 7f, colors.success)
        dot(84f, 46f, 2.4f, colors.success)
        scooter(colors.textSecondary)
        val idle = g.path {
            moveTo(16f, 28f)
            quadTo(24f, 22f, 32f, 28f)
        }
        drawPath(idle, color = colors.textDisabled, style = strokeStyle(colors.textDisabled, 2.0f))
    }
}

/**
 * "Offline": the duty power ring, open at the top, around a parked scooter — the offline state
 * without alarm colour.
 */
@Composable
fun DriveOfflineIllustration(modifier: Modifier = Modifier) {
    val colors = DriveColors.current
    Canvas(modifier = modifier.aspectRatio(1.25f)) {
        val g = grid()
        dot(50f, 50f, 38f, colors.neutral.copy(alpha = if (colors.isDark) 0.16f else 0.08f))
        drawArc(
            color = colors.neutral,
            startAngle = -60f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = g.pt(14f, 12f),
            size = Size(72f * g.unit, 72f * g.unit),
            style = strokeStyle(colors.neutral, 2.6f)
        )
        seg(50f, 20f, 50f, 34f, colors.neutral, 2.6f)
        seg(10f, 88f, 90f, 88f, colors.border, 2.0f)
        scooter(colors.textSecondary)
    }
}

/**
 * "Verification pending": a shield holding a document with a clock badge — KYC under review.
 */
@Composable
fun DriveVerificationIllustration(modifier: Modifier = Modifier) {
    val colors = DriveColors.current
    Canvas(modifier = modifier.aspectRatio(1.1f)) {
        val g = grid()
        dot(50f, 50f, 40f, colors.brand.copy(alpha = if (colors.isDark) 0.10f else 0.06f))
        drawPath(g.shield(), color = colors.brand, style = strokeStyle(colors.brand, 2.6f))
        seg(36f, 36f, 64f, 36f, colors.textSecondary, 2.2f)
        seg(36f, 46f, 64f, 46f, colors.textSecondary, 2.2f)
        seg(36f, 56f, 52f, 56f, colors.textSecondary, 2.2f)
        dot(74f, 70f, 11f, colors.warningContainer)
        ring(74f, 70f, 11f, colors.warning, 2.2f)
        seg(74f, 65f, 74f, 70f, colors.warningStrong, 2.2f)
        seg(74f, 70f, 79f, 73f, colors.warningStrong, 2.2f)
    }
}

/**
 * "Wallet & payouts": a banknote carrying the rupee mark, a coin stack and a settled trend.
 */
@Composable
fun DriveWalletIllustration(modifier: Modifier = Modifier) {
    val colors = DriveColors.current
    Canvas(modifier = modifier.aspectRatio(1.25f)) {
        val g = grid()
        dot(50f, 50f, 38f, colors.success.copy(alpha = if (colors.isDark) 0.12f else 0.07f))
        val note = g.path {
            moveTo(14f, 34f)
            lineTo(66f, 34f)
            quadTo(70f, 34f, 70f, 38f)
            lineTo(70f, 62f)
            quadTo(70f, 66f, 66f, 66f)
            lineTo(14f, 66f)
            quadTo(10f, 66f, 10f, 62f)
            lineTo(10f, 38f)
            quadTo(10f, 34f, 14f, 34f)
            close()
        }
        drawPath(note, color = colors.success, style = strokeStyle(colors.success, 2.4f))
        seg(24f, 43f, 42f, 43f, colors.textPrimary, 2.2f)
        seg(24f, 50f, 42f, 50f, colors.textPrimary, 2.2f)
        val rupee = g.path {
            moveTo(38f, 43f)
            quadTo(38f, 52f, 26f, 52f)
            lineTo(42f, 58f)
        }
        drawPath(rupee, color = colors.textPrimary, style = strokeStyle(colors.textPrimary, 2.2f))
        ring(56f, 50f, 7f, colors.warning, 2.2f)
        seg(46f, 78f, 92f, 78f, colors.border, 2.0f)
        ring(60f, 74f, 4f, colors.warning, 2.0f)
        ring(70f, 74f, 4f, colors.warning, 2.0f)
        ring(80f, 74f, 4f, colors.warning, 2.0f)
        val trend = g.path {
            moveTo(74f, 44f)
            lineTo(82f, 36f)
            lineTo(88f, 41f)
            lineTo(94f, 30f)
        }
        drawPath(trend, color = colors.successStrong, style = strokeStyle(colors.successStrong, 2.2f))
    }
}

/**
 * "Something went wrong": a rain cloud with a warning triangle — informative, never alarming.
 */
@Composable
fun DriveErrorIllustration(modifier: Modifier = Modifier) {
    val colors = DriveColors.current
    Canvas(modifier = modifier.aspectRatio(1.25f)) {
        val g = grid()
        dot(50f, 46f, 36f, colors.danger.copy(alpha = if (colors.isDark) 0.12f else 0.06f))
        dot(38f, 40f, 13f, colors.surfaceTonal)
        dot(58f, 36f, 16f, colors.surfaceTonal)
        val cloud = g.path {
            moveTo(20f, 56f)
            quadTo(20f, 46f, 30f, 45f)
            quadTo(34f, 30f, 50f, 30f)
            quadTo(66f, 30f, 70f, 43f)
            quadTo(82f, 44f, 82f, 56f)
            close()
        }
        drawPath(cloud, color = colors.textSecondary, style = strokeStyle(colors.textSecondary, 2.4f))
        seg(32f, 66f, 29f, 74f, colors.textDisabled, 2.2f)
        seg(72f, 66f, 69f, 74f, colors.textDisabled, 2.2f)
        dot(58f, 78f, 13f, colors.dangerContainer)
        val triangle = g.path {
            moveTo(58f, 62f)
            lineTo(74f, 88f)
            lineTo(42f, 88f)
            close()
        }
        drawPath(triangle, color = colors.danger, style = strokeStyle(colors.danger, 2.4f))
        seg(58f, 72f, 58f, 79f, colors.dangerStrong, 2.4f)
        dot(58f, 83.5f, 1.4f, colors.dangerStrong)
    }
}

/**
 * "Safety centre": a shield cradling a heart — the SOS and safety screens.
 */
@Composable
fun DriveSafetyIllustration(modifier: Modifier = Modifier) {
    val colors = DriveColors.current
    Canvas(modifier = modifier.aspectRatio(1.1f)) {
        val g = grid()
        dot(50f, 50f, 40f, colors.danger.copy(alpha = if (colors.isDark) 0.10f else 0.05f))
        drawPath(g.shield(), color = colors.brand, style = strokeStyle(colors.brand, 2.6f))
        val heart = g.path {
            moveTo(50f, 66f)
            quadTo(34f, 54f, 36f, 44f)
            quadTo(38f, 36f, 46f, 38f)
            quadTo(50f, 39f, 50f, 44f)
            quadTo(50f, 39f, 54f, 38f)
            quadTo(62f, 36f, 64f, 44f)
            quadTo(66f, 54f, 50f, 66f)
            close()
        }
        drawPath(heart, color = colors.danger, style = strokeStyle(colors.danger, 2.4f))
        seg(8f, 26f, 14f, 31f, colors.textDisabled, 2.0f)
        seg(92f, 26f, 86f, 31f, colors.textDisabled, 2.0f)
    }
}

/**
 * The Drive Partner brand mark: a rounded shield carrying two forward route chevrons.
 * Original artwork, drawn from code so it tints correctly on any surface (splash, drawer, About).
 */
@Composable
fun DriveBrandMark(
    modifier: Modifier = Modifier,
    shieldColor: Color? = null,
    markColor: Color? = null
) {
    val colors = DriveColors.current
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val g = grid()
        val shield = g.path {
            moveTo(50f, 4f)
            lineTo(86f, 17f)
            lineTo(86f, 48f)
            quadTo(86f, 82f, 50f, 96f)
            quadTo(14f, 82f, 14f, 48f)
            lineTo(14f, 17f)
            close()
        }
        drawPath(shield, color = shieldColor ?: colors.brand, style = Fill)
        val chevron1 = g.path {
            moveTo(38f, 34f)
            lineTo(56f, 50f)
            lineTo(38f, 66f)
        }
        val chevron2 = g.path {
            moveTo(54f, 38f)
            lineTo(66f, 50f)
            lineTo(54f, 62f)
        }
        val mark = markColor ?: colors.onBrand
        drawPath(chevron1, color = mark, style = Stroke(6f * g.unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(
            chevron2,
            color = mark.copy(alpha = 0.65f),
            style = Stroke(6f * g.unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/**
 * A tinted rounded plate that presents an illustration inside a card, keeping artwork visually
 * separated from text while staying transparent to dark mode.
 */
@Composable
fun DriveIllustrationPlate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = DriveColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(DriveCardLargeShape)
            .background(colors.tintedWell(colors.brand))
            .padding(DriveSpacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.fillMaxWidth(0.85f)) {
            content()
        }
    }
}
