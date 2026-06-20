package ru.merrcurys.seacard.features.scan

import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.merrcurys.seacard.core.design.GradientBackground
import ru.merrcurys.seacard.core.design.GradientUtils
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.ContentScale
import ru.merrcurys.seacard.core.barcode.BARCODE_TYPE_OPTIONS
import ru.merrcurys.seacard.core.barcode.generateBarcodeBitmap
import ru.merrcurys.seacard.core.barcode.validateBarcodeCode

// Функция для загрузки bitmap из URI или asset
@Composable
fun loadBitmap(frontCoverUri: Uri?, coverAsset: String?): android.graphics.Bitmap? {
    val context = LocalContext.current
    return remember(frontCoverUri, coverAsset) {
        try {
            var result: android.graphics.Bitmap? = null
            frontCoverUri?.let { uri ->
                try {
                    val input = context.contentResolver.openInputStream(uri)
                    val bmp = BitmapFactory.decodeStream(input)
                    input?.close()
                    result = bmp
                } catch (_: Exception) { }
            }
            if (result == null && coverAsset != null) {
                try {
                    val input = context.assets.open(coverAsset)
                    val bmp = BitmapFactory.decodeStream(input)
                    input.close()
                    result = bmp
                } catch (_: Exception) { }
            }
            result
        } catch (_: Exception) { null }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarcodeDropdownField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = options.firstOrNull { it.first == value }?.second ?: value

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colorScheme.onSurface,
                unfocusedTextColor = colorScheme.onSurface,
                disabledTextColor = colorScheme.onSurface.copy(alpha = 0.5f),
                focusedBorderColor = colorScheme.primary,
                unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.5f),
                focusedLabelColor = colorScheme.primary,
                unfocusedLabelColor = colorScheme.onSurface.copy(alpha = 0.7f)
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(colorScheme.surface)
        ) {
            options.forEach { (key, optionLabel) ->
                DropdownMenuItem(
                    text = { Text(optionLabel) },
                    onClick = {
                        onValueChange(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

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
    isEditMode: Boolean = false,
    frontCoverUri: Uri? = null,
    backCoverUri: Uri? = null,
    onFrontCoverPick: (() -> Unit)? = null,
    onBackCoverPick: (() -> Unit)? = null,
    onFrontCoverRemove: (() -> Unit)? = null,
    onBackCoverRemove: (() -> Unit)? = null,
    codeType: String = "code128",
    onCodeTypeChange: ((String) -> Unit)? = null,
    showBarcodeFields: Boolean = false
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val gradientColor = GradientUtils.loadGradientColorPref(context)
    val cardColors = listOf(
        0xFFFFFFFF.toInt(),
        0xFFFF4444.toInt(),
        0xFF4CAF50.toInt(),
        0xFF2196F3.toInt(),
        0xFFFF9800.toInt(),
        0xFFFFEB3B.toInt(),
        0xFFE91E63.toInt(),
        0xFF9C27B0.toInt(),
        0xFF000000.toInt(),
        0xFF9E9E9E.toInt()
    )

    val codeError by remember(cardCode, codeType, showBarcodeFields) {
        derivedStateOf {
            if (!showBarcodeFields || codeType == "none") null
            else validateBarcodeCode(cardCode, codeType)
        }
    }

    val barcodeBitmap by remember(cardCode, codeType, showBarcodeFields, codeError) {
        derivedStateOf {
            if (!showBarcodeFields || codeType == "none" || codeError != null) null
            else generateBarcodeBitmap(cardCode, codeType)
        }
    }

    val canSave by remember(cardName, cardCode, codeType, codeError, showBarcodeFields) {
        derivedStateOf {
            if (cardName.isBlank()) false
            else if (!showBarcodeFields) cardCode.isNotBlank()
            else if (codeType == "none") true
            else cardCode.isNotBlank() && codeError == null
        }
    }

    GradientBackground(gradientColor = gradientColor) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                isEditMode -> {
                    TopAppBar(
                        title = {
                            Text(
                                "Изменить карту",
                                color = colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = colorScheme.onSurface)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
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
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = colorScheme.onSurface)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
                else -> {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            Box(
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = cardName,
                        onValueChange = { if (it.length <= 20) onCardNameChange(it) },
                        label = { Text("Название") },
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

                    val showCodeError = codeError != null && cardCode.isNotBlank() && codeType != "none" && showBarcodeFields
                    OutlinedTextField(
                        value = cardCode,
                        onValueChange = { if (showBarcodeFields) onCardCodeChange(it) },
                        label = { Text("Номер карты") },
                        readOnly = !showBarcodeFields,
                        singleLine = true,
                        isError = showCodeError,
                        supportingText = if (showCodeError) {
                            { Text(codeError!!, color = colorScheme.error) }
                        } else null,
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

                    if (showBarcodeFields && onCodeTypeChange != null) {
                        BarcodeDropdownField(
                            label = "Тип штрих-кода",
                            value = codeType,
                            options = BARCODE_TYPE_OPTIONS.map { it.key to it.label },
                            onValueChange = onCodeTypeChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }

                    if (barcodeBitmap != null) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            val isSquare = codeType in listOf("qr", "datamatrix", "aztec", "pdf417")
                            Image(
                                bitmap = barcodeBitmap!!.asImageBitmap(),
                                contentDescription = "Предпросмотр штрих-кода",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (isSquare) 200.dp else 100.dp)
                                    .padding(12.dp)
                            )
                        }
                    }

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
                                                    interactionSource = remember { MutableInteractionSource() },
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
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { onColorChange(color) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("Лицевая обложка", fontSize = 14.sp, color = colorScheme.onSurface)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.574f)
                                    .background(Color.LightGray, shape = RoundedCornerShape(18.dp))
                                    .clickable { onFrontCoverPick?.invoke() },
                                contentAlignment = Alignment.Center
                            ) {
                                val frontBitmap = loadBitmap(frontCoverUri, coverAsset)

                                if (frontBitmap != null) {
                                    Image(
                                        bitmap = frontBitmap.asImageBitmap(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(18.dp))
                                    )
                                } else {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                                }
                            }
                            if ((frontCoverUri != null || coverAsset != null) && onFrontCoverRemove != null) {
                                TextButton(onClick = { onFrontCoverRemove() }) {
                                    Text("Удалить")
                                }
                            }
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("Тыльная обложка", fontSize = 14.sp, color = colorScheme.onSurface)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.574f)
                                    .background(Color.LightGray, shape = RoundedCornerShape(18.dp))
                                    .clickable { onBackCoverPick?.invoke() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (backCoverUri != null) {
                                    val context = LocalContext.current
                                    val bitmap = remember(backCoverUri) {
                                        try {
                                            val input = context.contentResolver.openInputStream(backCoverUri)
                                            val bmp = BitmapFactory.decodeStream(input)
                                            input?.close()
                                            bmp
                                        } catch (_: Exception) { null }
                                    }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(18.dp))
                                        )
                                    }
                                } else {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                                }
                            }
                            if (backCoverUri != null && onBackCoverRemove != null) {
                                TextButton(onClick = { onBackCoverRemove() }) {
                                    Text("Удалить")
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            Button(
                onClick = onSaveCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                enabled = canSave
            ) {
                Text("Сохранить карту", color = colorScheme.onPrimary)
            }
        }
    }
}
