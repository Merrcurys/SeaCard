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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
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

class ScanCardActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var scannedCode: String? = null

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
                    onScanResult = { code ->
                        if (!showManualInput) {
                            cardCode = code
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
                            saveCard(this@ScanCardActivity, cardName, cardCode)
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
    
    private fun saveCard(context: Context, name: String, code: String) {
        val prefs = context.getSharedPreferences("cards", Context.MODE_PRIVATE)
        val cards = prefs.getStringSet("card_list", setOf())?.toMutableSet() ?: mutableSetOf()
        cards.add("$name|$code")
        prefs.edit().putStringSet("card_list", cards).apply()
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
    onScanResult: (String) -> Unit,
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
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
                    onSaveCard = onSaveCard
                )
            } else {
                CameraSection(
                    hasCameraPermission = hasCameraPermission,
                    scanSuccess = scanSuccess,
                    onScanResult = onScanResult,
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
    onScanResult: (String) -> Unit,
    onManualInputToggle: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasCameraPermission) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .padding(16.dp)
            ) {
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
                                                    if (barcode.format == Barcode.FORMAT_CODE_128 ||
                                                        barcode.format == Barcode.FORMAT_CODE_39 ||
                                                        barcode.format == Barcode.FORMAT_EAN_13) {
                                                        onScanResult(barcode.rawValue ?: "")
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
                
                // Индикатор успешного сканирования
                if (scanSuccess) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Green.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Green),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Код распознан!",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .padding(16.dp)
                    .background(Color.Gray, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Нет разрешения на камеру", color = Color.White)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onManualInputToggle,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Keyboard, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ручной ввод", color = Color.Black)
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
    onSaveCard: () -> Unit
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