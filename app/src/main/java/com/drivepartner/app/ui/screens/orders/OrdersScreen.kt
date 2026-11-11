package com.drivepartner.app.ui.screens.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ElectricRickshaw
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drivepartner.app.R
import com.drivepartner.app.data.model.PartnerStatus
import com.drivepartner.app.ui.components.state.DriveEmptyView
import com.drivepartner.app.ui.components.state.DriveOfflineView
import com.drivepartner.app.ui.theme.DriveBluePrimary
import com.drivepartner.app.ui.theme.DriveSurfaceLightGrey
import com.drivepartner.app.ui.theme.DriveSurfaceWhite
import com.drivepartner.app.ui.theme.DriveTextMuted
import com.drivepartner.app.ui.theme.DriveTextPrimary

@Composable
fun OrdersScreen(
    partnerStatus: PartnerStatus,
    onGoOnlineClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.orders_tab_active),
        stringResource(R.string.orders_tab_completed),
        stringResource(R.string.orders_tab_cancelled)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DriveSurfaceLightGrey)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = DriveSurfaceWhite,
            contentColor = DriveBluePrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = DriveBluePrimary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selectedTab == index) DriveBluePrimary else DriveTextMuted
                        )
                    }
                )
            }
        }

        if (partnerStatus == PartnerStatus.OFFLINE && selectedTab == 0) {
            DriveOfflineView(
                title = stringResource(R.string.state_offline_title),
                subtitle = stringResource(R.string.orders_empty_description),
                onGoOnlineClick = onGoOnlineClick
            )
        } else {
            DriveEmptyView(
                icon = Icons.Outlined.ListAlt,
                title = stringResource(R.string.orders_empty_title),
                description = stringResource(R.string.orders_empty_description)
            )
        }
    }
}
