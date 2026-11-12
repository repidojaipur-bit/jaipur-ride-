package com.drivepartner.app.ui.screens.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drivepartner.app.R
import com.drivepartner.app.ui.components.DriveCard
import com.drivepartner.app.ui.components.DriveDangerButton
import com.drivepartner.app.ui.components.DriveHeroCard
import com.drivepartner.app.ui.components.DriveIcon
import com.drivepartner.app.ui.components.DriveIconWell
import com.drivepartner.app.ui.components.DriveOutlinedButton
import com.drivepartner.app.ui.components.DrivePrimaryButton
import com.drivepartner.app.ui.components.DriveSuccessButton
import com.drivepartner.app.ui.components.DriveSunkenCard
import com.drivepartner.app.ui.components.EmptyState
import com.drivepartner.app.ui.components.ErrorState
import com.drivepartner.app.ui.components.LoadingIndicator
import com.drivepartner.app.ui.components.StatusChip
import com.drivepartner.app.ui.icons.DriveIcons
import com.drivepartner.app.ui.illustrations.DriveBrandMark
import com.drivepartner.app.ui.illustrations.DriveEmptyOrdersIllustration
import com.drivepartner.app.ui.illustrations.DriveErrorIllustration
import com.drivepartner.app.ui.illustrations.DriveOfflineIllustration
import com.drivepartner.app.ui.illustrations.DriveSafetyIllustration
import com.drivepartner.app.ui.illustrations.DriveVerificationIllustration
import com.drivepartner.app.ui.illustrations.DriveWalletIllustration
import com.drivepartner.app.ui.theme.DriveAccessibility
import com.drivepartner.app.ui.theme.DriveCanvas
import com.drivepartner.app.ui.theme.DriveCanvasStyle
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveDesignSystem
import com.drivepartner.app.ui.theme.driveHeading
import com.drivepartner.app.ui.theme.driveShadow
import com.drivepartner.app.ui.theme.DriveElevation
import com.drivepartner.app.ui.theme.DriveMotion
import com.drivepartner.app.ui.theme.DriveResponsive
import com.drivepartner.app.ui.theme.DriveShapeRadius
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.theme.DriveTextRoles
import com.drivepartner.app.ui.theme.contrastRatio
import java.util.Locale

/**
 * Design System gallery — the living specification.
 *
 * Renders every token family (colour, type, shape, spacing, elevation, motion), the original icon
 * set, the vector illustrations and the component library *from the running theme*, so a reviewer
 * can flip light/dark and see the whole system respond. Contrast ratios are computed live with the
 * same WCAG maths the unit tests assert, which keeps documentation honest.
 *
 * Available from the drawer in demo builds (see DriveDrawerSheet) and via the `design_system`
 * route for QA.
 */
@Composable
fun DesignSystemGalleryScreen(modifier: Modifier = Modifier) {
    var previewDark by remember { mutableStateOf(false) }
    DriveDesignSystem(darkTheme = previewDark) {
        DriveCanvas(style = DriveCanvasStyle.SECONDARY, modifier = modifier) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = DriveResponsive.current.screenGutter),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = DriveSpacing.lg,
                    bottom = DriveSpacing.xxxl
                ),
                verticalArrangement = Arrangement.spacedBy(DriveSpacing.cardGap)
            ) {
                item { GalleryHeader(previewDark = previewDark, onPreviewDarkChange = { previewDark = it }) }
                item { ColorSection() }
                item { TypographySection() }
                item { ShapeSpacingSection() }
                item { ElevationSection() }
                item { MotionSection() }
                item { IconSection() }
                item { IllustrationSection() }
                item { ComponentSection() }
                item { StateSection() }
                item { ResponsiveSection() }
                item { AccessibilitySection() }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sections
// ---------------------------------------------------------------------------

@Composable
private fun GalleryHeader(previewDark: Boolean, onPreviewDarkChange: (Boolean) -> Unit) {
    val colors = DriveColors.current
    DriveHeroCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DriveBrandMark(modifier = Modifier.size(DriveSpacing.iconWellLarge))
            Spacer(modifier = Modifier.width(DriveSpacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nav_design_system),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = stringResource(R.string.ds_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(DriveSpacing.md))
        HorizontalDivider(color = colors.border)
        Spacer(modifier = Modifier.height(DriveSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.ds_preview_dark),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = previewDark, onCheckedChange = onPreviewDarkChange)
        }
    }
}

@Composable
private fun SectionCard(
    titleRes: Int,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val colors = DriveColors.current
    DriveCard {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.driveHeadingCompat()
        )
        Spacer(modifier = Modifier.height(DriveSpacing.md))
        content()
    }
}

@Composable
private fun ColorSection() {
    val colors = DriveColors.current
    SectionCard(R.string.ds_section_colors) {
        val swatches = listOf(
            "brand" to colors.brand,
            "background" to colors.background,
            "backgroundSubtle" to colors.backgroundSubtle,
            "surface" to colors.surface,
            "border" to colors.border,
            "textPrimary" to colors.textPrimary,
            "textSecondary" to colors.textSecondary,
            "success" to colors.success,
            "successStrong" to colors.successStrong,
            "warning" to colors.warning,
            "warningStrong" to colors.warningStrong,
            "danger" to colors.danger,
            "neutral" to colors.neutral
        )
        Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)) {
            swatches.forEach { (name, color) ->
                SwatchRow(name = name, color = color, canvas = colors.surface)
            }
        }
    }
}

@Composable
private fun SwatchRow(name: String, color: Color, canvas: Color) {
    val colors = DriveColors.current
    val ratio = contrastRatio(color, canvas)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(DriveSpacing.iconWellSmall)
                .background(color, RoundedCornerShape(DriveSpacing.sm))
                .border(DriveSpacing.hairlineBorder, colors.border, RoundedCornerShape(DriveSpacing.sm))
        )
        Spacer(modifier = Modifier.width(DriveSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Text(
                text = "#%08X".format(Locale.US, color.toArgb()),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        Text(
            text = stringResource(R.string.ds_contrast_on_surface, ratio),
            style = MaterialTheme.typography.labelSmall,
            color = if (ratio >= DriveAccessibility.wcagAaNormalText) colors.successStrong else colors.textTertiary
        )
    }
}

@Composable
private fun TypographySection() {
    val colors = DriveColors.current
    SectionCard(R.string.ds_section_type) {
        Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.md)) {
            TypeRow("displayMedium / money hero", DriveTextRoles.moneyHero, "₹1,450.00")
            TypeRow("headlineMedium", MaterialTheme.typography.headlineMedium, stringResource(R.string.ds_type_sample))
            TypeRow("titleMedium", MaterialTheme.typography.titleMedium, stringResource(R.string.ds_type_sample))
            TypeRow("bodyLarge", MaterialTheme.typography.bodyLarge, stringResource(R.string.ds_type_sample))
            TypeRow("bodyMedium", MaterialTheme.typography.bodyMedium, stringResource(R.string.ds_type_sample))
            TypeRow("labelSmall", MaterialTheme.typography.labelSmall, stringResource(R.string.ds_type_sample))
            HorizontalDivider(color = colors.border)
            TypeRow("Devanagari / bodyLarge", MaterialTheme.typography.bodyLarge, stringResource(R.string.ds_sample_hindi))
            TypeRow("Devanagari / money", DriveTextRoles.moneyLarge, "₹१,४५०.००")
        }
    }
}

@Composable
private fun TypeRow(role: String, style: TextStyle, sample: String) {
    val colors = DriveColors.current
    Column {
        Text(text = role, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        Text(text = sample, style = style, color = colors.textPrimary)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShapeSpacingSection() {
    val colors = DriveColors.current
    SectionCard(R.string.ds_section_shape) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DriveSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DriveSpacing.md)
        ) {
            RadiusSample("control", DriveShapeRadius.control)
            RadiusSample("cardSmall", DriveShapeRadius.cardSmall)
            RadiusSample("card", DriveShapeRadius.card)
            RadiusSample("cardLarge", DriveShapeRadius.cardLarge)
            RadiusSample("sheet", DriveShapeRadius.sheet)
        }
        Spacer(modifier = Modifier.height(DriveSpacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.xs)) {
            listOf(
                "xs 4" to DriveSpacing.xs,
                "sm 8" to DriveSpacing.sm,
                "md 12" to DriveSpacing.md,
                "lg 16" to DriveSpacing.lg,
                "xl 24" to DriveSpacing.xl,
                "xxl 32" to DriveSpacing.xxl
            ).forEach { (label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        modifier = Modifier.width(56.dp)
                    )
                    Box(
                        modifier = Modifier
                            .height(DriveSpacing.sm)
                            .width(value)
                            .background(colors.brand, RoundedCornerShape(DriveSpacing.xxs))
                    )
                }
            }
        }
    }
}

@Composable
private fun RadiusSample(name: String, radius: Dp) {
    val colors = DriveColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(colors.brandContainer, RoundedCornerShape(radius))
                .border(DriveSpacing.hairlineBorder, colors.brand, RoundedCornerShape(radius))
        )
        Spacer(modifier = Modifier.height(DriveSpacing.xs))
        Text(text = "$name\n$radius", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
    }
}

@Composable
private fun ElevationSection() {
    val colors = DriveColors.current
    val elevation = DriveElevation.current
    SectionCard(R.string.ds_section_elevation) {
        Row(horizontalArrangement = Arrangement.spacedBy(DriveSpacing.md)) {
            listOf(
                "L0" to elevation.level0,
                "L1" to elevation.level1,
                "L2" to elevation.level2,
                "L3" to elevation.level3,
                "L5" to elevation.level5
            ).forEach { (label, level) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(colors.surface, RoundedCornerShape(DriveShapeRadius.cardSmall))
                            .border(DriveSpacing.hairlineBorder, colors.border, RoundedCornerShape(DriveShapeRadius.cardSmall))
                            .then(
                                Modifier.driveShadowCompat(
                                    level = level,
                                    shape = RoundedCornerShape(DriveShapeRadius.cardSmall)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun MotionSection() {
    val colors = DriveColors.current
    val motion = DriveMotion.current
    var playing by remember { mutableStateOf(false) }
    val animated by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = motion.tweenOf(durationMillis = motion.slow),
        label = "motionDemo"
    )
    SectionCard(R.string.ds_section_motion) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(DriveSpacing.lg)
                    .background(colors.surfaceTonal, RoundedCornerShape(DriveShapeRadius.pill))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = 0.15f + 0.85f * animated)
                        .height(DriveSpacing.lg)
                        .background(colors.brand, RoundedCornerShape(DriveShapeRadius.pill))
                )
            }
            Spacer(modifier = Modifier.width(DriveSpacing.md))
            DriveOutlinedButton(
                onClick = { playing = !playing },
                modifier = Modifier.width(140.dp)
            ) {
                Text(text = stringResource(R.string.ds_motion_play))
            }
        }
        Spacer(modifier = Modifier.height(DriveSpacing.md))
        Text(
            text = "instant ${motion.instant} ms • fast ${motion.fast} ms • standard ${motion.standard} ms • " +
                "moderate ${motion.moderate} ms • slow ${motion.slow} ms",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
        Text(
            text = stringResource(
                if (motion.reducedMotion) R.string.ds_reduced_motion_on else R.string.ds_reduced_motion_off
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (motion.reducedMotion) colors.warningStrong else colors.textTertiary
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconSection() {
    val colors = DriveColors.current
    SectionCard(R.string.ds_section_icons) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DriveSpacing.md),
            verticalArrangement = Arrangement.spacedBy(DriveSpacing.md)
        ) {
            DriveIcons.all.forEach { spec ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp)
                ) {
                    DriveIconWell(icon = spec, contentDescription = null)
                    Spacer(modifier = Modifier.height(DriveSpacing.xs))
                    Text(
                        text = spec.key,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun IllustrationSection() {
    SectionCard(R.string.ds_section_illustrations) {
        Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.lg)) {
            DriveEmptyOrdersIllustration(modifier = Modifier.fillMaxWidth())
            DriveOfflineIllustration(modifier = Modifier.fillMaxWidth())
            DriveVerificationIllustration(modifier = Modifier.fillMaxWidth(0.7f).then(Modifier.align(Alignment.CenterHorizontally)))
            DriveWalletIllustration(modifier = Modifier.fillMaxWidth())
            DriveErrorIllustration(modifier = Modifier.fillMaxWidth())
            DriveSafetyIllustration(modifier = Modifier.fillMaxWidth(0.7f).then(Modifier.align(Alignment.CenterHorizontally)))
        }
    }
}

@Composable
private fun ComponentSection() {
    val colors = DriveColors.current
    SectionCard(R.string.ds_section_components) {
        Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.md)) {
            DrivePrimaryButton(onClick = { }) { Text(text = stringResource(R.string.go_online)) }
            DriveSuccessButton(onClick = { }) { Text(text = stringResource(R.string.ds_button_accept)) }
            DriveOutlinedButton(onClick = { }) { Text(text = stringResource(R.string.ds_button_decline)) }
            DriveDangerButton(onClick = { }, enabled = false) { Text(text = stringResource(R.string.nav_emergency_sos)) }
            Row(horizontalArrangement = Arrangement.spacedBy(DriveSpacing.sm)) {
                StatusChip(label = stringResource(R.string.status_online), showDot = true)
                StatusChip(
                    label = stringResource(R.string.status_offline),
                    containerColor = colors.neutralContainer,
                    contentColor = colors.onNeutralContainer,
                    showDot = true
                )
                StatusChip(
                    label = stringResource(R.string.duty_state_blocked),
                    containerColor = colors.dangerContainer,
                    contentColor = colors.onDangerContainer
                )
            }
            DriveSunkenCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DriveIconWell(icon = DriveIcons.Wallet, contentDescription = null, accent = colors.success)
                    Spacer(modifier = Modifier.width(DriveSpacing.md))
                    Column {
                        Text(
                            text = stringResource(R.string.ds_section_components),
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.ds_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StateSection() {
    SectionCard(R.string.ds_section_states) {
        Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.md)) {
            LoadingIndicator(modifier = Modifier.heightIn(max = 160.dp), message = stringResource(R.string.ds_state_loading))
            EmptyState(
                title = stringResource(R.string.ds_state_empty),
                subtitle = stringResource(R.string.ds_subtitle)
            )
            ErrorState(
                title = stringResource(R.string.ds_state_error),
                message = stringResource(R.string.ds_subtitle),
                onRetryClick = { }
            )
        }
    }
}

@Composable
private fun ResponsiveSection() {
    val colors = DriveColors.current
    val window = DriveResponsive.current
    SectionCard(R.string.ds_section_responsive) {
        Text(
            text = stringResource(
                R.string.ds_window_metrics,
                window.width.name,
                window.screenWidth.value.toInt(),
                window.screenHeight.value.toInt(),
                window.screenGutter.value.toInt()
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(DriveSpacing.sm))
        Text(
            text = "grid ${window.gridColumns} cols • metrics ${window.metricColumns} cols • " +
                "two-pane ${if (window.supportsTwoPane) "yes" else "no"} • map ${window.mapHeight}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary
        )
    }
}

@Composable
private fun AccessibilitySection() {
    val colors = DriveColors.current
    SectionCard(R.string.ds_section_accessibility) {
        Column(verticalArrangement = Arrangement.spacedBy(DriveSpacing.sm)) {
            Text(
                text = stringResource(
                    R.string.ds_a11y_touch,
                    DriveAccessibility.minTouchTarget.value.toInt(),
                    DriveAccessibility.recommendedTouchTarget.value.toInt()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary
            )
            Text(
                text = stringResource(R.string.ds_a11y_contrast),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        }
    }
}

// Small indirections keep section code readable while using the public token modifiers.
private fun Modifier.driveHeadingCompat(): Modifier = this.driveHeading()

@Composable
private fun Modifier.driveShadowCompat(level: Dp, shape: Shape): Modifier =
    this.driveShadow(level = level, shape = shape, tokens = DriveElevation.current)
