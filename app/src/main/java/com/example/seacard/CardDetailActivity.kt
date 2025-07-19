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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Close
import com.example.seacard.ui.theme.SeaCardTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import androidx.core.graphics.set
import androidx.core.graphics.createBitmap
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.zxing.datamatrix.encoder.SymbolShapeHint
import android.graphics.BitmapFactory
import androidx.compose.ui.draw.shadow
import com.example.seacard.ui.theme.DynamicGradientBackground
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.core.graphics.get
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.TouchApp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import java.io.FileInputStream
import java.io.InputStream

@Composable
fun rememberBitmapFromUri(uri: Uri?): Bitmap? {
    val context = LocalContext.current
    return remember(uri) {
        uri?.let {
            try {
                val input = context.contentResolver.openInputStream(it)
                val bmp = BitmapFactory.decodeStream(input)
                input?.close()
                bmp
            } catch (_: Exception) { null }
        }
    }
}

suspend fun getDominantColorFromAsset(context: Context, assetPath: String): Int? = withContext(Dispatchers.IO) {
    try {
        val inputStream: InputStream = context.assets.open(assetPath)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        val colorCount = mutableMapOf<Int, Int>()
        val width = bitmap.width
        val height = bitmap.height
        val step = (width * height / 10000).coerceAtLeast(1)
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val color = bitmap[x, y]
                val alpha = (color shr 24) and 0xFF
                if (alpha > 200) {
                    colorCount[color] = (colorCount[color] ?: 0) + 1
                }
            }
        }
        colorCount.maxByOrNull { it.value }?.key
    } catch (e: Exception) {
        null
    }
}

// Получить доминантный цвет из локального файла
suspend fun getDominantColorFromFile(filePath: String): Int? = withContext(Dispatchers.IO) {
    try {
        val inputStream: InputStream = FileInputStream(filePath)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        val colorCount = mutableMapOf<Int, Int>()
        val width = bitmap.width
        val height = bitmap.height
        val step = (width * height / 10000).coerceAtLeast(1)
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val color = bitmap[x, y]
                val alpha = (color shr 24) and 0xFF
                if (alpha > 200) {
                    colorCount[color] = (colorCount[color] ?: 0) + 1
                }
            }
        }
        colorCount.maxByOrNull { it.value }?.key
    } catch (e: Exception) {
        null
    }
}

// Функция для сохранения Bitmap в webp-файл
fun saveBitmapAsWebp(context: Context, bitmap: Bitmap, fileName: String): String? {
    return try {
        val coversDir = File(context.filesDir, "covers")
        if (!coversDir.exists()) coversDir.mkdirs()
        val file = File(coversDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.WEBP, 90, out)
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

class CardDetailActivity : ComponentActivity() {
    private var originalBrightness: Float = 0f
    private var hasBrightnessPermission: Boolean = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val cardName = intent.getStringExtra("card_name") ?: ""
        val cardCode = intent.getStringExtra("card_code") ?: ""
        val codeType = intent.getStringExtra("code_type") ?: "barcode"
        val cardColor = intent.getIntExtra("card_color", 0xFFFFFFFF.toInt())
        val coverAsset = intent.getStringExtra("cover_asset")
        
        // Проверяем разрешение на изменение яркости
        hasBrightnessPermission = Settings.System.canWrite(this)
        
        // Сохраняем текущую яркость и увеличиваем её
        if (hasBrightnessPermission) {
            originalBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
            setBrightness(1.0f) // Максимальная яркость
        }
        
        setContent {
            // frontCoverPath нужен для LaunchedEffect с доминантным цветом
            var frontCoverPath by remember { mutableStateOf(
                getSharedPreferences("cards", Context.MODE_PRIVATE)
                    .getString("cover_front_${cardName}_${cardCode}", null)
            ) }
            var showDeleteDialog by remember { mutableStateOf(false) }
            var isDark by remember { mutableStateOf(loadThemePref(this@CardDetailActivity)) }
            var showPermissionDialog by remember { mutableStateOf(!hasBrightnessPermission) }
            var cardNameState by remember { mutableStateOf(cardName) }
            var cardCodeState by remember { mutableStateOf(cardCode) }
            var codeTypeState by remember { mutableStateOf(codeType) }
            var cardColorState by remember { mutableStateOf(cardColor) }
            val context = this@CardDetailActivity
            var dominantColor by remember { mutableStateOf<Int?>(null) }
            var frontImageUri by remember { mutableStateOf<Uri?>(null) }
            var showCropDialog by remember { mutableStateOf(false) }
            var cropImageUri by remember { mutableStateOf<Uri?>(null) }
            val frontImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) {
                    cropImageUri = uri
                    showCropDialog = true
                }
            }
            
            // Получаем доминантный цвет из frontCoverPath (загруженная обложка) или coverAsset
            LaunchedEffect(frontCoverPath, coverAsset, cardNameState, cardCodeState) {
                if (frontCoverPath != null) {
                    dominantColor = getDominantColorFromFile(frontCoverPath!!)
                } else if (coverAsset != null) {
                    dominantColor = getDominantColorFromAsset(context, coverAsset)
                }
            }
            
            // Обновляем проверку разрешения при изменении состояния
            LaunchedEffect(Unit) {
                if (Settings.System.canWrite(this@CardDetailActivity)) {
                    hasBrightnessPermission = true
                    originalBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
                    setBrightness(1.0f)
                }
            }
            
            SeaCardTheme(darkTheme = isDark) {
                val baseColor = dominantColor?.let { Color(it) } ?: Color(cardColorState)
                val backgroundColor = MaterialTheme.colorScheme.background
                val gradientColors = listOf(
                    baseColor,
                    backgroundColor
                )
                DynamicGradientBackground(colors = gradientColors) {
                    CardDetailScreen(
                        cardName = cardNameState,
                        cardCode = cardCodeState,
                        codeType = codeTypeState,
                        cardColor = cardColorState,
                        coverAsset = coverAsset,
                        frontCoverPath = frontCoverPath,
                        frontImageUri = frontImageUri,
                        onFrontCoverPick = { frontImagePicker.launch("image/*") },
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
                            // обновить frontCoverPath на новый путь
                            frontCoverPath = getSharedPreferences("cards", Context.MODE_PRIVATE)
                                .getString("cover_front_${newName}_${newCode}", null)
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
            // Показываем crop-диалог если нужно
            if (showCropDialog && cropImageUri != null) {
                ImageCropDialog(
                    imageUri = cropImageUri!!,
                    aspectRatio = 1.7f,
                    onCrop = { croppedBitmap ->
                        // Удаляем старый файл, если был
                        frontCoverPath?.let { oldPath ->
                            try { File(oldPath).delete() } catch (_: Exception) {}
                        }
                        val timestamp = System.currentTimeMillis()
                        val fileName = "front_${cardName}_${cardCode}_$timestamp.webp"
                        val path = saveBitmapAsWebp(context, croppedBitmap, fileName)
                        if (path != null) {
                            context.getSharedPreferences("cards", Context.MODE_PRIVATE)
                                .edit { putString("cover_front_${cardName}_${cardCode}", path) }
                            frontCoverPath = path
                        }
                        showCropDialog = false
                        cropImageUri = null
                    },
                    onDismiss = {
                        showCropDialog = false
                        cropImageUri = null
                    }
                )
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
            val coverAsset = if (parts.size >= 7) parts[6] else null
            val newCardString = when {
                coverAsset != null -> "$newName|$newCode|$newType|${parts.getOrNull(3) ?: System.currentTimeMillis()}|${parts.getOrNull(4) ?: 0}|$newColor|$coverAsset"
                parts.size == 2 -> "$newName|$newCode"
                parts.size == 3 -> "$newName|$newCode|$newType"
                parts.size == 5 -> "$newName|$newCode|$newType|${parts[3]}|${parts[4]}"
                parts.size == 6 -> "$newName|$newCode|$newType|${parts[3]}|${parts[4]}|$newColor"
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
    coverAsset: String? = null,
    frontCoverPath: String? = null,
    frontImageUri: Uri? = null,
    onFrontCoverPick: () -> Unit = {},
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

    // Заметки
    val noteKey = "note_${cardName}_${cardCode}_${codeType}"
    var note by remember {
        mutableStateOf(
            context.getSharedPreferences("cards", Context.MODE_PRIVATE)
                .getString(noteKey, "") ?: ""
        )
    }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showCoverDialog by remember { mutableStateOf(false) }
    var backImageUri by remember { mutableStateOf<Uri?>(null) }
    var showFullScreenImage by remember { mutableStateOf<Pair<Boolean, Uri?>>(false to null) }
    // Новые состояния для crop back cover
    var backCropImageUri by remember { mutableStateOf<Uri?>(null) }
    var showBackCropDialog by remember { mutableStateOf(false) }
    val context2 = LocalContext.current
    // Получаем путь к сохранённым обложкам (front/back) из SharedPreferences
    val backCoverPath = context.getSharedPreferences("cards", Context.MODE_PRIVATE)
        .getString("cover_back_${cardName}_${cardCode}", null)
    // Новое: Uri для CardInputSection
    val frontCoverUri = frontCoverPath?.let { android.net.Uri.fromFile(java.io.File(it)) }
    val backCoverUri = backCoverPath?.let { android.net.Uri.fromFile(java.io.File(it)) }
    // Показываем frontCoverPath (webp) если есть, иначе coverAsset (assets/cards)
    val coverBitmap: ImageBitmap? = remember(frontCoverPath to coverAsset) {
        try {
            var result: ImageBitmap? = null
            frontCoverPath?.let { path ->
                val bmp = BitmapFactory.decodeFile(path)
                if (bmp != null) result = bmp.asImageBitmap()
            }
            if (result == null && coverAsset != null) {
                val input = context2.assets.open(coverAsset)
                val bmp = BitmapFactory.decodeStream(input)
                input.close()
                if (bmp != null) result = bmp.asImageBitmap()
            }
            result
        } catch (_: Exception) { null }
    }
    // Лаунчеры для выбора фото
    val backImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            backCropImageUri = uri
            showBackCropDialog = true
        }
    }
    var noteDraft by remember { mutableStateOf("") }
    var noteError by remember { mutableStateOf("") }

    // Сброс draft при открытии диалога
    LaunchedEffect(showNoteDialog) {
        if (showNoteDialog) {
            noteDraft = note
        }
    }

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
    ) {
        Column {
            if (!showEditDialog) {
                TopAppBar(
                    title = {
                        Text(
                            text = editName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    },
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
            }
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
                        },
                        coverAsset = coverAsset,
                        showTopBar = false, // убираем TopAppBar при редактировании
                        isEditMode = true, // показываем TopAppBar с заголовком 'Изменение карты'
                        onBack = onBack,
                        frontCoverUri = frontCoverUri,
                        backCoverUri = backCoverUri
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
                    val isSquareCode = editType == "qr" || editType == "datamatrix"
                    val cardHeight = if (isSquareCode) 350.dp else 300.dp
                    val imageHeight = if (editType == "qr" || editType == "datamatrix") 230.dp else 230.dp
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 8.dp)
                            .height(cardHeight)
                            .shadow(18.dp, RoundedCornerShape(28.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
                        shape = RoundedCornerShape(28.dp),
                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.25f))
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
                                    .height(imageHeight)
                            )
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 32.dp),
                    thickness = 1.dp,
                    color = colorScheme.onSurface.copy(alpha = 0.2f)
                )
                // Код карты внизу
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface.copy(alpha = 0.20f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val formattedCode = formatBarcodeForStandard(editCode, editType)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        copyToClipboard()
                                    }
                                )
                                    },
                                contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formattedCode,
                                    fontSize = if (editCode.length > 20) 20.sp else 28.sp,
                            fontWeight = FontWeight.Bold,
                                    color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Долгое нажатие — скопировать",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    // Заметки и Обложка
                    Spacer(modifier = Modifier.height(1.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Кнопка "Заметки"
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { showNoteDialog = true }
                                    )
                                },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface.copy(alpha = 0.20f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Заметки",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Заметки",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (note.isNotBlank()) {
                        Text(
                                        text = note,
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(2f)
                                    )
                                }
                            }
                        }
                        // Кнопка "Обложка"
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface.copy(alpha = 0.20f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { showCoverDialog = true }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Обложка",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Обложка",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            // Диалог для заметки
            if (showNoteDialog) {
                AlertDialog(
                    onDismissRequest = { showNoteDialog = false },
                    title = { Text("Заметка к карте") },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = noteDraft,
                                onValueChange = {
                                    if (it.length <= 100) {
                                        noteDraft = it
                                        noteError = ""
                                    }
                                },
                                label = { Text("Введите заметку") },
                                singleLine = false,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    if (noteDraft.isNotEmpty()) {
                                        IconButton(onClick = { noteDraft = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Очистить")
                    }
                }
                                }
                            )
                            if (noteError.isNotEmpty()) {
                                Text(noteError, color = Color.Red, fontSize = 13.sp)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (noteDraft.length <= 100) {
                                    note = noteDraft
                                    context.getSharedPreferences("cards", Context.MODE_PRIVATE)
                                        .edit { putString(noteKey, noteDraft) }
                                    showNoteDialog = false
                                } else {
                                    noteError = "Максимум 100 символов"
                                }
                            }
                        ) {
                            Text("Сохранить")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNoteDialog = false }) {
                            Text("Отмена")
                        }
                    },
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.onSurface,
                    textContentColor = colorScheme.onSurface
                )
            }
            // Диалог выбора/просмотра обложки
            if (showCoverDialog) {
                AlertDialog(
                    onDismissRequest = { showCoverDialog = false },
                    title = { Text("Обложка карты") },
                    text = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)
                            ) {
                                // Лицевая сторона
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(80.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                        .clickable {
                                            // Только просмотр
                                            frontCoverPath?.let { path ->
                                                showFullScreenImage = true to Uri.fromFile(File(path))
                                            } ?: run {
                                                if (frontImageUri != null) {
                                                    showFullScreenImage = true to frontImageUri
                                                } else if (coverBitmap != null) {
                                                    showFullScreenImage = true to null
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val frontBitmap = frontCoverPath?.let { path ->
                                        try {
                                            BitmapFactory.decodeFile(path)
                                        } catch (_: Exception) { null }
                                    } ?: frontImageUri?.let { rememberBitmapFromUri(it) }
                                    if (frontBitmap != null) {
                                        Image(
                                            bitmap = frontBitmap.asImageBitmap(),
                                            contentDescription = "Лицевая сторона",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (coverBitmap != null) {
                                        Image(
                                            bitmap = coverBitmap,
                                            contentDescription = "Лицевая сторона",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = "Лицевая сторона",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                                // Тыльная сторона
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(80.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                        .clickable {
                                            // Только просмотр
                                            backCoverPath?.let { path ->
                                                showFullScreenImage = true to Uri.fromFile(File(path))
                                            } ?: run {
                                                if (backImageUri != null) {
                                                    showFullScreenImage = true to backImageUri
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val backBitmap = if (backCoverPath != null) {
                                        try {
                                            BitmapFactory.decodeFile(backCoverPath)
                                        } catch (_: Exception) { null }
                                    } else rememberBitmapFromUri(backImageUri)
                                    if (backBitmap != null) {
                                        Image(
                                            bitmap = backBitmap.asImageBitmap(),
                                            contentDescription = "Тыльная сторона",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(Color.Black.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = "Тыльная сторона",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                            // Кнопки загрузки под карточками
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)
                            ) {
                                Button(
                                    onClick = onFrontCoverPick,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorScheme.surfaceVariant,
                                        contentColor = colorScheme.onSurface
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Text("Загрузить", color = colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                }
                                Button(
                                    onClick = { backImagePicker.launch("image/*") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorScheme.surfaceVariant,
                                        contentColor = colorScheme.onSurface
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Text("Загрузить", color = colorScheme.onSurface, fontWeight = FontWeight.Medium)
                                }
                            }
                            Text("Нажмите на карточку, чтобы просмотреть изображение", fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showCoverDialog = false }) {
                            Text("Готово")
                        }
                    },
                    containerColor = colorScheme.surface,
                    titleContentColor = colorScheme.onSurface,
                    textContentColor = colorScheme.onSurface
                )
            }
                    // Полноэкранный просмотр изображения
                    if (showFullScreenImage.first) {
                        Dialog(onDismissRequest = { showFullScreenImage = false to null }) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                val uri = showFullScreenImage.second
                                val bitmap = if (uri != null) rememberBitmapFromUri(uri)?.asImageBitmap() else coverBitmap
                                if (bitmap != null) {
                                    var scale by remember { mutableStateOf(1f) }
                                    var offset by remember { mutableStateOf(Offset.Zero) }
                                    var lastOffset by remember { mutableStateOf(Offset.Zero) }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.98f)
                                            .fillMaxHeight(0.98f)
                                            .pointerInput(Unit) {
                                                detectTransformGestures { _, pan, zoom, _ ->
                                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                                    offset += pan
                                                }
                                            }
                                            .pointerInput(Unit) {
                                                detectTapGestures(onDoubleTap = {
                                                    scale = 1f
                                                    offset = Offset.Zero
                                                })
                                            }
                                    ) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .graphicsLayer(
                                                    scaleX = scale,
                                                    scaleY = scale,
                                                    translationX = offset.x,
                                                    translationY = offset.y
                                                )
                                                .fillMaxSize()
                                        )
                                    }
                                }
                            }
                }
            }
        }
    }
    // Показываем crop-диалог если нужно (back cover)
    if (showBackCropDialog && backCropImageUri != null) {
        ImageCropDialog(
            imageUri = backCropImageUri!!,
            aspectRatio = 1.7f,
            onCrop = { croppedBitmap ->
                // Удаляем старый файл, если был
                backCoverPath?.let { oldPath ->
                    try { File(oldPath).delete() } catch (_: Exception) {}
                }
                val timestamp = System.currentTimeMillis()
                val fileName = "back_${cardName}_${cardCode}_$timestamp.webp"
                val path = saveBitmapAsWebp(context, croppedBitmap, fileName)
                if (path != null) {
                    context.getSharedPreferences("cards", Context.MODE_PRIVATE)
                        .edit { putString("cover_back_${cardName}_${cardCode}", path) }
                }
                showBackCropDialog = false
                backCropImageUri = null
            },
            onDismiss = {
                showBackCropDialog = false
                backCropImageUri = null
            }
        )
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
        hints[EncodeHintType.MARGIN] = 0
        hints[EncodeHintType.ERROR_CORRECTION] = com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M // Средний уровень коррекции ошибок
        
        val bitMatrix: BitMatrix = writer.encode(
            content,
            BarcodeFormat.QR_CODE,
            600,
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
            "datamatrix" -> com.google.zxing.datamatrix.DataMatrixWriter()
            "pdf417" -> com.google.zxing.pdf417.PDF417Writer()
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
            "datamatrix" -> BarcodeFormat.DATA_MATRIX
            "pdf417" -> BarcodeFormat.PDF_417
            else -> BarcodeFormat.CODE_128
        }
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.MARGIN] = 0
        if (codeType.lowercase() == "datamatrix") {
            hints[EncodeHintType.DATA_MATRIX_SHAPE] = SymbolShapeHint.FORCE_SQUARE
        }
        val bitMatrix: BitMatrix = writer.encode(
            content,
            format,
            if (codeType.lowercase() == "datamatrix") 600 else 800,
            if (codeType.lowercase() == "datamatrix") 600 else 200,
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