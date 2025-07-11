package com.example.seacard

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import com.example.seacard.ui.theme.SeaCardTheme
import com.example.seacard.ui.theme.GradientBackground
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
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
        val cardColor = intent.getIntExtra("card_color", 0xFFFFFFFF.toInt())
        
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
            var cardNameState by remember { mutableStateOf(cardName) }
            var cardCodeState by remember { mutableStateOf(cardCode) }
            var codeTypeState by remember { mutableStateOf(codeType) }
            var cardColorState by remember { mutableStateOf(cardColor) }
            
            // Обновляем проверку разрешения при изменении состояния
            LaunchedEffect(Unit) {
                if (Settings.System.canWrite(this@CardDetailActivity)) {
                    hasBrightnessPermission = true
                    originalBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
                    setBrightness(1.0f)
                }
            }
            
            SeaCardTheme(darkTheme = isDark) {
                GradientBackground(darkTheme = isDark) {
                    CardDetailScreen(
                        cardName = cardNameState,
                        cardCode = cardCodeState,
                        codeType = codeTypeState,
                        cardColor = cardColorState,
                        onBack = { finish() },
                        onDelete = {
                            deleteCard(this@CardDetailActivity, cardNameState, cardCodeState, codeTypeState, cardColorState)
                            setResult(RESULT_OK)
                            finish()
                        },
                        onEdit = { newName, newCode, newType, newColor ->
                            editCard(this@CardDetailActivity, cardNameState, cardCodeState, codeTypeState, cardColorState, newName, newCode, newType, newColor)
                            cardNameState = newName
                            cardCodeState = newCode
                            codeTypeState = newType
                            cardColorState = newColor
                        },
                        topBarContainerColor = Color.Transparent
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
                            text = { Text("Карта '$cardNameState' будет удалена безвозвратно.") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showDeleteDialog = false
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
    
    private fun deleteCard(context: Context, name: String, code: String, type: String, color: Int) {
        val prefs = context.getSharedPreferences("cards", Context.MODE_PRIVATE)
        val cards = prefs.getStringSet("card_list", setOf())?.toMutableSet() ?: mutableSetOf()
        val cardToRemove = cards.find { cardString ->
            val parts = cardString.split("|")
            parts.isNotEmpty() && parts[0] == name && parts[1] == code && (parts.size < 3 || parts[2] == type) && (parts.size < 6 || parts[5].toIntOrNull() == color)
        }
        cardToRemove?.let { cards.remove(it) }
        prefs.edit { putStringSet("card_list", cards) }
    }
    
    private fun editCard(context: Context, oldName: String, oldCode: String, oldType: String, oldColor: Int, newName: String, newCode: String, newType: String, newColor: Int) {
        val prefs = context.getSharedPreferences("cards", Context.MODE_PRIVATE)
        val cards = prefs.getStringSet("card_list", setOf())?.toMutableSet() ?: mutableSetOf()
        val cardToEdit = cards.find { cardString ->
            val parts = cardString.split("|")
            parts.isNotEmpty() && parts[0] == oldName && parts[1] == oldCode && (parts.size < 3 || parts[2] == oldType) && (parts.size < 6 || parts[5].toIntOrNull() == oldColor)
        }
        if (cardToEdit != null) {
            cards.remove(cardToEdit)
            val parts = cardToEdit.split("|")
            val newCardString = when (parts.size) {
                2 -> "$newName|$newCode"
                3 -> "$newName|$newCode|$newType"
                5 -> "$newName|$newCode|$newType|${parts[3]}|${parts[4]}"
                6 -> "$newName|$newCode|$newType|${parts[3]}|${parts[4]}|$newColor"
                else -> "$newName|$newCode|$newType|${System.currentTimeMillis()}|0|$newColor"
            }
            cards.add(newCardString)
            prefs.edit { putStringSet("card_list", cards) }
        }
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
    cardColor: Int,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (String, String, String, Int) -> Unit,
    topBarContainerColor: Color = Color.Transparent
) {
    var barcodeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(cardName) }
    var editCode by remember { mutableStateOf(cardCode) }
    var editType by remember { mutableStateOf(codeType) }
    var editColor by remember { mutableStateOf(cardColor) }
    var editError by remember { mutableStateOf("") }

    LaunchedEffect(editCode, editType) {
        if (isValidBarcodeWithChecksum(editCode, editType)) {
            barcodeBitmap = if (editType == "qr") {
                generateQRCode(editCode)
            } else {
                generateBarcode(editCode, editType)
            }
        }
    }

    // Функция копирования в буфер обмена
    fun copyToClipboard() {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Код карты", editCode)
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
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = colorScheme.onSurface)
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Меню", tint = colorScheme.onSurface)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Изменить карту") },
                                onClick = {
                                    showMenu = false
                                    showEditDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Удалить карту", color = Color.Red) },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarContainerColor)
            )
            // Диалог удаления
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Удалить карту?") },
                    text = { Text("Карта '$cardName' будет удалена безвозвратно.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                onDelete()
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
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.onSurface,
                    textContentColor = colorScheme.onSurface
                )
            }
            // Диалог редактирования
            if (showEditDialog) {
                Surface(
                    color = colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CardInputSection(
                        cardName = editName,
                        cardCode = editCode,
                        selectedColor = editColor,
                        onCardNameChange = { editName = it },
                        onCardCodeChange = {}, // поле не изменяется
                        onColorChange = { editColor = it },
                        onSaveCard = {
                            if (editName.isBlank()) {
                                editError = "Заполните имя карты"
                            } else {
                                showEditDialog = false
                                editError = ""
                                onEdit(editName, editCode, editType, editColor)
                            }
                        }
                    )
                    if (editError.isNotEmpty()) {
                        Text(editError, color = Color.Red, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
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
                        text = editName,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (editType == "qr") 350.dp else 300.dp),
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
                                contentDescription = editName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (editType == "qr") 280.dp else 230.dp)
                            )
                        }
                    }
                }
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
                        val formattedCode = formatBarcodeForStandard(editCode, editType)
                        Text(
                            text = formattedCode,
                            fontSize = if (editCode.length > 20) 18.sp else 24.sp,
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

// Функция для форматирования штрихкода с отступами по стандарту
fun formatBarcodeForStandard(code: String, codeType: String): String {
    return when (codeType.lowercase()) {
        "ean13" -> code.chunked(1)
            .let { if (it.size >= 13) it[0] + " " + it.subList(1,7).joinToString("") + " " + it.subList(7,13).joinToString("") else code }
        "upca" -> code.chunked(1)
            .let { if (it.size >= 12) it[0] + " " + it.subList(1,6).joinToString("") + " " + it.subList(6,11).joinToString("") + " " + it[11] else code }
        "ean8" -> code.chunked(1)
            .let { if (it.size >= 8) it.subList(0,4).joinToString("") + " " + it.subList(4,8).joinToString("") else code }
        else -> code
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

private fun generateBarcode(content: String, codeType: String = "code128"): Bitmap? {
    return try {
        val writer = when (codeType.lowercase()) {
            "ean13" -> com.google.zxing.oned.EAN13Writer()
            "upca" -> com.google.zxing.oned.UPCAWriter()
            "code128" -> com.google.zxing.oned.Code128Writer()
            "code39" -> com.google.zxing.oned.Code39Writer()
            "code93" -> com.google.zxing.oned.Code93Writer()
            "codabar" -> com.google.zxing.oned.CodaBarWriter()
            "ean8" -> com.google.zxing.oned.EAN8Writer()
            "itf" -> com.google.zxing.oned.ITFWriter()
            "upce" -> com.google.zxing.oned.UPCEWriter()
            else -> com.google.zxing.oned.Code128Writer()
        }
        val format = when (codeType.lowercase()) {
            "ean13" -> BarcodeFormat.EAN_13
            "upca" -> BarcodeFormat.UPC_A
            "code128" -> BarcodeFormat.CODE_128
            "code39" -> BarcodeFormat.CODE_39
            "code93" -> BarcodeFormat.CODE_93
            "codabar" -> BarcodeFormat.CODABAR
            "ean8" -> BarcodeFormat.EAN_8
            "itf" -> BarcodeFormat.ITF
            "upce" -> BarcodeFormat.UPC_E
            else -> BarcodeFormat.CODE_128
        }
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 0
        val bitMatrix: BitMatrix = writer.encode(
            content,
            format,
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

fun isValidBarcodeWithChecksum(code: String, codeType: String): Boolean {
    fun ean8Checksum(s: String): Int {
        val sum = s.take(7).mapIndexed { i, c ->
            val n = c.digitToInt()
            if (i % 2 == 0) n * 3 else n
        }.sum()
        return (10 - (sum % 10)) % 10
    }
    fun ean13Checksum(s: String): Int {
        val sum = s.take(12).mapIndexed { i, c ->
            val n = c.digitToInt()
            if (i % 2 == 0) n else n * 3
        }.sum()
        return (10 - (sum % 10)) % 10
    }
    fun upcaChecksum(s: String): Int {
        val sum = s.take(11).mapIndexed { i, c ->
            val n = c.digitToInt()
            if (i % 2 == 0) n * 3 else n
        }.sum()
        return (10 - (sum % 10)) % 10
    }
    return when (codeType.lowercase()) {
        "ean8" -> code.length == 8 && code.all { it.isDigit() } && code.last().digitToInt() == ean8Checksum(code)
        "ean13" -> code.length == 13 && code.all { it.isDigit() } && code.last().digitToInt() == ean13Checksum(code)
        "upca" -> code.length == 12 && code.all { it.isDigit() } && code.last().digitToInt() == upcaChecksum(code)
        else -> true
    }
} 