package com.drivepartner.app.ui.screens.onboarding

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.drivepartner.app.R
import com.drivepartner.app.core.config.DataOrigin
import com.drivepartner.app.data.model.labelRes
import com.drivepartner.app.data.remote.dto.KycDocumentStatus
import com.drivepartner.app.data.remote.dto.KycDocumentType
import com.drivepartner.app.ui.components.EnvironmentBanner
import com.drivepartner.app.ui.theme.DriveCardShape
import com.drivepartner.app.ui.theme.DriveSpacing
import com.drivepartner.app.ui.viewmodel.OnboardingAuthUiState

@Composable
fun DocumentsKycScreen(
    uiState: OnboardingAuthUiState,
    onUploadDocument: (
        type: KycDocumentType,
        mimeType: String,
        sizeBytes: Long,
        maskedReference: String?,
        expiresOnUtc: String?
    ) -> Unit,
    onSimulateDemoReview: (approveAll: Boolean, rejectType: KycDocumentType?) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeType by remember { mutableStateOf<KycDocumentType?>(null) }
    var maskedRef by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf("") }
    var previewUris by remember { mutableStateOf<Map<KycDocumentType, Uri>>(emptyMap()) }

    val context = LocalContext.current
    var pendingCapture by remember { mutableStateOf<Pair<KycDocumentType, Uri>?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val pending = pendingCapture
        pendingCapture = null
        if (success && pending != null) {
            val (type, captureUri) = pending
            previewUris = previewUris + (type to captureUri)
            onUploadDocument(
                type,
                "image/jpeg",
                captureFileSize(context, captureUri),
                maskedRef.takeIf { it.isNotBlank() },
                expiry.takeIf { it.isNotBlank() }
            )
        }
    }

    fun launchCamera(type: KycDocumentType) {
        val directory = java.io.File(context.externalCacheDir, "captures").apply { mkdirs() }
        val target = java.io.File(
            directory,
            "kyc_${type.name.lowercase(java.util.Locale.US)}_${System.currentTimeMillis()}.jpg"
        )
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            target
        )
        activeType = type
        pendingCapture = type to uri
        cameraLauncher.launch(uri)
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        val type = activeType
        if (uri != null && type != null) {
            previewUris = previewUris + (type to uri)
            onUploadDocument(
                type,
                "image/jpeg",
                285_000L,
                maskedRef.takeIf { it.isNotBlank() },
                expiry.takeIf { it.isNotBlank() }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(DriveSpacing.space16),
        verticalArrangement = Arrangement.spacedBy(DriveSpacing.space12)
    ) {
        if (uiState.dataOrigin == DataOrigin.DEMO) {
            EnvironmentBanner(dataOrigin = uiState.dataOrigin)
        }

        Text(
            text = stringResource(R.string.kyc_documents_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.kyc_aadhaar_compliance_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!uiState.errorMessage.isNullOrBlank()) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = DriveCardShape) {
                Text(
                    text = uiState.errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(DriveSpacing.space12)
                )
            }
        }
        if (!uiState.successMessage.isNullOrBlank()) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = DriveCardShape) {
                Text(
                    text = uiState.successMessage,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(DriveSpacing.space12)
                )
            }
        }

        OutlinedTextField(
            value = maskedRef,
            onValueChange = { maskedRef = it },
            label = { Text(stringResource(R.string.kyc_optional_reference_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("kyc_masked_ref_input")
        )
        OutlinedTextField(
            value = expiry,
            onValueChange = { expiry = it },
            label = { Text(stringResource(R.string.kyc_optional_expiry_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        KycDocumentType.entries.forEach { docType ->
            val doc = uiState.documents.find { it.documentType == docType }
            val statusColor = when (doc?.status) {
                KycDocumentStatus.APPROVED -> MaterialTheme.colorScheme.primary
                KycDocumentStatus.REJECTED, KycDocumentStatus.EXPIRED -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Card(
                shape = DriveCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(DriveSpacing.space16),
                    verticalArrangement = Arrangement.spacedBy(DriveSpacing.space8)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(docType.labelRes()),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = doc?.let { stringResource(it.status.labelRes()) }
                                ?: stringResource(R.string.kyc_status_not_submitted),
                            style = MaterialTheme.typography.labelMedium,
                            color = statusColor
                        )
                    }
                    if (!doc?.maskedReference.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.kyc_ref_display, doc?.maskedReference.orEmpty()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!doc?.rejectionReason.isNullOrBlank()) {
                        Text(
                            text = doc?.rejectionReason.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    val previewUri = previewUris[docType]
                    if (previewUri != null) {
                        val bitmap by rememberDocumentBitmap(previewUri)
                        bitmap?.let { decoded ->
                            Image(
                                bitmap = decoded.asImageBitmap(),
                                contentDescription = stringResource(R.string.kyc_preview_cd),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(DriveCardShape)
                            )
                        }
                    }
                    if (uiState.isLoading && activeType == docType) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(DriveSpacing.space16)
                            )
                            Spacer(modifier = Modifier.width(DriveSpacing.space8))
                            Text(
                                text = stringResource(R.string.kyc_upload_in_progress),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(DriveSpacing.space8)) {
                        OutlinedButton(
                            onClick = { launchCamera(docType) },
                            enabled = !uiState.isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                        ) {
                            Text(text = stringResource(
                                if (previewUris[docType] == null) R.string.kyc_capture_camera
                                else R.string.kyc_retake_replace
                            ))
                        }
                        OutlinedButton(
                            onClick = {
                                activeType = docType
                                picker.launch("image/*")
                            },
                            enabled = !uiState.isLoading,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                        ) {
                            Text(text = stringResource(R.string.kyc_pick_gallery))
                        }
                        if (uiState.dataOrigin == DataOrigin.DEMO) {
                            Button(
                                onClick = {
                                    onUploadDocument(
                                        docType,
                                        "image/jpeg",
                                        240_000L,
                                        maskedRef.takeIf { it.isNotBlank() },
                                        expiry.takeIf { it.isNotBlank() }
                                    )
                                },
                                modifier = Modifier.defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                            ) {
                                Text(text = stringResource(R.string.kyc_quick_upload_demo))
                            }
                        }
                    }
                }
            }
        }

        if (uiState.dataOrigin == DataOrigin.DEMO) {
            Card(
                shape = DriveCardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(DriveSpacing.space16),
                    verticalArrangement = Arrangement.spacedBy(DriveSpacing.space8)
                ) {
                    Text(
                        text = stringResource(R.string.kyc_demo_reviewer_controls),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(DriveSpacing.space8)) {
                        Button(
                            onClick = { onSimulateDemoReview(true, null) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                        ) {
                            Text(text = stringResource(R.string.kyc_demo_approve_all))
                        }
                        OutlinedButton(
                            onClick = { onSimulateDemoReview(false, KycDocumentType.DRIVING_LICENSE) },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = DriveSpacing.minTouchTarget)
                        ) {
                            Text(text = stringResource(R.string.kyc_demo_reject_dl))
                        }
                    }
                }
            }
        }
    }
}

/** Decodes a local capture/gallery image off the main thread for the inline preview. */
@Composable
private fun rememberDocumentBitmap(uri: Uri): State<android.graphics.Bitmap?> {
    val context = LocalContext.current
    return produceState<android.graphics.Bitmap?>(initialValue = null, key1 = uri) {
        val decoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
        value = decoded
    }
}

private fun captureFileSize(context: android.content.Context, uri: Uri): Long =
    runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
    }.getOrNull() ?: 0L
