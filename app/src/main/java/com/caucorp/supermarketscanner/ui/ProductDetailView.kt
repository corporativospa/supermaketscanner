package com.caucorp.supermarketscanner.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caucorp.supermarketscanner.model.Product
import com.caucorp.supermarketscanner.viewmodel.EditableField

@Composable
fun ProductDetailView(
    product: Product,
    barcode: String,
    fromSearch: Boolean,
    onBackToScan: () -> Unit,
    actionButtonText: String = "Escanear Siguiente",
    onClose: (() -> Unit)? = null,
    onHide: (() -> Unit)? = null,
    editingField: EditableField? = null,
    editDraftValue: String = "",
    isSavingField: Boolean = false,
    editError: String? = null,
    onStartEditField: (EditableField, String) -> Unit = { _, _ -> },
    onEditDraftChange: (String) -> Unit = {},
    onSaveEditField: () -> Unit = {},
    onCancelEditField: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Badge / Icon
                if (!fromSearch) {
                    // Success extraction animation badge
                    Row(
                        modifier = Modifier
                            .background(
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Éxito",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Extracción Gemini Exitosa",
                            color = Color(0xFF2E7D32),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Producto Existente",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = if (fromSearch) "Producto Encontrado" else "Nuevo Producto Registrado",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Código: $barcode",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(24.dp))

                // Details Area
                EditableDetailRow(
                    label = "MARCA",
                    value = product.marca,
                    field = EditableField.MARCA,
                    editingField = editingField,
                    editDraftValue = editDraftValue,
                    isSavingField = isSavingField,
                    onStartEditField = onStartEditField,
                    onEditDraftChange = onEditDraftChange,
                    onSaveEditField = onSaveEditField,
                    onCancelEditField = onCancelEditField
                )
                Spacer(modifier = Modifier.height(16.dp))
                EditableDetailRow(
                    label = "NOMBRE DEL PRODUCTO",
                    value = product.nombre,
                    field = EditableField.NOMBRE,
                    editingField = editingField,
                    editDraftValue = editDraftValue,
                    isSavingField = isSavingField,
                    onStartEditField = onStartEditField,
                    onEditDraftChange = onEditDraftChange,
                    onSaveEditField = onSaveEditField,
                    onCancelEditField = onCancelEditField
                )
                Spacer(modifier = Modifier.height(16.dp))
                EditableDetailRow(
                    label = "CONTENIDO / NETO",
                    value = product.contenido,
                    field = EditableField.CONTENIDO,
                    editingField = editingField,
                    editDraftValue = editDraftValue,
                    isSavingField = isSavingField,
                    onStartEditField = onStartEditField,
                    onEditDraftChange = onEditDraftChange,
                    onSaveEditField = onSaveEditField,
                    onCancelEditField = onCancelEditField
                )

                if (!editError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = editError,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(36.dp))

                val detailActionsEnabled = editingField == null && !isSavingField

                if (onClose != null && onHide != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onHide,
                            enabled = detailActionsEnabled,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = "Ocultar",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Button(
                            onClick = onClose,
                            enabled = detailActionsEnabled,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = "Cerrar",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = onBackToScan,
                        enabled = detailActionsEnabled,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Escanear otro"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = actionButtonText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditableDetailRow(
    label: String,
    value: String,
    field: EditableField,
    editingField: EditableField?,
    editDraftValue: String,
    isSavingField: Boolean,
    onStartEditField: (EditableField, String) -> Unit,
    onEditDraftChange: (String) -> Unit,
    onSaveEditField: () -> Unit,
    onCancelEditField: () -> Unit
) {
    val isEditingThisField = editingField == field
    val canStartEditing = editingField == null && !isSavingField

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f)
            )

            if (isEditingThisField) {
                IconButton(onClick = onSaveEditField, enabled = !isSavingField) {
                    if (isSavingField) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Guardar campo")
                    }
                }
                IconButton(onClick = onCancelEditField, enabled = !isSavingField) {
                    Icon(Icons.Default.Close, contentDescription = "Cancelar edición")
                }
            } else {
                IconButton(
                    onClick = { onStartEditField(field, value) },
                    enabled = canStartEditing
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar campo")
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        if (isEditingThisField) {
            OutlinedTextField(
                value = editDraftValue,
                onValueChange = onEditDraftChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isSavingField
            )
        } else {
            Text(
                text = value.ifEmpty { "No especificado" },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
