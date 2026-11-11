package com.drivepartner.app.ui.components.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.drivepartner.app.R

/**
 * Unified state renderer ensuring Loading, Empty, Error, Offline, and Permission states
 * look consistent across all Drive Partner screens.
 */
@Composable
fun DriveUiStateHost(
    uiState: DriveUiState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    onGoOnline: () -> Unit = {},
    onPermissionAction: (() -> Unit)? = null,
    successContent: @Composable () -> Unit
) {
    when (uiState) {
        is DriveUiState.Idle,
        is DriveUiState.Success -> successContent()

        is DriveUiState.Loading -> DriveLoadingView(modifier = modifier)

        is DriveUiState.Empty -> DriveEmptyView(
            modifier = modifier,
            title = uiState.title ?: stringResource(R.string.state_empty_title),
            description = uiState.message ?: stringResource(R.string.state_empty_subtitle)
        )

        is DriveUiState.Error -> DriveErrorView(
            modifier = modifier,
            message = uiState.message ?: stringResource(R.string.state_error_subtitle),
            onRetry = if (uiState.canRetry) onRetry else null
        )

        is DriveUiState.Offline -> DriveOfflineView(
            modifier = modifier,
            onGoOnlineClick = onGoOnline
        )

        is DriveUiState.PermissionRequired -> DrivePermissionView(
            modifier = modifier,
            title = uiState.title ?: stringResource(R.string.state_permission_title),
            message = uiState.message ?: stringResource(R.string.state_permission_subtitle),
            onActionClick = onPermissionAction
        )
    }
}
