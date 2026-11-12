package com.drivepartner.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrivePartnerNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigation_opensAllFiveDestinations_andSecondaryBackWorks() {
        val context = composeRule.activity

        val homeLabel = context.getString(R.string.nav_home)
        val ordersLabel = context.getString(R.string.nav_orders)
        val earningsLabel = context.getString(R.string.nav_earnings)
        val rewardsLabel = context.getString(R.string.nav_rewards)
        val profileLabel = context.getString(R.string.nav_profile)

        composeRule.onNodeWithText(context.getString(R.string.home_today_summary)).assertIsDisplayed()

        composeRule.onNodeWithText(ordersLabel).performClick()
        composeRule.onNodeWithText(context.getString(R.string.orders_tab_active)).assertIsDisplayed()

        composeRule.onNodeWithText(earningsLabel).performClick()
        composeRule.onNodeWithText(context.getString(R.string.earnings_transactions_heading))
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.onNodeWithText(rewardsLabel).performClick()
        composeRule.onNodeWithText(context.getString(R.string.rewards_campaigns_heading)).assertIsDisplayed()

        composeRule.onNodeWithText(profileLabel).performClick()
        composeRule.onNodeWithText(context.getString(R.string.profile_vehicle_info))
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText(context.getString(R.string.vehicle_save_cta)).assertIsDisplayed()

        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_back)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.profile_documents)).assertIsDisplayed()

        composeRule.onNodeWithText(homeLabel).performClick()
        composeRule.onNodeWithText(context.getString(R.string.home_today_summary)).assertIsDisplayed()
    }
}
