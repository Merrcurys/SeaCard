package ru.merrcurys.seacard.features.scan

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.merrcurys.seacard.core.design.GradientBackground
import ru.merrcurys.seacard.core.design.GradientUtils
import ru.merrcurys.seacard.core.design.SeaCardTheme
import ru.merrcurys.seacard.core.design.applySeaCardSystemBarColors
import ru.merrcurys.seacard.core.utils.createImagePickerChooserIntent
import ru.merrcurys.seacard.features.crop.ImageCropDialog
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds

class ScanCardActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySeaCardSystemBarColors()
        cameraExecutor = Executors.newSingleThreadExecutor()
        val coverAsset = intent.getStringExtra("cover_asset")
        val barcodePickOnly = intent.getBooleanExtra(EXTRA_BARCODE_PICK_ONLY, false)

        fun deliverBarcodeResult(code: String, type: String) {
            setResult(
                RESULT_OK,
                android.content.Intent().apply {
                    putExtra(RESULT_BARCODE_CODE, code)
                    putExtra(RESULT_BARCODE_TYPE, type)
                },
            )
            finish()
        }

        fun vibrateOnScan() {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        }

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
                                for (barcode in barcodes) {
                                    val codeType = barcodeFormatToCodeType(barcode.format)
                                    val code = barcode.rawValue ?: continue
                                    if (barcodePickOnly) {
                                        vibrateOnScan()
                                        deliverBarcodeResult(code, codeType)
                                        return@addOnSuccessListener
                                    }
                                    if (!viewModel.scanned.value) {
                                        viewModel.onScanResult(code, codeType)
                                        vibrateOnScan()
                                        coroutineScope.launch {
                                            delay(2000.milliseconds)
                                            viewModel.setScanSuccess(false)
                                        }
                                        return@addOnSuccessListener
                                    }
                                }
                                android.widget.Toast.makeText(context, "Код не был найден", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                android.widget.Toast.makeText(
                                    context,
                                    "Ошибка сканирования: ${e.message}",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
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
                                    val color = viewModel.selectedColor.value
                                    if (viewModel.coverAsset != null) {
                                        viewModel.saveCardWithCover(cardName, code, type, color, viewModel.coverAsset, null)
                                    } else {
                                        viewModel.saveCardWithCoverUris(cardName, code, type, color)
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
                            onScanResult = { code, type ->
                                if (barcodePickOnly) {
                                    vibrateOnScan()
                                    deliverBarcodeResult(code, type)
                                } else {
                                    viewModel.onScanResult(code, type)
                                }
                            },
                            alternativeOptionsLabel = if (barcodePickOnly) "Галерея" else "Варианты",
                            alternativeOptionsIcon = if (barcodePickOnly) Icons.Filled.Photo else Icons.Filled.Tune,
                            onOptionsClick = {
                                if (barcodePickOnly) {
                                    galleryLauncher.launch("image/*")
                                } else {
                                    showOptionsSheet = true
                                }
                            },
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
            if (showOptionsSheet && !barcodePickOnly) {
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

    companion object {
        const val EXTRA_BARCODE_PICK_ONLY = "barcode_pick_only"
        const val RESULT_BARCODE_CODE = "barcode_code"
        const val RESULT_BARCODE_TYPE = "barcode_type"
    }
}
