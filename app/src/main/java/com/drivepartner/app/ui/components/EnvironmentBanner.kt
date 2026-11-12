package com.drivepartner.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.ui.theme.DriveChipShape
import com.drivepartner.app.ui.theme.DriveOnYellowContainer
import com.drivepartner.app.ui.theme.DriveYellowContainer

@Composable
fun EnvironmentBanner(
    dataOrigin: DataOrigin,
    modifier: Modifier = Modifier
) {
    if (dataOrigin != DataOrigin.DEMO) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DriveChipShape,
        color = DriveYellowContainer
    ) {
        Text(
            text = stringResource(R.string.demo_mode_banner),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = DriveOnYellowContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
