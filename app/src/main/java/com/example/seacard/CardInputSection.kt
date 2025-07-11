package com.example.seacard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.seacard.ui.theme.GradientBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardInputSection(
    cardName: String,
    cardCode: String,
    selectedColor: Int,
    onCardNameChange: (String) -> Unit,
    onCardCodeChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onSaveCard: () -> Unit,
    coverAsset: String? = null,
    onBack: () -> Unit = {},
    showTopBar: Boolean = true,
    isEditMode: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background == Color(0xFF111111)
    val cardColors = listOf(
        0xFFFFFFFF.toInt(), // Белый
        0xFFFF4444.toInt(), // Красный
        0xFF4CAF50.toInt(), // Зеленый
        0xFF2196F3.toInt(), // Синий
        0xFFFF9800.toInt(), // Оранжевый
        0xFFFFEB3B.toInt(), // Желтый
        0xFFE91E63.toInt(), // Розовый
        0xFF9C27B0.toInt(), // Фиолетовый
        0xFF000000.toInt(), // Черный
        0xFF9E9E9E.toInt()  // Серый
    )
    GradientBackground(darkTheme = isDark) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                showTopBar -> {
                    TopAppBar(
                        title = {
                            Text(
                                "Добавить карту",
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = colorScheme.onSurface)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
                isEditMode -> {
                    TopAppBar(
                        title = {
                            Text(
                                "Изменение карты",
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = colorScheme.onSurface)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
                else -> {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
            OutlinedTextField(
                value = cardName,
                onValueChange = { if (it.length <= 20) onCardNameChange(it) },
                label = { Text("Название карты") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colorScheme.onSurface,
                    unfocusedTextColor = colorScheme.onSurface,
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.5f),
                    focusedLabelColor = colorScheme.primary,
                    unfocusedLabelColor = colorScheme.onSurface.copy(alpha = 0.7f)
                )
            )
            OutlinedTextField(
                value = cardCode,
                onValueChange = {},
                label = { Text("Код карты") },
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colorScheme.onSurface,
                    unfocusedTextColor = colorScheme.onSurface,
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.5f),
                    focusedLabelColor = colorScheme.primary,
                    unfocusedLabelColor = colorScheme.onSurface.copy(alpha = 0.7f)
                )
            )
            if (coverAsset == null) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = "Выберите цвет карты",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            cardColors.take(5).forEach { color ->
                                val isSelected = color == selectedColor
                                val borderColor = if (isSelected) Color(0xFFBDBDBD) else colorScheme.onSurface.copy(alpha = 0.3f)
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .background(
                                            color = Color(color),
                                            shape = CircleShape
                                        )
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = borderColor,
                                            shape = CircleShape
                                        )
                                        .clickable(
                                            interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                                            indication = null
                                        ) { onColorChange(color) }
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            cardColors.drop(5).forEach { color ->
                                val isSelected = color == selectedColor
                                val borderColor = if (isSelected) Color(0xFFBDBDBD) else colorScheme.onSurface.copy(alpha = 0.3f)
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .background(
                                            color = Color(color),
                                            shape = CircleShape
                                        )
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = borderColor,
                                            shape = CircleShape
                                        )
                                        .clickable(
                                            interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() },
                                            indication = null
                                        ) { onColorChange(color) }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onSaveCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                enabled = cardName.isNotBlank() && cardCode.isNotBlank()
            ) {
                Text("Сохранить карту", color = colorScheme.onPrimary)
            }
        }
    }
} 