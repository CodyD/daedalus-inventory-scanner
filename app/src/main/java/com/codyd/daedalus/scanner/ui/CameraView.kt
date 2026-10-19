package com.codyd.daedalus.scanner.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.interfaces.Detector
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Camera preview that runs ML Kit [detectors] on every frame it can and hands
 * each result to [onResult]. Pinch to zoom and tap to focus come with the
 * camera controller. Analysis runs at 1080p: chip markings are small. The
 * preview is fitted, not cropped, so the whole analyzed frame is visible.
 */
@Composable
fun CameraView(
    detectors: List<Detector<*>>,
    onResult: (MlKitAnalyzer.Result) -> Unit,
    modifier: Modifier = Modifier,
    torch: Boolean = false,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val latestOnResult by rememberUpdatedState(onResult)
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            // Preview and analysis share one aspect ratio, so the fitted preview
            // shows exactly the frame OCR reads.
            val selector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build()
            previewResolutionSelector = selector
            imageAnalysisResolutionSelector = selector
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
        }
    }
    DisposableEffect(owner, detectors) {
        val executor = ContextCompat.getMainExecutor(context)
        controller.setImageAnalysisAnalyzer(
            executor,
            MlKitAnalyzer(detectors, ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor) { latestOnResult(it) },
        )
        controller.bindToLifecycle(owner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
        }
    }
    LaunchedEffect(torch) { controller.enableTorch(torch) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.BLACK)
                this.controller = controller
            }
        },
    )
}

/** On-device text recognizer (Latin script), closed when it leaves the composition. */
@Composable
fun rememberTextRecognizer(): TextRecognizer {
    val r = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    DisposableEffect(r) { onDispose { r.close() } }
    return r
}

/** Barcode scanner for the formats on pairing QRs and distributor bag labels. */
@Composable
fun rememberBarcodeScanner(): BarcodeScanner {
    val s = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_PDF417, Barcode.FORMAT_CODE_128)
                .build(),
        )
    }
    DisposableEffect(s) { onDispose { s.close() } }
    return s
}

/** Shows [content] once camera permission is granted; asks for it first. */
@Composable
fun WithCameraPermission(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
    if (granted) {
        content()
    } else {
        Column(
            modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "The camera reads part numbers and the pairing code. Nothing is uploaded: text is recognised on the phone.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
        }
    }
}
