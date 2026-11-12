package com.drivepartner.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.drivepartner.app.R
import com.drivepartner.app.data.model.DutyControlState
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveColors
import com.drivepartner.app.ui.theme.DriveElevation
import com.drivepartner.app.ui.theme.DriveMotion
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.theme.DriveTextRoles
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface

/**
 * Screen 5 (spec section 9): the large Online/Offline duty control on the home dashboard.
 *
 * The switch is oversized on purpose (a driver taps it through a phone mount), animates between
 * neutral and success green, and stays disabled with a reason while a transition is in flight or
 * verification blocks going online.
 */
@Composable
fun DutySwitchLarge(
    dutyControlState: DutyControlState,
    isEligible: Boolean,
    blockingNotice: String?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = DriveColors.current
    val motion = DriveMotion.current
    val isOnline = dutyControlState.isOnline
    val isPending = dutyControlState.isTransitionPending
    val isEnabled = isEligible && !isPending

    val trackColor by animateColorAsState(
        targetValue = when {
            !isEnabled -> colors.neutralContainer
            isOnline -> colors.success
            else -> colors.neutral
        },
        animationSpec = motion.tweenOf(),
        label = "dutyTrackColor"
    )
    val knobTravel = SWITCH_WIDTH - SWITCH_HEIGHT
    val knobOffset by animateDpAsState(
        targetValue = if (isOnline) knobTravel else 0.dp,
        animationSpec = motion.tweenOf(),
        label = "dutyKnobOffset"
    )

    val stateText = when {
        isPending -> stringResource(R.string.duty_switch_updating)
        isOnline -> stringResource(R.string.duty_switch_state_online)
        else -> stringResource(R.string.duty_switch_state_offline)
    }
    val caption = when {
        isPending -> stringResource(dutyControlState.labelRes)
        !isEligible -> blockingNotice ?: stringResource(R.string.duty_switch_unavailable)
        isOnline -> stringResource(R.string.duty_switch_go_offline)
        else -> stringResource(R.string.duty_switch_go_online)
    }

    Surface(
        shape = DriveCardShape,
        color = colors.surface,
        border = BorderStroke(DriveSpacing.hairlineBorder, colors.border),
        shadowElevation = DriveElevation.current.level2,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 72.dp)
                .clickable(
                    enabled = isEnabled,
                    role = Role.Switch,
                    onClick = onToggle
                )
                .padding(DriveSpacing.lg)
                .semantics {
                    stateDescription = stateText
                    contentDescription = stringResource(R.string.duty_switch_title)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(DriveSpacing.xxs)
            ) {
                Text(
                    text = stringResource(R.string.duty_switch_title),
                    style = DriveTextRoles.overline,
                    color = colors.textSecondary
                )
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isOnline) colors.successStrong else colors.textPrimary
                )
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!isEligible) colors.dangerStrong else colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.width(DriveSpacing.md))
            Box(
                modifier = Modifier
                    .width(SWITCH_WIDTH)
                    .height(SWITCH_HEIGHT)
                    .background(trackColor, CircleShape),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = knobOffset)
                        .padding(horizontal = KNOB_INSET)
                        .size(SWITCH_HEIGHT - KNOB_INSET * 2)
                        .background(colors.surface, CircleShape)
                )
            }
        }
    }
}

private val SWITCH_WIDTH = 76.dp
private val SWITCH_HEIGHT = 40.dp
private val KNOB_INSET = 4.dp
