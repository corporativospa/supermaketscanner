package com.caucorp.supermarketscanner.repository

import android.graphics.Bitmap
import com.caucorp.supermarketscanner.model.Product
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

class ProductRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    suspend fun getProduct(barcode: String): Product? {
        return try {
            val document = firestore.collection("productos").document(barcode).get().await()
            if (document.exists()) {
                val marca = document.getString("marca") ?: ""
                val nombre = document.getString("nombre") ?: ""
                val contenido = document.getString("contenido") ?: ""
                Product(marca, nombre, contenido)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun uploadProductImage(barcode: String, bitmap: Bitmap): String {
        return try {
            // Optimize image size: limit maximum dimension to 1024px to save bandwidth/storage while keeping text sharp
            val optimizedBitmap = resizeBitmap(bitmap, 1024)
            
            val baos = ByteArrayOutputStream()
            // Compress as JPEG 80 (visual differences are imperceptible for AI OCR, but file size is significantly smaller than 90)
            optimizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val data = baos.toByteArray()
            
            // Recycle the scaled bitmap if a new one was created to free up memory
            if (optimizedBitmap != bitmap) {
                optimizedBitmap.recycle()
            }
            
            val timestamp = System.currentTimeMillis()
            val storageRef = storage.reference.child("productos/$barcode/$timestamp.jpg")
            storageRef.putBytes(data).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (ratio > 1) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun listenToProduct(barcode: String): Flow<Product?> = callbackFlow {
        val docRef = firestore.collection("productos").document(barcode)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val marca = snapshot.getString("marca")
                val nombre = snapshot.getString("nombre")
                val contenido = snapshot.getString("contenido")
                
                if (marca != null && nombre != null && contenido != null) {
                    trySend(Product(marca, nombre, contenido))
                } else {
                    trySend(null)
                }
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }
}
