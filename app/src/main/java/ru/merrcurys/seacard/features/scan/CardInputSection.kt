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
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Photo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.merrcurys.seacard.core.design.GradientBackground
import ru.merrcurys.seacard.core.design.GradientUtils
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.layout.ContentScale
import ru.merrcurys.seacard.core.barcode.BARCODE_TYPE_OPTIONS
import ru.merrcurys.seacard.core.barcode.generateBarcodeBitmap
import ru.merrcurys.seacard.core.barcode.validateBarcodeCode
import ru.merrcurys.seacard.core.utils.DominantColorExtractor

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

private val CoverPickerShape = RoundedCornerShape(18.dp)

@Composable
private fun loadBitmapFromUri(uri: Uri?): android.graphics.Bitmap? {
    val context = LocalContext.current
    return remember(uri) {
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            } catch (_: Exception) {
                null
            }
        }
    }
}

@Composable
private fun CoverPickerSlot(
    label: String,
    modifier: Modifier = Modifier,
    bitmap: android.graphics.Bitmap?,
    onClick: () -> Unit,
    showRemove: Boolean,
    onRemove: (() -> Unit)?,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Text(label, fontSize = 14.sp, color = colorScheme.onSurface)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.574f)
                .graphicsLayer {
                    shape = CoverPickerShape
                    clip = true
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .background(Color.LightGray)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        if (showRemove && onRemove != null) {
            TextButton(onClick = onRemove) {
                Text("Удалить")
            }
        }
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

private fun colorIntToHsv(color: Int): FloatArray =
    FloatArray(3).also { AndroidColor.colorToHSV(color and 0xFFFFFF or 0xFF000000.toInt(), it) }

private fun hsvToOpaqueColorInt(hue: Float, saturation: Float, value: Float): Int =
    AndroidColor.HSVToColor(floatArrayOf(hue, saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f)))

private fun hsvToComposeColor(hue: Float, saturation: Float, value: Float): Color =
    Color(hsvToOpaqueColorInt(hue, saturation, value))

@Composable
private fun CardColorPreviewRow(
    selectedColor: Int,
    onPickColor: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val previewColor = Color(selectedColor)
    val borderColor = colorScheme.onSurface.copy(alpha = 0.25f)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Цвет карты",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(previewColor)
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPickColor
                    )
            )
            FilledIconButton(
                onClick = onPickColor,
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colorScheme.surfaceVariant,
                    contentColor = colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Colorize,
                    contentDescription = "Выбрать цвет"
                )
            }
        }
    }
}

@Composable
private fun CardRgbColorDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val initialHsv = remember(initialColor) { colorIntToHsv(initialColor) }
    var hue by remember(initialColor) { mutableStateOf(initialHsv[0]) }
    var saturation by remember(initialColor) { mutableStateOf(initialHsv[1]) }
    var value by remember(initialColor) { mutableStateOf(initialHsv[2]) }
    val previewColor = hsvToComposeColor(hue, saturation, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите цвет") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SaturationValuePicker(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onSaturationValueChange = { s, v ->
                        saturation = s
                        value = v
                    }
                )
                HuePickerBar(
                    hue = hue,
                    onHueChange = { hue = it }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(previewColor)
                        .border(1.dp, colorScheme.onSurface.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hsvToOpaqueColorInt(hue, saturation, value)) }) {
                Text("Готово")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        containerColor = colorScheme.surface,
        titleContentColor = colorScheme.onSurface,
        textContentColor = colorScheme.onSurface
    )
}

@Composable
private fun SaturationValuePicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationValueChange: (Float, Float) -> Unit
) {
    var areaSize by remember { mutableStateOf(IntSize.Zero) }

    fun updateFromOffset(offset: Offset) {
        if (areaSize.width == 0 || areaSize.height == 0) return
        onSaturationValueChange(
            (offset.x / areaSize.width).coerceIn(0f, 1f),
            (1f - offset.y / areaSize.height).coerceIn(0f, 1f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .onSizeChanged { areaSize = it }
    ) {
        Box(Modifier.fillMaxSize().background(hsvToComposeColor(hue, 1f, 1f)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color.White, Color.Transparent))
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
            )
        )
        if (areaSize.width > 0 && areaSize.height > 0) {
            ColorPickerHandle(
                centerX = saturation * areaSize.width,
                centerY = (1f - value) * areaSize.height
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(areaSize) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        updateFromOffset(down.position)
                        drag(down.id) { change ->
                            updateFromOffset(change.position)
                            change.consume()
                        }
                    }
                }
        )
    }
}

@Composable
private fun HuePickerBar(
    hue: Float,
    onHueChange: (Float) -> Unit
) {
    var barSize by remember { mutableStateOf(IntSize.Zero) }

    fun updateFromOffset(offset: Offset) {
        if (barSize.width == 0) return
        onHueChange((offset.x / barSize.width).coerceIn(0f, 1f) * 360f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .onSizeChanged { barSize = it }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFF0000),
                            Color(0xFFFFFF00),
                            Color(0xFF00FF00),
                            Color(0xFF00FFFF),
                            Color(0xFF0000FF),
                            Color(0xFFFF00FF),
                            Color(0xFFFF0000)
                        )
                    )
                )
        )
        if (barSize.width > 0) {
            ColorPickerHandle(
                centerX = (hue / 360f) * barSize.width,
                centerY = barSize.height / 2f,
                size = 22.dp
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(barSize) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        updateFromOffset(down.position)
                        drag(down.id) { change ->
                            updateFromOffset(change.position)
                            change.consume()
                        }
                    }
                }
        )
    }
}

@Composable
private fun ColorPickerHandle(
    centerX: Float,
    centerY: Float,
    size: Dp = 24.dp
) {
    val radiusPx = with(LocalDensity.current) { (size / 2).toPx() }
    Box(
        modifier = Modifier.offset {
            IntOffset(
                (centerX - radiusPx).toInt(),
                (centerY - radiusPx).toInt()
            )
        }
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .border(2.dp, Color.White, CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.35f), CircleShape)
        )
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
    showBarcodeFields: Boolean = false,
    onPickBarcode: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val gradientColor = GradientUtils.loadGradientColorPref(context)
    var showRgbColorDialog by remember { mutableStateOf(false) }
    var colorManuallyOverridden by remember { mutableStateOf(false) }
    val coverColorKey = frontCoverUri?.toString() ?: coverAsset
    val initialCoverKey = remember { coverColorKey }

    LaunchedEffect(coverColorKey) {
        val key = coverColorKey ?: return@LaunchedEffect
        // В режиме редактирования сохраняем цвет карты при открытии; при смене обложки — пересчитываем.
        if (isEditMode && key == initialCoverKey) return@LaunchedEffect

        colorManuallyOverridden = false
        val accentColor = when {
            frontCoverUri != null -> DominantColorExtractor.fromUri(context, frontCoverUri)
            coverAsset != null -> DominantColorExtractor.fromAsset(context, coverAsset)
            else -> null
        }
        if (accentColor != null && !colorManuallyOverridden) {
            onColorChange(accentColor)
        }
    }

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
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
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
                    else -> Unit
                }
            },
            bottomBar = {
                Button(
                    onClick = onSaveCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                    enabled = canSave
                ) {
                    Text("Сохранить карту", color = colorScheme.onPrimary)
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
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

                    if (showBarcodeFields && onPickBarcode != null) {
                        OutlinedButton(
                            onClick = onPickBarcode,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Photo,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Изменить штрих-код")
                        }
                    }

                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        CardColorPreviewRow(
                            selectedColor = selectedColor,
                            onPickColor = { showRgbColorDialog = true }
                        )
                        if (showRgbColorDialog) {
                            CardRgbColorDialog(
                                initialColor = selectedColor,
                                onDismiss = { showRgbColorDialog = false },
                                onConfirm = { color ->
                                    colorManuallyOverridden = true
                                    onColorChange(color)
                                    showRgbColorDialog = false
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val frontBitmap = loadBitmap(frontCoverUri, coverAsset)
                        val backBitmap = loadBitmapFromUri(backCoverUri)

                        CoverPickerSlot(
                            label = "Лицевая обложка",
                            modifier = Modifier.weight(1f),
                            bitmap = frontBitmap,
                            onClick = { onFrontCoverPick?.invoke() },
                            showRemove = (frontCoverUri != null || coverAsset != null) && onFrontCoverRemove != null,
                            onRemove = onFrontCoverRemove,
                        )
                        CoverPickerSlot(
                            label = "Тыльная обложка",
                            modifier = Modifier.weight(1f),
                            bitmap = backBitmap,
                            onClick = { onBackCoverPick?.invoke() },
                            showRemove = backCoverUri != null && onBackCoverRemove != null,
                            onRemove = onBackCoverRemove,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
