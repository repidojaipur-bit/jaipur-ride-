package com.drivepartner.app.ui.theme

import androidx.compose.ui.unit.dp
import com.drivepartner.app.ui.icons.DriveIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Design system contract tests.
 *
 * These assertions are the reason the palette in `Color.kt` carries WCAG ratios in comments: the
 * numbers are re-derived here from the WCAG 2.1 relative-luminance formula on every test run, so a
 * brand refresh that silently drops contrast fails the build instead of shipping to drivers.
 */
class DesignTokensTest {

    private val light = LightDriveColors
    private val dark = DarkDriveColors

    // -- Contrast: light ----------------------------------------------------

    @Test
    fun `primary ink reaches AAA on the white canvas`() {
        assertTrue(
            "textPrimary on background must be >= 7:1",
            contrastRatio(light.textPrimary, light.background) >= DriveAccessibility.wcagAaaNormalText
        )
    }

    @Test
    fun `secondary ink reaches AA on both light canvases`() {
        assertTrue(contrastRatio(light.textSecondary, light.background) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(light.textOnSubtle, light.backgroundSubtle) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(light.textTertiary, light.background) >= DriveAccessibility.wcagAaNormalText)
    }

    @Test
    fun `brand and white text reach AA`() {
        assertTrue(contrastRatio(light.onBrand, light.brand) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(light.brandText, light.background) >= DriveAccessibility.wcagAaNormalText)
    }

    @Test
    fun `semantic text variants reach AA on their containers`() {
        assertTrue(contrastRatio(light.onSuccessContainer, light.successContainer) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(light.onWarningContainer, light.warningContainer) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(light.onDangerContainer, light.dangerContainer) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(light.onNeutralContainer, light.neutralContainer) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(light.warningStrong, light.background) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(light.successStrong, light.background) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(light.danger, light.background) >= DriveAccessibility.wcagAaNormalText)
    }

    @Test
    fun `filled action buttons carry accessible content colour`() {
        assertTrue(contrastRatio(light.onBrand, light.brand) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(light.onSuccess, light.successStrong) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(light.onDanger, light.danger) >= DriveAccessibility.wcagAaNormalText)
    }

    // -- Contrast: dark -----------------------------------------------------

    @Test
    fun `dark ink reaches AA on dark surfaces`() {
        assertTrue(contrastRatio(dark.textPrimary, dark.surface) >= DriveAccessibility.wcagAaaNormalText)
        assertTrue(contrastRatio(dark.textSecondary, dark.surface) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(dark.textTertiary, dark.surface) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(dark.onBrand, dark.brand) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(dark.onSuccessContainer, dark.successContainer) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(dark.onWarningContainer, dark.warningContainer) >= DriveAccessibility.wcagAaNormalText)
        assertTrue(contrastRatio(dark.onDangerContainer, dark.dangerContainer) >= DriveAccessibility.wcagAaNormalText)
    }

    @Test
    fun `structural borders reach the 3-to-1 UI component ratio`() {
        assertTrue(meetsUiComponentContrast(light.borderStrong, light.background))
        assertTrue(meetsUiComponentContrast(dark.borderStrong, dark.surface))
    }

    // -- Theme separation ---------------------------------------------------

    @Test
    fun `light and dark themes are genuinely different palettes`() {
        assertFalse(light.isDark)
        assertTrue(dark.isDark)
        assertFalse("canvases must differ", light.background == dark.background)
        assertFalse("surfaces must differ", light.surface == dark.surface)
        assertFalse("brand must differ", light.brand == dark.brand)
    }

    // -- Shape band ---------------------------------------------------------

    @Test
    fun `card radii stay inside the 14 to 20 dp design band`() {
        val band = DriveShapeRadius.cardRadiusRange
        listOf(
            DriveShapeRadius.cardSmall,
            DriveShapeRadius.card,
            DriveShapeRadius.cardLarge,
            DriveShapeRadius.sheet
        ).forEach { radius ->
            assertTrue("$radius outside $band", radius in band)
        }
        assertEquals(DriveShapeRadius.card, 16.dp)
    }

    // -- Touch targets ------------------------------------------------------

    @Test
    fun `touch targets meet and exceed the Android guideline`() {
        assertTrue(DriveSpacing.minTouchTarget >= 48.dp)
        assertTrue(DriveSpacing.primaryButtonHeight >= 52.dp)
        assertTrue(DriveSpacing.secondaryButtonHeight >= 48.dp)
    }

    // -- Motion -------------------------------------------------------------

    @Test
    fun `reduced motion collapses every duration`() {
        val reduced = DriveMotionTokens(reducedMotion = true)
        assertEquals(0, reduced.instant)
        assertEquals(0, reduced.fast)
        assertEquals(0, reduced.standard)
        assertEquals(0, reduced.moderate)
        assertEquals(0, reduced.slow)
        val normal = DriveMotionTokens(reducedMotion = false)
        assertTrue(normal.instant < normal.fast)
        assertTrue(normal.fast < normal.standard)
        assertTrue(normal.standard < normal.moderate)
        assertTrue(normal.moderate < normal.slow)
    }

    // -- Responsive ---------------------------------------------------------

    @Test
    fun `window size classes follow the material breakpoints`() {
        assertEquals(DriveWindowWidth.COMPACT, DriveWindowSizeClass.calculate(412.dp, 892.dp).width)
        assertEquals(DriveWindowWidth.MEDIUM, DriveWindowSizeClass.calculate(700.dp, 900.dp).width)
        assertEquals(DriveWindowWidth.EXPANDED, DriveWindowSizeClass.calculate(1024.dp, 1200.dp).width)
        assertEquals(DriveWindowHeight.COMPACT, DriveWindowSizeClass.calculate(412.dp, 400.dp).height)
        assertTrue(DriveWindowSizeClass.calculate(700.dp, 900.dp).supportsTwoPane)
        assertFalse(DriveWindowSizeClass.calculate(412.dp, 892.dp).supportsTwoPane)
        assertEquals(16.dp, DriveWindowSizeClass.calculate(412.dp, 892.dp).screenGutter)
        assertEquals(32.dp, DriveWindowSizeClass.calculate(1024.dp, 1200.dp).screenGutter)
    }

    // -- Iconography --------------------------------------------------------

    @Test
    fun `icon registry is complete and unique`() {
        val keys = DriveIcons.all.map { it.key }
        assertTrue("icon set must not be empty", keys.isNotEmpty())
        assertEquals("duplicate icon keys", keys.size, keys.toSet().size)
        val resources = DriveIcons.all.map { it.resource }
        assertEquals("duplicate icon resources", resources.size, resources.toSet().size)
        assertTrue(resources.all { it != 0 })
        // The five primary navigation glyphs must exist for the bottom bar.
        assertTrue(DriveIcons.all.contains(DriveIcons.Home))
        assertTrue(DriveIcons.all.contains(DriveIcons.Orders))
        assertTrue(DriveIcons.all.contains(DriveIcons.Earnings))
        assertTrue(DriveIcons.all.contains(DriveIcons.Rewards))
        assertTrue(DriveIcons.all.contains(DriveIcons.Profile))
    }

    // -- Accessibility maths ------------------------------------------------

    @Test
    fun `contrast ratio matches published WCAG examples`() {
        // Black on white is exactly 21:1; white on the brand blue is ~5.77:1.
        assertEquals(21.0, contrastRatio(androidx.compose.ui.graphics.Color.Black, androidx.compose.ui.graphics.Color.White), 0.05)
        assertEquals(5.77, contrastRatio(DriveSurfaceWhite, DriveBluePrimary), 0.05)
    }
}
