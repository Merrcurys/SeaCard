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
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CreditCardOff
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
import androidx.compose.ui.unit.sp
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
            var showOptionsSheet by remember { mutableStateOf(false) }
            var showManualInputWarning by remember { mutableStateOf(false) }
            var showManualBarcodeSelection by remember { mutableStateOf(false) }
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
                                        else -> "code128"
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


            SeaCardTheme {
                GradientBackground(gradientColor = GradientUtils.loadGradientColorPref(context)) {
                    when {
                        showManualBarcodeSelection -> {
                            ManualBarcodeSelectionScreen(
                                onBack = { showManualBarcodeSelection = false },
                                onBarcodeSelected = { code, type ->
                                    showManualBarcodeSelection = false
                                    viewModel.enterManualMode(code, type)
                                }
                            )
                        }
                        scanned -> {
                        CardInputSection(
                            cardName = cardName,
                            cardCode = cardCode,
                            selectedColor = selectedColor,
                            onCardNameChange = { viewModel.setCardName(it) },
                            onCardCodeChange = { viewModel.setCardCode(it) },
                            onColorChange = { viewModel.setSelectedColor(it) },
                            onSaveCard = {
                                coroutineScope.launch {
                                    val type = codeTypeState.ifBlank { if (cardCode.isBlank()) "none" else "code128" }
                                    val code = cardCode
                                    if (viewModel.coverAsset != null) {
                                        viewModel.saveCardWithCover(cardName, code, type, selectedColor, viewModel.coverAsset, null)
                                    } else {
                                        viewModel.saveCardWithCoverUris(cardName, code, type, selectedColor)
                                    }
                                    setResult(RESULT_OK)
                                    finish()
                                }
                            },
                            coverAsset = viewModel.coverAsset,
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
                            onBackCoverRemove = { viewModel.setBackCoverUri(null) },
                            codeType = codeTypeState.ifBlank { "code128" },
                            onCodeTypeChange = { viewModel.setCodeType(it) },
                            showBarcodeFields = true
                        )
                        }
                        else -> {
                        ScanCardScreen(
                            hasCameraPermission = hasCameraPermission,
                            scanned = scanned,
                            scanSuccess = scanSuccess,
                            onScanResult = viewModel::onScanResult,
                            onOptionsClick = { showOptionsSheet = true }
                        )
                        }
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

            // Bottom sheet выбора способа добавления карты
            if (showOptionsSheet) {
                AddCardOptionsSheet(
                    onDismiss = { showOptionsSheet = false },
                    onPickGallery = {
                        showOptionsSheet = false
                        galleryLauncher.launch("image/*")
                    },
                    onManualInput = {
                        showOptionsSheet = false
                        showManualInputWarning = true
                    },
                    onNoBarcode = {
                        showOptionsSheet = false
                        viewModel.enterNoCodeMode()
                    }
                )
            }

            if (showManualInputWarning) {
                ManualInputWarningDialog(
                    onContinue = {
                        showManualInputWarning = false
                        showManualBarcodeSelection = true
                    },
                    onDismiss = { showManualInputWarning = false }
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

private val sectionCardColor = Color(0xFF141414)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCardOptionsSheet(
    onDismiss: () -> Unit,
    onPickGallery: () -> Unit,
    onManualInput: () -> Unit,
    onNoBarcode: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sectionCardColor,
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
    ) {
        BackHandler(onBack = onDismiss)
        AddCardOptionsContent(
            onPickGallery = onPickGallery,
            onManualInput = onManualInput,
            onNoBarcode = onNoBarcode
        )
    }
}

@Composable
private fun AddCardOptionsContent(
    onPickGallery: () -> Unit,
    onManualInput: () -> Unit,
    onNoBarcode: () -> Unit
) {
    val sheetTextColor = Color.White
    val sheetMutedColor = Color.White.copy(alpha = 0.55f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp)
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = "Способ добавления",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = sheetTextColor,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        OptionRow(
            icon = Icons.Filled.Photo,
            iconContentDescription = "Галерея",
            title = "Выбрать изображение из галереи",
            subtitle = "Распознать код с картинки",
            tint = sheetTextColor,
            titleColor = sheetTextColor,
            subtitleColor = sheetMutedColor,
            onClick = onPickGallery
        )
        OptionRow(
            icon = Icons.Filled.Edit,
            iconContentDescription = "Ручной ввод",
            title = "Ручной ввод номера",
            subtitle = "Введите номер на странице добавления",
            tint = sheetTextColor,
            titleColor = sheetTextColor,
            subtitleColor = sheetMutedColor,
            onClick = onManualInput
        )
        OptionRow(
            icon = Icons.Filled.CreditCardOff,
            iconContentDescription = "Без штрих-кода",
            title = "Добавить карту без штрих-кода",
            subtitle = "Только обложка и название",
            tint = sheetTextColor,
            titleColor = sheetTextColor,
            subtitleColor = sheetMutedColor,
            onClick = onNoBarcode,
            isLast = true
        )
    }
}

@Composable
private fun OptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconContentDescription: String,
    title: String,
    subtitle: String,
    tint: Color,
    titleColor: Color,
    subtitleColor: Color,
    onClick: () -> Unit,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = iconContentDescription, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = titleColor)
            Text(text = subtitle, fontSize = 13.sp, color = subtitleColor)
        }
    }
    if (!isLast) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = Color.White.copy(alpha = 0.12f)
        )
    }
}

@Composable
private fun ManualInputWarningDialog(
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ручной ввод", color = Color.White) },
        text = {
            Text(
                "В некоторых картах значение штрих-кода может отличаться от написанного на карте номера, " +
                    "из-за чего введение номера вручную не всегда работает. " +
                    "Настоятельно советуем вместо этого отсканировать штрих-код камерой.",
                color = Color.White.copy(alpha = 0.9f)
            )
        },
        confirmButton = {
            TextButton(onClick = onContinue) {
                Text("Продолжить", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = sectionCardColor,
        titleContentColor = Color.White,
        textContentColor = Color.White
    )
}

@Composable
fun ScanCardScreen(
    hasCameraPermission: Boolean,
    scanned: Boolean,
    scanSuccess: Boolean,
    onScanResult: (String, String) -> Unit,
    onOptionsClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        CameraSection(
            hasCameraPermission = hasCameraPermission,
            scanSuccess = scanSuccess,
            scanned = scanned,
            onScanResult = onScanResult,
            onOptionsClick = onOptionsClick
        )
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun CameraSection(
    hasCameraPermission: Boolean,
    scanSuccess: Boolean,
    scanned: Boolean,
    onScanResult: (String, String) -> Unit,
    onOptionsClick: () -> Unit
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
                                            else -> "code128"
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
            // Кнопка «Варианты» по центру под текстом
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
            ) {
                Button(
                    onClick = onOptionsClick,
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
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Другие способы добавления",
                        tint = if (isDark) Color.White else Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Варианты", fontWeight = FontWeight.Medium)
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