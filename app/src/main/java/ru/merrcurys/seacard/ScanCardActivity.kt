package ru.merrcurys.seacard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import ru.merrcurys.seacard.ui.theme.SeaCardTheme
import ru.merrcurys.seacard.ui.theme.BlackBackground
import ru.merrcurys.seacard.ui.theme.GradientBackground
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.content.edit
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream

class ScanCardActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var scannedCode: String? = null
    private var scannedCodeType: String = "barcode"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        val coverAsset: String? = intent.getStringExtra("cover_asset")
        
        setContent {
            var hasCameraPermission by remember { mutableStateOf(false) }
            val initialCardName = coverAsset?.let {
                val fileName = it.substringAfterLast('/')
                CoverNames.coverNameMap[fileName] ?: fileName.substringBeforeLast('.')
            } ?: ""
            var cardName by remember { mutableStateOf(initialCardName) }
            var cardCode by remember { mutableStateOf("") }
            var scanSuccess by remember { mutableStateOf(false) }
            var isDark by remember { mutableStateOf(loadThemePref(this@ScanCardActivity)) }
            var selectedColor by remember { mutableStateOf(0xFFFFFFFF.toInt()) }
            var scanned by remember { mutableStateOf(false) }
            var cardSaved by remember { mutableStateOf(false) }
            var codeTypeState by remember { mutableStateOf("") }

            // Новое: состояния для Uri обложек
            var frontCoverUri by remember { mutableStateOf<android.net.Uri?>(null) }
            var backCoverUri by remember { mutableStateOf<android.net.Uri?>(null) }
            // Новое: состояния для crop-диалога
            var showFrontCropDialog by remember { mutableStateOf(false) }
            var showBackCropDialog by remember { mutableStateOf(false) }
            var frontCropImageUri by remember { mutableStateOf<android.net.Uri?>(null) }
            var backCropImageUri by remember { mutableStateOf<android.net.Uri?>(null) }

            val context = this@ScanCardActivity
            val coroutineScope = rememberCoroutineScope()

            // Launcher для выбора лицевой обложки
            val frontCoverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) {
                    frontCropImageUri = uri
                    showFrontCropDialog = true
                }
            }
            // Launcher для выбора тыльной обложки
            val backCoverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) {
                    backCropImageUri = uri
                    showBackCropDialog = true
                }
            }

            // Launcher для запроса разрешения на камеру
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                hasCameraPermission = isGranted
            }

            // Launcher для выбора изображения из галереи
            val galleryLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    try {
                        val image = InputImage.fromFilePath(context, uri)
                        val scanner = BarcodeScanning.getClient()
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                var found = false
                                for (barcode in barcodes) {
                                    val codeType = when (barcode.format) {
                                        Barcode.FORMAT_QR_CODE -> "qr"
                                        Barcode.FORMAT_AZTEC -> "aztec"
                                        Barcode.FORMAT_DATA_MATRIX -> "datamatrix"
                                        Barcode.FORMAT_PDF417 -> "pdf417"
                                        Barcode.FORMAT_CODE_128 -> "code128"
                                        Barcode.FORMAT_EAN_13 -> "ean13"
                                        Barcode.FORMAT_UPC_A -> "upca"
                                        Barcode.FORMAT_CODE_39 -> "code39"
                                        Barcode.FORMAT_CODE_93 -> "code93"
                                        Barcode.FORMAT_CODABAR -> "codabar"
                                        Barcode.FORMAT_EAN_8 -> "ean8"
                                        Barcode.FORMAT_ITF -> "itf"
                                        Barcode.FORMAT_UPC_E -> "upce"
                                        else -> "barcode"
                                    }
                                    if (!scanned) {
                                        cardCode = barcode.rawValue ?: ""
                                        codeTypeState = codeType
                                        scannedCodeType = codeType
                                        scanSuccess = true
                                        scanned = true
                                        found = true
                                        // Вибрация при успешном сканировании
                                        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                                            vibratorManager.defaultVibrator
                                        } else {
                                            @Suppress("DEPRECATION")
                                            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                                        }
                                        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                                        coroutineScope.launch {
                                            delay(2000)
                                            scanSuccess = false
                                        }
                                        break
                                    }
                                }
                                if (!found) {
                                    android.widget.Toast.makeText(context, "Код не был найден", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            LaunchedEffect(Unit) {
                when (PackageManager.PERMISSION_GRANTED) {
                    ContextCompat.checkSelfPermission(
                        this@ScanCardActivity,
                        Manifest.permission.CAMERA
                    ) -> {
                        hasCameraPermission = true
                    }
                    else -> {
                        launcher.launch(Manifest.permission.CAMERA)
                    }
                }
            }
            
            if (coverAsset != null) {
                LaunchedEffect(cardCode) {
                    if (!cardSaved && cardName.isNotBlank() && cardCode.isNotBlank()) {
                        saveCardWithCover(this@ScanCardActivity, cardName, cardCode, codeTypeState.ifBlank { "barcode" }, selectedColor, coverAsset)
                        cardSaved = true
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            }

            SeaCardTheme(darkTheme = isDark) {
                GradientBackground(darkTheme = isDark) {
                    if (cardCode.isBlank()) {
                        // ВСЕГДА сначала показываем сканер, если код пустой
                        ScanCardScreen(
                            hasCameraPermission = hasCameraPermission,
                            scanned = scanned,
                            cardName = cardName,
                            cardCode = cardCode,
                            scanSuccess = scanSuccess,
                            selectedColor = selectedColor,
                            onCardNameChange = { cardName = it },
                            onCardCodeChange = { newCode -> cardCode = newCode },
                            onColorChange = { selectedColor = it },
                            onScanResult = { code, codeType ->
                                if (!scanned) {
                                    cardCode = code
                                    codeTypeState = codeType
                                    scanSuccess = true
                                    scanned = true
                                    // Вибрация при успешном сканировании
                                    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                                        vibratorManager.defaultVibrator
                                    } else {
                                        @Suppress("DEPRECATION")
                                        getSystemService(VIBRATOR_SERVICE) as Vibrator
                                    }
                                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                                }
                            },
                            onSaveCard = {
                                if (cardName.isNotBlank() && cardCode.isNotBlank()) {
                                    saveCardWithCover(this@ScanCardActivity, cardName, cardCode, codeTypeState.ifBlank { "barcode" }, selectedColor, if (coverAsset != null) coverAsset else null)
                                    setResult(RESULT_OK)
                                    finish()
                                }
                            },
                            onBack = { finish() },
                            onGalleryClick = { galleryLauncher.launch("image/*") },
                            coverAsset = coverAsset
                        )
                    } else {
                        // Ручной ввод
                        CardInputSection(
                            cardName = cardName,
                            cardCode = cardCode,
                            selectedColor = selectedColor,
                            onCardNameChange = { cardName = it },
                            onCardCodeChange = {},
                            onColorChange = { selectedColor = it },
                            onSaveCard = {
                                if (cardName.isNotBlank() && cardCode.isNotBlank()) {
                                    // Сохраняем обложки в webp
                                    var frontPath: String? = null
                                    var backPath: String? = null
                                    frontCoverUri?.let { uri ->
                                        val input = context.contentResolver.openInputStream(uri)
                                        val bmp = BitmapFactory.decodeStream(input)
                                        input?.close()
                                        if (bmp != null) {
                                            val timestamp = System.currentTimeMillis()
                                            val fileName = "front_${cardName}_$timestamp.webp"
                                            frontPath = saveBitmapAsWebp(context, bmp, fileName)
                                        }
                                    }
                                    backCoverUri?.let { uri ->
                                        val input = context.contentResolver.openInputStream(uri)
                                        val bmp = BitmapFactory.decodeStream(input)
                                        input?.close()
                                        if (bmp != null) {
                                            val timestamp = System.currentTimeMillis()
                                            val fileName = "back_${cardName}_$timestamp.webp"
                                            backPath = saveBitmapAsWebp(context, bmp, fileName)
                                        }
                                    }
                                    // Сохраняем пути в SharedPreferences
                                    val prefs = context.getSharedPreferences("cards", MODE_PRIVATE)
                                    if (frontPath != null) {
                                        prefs.edit { putString("cover_front_${cardName}_${cardCode}", frontPath) }
                                    }
                                    if (backPath != null) {
                                        prefs.edit { putString("cover_back_${cardName}_${cardCode}", backPath) }
                                    }
                                    // Сохраняем карту: путь к лицевой обложке как coverAsset
                                    saveCardWithCover(context, cardName, cardCode, codeTypeState.ifBlank { "barcode" }, selectedColor, frontPath)
                                    setResult(RESULT_OK)
                                    finish()
                                }
                            },
                            coverAsset = null,
                            onBack = { finish() },
                            frontCoverUri = frontCoverUri,
                            backCoverUri = backCoverUri,
                            onFrontCoverPick = { frontCoverPicker.launch("image/*") },
                            onBackCoverPick = { backCoverPicker.launch("image/*") },
                            onFrontCoverRemove = { frontCoverUri = null },
                            onBackCoverRemove = { backCoverUri = null }
                        )
                    }
                }
            }
            // Показываем crop-диалоги если нужно
            if (showFrontCropDialog && frontCropImageUri != null) {
                ImageCropDialog(
                    imageUri = frontCropImageUri!!,
                    aspectRatio = 1.574f,
                    onCrop = { croppedBitmap ->
                        // Сохраняем bitmap во временный файл и обновляем frontCoverUri
                        val file = File.createTempFile("front_crop_", ".webp", context.cacheDir)
                        FileOutputStream(file).use { out ->
                            croppedBitmap.compress(Bitmap.CompressFormat.WEBP, 90, out)
                        }
                        frontCoverUri = Uri.fromFile(file)
                        showFrontCropDialog = false
                        frontCropImageUri = null
                    },
                    onDismiss = {
                        showFrontCropDialog = false
                        frontCropImageUri = null
                    }
                )
            }
            if (showBackCropDialog && backCropImageUri != null) {
                ImageCropDialog(
                    imageUri = backCropImageUri!!,
                    aspectRatio = 1.574f,
                    onCrop = { croppedBitmap ->
                        val file = File.createTempFile("back_crop_", ".webp", context.cacheDir)
                        FileOutputStream(file).use { out ->
                            croppedBitmap.compress(Bitmap.CompressFormat.WEBP, 90, out)
                        }
                        backCoverUri = Uri.fromFile(file)
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
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
    
    private fun saveCard(context: Context, name: String, code: String, codeType: String, color: Int) {
        val prefs = context.getSharedPreferences("cards", Context.MODE_PRIVATE)
        val cards = prefs.getStringSet("card_list", setOf())?.toMutableSet() ?: mutableSetOf()
        val currentTime = System.currentTimeMillis()
        cards.add("$name|$code|$codeType|$currentTime|0|$color")
        prefs.edit { putStringSet("card_list", cards) }
    }

    private fun saveCardWithCover(context: Context, name: String, code: String, codeType: String, color: Int, coverAsset: String?) {
        val prefs = context.getSharedPreferences("cards", Context.MODE_PRIVATE)
        val cards = prefs.getStringSet("card_list", setOf())?.toMutableSet() ?: mutableSetOf()
        val currentTime = System.currentTimeMillis()
        if (coverAsset != null) {
            cards.add("$name|$code|$codeType|$currentTime|0|$color|$coverAsset")
        } else {
            cards.add("$name|$code|$codeType|$currentTime|0|$color")
        }
        prefs.edit { putStringSet("card_list", cards) }
    }
    
    private fun loadThemePref(context: Context): Boolean {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("dark_theme", true)
    }

    companion object {
        fun detectCodeType(code: String): String {
            return when {
                code.all { it.isDigit() } && code.length == 13 -> "ean13"
                code.all { it.isDigit() } && code.length == 12 -> "upca"
                code.all { it.isDigit() } && code.length == 8 -> "ean8"
                code.all { it.isDigit() } -> "code128"
                else -> "qr"
            }
        }
    }
}

@Composable
fun ScanCardScreen(
    hasCameraPermission: Boolean,
    scanned: Boolean,
    cardName: String,
    cardCode: String,
    scanSuccess: Boolean,
    selectedColor: Int,
    onCardNameChange: (String) -> Unit,
    onCardCodeChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onScanResult: (String, String) -> Unit,
    onSaveCard: () -> Unit,
    onBack: () -> Unit,
    onGalleryClick: () -> Unit,
    coverAsset: String?
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column {
            if (coverAsset == null && cardCode.isNotBlank()) {
                // После сканирования вручную — показываем форму
                CardInputSection(
                    cardName = cardName,
                    cardCode = cardCode,
                    selectedColor = selectedColor,
                    onCardNameChange = onCardNameChange,
                    onCardCodeChange = {},
                    onColorChange = onColorChange,
                    onSaveCard = onSaveCard,
                    coverAsset = null,
                    onBack = onBack
                )
            } else {
                CameraSection(
                    hasCameraPermission = hasCameraPermission,
                    scanSuccess = scanSuccess,
                    onScanResult = { code, codeType ->
                        onScanResult(code, codeType)
                    },
                    onGalleryClick = onGalleryClick
                )
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun CameraSection(
    hasCameraPermission: Boolean,
    scanSuccess: Boolean,
    onScanResult: (String, String) -> Unit,
    onGalleryClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background == BlackBackground
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build()
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .apply {
                                setAnalyzer(
                                    Executors.newSingleThreadExecutor()
                                ) { imageProxy ->
                                    val image = InputImage.fromMediaImage(
                                        imageProxy.image!!,
                                        imageProxy.imageInfo.rotationDegrees
                                    )
                                    val scanner = BarcodeScanning.getClient()
                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            for (barcode in barcodes) {
                                                val codeType = when (barcode.format) {
                                                    Barcode.FORMAT_QR_CODE -> "qr"
                                                    Barcode.FORMAT_AZTEC -> "aztec"
                                                    Barcode.FORMAT_DATA_MATRIX -> "datamatrix"
                                                    Barcode.FORMAT_PDF417 -> "pdf417"
                                                    Barcode.FORMAT_CODE_128 -> "code128"
                                                    Barcode.FORMAT_EAN_13 -> "ean13"
                                                    Barcode.FORMAT_UPC_A -> "upca"
                                                    Barcode.FORMAT_CODE_39 -> "code39"
                                                    Barcode.FORMAT_CODE_93 -> "code93"
                                                    Barcode.FORMAT_CODABAR -> "codabar"
                                                    Barcode.FORMAT_EAN_8 -> "ean8"
                                                    Barcode.FORMAT_ITF -> "itf"
                                                    Barcode.FORMAT_UPC_E -> "upce"
                                                    else -> "barcode"
                                                }
                                                if (barcode.format == Barcode.FORMAT_CODE_128 ||
                                                    barcode.format == Barcode.FORMAT_CODE_39 ||
                                                    barcode.format == Barcode.FORMAT_CODE_93 ||
                                                    barcode.format == Barcode.FORMAT_CODABAR ||
                                                    barcode.format == Barcode.FORMAT_EAN_13 ||
                                                    barcode.format == Barcode.FORMAT_EAN_8 ||
                                                    barcode.format == Barcode.FORMAT_ITF ||
                                                    barcode.format == Barcode.FORMAT_UPC_A ||
                                                    barcode.format == Barcode.FORMAT_UPC_E ||
                                                    barcode.format == Barcode.FORMAT_QR_CODE ||
                                                    barcode.format == Barcode.FORMAT_AZTEC ||
                                                    barcode.format == Barcode.FORMAT_DATA_MATRIX ||
                                                    barcode.format == Barcode.FORMAT_PDF417) {
                                                    onScanResult(barcode.rawValue ?: "", codeType)
                                                }
                                            }
                                        }
                                        .addOnCompleteListener {
                                            imageProxy.close()
                                        }
                                }
                            }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                            preview.setSurfaceProvider(previewView.surfaceProvider)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            )
            // Затемнение сверху
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(if (isDark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f))
            )
            // Затемнение снизу
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .align(Alignment.BottomCenter)
                    .background(if (isDark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f))
            )
            // Область сканирования в центре
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .align(Alignment.TopCenter)
                    .offset(y = 225.dp)
                    .padding(horizontal = 60.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
                        .border(2.dp, if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        repeat(20) { index ->
                            Box(
                                modifier = Modifier
                                    .width(if (index % 2 == 0) 4.dp else 2.dp)
                                    .height(80.dp)
                                    .background(
                                        if (index % 3 == 0) {
                                            if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f)
                                        } else {
                                            if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
                                        }
                                    )
                            )
                        }
                    }
                }
            }
            // Текст инструкции в затемненной зоне
            Text(
                text = "Поднесите карту к камере,\nчтобы отсканировать код",
                color = if (isDark) Color.White else Color.Black,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp, start = 32.dp, end = 32.dp)
            )
            // Кнопка галереи по центру под текстом
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
            ) {
                Button(
                    onClick = onGalleryClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDark) Color.Black.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.35f),
                        contentColor = if (isDark) Color.White else Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
                    modifier = Modifier
                        .width(140.dp)
                        .height(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Photo,
                        contentDescription = "Открыть галерею",
                        tint = if (isDark) Color.White else Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Галерея", fontWeight = FontWeight.Medium)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text("Нет разрешения на камеру", color = colorScheme.onSurface)
            }
        }
    }
} 