package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Phishing
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.ai.SonarVisionAnalysis
import com.example.ui.components.AiSampleNoticeBanner
import com.example.ui.components.MarineScreenHeader
import com.example.ui.components.TelemetryTile
import com.example.viewmodel.SonarVisionUiState
import java.io.File

@Composable
fun SonarCameraScreen(
    capturedBitmap: Bitmap?,
    visionState: SonarVisionUiState,
    onImageSelected: (Bitmap) -> Unit,
    onClearImage: () -> Unit,
    onAnalyzeImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var pendingPhotoUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionError by remember { mutableStateOf<String?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingPhotoUriString?.let(Uri::parse)
        if (success && uri != null) {
            decodeScaledBitmap(context, uri)?.let { bmp ->
                permissionError = null
                onImageSelected(bmp)
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionError = null
            val photoUri = createTempSonarPhotoUri(context)
            pendingPhotoUriString = photoUri.toString()
            takePictureLauncher.launch(photoUri)
        } else {
            permissionError = context.getString(R.string.camera_permission_denied)
        }
    }

    // Zero-permission Android Photo Picker (Google Play policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            decodeScaledBitmap(context, uri)?.let { bmp ->
                permissionError = null
                onImageSelected(bmp)
            }
        }
    }

    LaunchedEffect(visionState) {
        if (visionState is SonarVisionUiState.Success) {
            listState.animateScrollToItem(index = 4)
        }
    }

    val isAnalyzing = visionState is SonarVisionUiState.Loading

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            MarineScreenHeader(
                title = stringResource(R.string.camera_title),
                subtitle = stringResource(R.string.camera_subtitle),
                statusText = if (capturedBitmap != null) "CITRA SIAP" else "STANDBY",
                statusActive = capturedBitmap != null
            )
        }

        // Camera / Fishfinder Preview Frame with Marine Reticle Overlay
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.5.dp,
                        color = if (capturedBitmap != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = RoundedCornerShape(18.dp)
                    )
            ) {
                if (capturedBitmap != null) {
                    Image(
                        bitmap = capturedBitmap.asImageBitmap(),
                        contentDescription = "Foto layar fishfinder atau kondisi air laut",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = onClearImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.78f))
                            .testTag("clear_sonar_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Hapus foto sonar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.img_sonar_guide_1795472747368),
                        contentDescription = "Ilustrasi tampilan sonar fishfinder karang Bintan",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.42f))
                    )
                    Text(
                        text = stringResource(R.string.camera_guide_caption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.82f))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }

                // Corner Marine Targeting Reticle Overlay
                SonarReticleOverlay(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                )
            }
        }

        if (permissionError != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = permissionError.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Dual Action Row: Native Camera + Zero-Permission Photo Picker
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val hasCameraPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCameraPerm) {
                            val photoUri = createTempSonarPhotoUri(context)
                            pendingPhotoUriString = photoUri.toString()
                            takePictureLauncher.launch(photoUri)
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("capture_sonar_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_capture_photo),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("pick_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_pick_gallery),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Analyze Button
        item {
            Button(
                onClick = onAnalyzeImage,
                enabled = capturedBitmap != null && !isAnalyzing,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("analyze_sonar_image_button")
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.btn_analyzing_image),
                        style = MaterialTheme.typography.labelLarge
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.btn_analyze_image),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        // Vision Result Section
        item {
            when (visionState) {
                is SonarVisionUiState.Idle -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = if (capturedBitmap == null) {
                                "Ambil foto layar fishfinder portabel atau permukaan air laut untuk memulai deteksi pola fish arch, struktur dasar, dan saran umpan."
                            } else {
                                "Foto siap dianalisis. Tekan tombol \"ANALISIS GAMBAR SONAR\" di atas."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is SonarVisionUiState.Loading -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Membaca gema ikan, kontur dasar, dan kejernihan air...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                is SonarVisionUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = visionState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                is SonarVisionUiState.Success -> {
                    SonarVisionResultCard(analysis = visionState.analysis)
                }
            }
        }
    }
}

@Composable
private fun SonarReticleOverlay(modifier: Modifier = Modifier) {
    val reticleColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    Canvas(modifier = modifier) {
        val len = 24.dp.toPx()
        val stroke = 2.5.dp.toPx()
        // Top-left bracket
        drawLine(reticleColor, Offset(0f, 0f), Offset(len, 0f), stroke, StrokeCap.Round)
        drawLine(reticleColor, Offset(0f, 0f), Offset(0f, len), stroke, StrokeCap.Round)
        // Top-right bracket
        drawLine(reticleColor, Offset(size.width - len, 0f), Offset(size.width, 0f), stroke, StrokeCap.Round)
        drawLine(reticleColor, Offset(size.width, 0f), Offset(size.width, len), stroke, StrokeCap.Round)
        // Bottom-left bracket
        drawLine(reticleColor, Offset(0f, size.height), Offset(len, size.height), stroke, StrokeCap.Round)
        drawLine(reticleColor, Offset(0f, size.height - len), Offset(0f, size.height), stroke, StrokeCap.Round)
        // Bottom-right bracket
        drawLine(reticleColor, Offset(size.width - len, size.height), Offset(size.width, size.height), stroke, StrokeCap.Round)
        drawLine(reticleColor, Offset(size.width, size.height - len), Offset(size.width, size.height), stroke, StrokeCap.Round)
    }
}

@Composable
private fun SonarVisionResultCard(analysis: SonarVisionAnalysis) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (analysis.isSampleMode) {
            AiSampleNoticeBanner(notice = analysis.notice)
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    RoundedCornerShape(16.dp)
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HASIL PEMBACAAN CITRA",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "KEYAKINAN ${analysis.confidencePercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = analysis.fishDetectionSummary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryTile(
                icon = Icons.Rounded.Phishing,
                label = "KEPADATAN IKAN",
                value = analysis.estimatedFishDensity,
                accentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            TelemetryTile(
                icon = Icons.Rounded.Waves,
                label = "ZONA KEDALAMAN",
                value = analysis.targetDepthRange,
                accentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }

        TelemetryTile(
            icon = Icons.Rounded.Layers,
            label = "STRUKTUR DASAR & KARANG",
            value = analysis.seabedStructure,
            accentColor = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.fillMaxWidth()
        )

        TelemetryTile(
            icon = Icons.Rounded.Visibility,
            label = "KEJERNIHAN & WARNA AIR",
            value = analysis.waterClarity,
            accentColor = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxWidth()
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    RoundedCornerShape(14.dp)
                )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "REKOMENDASI JIG & UMPAN",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = analysis.recommendedLure,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun createTempSonarPhotoUri(context: Context): Uri {
    val dir = File(context.cacheDir, "sonar_photos").apply { mkdirs() }
    val file = File(dir, "sonar_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

private fun decodeScaledBitmap(context: Context, uri: Uri, maxDimension: Int = 1280): Bitmap? {
    return runCatching {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val rawWidth = boundsOptions.outWidth
        val rawHeight = boundsOptions.outHeight
        if (rawWidth <= 0 || rawHeight <= 0) return null

        var sampleSize = 1
        while (rawWidth / sampleSize > maxDimension || rawHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }.getOrNull()
}
