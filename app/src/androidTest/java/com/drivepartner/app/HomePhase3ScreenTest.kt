package com.drivepartner.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.core.location.GpsReadinessStatus
import com.drivepartner.app.core.location.LocationPermissionStatus
import com.drivepartner.app.data.model.DriverEligibility
import com.drivepartner.app.data.model.DriverPreferences
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.data.model.ServiceType
import com.drivepartner.app.data.model.ServiceZone
import com.drivepartner.app.data.remote.dto.EligibilityBlockingReason
import com.drivepartner.app.ui.screens.home.HomeScreen
import com.drivepartner.app.ui.theme.DrivePartnerTheme
import com.drivepartner.app.ui.viewmodel.HomeUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomePhase3ScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun homeScreen_whenDriverBlocked_showsReasonAndDisablesGoOnline() {
        val blockedState = HomeUiState(
            dataOrigin = DataOrigin.DEMO,
            dutyControlState = DutyControlState.BLOCKED,
            eligibility = DriverEligibility(
                isEligibleForOnline = false,
                blockingReasons = listOf(EligibilityBlockingReason.DOCUMENT_EXPIRED),
                approvedServiceCategoryCodes = listOf("BIKE"),
                assignedZoneIds = listOf("DEMO-ZONE-MI-ROAD"),
                evaluatedAtUtc = "2026-11-11T10:00:00Z"
            ),
            permissionStatus = LocationPermissionStatus.GRANTED_FINE,
            gpsStatus = GpsReadinessStatus.READY
        )

        composeRule.setContent {
            DrivePartnerTheme {
                HomeScreen(
                    uiState = blockedState,
                    onToggleDutyStatus = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.eligibility_blocked_title)).assertIsDisplayed()
        composeRule.onNodeWithText("• ${context.getString(R.string.elig_reason_document_expired)}")
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.go_online)).assertIsNotEnabled()
    }

    @Test
    fun homeScreen_whenGpsDisabled_showsEnableGpsBanner_andPreferencesSheetOpens() {
        val gpsOffState = HomeUiState(
            dataOrigin = DataOrigin.DEMO,
            dutyControlState = DutyControlState.OFFLINE,
            permissionStatus = LocationPermissionStatus.GRANTED_FINE,
            gpsStatus = GpsReadinessStatus.DISABLED,
            availableZones = listOf(
                ServiceZone("DEMO-ZONE-MI-ROAD", "JAI", "MI Road", isEnabled = true, isDemoZone = true)
            ),
            preferences = DriverPreferences(
                preferredZoneIds = listOf("DEMO-ZONE-MI-ROAD"),
                autoBoostEnabled = false,
                activeServiceType = ServiceType.BIKE_TAXI
            )
        )

        composeRule.setContent {
            DrivePartnerTheme {
                HomeScreen(
                    uiState = gpsOffState,
                    onToggleDutyStatus = {}
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.action_enable_gps)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.map_no_surge_notice))
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.home_manage_preferences))
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText(context.getString(R.string.prefs_autoboost_desc)).assertIsDisplayed()
    }
}
