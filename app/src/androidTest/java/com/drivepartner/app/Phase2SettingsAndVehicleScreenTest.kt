package com.drivepartner.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.local.AppLanguage
import com.drivepartner.app.data.model.ThemePreference
import com.drivepartner.app.ui.screens.onboarding.SplashLanguageScreen
import com.drivepartner.app.ui.screens.settings.SettingsLegalScreen
import com.drivepartner.app.ui.theme.DrivePartnerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase2SettingsAndVehicleScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun splashLanguageScreen_switchesLanguageAndContinues() {
        val context = composeRule.activity
        var selected = AppLanguage.ENGLISH
        var continued = false

        composeRule.setContent {
            DrivePartnerTheme {
                SplashLanguageScreen(
                    dataOrigin = DataOrigin.DEMO,
                    selectedLanguage = selected,
                    isLoading = false,
                    onSelectLanguage = { selected = it },
                    onContinue = { continued = true }
                )
            }
        }

        composeRule.onNodeWithTag("lang_card_hi").performClick()
        assertEquals(AppLanguage.HINDI, selected)

        composeRule.onNodeWithTag("splash_continue_button").performClick()
        assertTrue(continued)
        composeRule.onNodeWithText(context.getString(R.string.brand_tagline)).assertIsDisplayed()
    }

    @Test
    fun settingsLegalScreen_togglesThemeAndOpensPrivacyModal() {
        val context = composeRule.activity
        var theme = ThemePreference.LIGHT

        composeRule.setContent {
            DrivePartnerTheme(themePreference = theme) {
                SettingsLegalScreen(
                    dataOrigin = DataOrigin.DEMO,
                    currentLanguage = AppLanguage.ENGLISH,
                    currentTheme = theme,
                    hasActiveTrip = false,
                    authErrorMessage = null,
                    onSelectLanguage = {},
                    onSelectTheme = { theme = it },
                    onOpenOnboardingFlow = {},
                    onLogout = {}
                )
            }
        }

        composeRule.onNodeWithTag("theme_chip_dark").performClick()
        assertEquals(ThemePreference.DARK, theme)

        composeRule.onNodeWithTag("settings_privacy_button")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText(context.getString(R.string.legal_privacy_policy_body))
            .assertIsDisplayed()
    }
}
