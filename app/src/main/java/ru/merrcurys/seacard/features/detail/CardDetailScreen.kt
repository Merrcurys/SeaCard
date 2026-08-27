package ru.merrcurys.seacard.features.detail

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.merrcurys.seacard.core.barcode.formatBarcodeForStandard
import ru.merrcurys.seacard.core.barcode.generateBarcodeBitmap
import ru.merrcurys.seacard.core.barcode.isValidBarcodeWithChecksum
import ru.merrcurys.seacard.core.utils.createImagePickerChooserIntent
import ru.merrcurys.seacard.features.crop.ImageCropDialog
import ru.merrcurys.seacard.features.scan.CardInputSection
import ru.merrcurys.seacard.features.scan.ScanCardActivity
import java.io.File

private fun plainCodeDisplayFontSize(code: String): TextUnit {
    val length = code.length
    return when {
        length <= 4 -> 48.sp
        length <= 8 -> 40.sp
        length <= 12 -> 32.sp
        length <= 16 -> 26.sp
        length <= 22 -> 22.sp
        length <= 30 -> 18.sp
        length <= 40 -> 16.sp
        else -> 14.sp
    }
}

// Функция для вычисления контрастного цвета текста
fun getContrastTextColor(backgroundColor: Color): Color {
    val luminance = backgroundColor.luminance()
    return if (luminance > 0.5f) Color.Black else Color.White
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    viewModel: CardDetailViewModel,
    cardName: String,
    cardCode: String,
    codeType: String,
    cardColor: Int,
    coverAsset: String? = null,
    frontCoverPath: String? = null,
    frontImageUri: Uri? = null,
    note: String = "",
    backCoverPath: String? = null,
    hasCameraPermission: Boolean = true,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>? = null,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    topBarContainerColor: Color = Color.Transparent,
    topBarTextColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val showMenu by viewModel.showMenu.collectAsState()
    val showDeleteDialog by viewModel.showDeleteDialog.collectAsState()
    val showEditDialog by viewModel.showEditDialog.collectAsState()
    val showNoteDialog by viewModel.showNoteDialog.collectAsState()
    val showCoverDialog by viewModel.showCoverDialog.collectAsState()
    val noteDraft by viewModel.noteDraft.collectAsState()
    val noteError by viewModel.noteError.collectAsState()
    val editName by viewModel.editName.collectAsState()
    val editCode by viewModel.editCode.collectAsState()
    val editType by viewModel.editType.collectAsState()
    val editColor by viewModel.editColor.collectAsState()
    val editError by viewModel.editError.collectAsState()
    val editFrontCoverUri by viewModel.editFrontCoverUri.collectAsState()
    val editBackCoverUri by viewModel.editBackCoverUri.collectAsState()
    val editFrontCoverRemoved by viewModel.editFrontCoverRemoved.collectAsState()
    val editBackCoverRemoved by viewModel.editBackCoverRemoved.collectAsState()
    val showEditFrontCrop by viewModel.showEditFrontCrop.collectAsState()
    val showEditBackCrop by viewModel.showEditBackCrop.collectAsState()
    val editFrontCropUri by viewModel.editFrontCropUri.collectAsState()
    val editBackCropUri by viewModel.editBackCropUri.collectAsState()
    val showFullScreenImage by viewModel.showFullScreenImage.collectAsState()

    val displayCodeType = when (codeType) {
        "barcode", "" -> "code128"
        else -> codeType
    }
    val barcodeBitmap = remember(cardCode, displayCodeType) {
        if (isValidBarcodeWithChecksum(cardCode, displayCodeType) && displayCodeType != "none") {
            generateBarcodeBitmap(cardCode, displayCodeType)
        } else {
            null
        }
    }
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    var pendingEditFrontCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingEditBackCameraUri by remember { mutableStateOf<Uri?>(null) }
    var pendingEditCoverPick by remember { mutableStateOf<String?>(null) }
    var backImageUri by remember { mutableStateOf<Uri?>(null) }

    val context2 = LocalContext.current
    val frontCoverUri = frontCoverPath?.takeIf { !it.startsWith("cards/") }?.let { Uri.fromFile(File(it)) }
    val backCoverUri = backCoverPath?.let { Uri.fromFile(File(it)) }

    val editFrontPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data ?: pendingEditFrontCameraUri
            pendingEditFrontCameraUri = null
            uri?.let { viewModel.onEditFrontPicked(it) }
        }
    }
    val editBackPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.data ?: pendingEditBackCameraUri
            pendingEditBackCameraUri = null
            uri?.let { viewModel.onEditBackPicked(it) }
        }
    }
    val scanBarcodeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val code = result.data?.getStringExtra(ScanCardActivity.RESULT_BARCODE_CODE)
            val type = result.data?.getStringExtra(ScanCardActivity.RESULT_BARCODE_TYPE)
            if (!code.isNullOrBlank() && !type.isNullOrBlank()) {
                viewModel.setEditCode(code)
                viewModel.setEditType(type)
            }
        }
    }
    LaunchedEffect(hasCameraPermission, pendingEditCoverPick) {
        if (!hasCameraPermission || pendingEditCoverPick == null) return@LaunchedEffect
        when (pendingEditCoverPick) {
            "edit_front" -> {
                val (intent, cameraUri) = createImagePickerChooserIntent(context2)
                pendingEditFrontCameraUri = cameraUri
                editFrontPicker.launch(intent)
            }
            "edit_back" -> {
                val (intent, cameraUri) = createImagePickerChooserIntent(context2)
                pendingEditBackCameraUri = cameraUri
                editBackPicker.launch(intent)
            }
        }
        pendingEditCoverPick = null
    }
    // Показываем frontCoverPath (webp) если есть, иначе coverAsset (assets/cards)
    val coverBitmap: ImageBitmap? = remember(frontCoverPath, coverAsset) {
        try {
            var result: ImageBitmap? = null
            frontCoverPath?.let { path ->
                if (path.startsWith("cards/")) {
                    val input = context2.assets.open(path)
                    val bmp = BitmapFactory.decodeStream(input)
                    input.close()
                    if (bmp != null) result = bmp.asImageBitmap()
                } else {
                    val bmp = BitmapFactory.decodeFile(path)
                    if (bmp != null) result = bmp.asImageBitmap()
                }
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

    // Функция копирования в буфер обмена
    fun copyToClipboard() {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Код карты", cardCode)
        clipboardManager.setPrimaryClip(clip)
        // Android 13+ показывает системное «Скопировано» — не дублируем своим Toast.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Код скопирован в буфер обмена", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler(
        enabled = showEditDialog || showEditFrontCrop || showEditBackCrop
    ) {
        when {
            showEditFrontCrop -> viewModel.dismissEditFrontCrop()
            showEditBackCrop -> viewModel.dismissEditBackCrop()
            showEditDialog -> viewModel.closeEditDialog()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (!showEditDialog) {
                TopAppBar(
                    title = {
                        Text(
                            text = cardName,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = topBarTextColor,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = topBarTextColor)
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { viewModel.setShowMenu(true) }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Меню", tint = topBarTextColor)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { viewModel.setShowMenu(false) },
                                modifier = Modifier.background(colorScheme.surface)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Изменить карту") },
                                    onClick = {
                                        viewModel.setShowMenu(false)
                                        viewModel.openEditDialog()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Edit, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Удалить карту", color = Color.Red) },
                                    onClick = {
                                        viewModel.setShowMenu(false)
                                        viewModel.setShowDeleteDialog(true)
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
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
        ) {
            // Диалог удаления
            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.setShowDeleteDialog(false) },
                    title = { Text("Удалить карту?") },
                    text = { Text("Карта '$cardName' будет удалена безвозвратно.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.setShowDeleteDialog(false)
                                onDelete()
                            }
                        ) {
                            Text("Удалить", color = Color.Red)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.setShowDeleteDialog(false) }) {
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
                CardInputSection(
                    cardName = editName,
                    cardCode = editCode,
                    selectedColor = editColor,
                    onCardNameChange = viewModel::setEditName,
                    onCardCodeChange = viewModel::setEditCode,
                    onColorChange = viewModel::setEditColor,
                    onSaveCard = viewModel::saveEdit,
                    coverAsset = if (editFrontCoverRemoved) null else coverAsset,
                    isEditMode = true,
                    showTopBar = false,
                    onBack = viewModel::closeEditDialog,
                    frontCoverUri = if (editFrontCoverRemoved) null else (editFrontCoverUri
                        ?: frontCoverUri),
                    backCoverUri = if (editBackCoverRemoved) null else editBackCoverUri,
                    onFrontCoverPick = {
                        if (hasCameraPermission) {
                            val (intent, cameraUri) = createImagePickerChooserIntent(context2)
                            pendingEditFrontCameraUri = cameraUri
                            editFrontPicker.launch(intent)
                        } else {
                            pendingEditCoverPick = "edit_front"
                            permissionLauncher?.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onBackCoverPick = {
                        if (hasCameraPermission) {
                            val (intent, cameraUri) = createImagePickerChooserIntent(context2)
                            pendingEditBackCameraUri = cameraUri
                            editBackPicker.launch(intent)
                        } else {
                            pendingEditCoverPick = "edit_back"
                            permissionLauncher?.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onFrontCoverRemove = viewModel::removeEditFrontCover,
                    onBackCoverRemove = viewModel::removeEditBackCover,
                    codeType = editType.ifBlank { "code128" },
                    onCodeTypeChange = viewModel::setEditType,
                    showBarcodeFields = true,
                    onPickBarcode = {
                        scanBarcodeLauncher.launch(
                            Intent(context2, ScanCardActivity::class.java).apply {
                                putExtra(ScanCardActivity.EXTRA_BARCODE_PICK_ONLY, true)
                            },
                        )
                    },
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Spacer(modifier = Modifier.height(innerPadding.calculateTopPadding() + 8.dp))
                    // Штрих-код или текстовый код (для карт без штрих-кода)
                    if (barcodeBitmap != null && cardCode.isNotBlank() && displayCodeType != "none") {
                        val isSquareCode =
                            displayCodeType == "qr" || displayCodeType == "datamatrix"
                        val cardHeight = if (isSquareCode) 350.dp else 300.dp
                        val imageHeight =
                            if (displayCodeType == "qr" || displayCodeType == "datamatrix") 230.dp else 230.dp
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
                                    bitmap = barcodeBitmap.asImageBitmap(),
                                    contentDescription = cardName,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(imageHeight)
                                )
                            }
                        }
                    } else if (displayCodeType == "none" && cardCode.isNotBlank()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 8.dp)
                                .height(300.dp)
                                .shadow(18.dp, RoundedCornerShape(28.dp)),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
                            shape = RoundedCornerShape(28.dp),
                            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.25f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(onLongPress = { copyToClipboard() })
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cardCode,
                                        fontSize = plainCodeDisplayFontSize(cardCode),
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                        textAlign = TextAlign.Center,
                                        lineHeight = plainCodeDisplayFontSize(cardCode) * 1.15f,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
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
                        // Текст кода карты — скрыт для карт без штрих-кода
                        if (cardCode.isNotBlank() && displayCodeType != "none") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = colorScheme.surface.copy(
                                        alpha = 0.20f
                                    )
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val formattedCode =
                                        formatBarcodeForStandard(cardCode, displayCodeType)
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
                                            fontSize = if (cardCode.length > 20) 20.sp else 28.sp,
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
                        }
                        // Заметки и Обложка
                        Spacer(modifier = Modifier.height(1.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(
                                space = 12.dp,
                                alignment = Alignment.CenterHorizontally
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Кнопка "Заметки"
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { viewModel.setShowNoteDialog(true) }
                                        )
                                    },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = colorScheme.surface.copy(
                                        alpha = 0.20f
                                    )
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(
                                        12.dp,
                                        Alignment.CenterHorizontally
                                    )
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
                                }
                            }
                            // Кнопка "Обложка"
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = colorScheme.surface.copy(
                                        alpha = 0.20f
                                    )
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { viewModel.setShowCoverDialog(true) }
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(
                                        12.dp,
                                        Alignment.CenterHorizontally
                                    )
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
                        Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding() + 24.dp))
                    }
                }
                // Диалог для заметки
                if (showNoteDialog) {
                    AlertDialog(
                        onDismissRequest = { viewModel.setShowNoteDialog(false) },
                        title = { Text("Заметка к карте") },
                        text = {
                            Column {
                                OutlinedTextField(
                                    value = noteDraft,
                                    onValueChange = {
                                        if (it.length <= 100) {
                                            viewModel.setNoteDraft(it)
                                        }
                                    },
                                    label = { Text("Введите заметку") },
                                    singleLine = false,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth(),
                                    trailingIcon = {
                                        if (noteDraft.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.setNoteDraft("") }) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Очистить"
                                                )
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
                                onClick = viewModel::saveNote
                            ) {
                                Text("Сохранить")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.setShowNoteDialog(false) }) {
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
                        onDismissRequest = { viewModel.setShowCoverDialog(false) },
                        title = { Text("Обложка карты") },
                        text = {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.574f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                        .clickable {
                                            when {
                                                frontCoverPath != null && !frontCoverPath.startsWith(
                                                    "cards/"
                                                ) ->
                                                    viewModel.setShowFullScreenImage(
                                                        true to Uri.fromFile(File(frontCoverPath))
                                                    )

                                                frontImageUri != null -> viewModel.setShowFullScreenImage(
                                                    true to frontImageUri
                                                )

                                                coverBitmap != null -> viewModel.setShowFullScreenImage(
                                                    true to null
                                                )
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val frontBitmap = frontCoverPath?.let { path ->
                                        if (path.startsWith("cards/")) null
                                        else try {
                                            BitmapFactory.decodeFile(path)
                                        } catch (_: Exception) {
                                            null
                                        }
                                    } ?: frontImageUri?.let { rememberBitmapFromUri(it) }
                                    if (frontBitmap != null) {
                                        Image(
                                            bitmap = frontBitmap.asImageBitmap(),
                                            contentDescription = "Лицевая обложка",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else if (coverBitmap != null) {
                                        Image(
                                            bitmap = coverBitmap,
                                            contentDescription = "Лицевая обложка",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.574f)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                        .clickable {
                                            backCoverPath?.let { path ->
                                                viewModel.setShowFullScreenImage(
                                                    true to Uri.fromFile(File(path))
                                                )
                                            } ?: run {
                                                if (backImageUri != null) {
                                                    viewModel.setShowFullScreenImage(true to backImageUri)
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val backBitmap = if (backCoverPath != null) {
                                        try {
                                            BitmapFactory.decodeFile(backCoverPath)
                                        } catch (_: Exception) {
                                            null
                                        }
                                    } else rememberBitmapFromUri(backImageUri)
                                    if (backBitmap != null) {
                                        Image(
                                            bitmap = backBitmap.asImageBitmap(),
                                            contentDescription = "Тыльная обложка",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Нажмите на обложку, чтобы увеличить изображение. А изменить обложку, можно в меню изменения карты.",
                                    fontSize = 13.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { viewModel.setShowCoverDialog(false) }) {
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
                    Dialog(
                        onDismissRequest = { viewModel.setShowFullScreenImage(false to null) },
                        properties = DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .safeDrawingPadding(),
                            contentAlignment = Alignment.Center
                        ) {
                            val uri = showFullScreenImage.second
                            val bitmap =
                                if (uri != null) rememberBitmapFromUri(uri)?.asImageBitmap() else coverBitmap
                            if (bitmap != null) {
                                var scale by remember { mutableFloatStateOf(1f) }
                                var offset by remember { mutableStateOf(Offset.Zero) }
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
        if (showEditFrontCrop && editFrontCropUri != null) {
            ImageCropDialog(
                imageUri = editFrontCropUri!!,
                aspectRatio = 1.574f,
                onCrop = viewModel::onEditFrontCrop,
                onDismiss = viewModel::dismissEditFrontCrop
            )
        }
        if (showEditBackCrop && editBackCropUri != null) {
            ImageCropDialog(
                imageUri = editBackCropUri!!,
                aspectRatio = 1.574f,
                onCrop = viewModel::onEditBackCrop,
                onDismiss = viewModel::dismissEditBackCrop
            )
        }
    }
}
