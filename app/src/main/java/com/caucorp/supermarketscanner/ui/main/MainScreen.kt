package com.caucorp.supermarketscanner.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Rect
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
import com.caucorp.supermarketscanner.viewmodel.QueueItem
import com.caucorp.supermarketscanner.viewmodel.QueueStatus

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val queueCardBoundsByBarcode = remember { mutableStateMapOf<String, Rect>() }
    
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
            val queueItems by viewModel.queueItems.collectAsStateWithLifecycle()
            val detailEditState by viewModel.detailEditState.collectAsStateWithLifecycle()

            Box(modifier = Modifier.fillMaxSize()) {
                when (val screen = screenState) {
                    is AppScreen.Scanning -> {
                        val queueInset = if (queueItems.isNotEmpty()) 84.dp else 0.dp
                        BarcodeScannerView(
                            isChecking = isChecking,
                            rightOverlayInset = queueInset,
                            onBarcodeDetected = { barcode ->
                                viewModel.onBarcodeDetected(barcode)
                            }
                        )
                    }
                    is AppScreen.CapturePhotos -> {
                        val queueInset = if (queueItems.isNotEmpty()) 84.dp else 0.dp
                        PhotoCaptureView(
                            barcode = screen.barcode,
                            photos = screen.photos,
                            rightOverlayInset = queueInset,
                            queueTargetBoundsProvider = { queueCardBoundsByBarcode[screen.barcode] },
                            onPhotoCaptured = { bitmap ->
                                viewModel.addCapturedPhoto(screen.barcode, bitmap)
                            },
                            onPhotoRemoved = { index ->
                                viewModel.removeCapturedPhoto(screen.barcode, index)
                            },
                            onConfirmUpload = {
                                viewModel.uploadPhotosAndStartProcessing(screen.barcode, screen.photos)
                            },
                            onPrepareUpload = {
                                viewModel.prepareQueueItemForAnimation(screen.barcode, screen.photos.firstOrNull())
                            },
                            onBackToScan = {
                                viewModel.navigateToScanning()
                            }
                        )
                    }
                    is AppScreen.ProcessingQueue -> {
                        ProcessingView(
                            progressMessage = uploadProgress,
                            onHide = { viewModel.hideQueueOverlay() }
                        )
                    }
                    is AppScreen.ProductDetail -> {
                        val isReadyItem = queueItems.any { it.barcode == screen.barcode && it.status == QueueStatus.READY }
                        ProductDetailView(
                            product = screen.product,
                            barcode = screen.barcode,
                            fromSearch = screen.fromSearch,
                            onBackToScan = {
                                if (isReadyItem && !screen.fromSearch) {
                                    viewModel.closeQueueReadyItem(screen.barcode)
                                } else {
                                    viewModel.navigateToScanning()
                                }
                            },
                            actionButtonText = if (isReadyItem && !screen.fromSearch) "Cerrar" else "Escanear Siguiente",
                            onClose = if (isReadyItem && !screen.fromSearch) {
                                { viewModel.closeQueueReadyItem(screen.barcode) }
                            } else {
                                null
                            },
                            onHide = if (isReadyItem && !screen.fromSearch) {
                                { viewModel.hideQueueOverlay() }
                            } else {
                                null
                            },
                            editingField = detailEditState.editingField,
                            editDraftValue = detailEditState.draftValue,
                            isSavingField = detailEditState.isSaving,
                            editError = detailEditState.error,
                            onStartEditField = { field, currentValue ->
                                viewModel.startEditingField(field, currentValue)
                            },
                            onEditDraftChange = { value ->
                                viewModel.updateEditDraft(value)
                            },
                            onSaveEditField = {
                                viewModel.saveEditingField(screen.barcode)
                            },
                            onCancelEditField = {
                                viewModel.cancelEditingField()
                            }
                        )
                    }
                }

                val showQueuePanel = screenState is AppScreen.Scanning || screenState is AppScreen.CapturePhotos
                if (showQueuePanel && queueItems.isNotEmpty()) {
                    QueuePanel(
                        items = queueItems,
                        onItemClick = { barcode -> viewModel.onQueueItemSelected(barcode) },
                        onItemPositioned = { barcode, rect -> queueCardBoundsByBarcode[barcode] = rect },
                        modifier = Modifier.align(Alignment.TopEnd)
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

@Composable
private fun QueuePanel(
    items: List<QueueItem>,
    onItemClick: (String) -> Unit,
    onItemPositioned: (String, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .padding(top = 96.dp, end = 12.dp)
            .width(72.dp)
            .fillMaxHeight(0.75f),
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.barcode }) { item ->
            QueueItemCard(
                item = item,
                onClick = { onItemClick(item.barcode) },
                onPositioned = { rect -> onItemPositioned(item.barcode, rect) }
            )
        }
    }
}

@Composable
private fun QueueItemCard(
    item: QueueItem,
    onClick: () -> Unit,
    onPositioned: (Rect) -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        modifier = Modifier.size(72.dp)
            .onGloballyPositioned { coordinates ->
                onPositioned(coordinates.boundsInWindow())
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
        ) {
            if (item.previewPhoto != null) {
                Image(
                    bitmap = item.previewPhoto.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {}

            when (item.status) {
                QueueStatus.READY -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF8BE28B))
                QueueStatus.ERROR -> Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFFF8A80))
                else -> {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
                }
            }
        }
    }
}
