package com.caucorp.supermarketscanner.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.caucorp.supermarketscanner.ui.BarcodeScannerView
import com.caucorp.supermarketscanner.ui.PhotoCaptureView
import com.caucorp.supermarketscanner.ui.ProductDetailView
import com.caucorp.supermarketscanner.ui.ProcessingView
import com.caucorp.supermarketscanner.viewmodel.AppScreen
import com.caucorp.supermarketscanner.viewmodel.MainViewModel

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(key1 = hasCameraPermission) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (hasCameraPermission) {
            val screenState by viewModel.currentScreen.collectAsStateWithLifecycle()
            val isChecking by viewModel.isCheckingBarcode.collectAsStateWithLifecycle()
            val uploadProgress by viewModel.uploadProgress.collectAsStateWithLifecycle()

            when (val screen = screenState) {
                is AppScreen.Scanning -> {
                    BarcodeScannerView(
                        isChecking = isChecking,
                        onBarcodeDetected = { barcode ->
                            viewModel.onBarcodeDetected(barcode)
                        }
                    )
                }
                is AppScreen.CapturePhotos -> {
                    PhotoCaptureView(
                        barcode = screen.barcode,
                        photos = screen.photos,
                        onPhotoCaptured = { bitmap ->
                            viewModel.addCapturedPhoto(screen.barcode, bitmap)
                        },
                        onPhotoRemoved = { index ->
                            viewModel.removeCapturedPhoto(screen.barcode, index)
                        },
                        onConfirmUpload = {
                            viewModel.uploadPhotosAndStartProcessing(screen.barcode, screen.photos)
                        },
                        onBackToScan = {
                            viewModel.navigateToScanning()
                        }
                    )
                }
                is AppScreen.ProcessingQueue -> {
                    ProcessingView(
                        progressMessage = uploadProgress
                    )
                }
                is AppScreen.ProductDetail -> {
                    ProductDetailView(
                        product = screen.product,
                        barcode = screen.barcode,
                        fromSearch = screen.fromSearch,
                        onBackToScan = {
                            viewModel.navigateToScanning()
                        }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Permiso de Cámara Requerido",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Esta aplicación requiere acceso a la cámara para escanear los códigos de barras y tomar fotos de los productos.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Otorgar Permiso")
                        }
                    }
                }
            }
        }
    }
}
