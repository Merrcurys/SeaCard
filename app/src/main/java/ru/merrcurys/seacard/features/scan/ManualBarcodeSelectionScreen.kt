package ru.merrcurys.seacard.features.scan

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.merrcurys.seacard.core.barcode.MANUAL_BARCODE_PREVIEW_TYPES
import ru.merrcurys.seacard.core.barcode.barcodePreviewCacheKey
import ru.merrcurys.seacard.core.barcode.generateBarcodePreviewBitmap
import ru.merrcurys.seacard.core.design.GradientBackground
import ru.merrcurys.seacard.core.design.GradientUtils

private val PreviewCardHeight = 96.dp
private const val INPUT_DEBOUNCE_MS = 350L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualBarcodeSelectionScreen(
    onBack: () -> Unit,
    onBarcodeSelected: (code: String, type: String) -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val gradientColor = GradientUtils.loadGradientColorPref(context)
    var cardNumber by remember { mutableStateOf("") }
    var debouncedNumber by remember { mutableStateOf("") }
    var previewCache by remember { mutableStateOf<Map<String, Bitmap?>>(emptyMap()) }
    var isGenerating by remember { mutableStateOf(false) }

    LaunchedEffect(cardNumber) {
        isGenerating = cardNumber.isNotBlank()
        delay(INPUT_DEBOUNCE_MS)
        debouncedNumber = cardNumber
    }

    LaunchedEffect(debouncedNumber) {
        val code = debouncedNumber
        if (code.isBlank()) {
            previewCache = emptyMap()
            isGenerating = false
            return@LaunchedEffect
        }
        val generated = withContext(Dispatchers.Default) {
            buildMap {
                MANUAL_BARCODE_PREVIEW_TYPES.forEach { type ->
                    val key = barcodePreviewCacheKey(type.key)
                    put(key, generateBarcodePreviewBitmap(code, type.key))
                }
            }
        }
        previewCache = generated
        isGenerating = false
    }

    GradientBackground(gradientColor = gradientColor) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        "Выбор штрих-кода",
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = cardNumber,
                        onValueChange = { cardNumber = it },
                        label = { Text("Номер карты") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface,
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = colorScheme.onSurface.copy(alpha = 0.5f),
                            focusedLabelColor = colorScheme.primary,
                            unfocusedLabelColor = colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    )
                }
                item {
                    Text(
                        text = "Введите номер с карты и нажмите тот штрих-код, который выглядит так же, как на карте.",
                        color = colorScheme.onSurface.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }

                items(MANUAL_BARCODE_PREVIEW_TYPES, key = { it.key }) { typeOption ->
                    BarcodePreviewRow(
                        typeKey = typeOption.key,
                        typeLabel = typeOption.label,
                        previewCache = previewCache,
                        isGenerating = isGenerating && cardNumber.isNotBlank(),
                        onSelect = {
                            onBarcodeSelected(cardNumber, typeOption.key)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BarcodePreviewRow(
    typeKey: String,
    typeLabel: String,
    previewCache: Map<String, Bitmap?>,
    isGenerating: Boolean,
    onSelect: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val bitmap = previewCache[barcodePreviewCacheKey(typeKey)]

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = typeLabel,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        BarcodePreviewCell(
            modifier = Modifier.fillMaxWidth(),
            typeKey = typeKey,
            bitmap = bitmap,
            isGenerating = isGenerating,
            onClick = onSelect
        )
    }
}

@Composable
private fun BarcodePreviewCell(
    modifier: Modifier = Modifier,
    typeKey: String,
    bitmap: Bitmap?,
    isGenerating: Boolean,
    onClick: () -> Unit
) {
    val isActive = bitmap != null

    Box(
        modifier = modifier
            .height(PreviewCardHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .then(if (isActive) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = typeKey,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Filled.QrCode2,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier
                    .size(28.dp)
                    .alpha(0.35f)
            )
            if (isGenerating) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = 0.45f))
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = Color.Gray
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Gray.copy(alpha = 0.28f))
                )
            }
        }
    }
}
