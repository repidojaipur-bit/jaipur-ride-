package com.drivepartner.app.ui.screens.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.drivepartner.app.BuildConfig
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.local.AppLanguage
import com.drivepartner.app.data.model.ThemePreference
import com.drivepartner.app.ui.components.EnvironmentBanner
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveSpacing

private enum class LegalDocumentModal {
    PRIVACY_POLICY,
    TERMS_CONDITIONS,
    LOCATION_DISCLOSURE
}

@Composable
fun SettingsLegalScreen(
    dataOrigin: DataOrigin,
    currentLanguage: AppLanguage,
    currentTheme: ThemePreference,
    hasActiveTrip: Boolean,
    authErrorMessage: String?,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectTheme: (ThemePreference) -> Unit,
    onOpenOnboardingFlow: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeLegalModal by remember { mutableStateOf<LegalDocumentModal?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DriveSpacing.space16),
        verticalArrangement = Arrangement.spacedBy(DriveSpacing.space12)
    ) {
        if (dataOrigin == DataOrigin.DEMO) {
            EnvironmentBanner(dataOrigin = dataOrigin)
        }

        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (!authErrorMessage.isNullOrBlank()) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = DriveCardShape) {
                Text(
                    text = authErrorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(DriveSpacing.space12)
                )
            }
        }

        Card(shape = DriveCardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DriveSpacing.space16),
                verticalArrangement = Arrangement.spacedBy(DriveSpacing.space8)
            ) {
                Text(
                    text = stringResource(R.string.settings_language_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DriveSpacing.space8)) {
                    FilterChip(
                        selected = currentLanguage == AppLanguage.ENGLISH,
                        onClick = { onSelectLanguage(AppLanguage.ENGLISH) },
                        label = { Text(stringResource(R.string.lang_english_title)) }
                    )
                    FilterChip(
                        selected = currentLanguage == AppLanguage.HINDI,
                        onClick = { onSelectLanguage(AppLanguage.HINDI) },
                        label = { Text(stringResource(R.string.lang_hindi_title)) }
                    )
                }
            }
        }

        Card(shape = DriveCardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DriveSpacing.space16),
                verticalArrangement = Arrangement.spacedBy(DriveSpacing.space8)
            ) {
                Text(
                    text = stringResource(R.string.settings_appearance_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(DriveSpacing.space8)) {
                    FilterChip(
                        selected = currentTheme == ThemePreference.LIGHT,
                        onClick = { onSelectTheme(ThemePreference.LIGHT) },
                        label = { Text(stringResource(R.string.theme_light)) },
                        modifier = Modifier.testTag("theme_chip_light")
                    )
                    FilterChip(
                        selected = currentTheme == ThemePreference.DARK,
                        onClick = { onSelectTheme(ThemePreference.DARK) },
                        label = { Text(stringResource(R.string.theme_dark)) },
                        modifier = Modifier.testTag("theme_chip_dark")
                    )
                    FilterChip(
                        selected = currentTheme == ThemePreference.SYSTEM,
                        onClick = { onSelectTheme(ThemePreference.SYSTEM) },
                        label = { Text(stringResource(R.string.theme_system)) },
                        modifier = Modifier.testTag("theme_chip_system")
                    )
                }
            }
        }

        Card(shape = DriveCardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DriveSpacing.space16),
                verticalArrangement = Arrangement.spacedBy(DriveSpacing.space8)
            ) {
                Text(
                    text = stringResource(R.string.settings_permissions_and_legal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                ) {
                    Text(text = stringResource(R.string.settings_notification_preferences))
                }
                OutlinedButton(
                    onClick = { activeLegalModal = LegalDocumentModal.PRIVACY_POLICY },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                        .testTag("settings_privacy_button")
                ) {
                    Text(text = stringResource(R.string.legal_privacy_policy_title))
                }
                OutlinedButton(
                    onClick = { activeLegalModal = LegalDocumentModal.TERMS_CONDITIONS },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                        .testTag("settings_terms_button")
                ) {
                    Text(text = stringResource(R.string.legal_terms_title))
                }
                OutlinedButton(
                    onClick = { activeLegalModal = LegalDocumentModal.LOCATION_DISCLOSURE },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                ) {
                    Text(text = stringResource(R.string.legal_location_disclosure_title))
                }
                OutlinedButton(
                    onClick = onOpenOnboardingFlow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                        .testTag("settings_replay_onboarding_button")
                ) {
                    Text(text = stringResource(R.string.settings_open_onboarding_flow))
                }
            }
        }

        Card(shape = DriveCardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(DriveSpacing.space16),
                verticalArrangement = Arrangement.spacedBy(DriveSpacing.space8)
            ) {
                Text(
                    text = stringResource(R.string.settings_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onLogout,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                        .testTag("settings_logout_button")
                ) {
                    Text(
                        text = if (hasActiveTrip) {
                            stringResource(R.string.settings_logout_blocked_hint)
                        } else {
                            stringResource(R.string.action_sign_out)
                        }
                    )
                }
            }
        }
    }

    activeLegalModal?.let { modal ->
        val (titleRes, bodyRes) = when (modal) {
            LegalDocumentModal.PRIVACY_POLICY ->
                R.string.legal_privacy_policy_title to R.string.legal_privacy_policy_body
            LegalDocumentModal.TERMS_CONDITIONS ->
                R.string.legal_terms_title to R.string.legal_terms_body
            LegalDocumentModal.LOCATION_DISCLOSURE ->
                R.string.legal_location_disclosure_title to R.string.legal_location_disclosure_body
        }
        AlertDialog(
            onDismissRequest = { activeLegalModal = null },
            title = { Text(text = stringResource(titleRes)) },
            text = { Text(text = stringResource(bodyRes)) },
            confirmButton = {
                TextButton(onClick = { activeLegalModal = null }) {
                    Text(text = stringResource(R.string.action_close))
                }
            }
        )
    }
}
