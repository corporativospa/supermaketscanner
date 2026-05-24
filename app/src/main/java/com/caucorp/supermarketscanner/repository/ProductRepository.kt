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
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            val data = baos.toByteArray()
            
            val timestamp = System.currentTimeMillis()
            val storageRef = storage.reference.child("productos/$barcode/$timestamp.jpg")
            storageRef.putBytes(data).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
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
