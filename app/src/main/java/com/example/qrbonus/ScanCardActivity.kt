package com.example.qrbonus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
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
import com.example.qrbonus.ui.theme.QRBonusTheme
import com.example.qrbonus.ui.theme.BlackBackground
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.content.edit

class ScanCardActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var scannedCode: String? = null
    private var scannedCodeType: String = "barcode"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        setContent {
            var hasCameraPermission by remember { mutableStateOf(false) }
            var showManualInput by remember { mutableStateOf(false) }
            var cardName by remember { mutableStateOf("") }
            var cardCode by remember { mutableStateOf("") }
            var scanSuccess by remember { mutableStateOf(false) }
            
            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                hasCameraPermission = isGranted
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
            
            QRBonusTheme(darkTheme = true) {
                ScanCardScreen(
                    hasCameraPermission = hasCameraPermission,
                    showManualInput = showManualInput,
                    cardName = cardName,
                    cardCode = cardCode,
                    scanSuccess = scanSuccess,
                    onCardNameChange = { cardName = it },
                    onCardCodeChange = { cardCode = it },
                    onManualInputToggle = { showManualInput = !showManualInput },
                    onScanResult = { code, codeType ->
                        if (!showManualInput) {
                            cardCode = code
                            scannedCodeType = codeType
                            scanSuccess = true
                            showManualInput = true  // Автоматически переключаемся на ручной ввод
                            // Вибрация при успешном сканировании
                            val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                                vibratorManager.defaultVibrator
                            } else {
                                @Suppress("DEPRECATION")
                                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            }
                            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                            
                            // Сбрасываем индикатор успеха через 2 секунды
                            MainScope().launch {
                                delay(2000)
                                scanSuccess = false
                            }
                        }
                    },
                    onSaveCard = {
                        if (cardName.isNotBlank() && cardCode.isNotBlank()) {
                            saveCard(this@ScanCardActivity, cardName, cardCode, scannedCodeType)
                            setResult(RESULT_OK)
                            finish()
                        }
                    },
                    onBack = { finish() }
                )
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
    
    private fun saveCard(context: Context, name: String, code: String, codeType: String) {
        val prefs = context.getSharedPreferences("cards", Context.MODE_PRIVATE)
        val cards = prefs.getStringSet("card_list", setOf())?.toMutableSet() ?: mutableSetOf()
        cards.add("$name|$code|$codeType")
        prefs.edit { putStringSet("card_list", cards) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanCardScreen(
    hasCameraPermission: Boolean,
    showManualInput: Boolean,
    cardName: String,
    cardCode: String,
    scanSuccess: Boolean,
    onCardNameChange: (String) -> Unit,
    onCardCodeChange: (String) -> Unit,
    onManualInputToggle: () -> Unit,
    onScanResult: (String, String) -> Unit,
    onSaveCard: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BlackBackground)
    ) {
        Column {
            TopAppBar(
                title = { Text("Добавить карту", color = Color.White, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = if (showManualInput) { onManualInputToggle } else { onBack }
                    ) {
                        Icon(
                            Icons.Filled.ArrowBack, 
                            contentDescription = if (showManualInput) "Вернуться к камере" else "Назад", 
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BlackBackground)
            )
            
            if (showManualInput) {
                ManualInputSection(
                    cardName = cardName,
                    cardCode = cardCode,
                    onCardNameChange = onCardNameChange,
                    onCardCodeChange = onCardCodeChange,
                    onSaveCard = onSaveCard,
                    onBackToCamera = onManualInputToggle
                )
            } else {
                CameraSection(
                    hasCameraPermission = hasCameraPermission,
                    scanSuccess = scanSuccess,
                    onScanResult = { code, codeType ->
                        onScanResult(code, codeType)
                    },
                    onManualInputToggle = onManualInputToggle
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
    onManualInputToggle: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
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
                                                    Barcode.FORMAT_AZTEC -> "qr"
                                                    Barcode.FORMAT_DATA_MATRIX -> "qr"
                                                    Barcode.FORMAT_PDF417 -> "qr"
                                                    Barcode.FORMAT_CODE_128,
                                                    Barcode.FORMAT_CODE_39,
                                                    Barcode.FORMAT_EAN_13 -> "barcode"
                                                    else -> "barcode"
                                                }
                                                
                                                if (barcode.format == Barcode.FORMAT_CODE_128 ||
                                                    barcode.format == Barcode.FORMAT_CODE_39 ||
                                                    barcode.format == Barcode.FORMAT_EAN_13 ||
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
                    .background(Color.Black.copy(alpha = 0.5f))
            )
            
            // Затемнение снизу
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.5f))
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
                // Полупрозрачный штрихкод
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
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
                                        if (index % 3 == 0) Color.White.copy(alpha = 0.3f)
                                        else Color.White.copy(alpha = 0.1f)
                                    )
                            )
                        }
                    }
                }
            }
            
            // Текст инструкции в затемненной зоне
            Text(
                text = "Поднесите карту или QR-код к камере,\nчтобы отсканировать код",
                color = Color.White,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp, start = 32.dp, end = 32.dp)
            )
            
            // Кнопка ручного ввода
            Button(
                onClick = onManualInputToggle,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp, start = 80.dp, end = 80.dp)
                    .height(48.dp)
            ) {
                Icon(Icons.Filled.Keyboard, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ручной ввод", color = Color.Black, fontWeight = FontWeight.Medium)
            }
        } else {
            // Заглушка если нет разрешения на камеру
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text("Нет разрешения на камеру", color = Color.White)
            }
        }
    }
}

@Composable
fun ManualInputSection(
    cardName: String,
    cardCode: String,
    onCardNameChange: (String) -> Unit,
    onCardCodeChange: (String) -> Unit,
    onSaveCard: () -> Unit,
    onBackToCamera: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = cardName,
            onValueChange = onCardNameChange,
            label = { Text("Название карты") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.Gray
            )
        )
        
        OutlinedTextField(
            value = cardCode,
            onValueChange = onCardCodeChange,
            label = { Text("Код карты") },
            trailingIcon = {
                IconButton(onClick = onBackToCamera) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = "Сканировать QR-код",
                        tint = Color.White
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.Gray,
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.Gray
            )
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = onSaveCard,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            enabled = cardName.isNotBlank() && cardCode.isNotBlank()
        ) {
            Text("Сохранить карту", color = Color.Black)
        }
    }
} 