package com.example.supermarketscanner.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.supermarketscanner.model.Product
import com.example.supermarketscanner.repository.ProductRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed class AppScreen {
    data object Scanning : AppScreen()
    data class ProductDetail(val product: Product, val barcode: String, val fromSearch: Boolean) : AppScreen()
    data class CapturePhotos(val barcode: String, val photos: List<Bitmap> = emptyList()) : AppScreen()
    data class ProcessingQueue(val barcode: String) : AppScreen()
}

class MainViewModel : ViewModel() {
    private val repository = ProductRepository()

    private val _currentScreen = MutableStateFlow<AppScreen>(AppScreen.Scanning)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _isCheckingBarcode = MutableStateFlow(false)
    val isCheckingBarcode: StateFlow<Boolean> = _isCheckingBarcode.asStateFlow()

    private val _uploadProgress = MutableStateFlow<String?>(null)
    val uploadProgress: StateFlow<String?> = _uploadProgress.asStateFlow()

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
        _currentScreen.value = AppScreen.ProcessingQueue(barcode)
        
        viewModelScope.launch {
            _uploadProgress.value = "Subiendo imágenes a Storage..."
            try {
                photos.forEachIndexed { index, bitmap ->
                    _uploadProgress.value = "Subiendo imagen ${index + 1} de ${photos.size}..."
                    repository.uploadProductImage(barcode, bitmap)
                }
                
                _uploadProgress.value = "PROCESANDO EN COLA...\nEsperando extracción de datos con Gemini..."
                
                repository.listenToProduct(barcode).collectLatest { product ->
                    if (product != null) {
                        _currentScreen.value = AppScreen.ProductDetail(product, barcode, fromSearch = false)
                        _uploadProgress.value = null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uploadProgress.value = "Error en la subida: ${e.message}"
            }
        }
    }

    fun navigateToScanning() {
        _currentScreen.value = AppScreen.Scanning
        _isCheckingBarcode.value = false
        _uploadProgress.value = null
    }
}
