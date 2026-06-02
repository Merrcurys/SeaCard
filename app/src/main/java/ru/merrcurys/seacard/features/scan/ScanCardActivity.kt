package ru.merrcurys.seacard.features.scan

import android.app.Application
import android.Manifest
import ru.merrcurys.seacard.features.crop.ImageCropDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
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
import androidx.compose.runtime.collectAsState
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
import ru.merrcurys.seacard.core.design.applySeaCardSystemBarColors
import ru.merrcurys.seacard.core.design.SeaCardTheme
import ru.merrcurys.seacard.core.design.BlackBackground
import ru.merrcurys.seacard.core.design.GradientBackground
import ru.merrcurys.seacard.core.design.GradientUtils
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.merrcurys.seacard.core.utils.createImagePickerChooserIntent
import android.net.Uri
import android.os.Build

class ScanCardActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySeaCardSystemBarColors()
        cameraExecutor = Executors.newSingleThreadExecutor()
        val coverAsset = intent.getStringExtra("cover_asset")

        setContent {
            var hasCameraPermission by remember { mutableStateOf(false) }
            val viewModel: ScanCardViewModel = viewModel(
                factory = ScanCardViewModelFactory(application, coverAsset)
            )
            val cardName by viewModel.cardName.collectAsState()
            val cardCode by viewModel.cardCode.collectAsState()
            val selectedColor by viewModel.selectedColor.collectAsState()
            val scanned by viewModel.scanned.collectAsState()
            val scanSuccess by viewModel.scanSuccess.collectAsState()
            val codeTypeState by viewModel.codeTypeState.collectAsState()
            val frontCoverUri by viewModel.frontCoverUri.collectAsState()
            val backCoverUri by viewModel.backCoverUri.collectAsState()
            val showFrontCropDialog by viewModel.showFrontCropDialog.collectAsState()
            val showBackCropDialog by viewModel.showBackCropDialog.collectAsState()
            val frontCropImageUri by viewModel.frontCropImageUri.collectAsState()
            val backCropImageUri by viewModel.backCropImageUri.collectAsState()

            val context = this@ScanCardActivity
            val coroutineScope = rememberCoroutineScope()

            var pendingFrontCameraUri by remember { mutableStateOf<Uri?>(null) }
            var pendingBackCameraUri by remember { mutableStateOf<Uri?>(null) }
            val frontCoverPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val uri = result.data?.data ?: pendingFrontCameraUri
                    pendingFrontCameraUri = null
                    uri?.let { viewModel.showFrontCrop(it) }
                }
            }
            val backCoverPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val uri = result.data?.data ?: pendingBackCameraUri
                    pendingBackCameraUri = null
                    uri?.let { viewModel.showBackCrop(it) }
                }
            }

            var pendingCoverPick by remember { mutableStateOf<String?>(null) }
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPermission = it }
            val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                if (uri != null) {
                    try {
                        val image = InputImage.fromFilePath(context, uri)
                        BarcodeScanning.getClient().process(image)
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
                                    if (!viewModel.scanned.value) {
                                        viewModel.onScanResult(barcode.rawValue ?: "", codeType)
                                        found = true
                                        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                                        } else {
                                            @Suppress("DEPRECATION")
                                            getSystemService(VIBRATOR_SERVICE) as Vibrator
                                        }
                                        vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                                        coroutineScope.launch {
                                            delay(2000)
                                            viewModel.setScanSuccess(false)
                                        }
                                        break
                                    }
                                }
                                if (!found) android.widget.Toast.makeText(context, "Код не был найден", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e -> android.widget.Toast.makeText(context, "Ошибка сканирования: ${e.message}", android.widget.Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Ошибка загрузки изображения", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }

            LaunchedEffect(Unit) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) hasCameraPermission = true
                else permissionLauncher.launch(Manifest.permission.CAMERA)
            }
            LaunchedEffect(hasCameraPermission, pendingCoverPick) {
                if (!hasCameraPermission || pendingCoverPick == null) return@LaunchedEffect
                when (pendingCoverPick) {
                    "front" -> {
                        val (intent, cameraUri) = createImagePickerChooserIntent(this@ScanCardActivity)
                        pendingFrontCameraUri = cameraUri
                        frontCoverPicker.launch(intent)
                    }
                    "back" -> {
                        val (intent, cameraUri) = createImagePickerChooserIntent(this@ScanCardActivity)
                        pendingBackCameraUri = cameraUri
                        backCoverPicker.launch(intent)
                    }
                }
                pendingCoverPick = null
            }

            LaunchedEffect(cardCode, viewModel.cardSaved.value) {
                if (viewModel.saveIfCoverAssetReady()) {
                    setResult(RESULT_OK)
                    finish()
                }
            }

            SeaCardTheme {
                GradientBackground(gradientColor = GradientUtils.loadGradientColorPref(context)) {
                    if (cardCode.isBlank()) {
                        ScanCardScreen(
                            hasCameraPermission = hasCameraPermission,
                            scanned = scanned,
                            cardName = cardName,
                            cardCode = cardCode,
                            scanSuccess = scanSuccess,
                            selectedColor = selectedColor,
                            onCardNameChange = { viewModel.setCardName(it) },
                            onCardCodeChange = { viewModel.setCardCode(it) },
                            onColorChange = { viewModel.setSelectedColor(it) },
                            onScanResult = viewModel::onScanResult,
                            onSaveCard = {
                                if (cardName.isNotBlank() && cardCode.isNotBlank()) {
                                    coroutineScope.launch {
                                        viewModel.saveCardWithCover(cardName, cardCode, codeTypeState.ifBlank { "barcode" }, selectedColor, viewModel.coverAsset, null)
                                        setResult(RESULT_OK)
                                        finish()
                                    }
                                }
                            },
                            onBack = { finish() },
                            onGalleryClick = { galleryLauncher.launch("image/*") },
                            coverAsset = viewModel.coverAsset
                        )
                    } else {
                        CardInputSection(
                            cardName = cardName,
                            cardCode = cardCode,
                            selectedColor = selectedColor,
                            onCardNameChange = { viewModel.setCardName(it) },
                            onCardCodeChange = {},
                            onColorChange = { viewModel.setSelectedColor(it) },
                            onSaveCard = {
                                if (cardName.isNotBlank() && cardCode.isNotBlank()) {
                                    coroutineScope.launch {
                                        viewModel.saveCardWithCoverUris(cardName, cardCode, codeTypeState.ifBlank { "barcode" }, selectedColor)
                                        setResult(RESULT_OK)
                                        finish()
                                    }
                                }
                            },
                            coverAsset = null,
                            onBack = { finish() },
                            frontCoverUri = frontCoverUri,
                            backCoverUri = backCoverUri,
                            onFrontCoverPick = {
                                if (hasCameraPermission) {
                                    val (intent, cameraUri) = createImagePickerChooserIntent(this@ScanCardActivity)
                                    pendingFrontCameraUri = cameraUri
                                    frontCoverPicker.launch(intent)
                                } else {
                                    pendingCoverPick = "front"
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            onBackCoverPick = {
                                if (hasCameraPermission) {
                                    val (intent, cameraUri) = createImagePickerChooserIntent(this@ScanCardActivity)
                                    pendingBackCameraUri = cameraUri
                                    backCoverPicker.launch(intent)
                                } else {
                                    pendingCoverPick = "back"
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            onFrontCoverRemove = { viewModel.setFrontCoverUri(null) },
                            onBackCoverRemove = { viewModel.setBackCoverUri(null) }
                        )
                    }
                }
            }

            if (showFrontCropDialog && frontCropImageUri != null) {
                ImageCropDialog(
                    imageUri = frontCropImageUri!!,
                    aspectRatio = 1.574f,
                    onCrop = { viewModel.onFrontCropResult(it) },
                    onDismiss = { viewModel.dismissFrontCrop() }
                )
            }
            if (showBackCropDialog && backCropImageUri != null) {
                ImageCropDialog(
                    imageUri = backCropImageUri!!,
                    aspectRatio = 1.574f,
                    onCrop = { viewModel.onBackCropResult(it) },
                    onDismiss = { viewModel.dismissBackCrop() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

class ScanCardViewModelFactory(private val application: Application, private val coverAsset: String?) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = ScanCardViewModel(application, coverAsset) as T
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
                    scanned = scanned,
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
    scanned: Boolean,
    onScanResult: (String, String) -> Unit,
    onGalleryClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background == BlackBackground
    
    // Состояние для управления сканированием
    var lastScanTime by remember { mutableLongStateOf(0L) }
    val scanCooldown = 1000L // Задержка 1 секунда между сканированиями
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var previewUseCase by remember { mutableStateOf<Preview?>(null) }
    var imageAnalysisUseCase by remember { mutableStateOf<ImageAnalysis?>(null) }
    
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
                        val provider = cameraProviderFuture.get()
                        cameraProvider = provider
                        
                        // Создаем use case для предпросмотра
                        val preview = Preview.Builder().build()
                        previewUseCase = preview
                        
                        // Улучшенный анализ изображений с высоким разрешением и троттлингом
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(android.util.Size(1280, 720)) // Высокое разрешение для лучшего распознавания
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        imageAnalysisUseCase = imageAnalysis
                            
                        imageAnalysis.setAnalyzer(
                            Executors.newSingleThreadExecutor()
                        ) { imageProxy ->
                            // Если код уже отсканирован, прекращаем анализ
                            if (scanned) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            
                            val currentTime = System.currentTimeMillis()
                            
                            // Троттлинг: пропускаем кадры, которые приходят слишком быстро после успешного сканирования
                            if (currentTime - lastScanTime < scanCooldown) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            
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
                                        
                                        // Проверяем, является ли это поддерживаемым форматом штрихкода
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
                                            
                                            // Обновляем время последнего сканирования перед вызовом onScanResult
                                            lastScanTime = System.currentTimeMillis()
                                            onScanResult(barcode.rawValue ?: "", codeType)
                                            break // Обрабатываем только первый найденный штрихкод
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    // Закрываем прокси изображения в случае ошибки
                                    imageProxy.close()
                                }
                                .addOnCompleteListener {
                                    // Всегда закрываем прокси изображения по завершении
                                    imageProxy.close()
                                }
                        }
                        
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
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
            
            // Останавливаем камеру при изменении состояния scanned
            LaunchedEffect(scanned) {
                if (scanned && cameraProvider != null) {
                    try {
                        previewUseCase?.let { preview ->
                            imageAnalysisUseCase?.let { imageAnalysis ->
                                cameraProvider?.unbind(preview, imageAnalysis)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            // Останавливаем камеру при уничтожении компонента
            DisposableEffect(Unit) {
                onDispose {
                    try {
                        previewUseCase?.let { preview ->
                            imageAnalysisUseCase?.let { imageAnalysis ->
                                cameraProvider?.unbind(preview, imageAnalysis)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
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
                    if (!scanSuccess) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .offset(y = 70.dp)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            if (isDark) Color.White else Color.Black,
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                    
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