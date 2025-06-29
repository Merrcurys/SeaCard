package com.example.qrbonus

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.provider.Settings
import android.content.Intent
import com.example.qrbonus.ui.theme.QRBonusTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.QRCodeWriter
import androidx.core.graphics.set
import androidx.core.graphics.createBitmap
import androidx.core.content.edit
import androidx.core.net.toUri

class CardDetailActivity : ComponentActivity() {
    private var originalBrightness: Float = 0f
    private var hasBrightnessPermission: Boolean = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val cardName = intent.getStringExtra("card_name") ?: ""
        val cardCode = intent.getStringExtra("card_code") ?: ""
        val codeType = intent.getStringExtra("code_type") ?: "barcode"
        
        // Проверяем разрешение на изменение яркости
        hasBrightnessPermission = Settings.System.canWrite(this)
        
        // Сохраняем текущую яркость и увеличиваем её
        if (hasBrightnessPermission) {
            originalBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
            setBrightness(1.0f) // Максимальная яркость
        }
        
        setContent {
            var showDeleteDialog by remember { mutableStateOf(false) }
            var isDark by remember { mutableStateOf(loadThemePref(this@CardDetailActivity)) }
            var showPermissionDialog by remember { mutableStateOf(!hasBrightnessPermission) }
            
            // Обновляем проверку разрешения при изменении состояния
            LaunchedEffect(Unit) {
                if (Settings.System.canWrite(this@CardDetailActivity)) {
                    hasBrightnessPermission = true
                    originalBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
                    setBrightness(1.0f)
                }
            }
            
            QRBonusTheme(darkTheme = isDark) {
                CardDetailScreen(
                    cardName = cardName,
                    cardCode = cardCode,
                    codeType = codeType,
                    onBack = { finish() },
                    onDelete = { showDeleteDialog = true }
                )
                
                // Диалог запроса разрешения на изменение яркости
                if (showPermissionDialog) {
                    AlertDialog(
                        onDismissRequest = { showPermissionDialog = false },
                        title = { Text("Разрешение на изменение яркости") },
                        text = { Text("Для лучшего сканирования кодов приложению нужно разрешение на изменение яркости экрана. Перейдите в настройки и включите разрешение для QRБонус.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                                    intent.data = "package:$packageName".toUri()
                                    startActivity(intent)
                                    showPermissionDialog = false
                                }
                            ) {
                                Text("Настройки", color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showPermissionDialog = false }) {
                                Text("Отмена")
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        textContentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Диалог подтверждения удаления
                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = { Text("Удалить карту?") },
                        text = { Text("Карта \"$cardName\" будет удалена безвозвратно.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    deleteCard(this@CardDetailActivity, cardName, cardCode)
                                    setResult(RESULT_OK)
                                    finish()
                                }
                            ) {
                                Text("Удалить", color = Color.Red)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text("Отмена")
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        textContentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Восстанавливаем оригинальную яркость при закрытии экрана
        if (hasBrightnessPermission) {
            setBrightness(originalBrightness)
        }
    }
    
    private fun setBrightness(brightness: Float) {
        try {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = brightness.coerceIn(0.01f, 1.0f)
            window.attributes = layoutParams
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun deleteCard(context: android.content.Context, name: String, code: String) {
        val prefs = context.getSharedPreferences("cards", android.content.Context.MODE_PRIVATE)
        val cards = prefs.getStringSet("card_list", setOf())?.toMutableSet() ?: mutableSetOf()
        
        // Удаляем карту по имени (первая часть строки)
        val cardToRemove = cards.find { cardString ->
            val parts = cardString.split("|")
            parts.isNotEmpty() && parts[0] == name
        }
        
        cardToRemove?.let { cards.remove(it) }
        prefs.edit { putStringSet("card_list", cards) }
    }
    
    private fun loadThemePref(context: Context): Boolean {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("dark_theme", true)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    cardName: String,
    cardCode: String,
    codeType: String,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    var barcodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    
    LaunchedEffect(cardCode, codeType) {
        barcodeBitmap = if (codeType == "qr") {
            generateQRCode(cardCode)
        } else {
            generateBarcode(cardCode)
        }
    }
    
    // Функция копирования в буфер обмена
    fun copyToClipboard() {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Код карты", cardCode)
        clipboardManager.setPrimaryClip(clip)
        Toast.makeText(context, "Код скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column {
            TopAppBar(
                title = { Text(cardName, color = colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить карту", tint = colorScheme.onSurface)
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Код (QR или штрихкод)
                if (barcodeBitmap != null) {
                    Text(
                        text = if (codeType == "qr") "QR-код" else "Штрихкод",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (codeType == "qr") 350.dp else 300.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = barcodeBitmap!!.asImageBitmap(),
                                contentDescription = if (codeType == "qr") "QR-код" else "Штрихкод",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (codeType == "qr") 280.dp else 230.dp)
                            )
                        }
                    }
                }
                
                // Разделитель
                Divider(
                    color = colorScheme.onSurface.copy(alpha = 0.2f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                
                // Код карты внизу
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Код карты",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        copyToClipboard()
                                    }
                                )
                            }
                    ) {
                        Text(
                            text = cardCode,
                            fontSize = if (cardCode.length > 20) 18.sp else 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun generateQRCode(content: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 2 // Добавляем небольшие отступы
        hints[EncodeHintType.ERROR_CORRECTION] = com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M // Средний уровень коррекции ошибок
        
        val bitMatrix: BitMatrix = writer.encode(
            content,
            BarcodeFormat.QR_CODE,
            600, // Увеличиваем размер для лучшего качества
            600,
            hints
        )
        
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = createBitmap(width, height)
        
        // Создаем красивый QR-код с закругленными углами
        for (x in 0 until width) {
            for (y in 0 until height) {
                val isBlack = bitMatrix[x, y]
                
                // Определяем, находимся ли мы в угловых маркерах (3 больших квадрата)
                val isCornerMarker = isInCornerMarker(x, y, width, height)
                
                if (isBlack) {
                    if (isCornerMarker) {
                        // Темно-синий цвет для угловых маркеров
                        bitmap[x, y] = AndroidColor.rgb(25, 118, 210)
                    } else
                        // Черный цвет для остальных элементов
                        bitmap[x, y] = AndroidColor.BLACK
                } else {
                    // Белый фон
                    bitmap[x, y] = AndroidColor.WHITE
                }
            }
        }
        
        bitmap
    } catch (e: WriterException) {
        e.printStackTrace()
        null
    }
}

private fun isInCornerMarker(x: Int, y: Int, width: Int, height: Int): Boolean {
    val markerSize = 7 // Размер углового маркера
    
    // Верхний левый угол
    if (x < markerSize && y < markerSize) return true
    
    // Верхний правый угол
    if (x >= width - markerSize && y < markerSize) return true
    
    // Нижний левый угол
    if (x < markerSize && y >= height - markerSize) return true
    
    return false
}

private fun generateBarcode(content: String): Bitmap? {
    return try {
        val writer = Code128Writer()
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 0
        
        val bitMatrix: BitMatrix = writer.encode(
            content,
            BarcodeFormat.CODE_128,
            800,
            200,
            hints
        )
        
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = createBitmap(width, height)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap[x, y] = if (bitMatrix[x, y]) AndroidColor.BLACK else AndroidColor.WHITE
            }
        }
        
        bitmap
    } catch (e: WriterException) {
        e.printStackTrace()
        null
    }
} 