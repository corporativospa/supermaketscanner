package com.caucorp.supermarketscanner.ui

import android.util.Log
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
fun BarcodeScannerView(
    isChecking: Boolean,
    rightOverlayInset: androidx.compose.ui.unit.Dp = 0.dp,
    onBarcodeDetected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A)
            .build()
        BarcodeScanning.getClient(options)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // CameraX Preview and Analysis
        if (!isChecking) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy, barcodeScanner) { barcode ->
                                // Prevent double execution
                                onBarcodeDetected(barcode)
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("BarcodeScannerView", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            )
        }

        // Viewfinder and styling overlay
        ScannerOverlayView()

        // Top UI Text Info
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 64.dp, start = 24.dp, end = 24.dp + rightOverlayInset),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "Supermarket Scanner",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Apunta al código de barras del producto (EAN-13, EAN-8 o UPC-A)",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Start
            )
        }

        // Loader Overlay
        if (isChecking) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Buscando producto...",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: ImageProxy,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onBarcodeDetected: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    barcode.rawValue?.let {
                        onBarcodeDetected(it)
                        return@addOnSuccessListener
                    }
                }
            }
            .addOnFailureListener {
                Log.e("BarcodeAnalyzer", "Barcode scan failed", it)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}

@Composable
fun ScannerOverlayView() {
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    
    // Animate the laser line position from top of the scanner box to bottom
    val laserOffsetFactor by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserLine"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        // Let's define the scan box size and position
        val boxWidth = width * 0.8f
        val boxHeight = height * 0.25f
        val left = (width - boxWidth) / 2
        val top = (height - boxHeight) / 2
        
        // Step 1: Draw semi-transparent grey mask over the screen, except for the scan box area
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(
                        offset = Offset(left, top),
                        size = Size(boxWidth, boxHeight)
                    ),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )
            )
        }
        
        // Background overlay
        drawRect(
            color = Color.Black.copy(alpha = 0.55f),
            size = Size(width, height)
        )
        
        // Blend mode to cut out the rounded rectangle viewfinder box
        drawPath(
            path = path,
            color = Color.Transparent,
            blendMode = BlendMode.Clear
        )

        // Step 2: Draw the glowing frame border around the cut-out viewfinder
        drawRoundRect(
            color = Color(0xFF6200EE),
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
            style = Stroke(width = 3.dp.toPx())
        )

        // Step 3: Draw the moving horizontal laser scan line
        val laserY = top + (boxHeight * laserOffsetFactor)
        drawLine(
            color = Color(0xFF03DAC6),
            start = Offset(left + 16.dp.toPx(), laserY),
            end = Offset(left + boxWidth - 16.dp.toPx(), laserY),
            strokeWidth = 2.dp.toPx()
        )
    }
}
