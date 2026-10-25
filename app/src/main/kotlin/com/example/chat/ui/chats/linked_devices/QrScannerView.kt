package com.example.chat.ui.chats.linked_devices

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/** Camera QR scanner matching mobile_scanner's Android behavior for
 *  linked_devices_qr_screen.dart: back camera, QR formats only,
 *  noDuplicates semantics (each distinct code delivered once per scanner
 *  session). mobile_scanner itself uses ML Kit on Android, so this is the
 *  exact same decode engine as the Dart screen.
 *
 *  Requires gradle dependencies (integration owner), all free/on-device:
 *      implementation("androidx.camera:camera-camera2:1.4.1")
 *      implementation("androidx.camera:camera-lifecycle:1.4.1")
 *      implementation("androidx.camera:camera-view:1.4.1")
 *      implementation("com.google.mlkit:barcode-scanning:17.3.0")
 *  and the CAMERA permission in AndroidManifest (integration owner). */
@OptIn(ExperimentalGetImage::class)
@Composable
fun QrScannerView(
    modifier: Modifier = Modifier,
    onDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        val scanner = BarcodeScanning.getClient(
            com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            // noDuplicates: suppress repeat delivery of the same raw value.
            var lastDelivered: String? = null
            analysis.setAnalyzer(executor) { proxy ->
                val mediaImage = proxy.image
                if (mediaImage == null) {
                    proxy.close()
                    return@setAnalyzer
                }
                val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val raw = barcodes.firstNotNullOfOrNull { it.rawValue }
                        if (!raw.isNullOrEmpty() && raw != lastDelivered) {
                            lastDelivered = raw
                            onDetected(raw)
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }
        providerFuture.addListener(listener, androidx.core.content.ContextCompat.getMainExecutor(context))
        onDispose {
            try {
                providerFuture.get().unbindAll()
            } catch (_: Exception) {
            }
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
