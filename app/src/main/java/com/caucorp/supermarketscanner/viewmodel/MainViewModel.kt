package com.caucorp.supermarketscanner.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caucorp.supermarketscanner.model.Product
import com.caucorp.supermarketscanner.repository.ProductRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class AppScreen {
    data object Scanning : AppScreen()
    data class ProductDetail(val product: Product, val barcode: String, val fromSearch: Boolean) : AppScreen()
    data class CapturePhotos(val barcode: String, val photos: List<Bitmap> = emptyList()) : AppScreen()
    data class ProcessingQueue(val barcode: String) : AppScreen()
}

enum class QueueStatus {
    UPLOADING,
    PROCESSING,
    READY,
    ERROR
}

data class QueueItem(
    val barcode: String,
    val status: QueueStatus,
    val progressMessage: String,
    val product: Product? = null,
    val previewPhoto: Bitmap? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class MainViewModel : ViewModel() {
    private val repository = ProductRepository()
    private val queueJobs = mutableMapOf<String, Job>()
    private var previousScreenBeforeQueueOpen: AppScreen = AppScreen.Scanning
    private var activeQueueBarcode: String? = null

    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Scanning)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _isCheckingBarcode = MutableStateFlow(false)
    val isCheckingBarcode: StateFlow<Boolean> = _isCheckingBarcode.asStateFlow()

    private val _uploadProgress = MutableStateFlow<String?>(null)
    val uploadProgress: StateFlow<String?> = _uploadProgress.asStateFlow()

    private val _queueItems = MutableStateFlow<List<QueueItem>>(emptyList())
    val queueItems: StateFlow<List<QueueItem>> = _queueItems.asStateFlow()

    fun onBarcodeDetected(barcode: String) {
        if (_isCheckingBarcode.value) return
        _isCheckingBarcode.value = true
        
        viewModelScope.launch {
            val product = repository.getProduct(barcode)
            _isCheckingBarcode.value = false
            if (product != null) {
                _currentScreen.value = AppScreen.ProductDetail(product, barcode, fromSearch = true)
            } else {
                _currentScreen.value = AppScreen.CapturePhotos(barcode)
            }
        }
    }

    fun addCapturedPhoto(barcode: String, bitmap: Bitmap) {
        val screen = _currentScreen.value
        if (screen is AppScreen.CapturePhotos && screen.barcode == barcode) {
            _currentScreen.value = screen.copy(photos = screen.photos + bitmap)
        }
    }

    fun removeCapturedPhoto(barcode: String, index: Int) {
        val screen = _currentScreen.value
        if (screen is AppScreen.CapturePhotos && screen.barcode == barcode) {
            val updatedPhotos = screen.photos.toMutableList().apply { removeAt(index) }
            _currentScreen.value = screen.copy(photos = updatedPhotos)
        }
    }

    fun uploadPhotosAndStartProcessing(barcode: String, photos: List<Bitmap>) {
        if (photos.isEmpty()) return

        prepareQueueItemForAnimation(barcode, photos.firstOrNull())
        _currentScreen.value = AppScreen.Scanning

        queueJobs[barcode]?.cancel()
        queueJobs[barcode] = viewModelScope.launch {
            try {
                photos.forEachIndexed { index, bitmap ->
                    upsertQueueItem(
                        barcode = barcode,
                        status = QueueStatus.UPLOADING,
                        message = "Subiendo imagen ${index + 1} de ${photos.size}...",
                        previewPhoto = photos.firstOrNull()
                    )
                    repository.uploadProductImage(barcode, bitmap)
                }

                upsertQueueItem(
                    barcode = barcode,
                    status = QueueStatus.PROCESSING,
                    message = "PROCESANDO EN COLA... Esperando extracción de datos con Gemini...",
                    previewPhoto = photos.firstOrNull()
                )

                val product = repository.listenToProduct(barcode).first { it != null }
                upsertQueueItem(
                    barcode = barcode,
                    status = QueueStatus.READY,
                    message = "Producto procesado",
                    product = product,
                    previewPhoto = photos.firstOrNull()
                )
            } catch (e: Exception) {
                e.printStackTrace()
                upsertQueueItem(
                    barcode = barcode,
                    status = QueueStatus.ERROR,
                    message = "Error: ${e.message ?: "fallo desconocido"}",
                    previewPhoto = photos.firstOrNull()
                )
            } finally {
                queueJobs.remove(barcode)
            }
        }
    }

    fun prepareQueueItemForAnimation(barcode: String, previewPhoto: Bitmap?) {
        upsertQueueItem(
            barcode = barcode,
            status = QueueStatus.UPLOADING,
            message = "Subiendo imágenes a Storage...",
            previewPhoto = previewPhoto
        )
    }

    fun onQueueItemSelected(barcode: String) {
        val item = _queueItems.value.firstOrNull { it.barcode == barcode } ?: return
        previousScreenBeforeQueueOpen = _currentScreen.value
        activeQueueBarcode = barcode
        when {
            item.status == QueueStatus.READY && item.product != null -> {
                _currentScreen.value = AppScreen.ProductDetail(item.product, item.barcode, fromSearch = false)
            }
            else -> {
                _uploadProgress.value = item.progressMessage
                _currentScreen.value = AppScreen.ProcessingQueue(item.barcode)
            }
        }
    }

    fun hideQueueOverlay() {
        activeQueueBarcode = null
        _currentScreen.value = previousScreenBeforeQueueOpen
    }

    fun closeQueueReadyItem(barcode: String) {
        activeQueueBarcode = null
        removeQueueItem(barcode)
        _currentScreen.value = previousScreenBeforeQueueOpen
    }

    fun dismissQueueError(barcode: String) {
        removeQueueItem(barcode)
    }

    fun navigateToScanning() {
        _currentScreen.value = AppScreen.Scanning
        _isCheckingBarcode.value = false
        _uploadProgress.value = null
    }

    private fun upsertQueueItem(
        barcode: String,
        status: QueueStatus,
        message: String,
        product: Product? = null,
        previewPhoto: Bitmap? = null
    ) {
        val existing = _queueItems.value.firstOrNull { it.barcode == barcode }
        val updated = QueueItem(
            barcode = barcode,
            status = status,
            progressMessage = message,
            product = product ?: existing?.product,
            previewPhoto = previewPhoto ?: existing?.previewPhoto,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )

        _queueItems.value = _queueItems.value
            .filterNot { it.barcode == barcode }
            .plus(updated)
            .sortedByDescending { it.createdAt }

        maybePromoteActiveQueueItemToDetail()
    }

    private fun maybePromoteActiveQueueItemToDetail() {
        val activeBarcode = activeQueueBarcode ?: return
        val current = _currentScreen.value
        if (current !is AppScreen.ProcessingQueue || current.barcode != activeBarcode) return

        val activeItem = _queueItems.value.firstOrNull { it.barcode == activeBarcode } ?: return
        _uploadProgress.value = activeItem.progressMessage

        if (activeItem.status == QueueStatus.READY && activeItem.product != null) {
            _currentScreen.value = AppScreen.ProductDetail(
                product = activeItem.product,
                barcode = activeItem.barcode,
                fromSearch = false
            )
        }
    }

    private fun removeQueueItem(barcode: String) {
        queueJobs.remove(barcode)?.cancel()
        _queueItems.value = _queueItems.value.filterNot { it.barcode == barcode }
    }

    override fun onCleared() {
        super.onCleared()
        queueJobs.values.forEach { it.cancel() }
        queueJobs.clear()
    }
}
