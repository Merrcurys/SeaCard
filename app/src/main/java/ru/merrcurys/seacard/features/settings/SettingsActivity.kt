package ru.merrcurys.seacard.features.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.merrcurys.seacard.core.design.applySeaCardSystemBarColors
import ru.merrcurys.seacard.core.design.SeaCardTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.tooling.preview.Preview
import android.content.Intent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import ru.merrcurys.seacard.core.design.GradientBackground
import android.widget.Toast
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import java.nio.charset.StandardCharsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.Brush
import ru.merrcurys.seacard.BuildConfig
import ru.merrcurys.seacard.core.design.BerlinAzure
import ru.merrcurys.seacard.core.design.GradientColorOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import ru.merrcurys.seacard.core.backup.BackupManager
import ru.merrcurys.seacard.core.db.CardEntity
import ru.merrcurys.seacard.core.db.DatabaseProvider
import ru.merrcurys.seacard.core.utils.CoverNames

private const val PRIVACY_POLICY_URL = "https://seacard.merrcurys.ru/privacy.html"

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySeaCardSystemBarColors()
        var exportCards: (() -> Unit)? = null
        var importCards: (() -> Unit)? = null
        val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
            if (uri != null) {
                try {
                    runBlocking(Dispatchers.IO) {
                        val dao = DatabaseProvider.get(this@SettingsActivity).cardDao()
                        val cards = dao.getAll()
                        contentResolver.openOutputStream(uri)?.use { out ->
                            BackupManager.exportToZip(this@SettingsActivity, cards, out)
                        }
                    }
                    Toast.makeText(this, "Бэкап экспортирован (карточки и обложки)", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка экспорта: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
        val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                try {
                    val result = runBlocking(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.use { input ->
                            val bytes = input.readBytes()
                            if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                                BackupManager.importFromZip(this@SettingsActivity, bytes.inputStream())
                            } else {
                                importLegacyTxt(this@SettingsActivity, String(bytes, StandardCharsets.UTF_8))
                            }
                        } ?: Pair(0, listOf("Не удалось открыть файл"))
                    }
                    val (imported, errors) = result
                    if (errors.isEmpty()) {
                        Toast.makeText(this, "Импортировано карт: $imported", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Импортировано: $imported. Ошибки: ${errors.take(3).joinToString()}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка импорта: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
        exportCards = { exportLauncher.launch("seacard_backup.zip") }
        importCards = {
            importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "text/plain"))
        }
        setContent {
            val viewModel: SettingsViewModel = viewModel()
            val gradientColor by viewModel.gradientColor.collectAsState(initial = ru.merrcurys.seacard.core.design.BerlinAzure)
            val gridColumns by viewModel.gridColumns.collectAsState(initial = 2)
            SeaCardTheme {
                GradientBackground(gradientColor = gradientColor) {
                    SettingsScreen(
                        gradientColor = gradientColor,
                        onGradientColorChange = { viewModel.setGradientColor(it) },
                        gridColumns = gridColumns,
                        onGridColumnsChange = { viewModel.setGridColumns(it) },
                        onBack = { finish() },
                        topBarContainerColor = Color.Transparent,
                        onExport = { exportCards?.invoke() },
                        onImport = { importCards?.invoke() }
                    )
                }
            }
        }
    }
}

/** Импорт из старого формата TXT (построчно name|code|type|...). Без обложек. */
private suspend fun importLegacyTxt(context: Context, content: String): Pair<Int, List<String>> {
    val dao = DatabaseProvider.get(context).cardDao()
    val assetList = context.assets.list("cards")?.toSet() ?: emptySet()
    val errors = mutableListOf<String>()
    var imported = 0
    content.lines().forEach { line ->
        val parts = line.split("|")
        if (parts.size >= 2) {
            val name = parts[0]
            val code = parts[1]
            val type = parts.getOrNull(2) ?: "barcode"
            if (dao.getByNameCodeType(name, code, type) != null) return@forEach
            val coverAsset = if (parts.size >= 7) parts[6] else null
            val fixedCover = if (coverAsset != null && coverAsset.startsWith("cards/") && assetList.contains(coverAsset.removePrefix("cards/"))) {
                coverAsset
            } else {
                val found = CoverNames.coverNameMap.entries.find { it.value.equals(name, ignoreCase = true) }?.key
                if (found != null) "cards/$found" else null
            }
            val addTime = parts.getOrNull(3)?.toLongOrNull() ?: System.currentTimeMillis()
            val usageCount = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val color = parts.getOrNull(5)?.toIntOrNull() ?: 0xFFFFFFFF.toInt()
            try {
                dao.insert(CardEntity(
                    name = name,
                    code = code,
                    type = type,
                    addTime = addTime,
                    usageCount = usageCount,
                    color = color,
                    frontCoverPath = fixedCover,
                    backCoverPath = null,
                    note = null
                ))
                imported++
            } catch (e: Exception) {
                errors.add(name)
            }
        } else if (line.isNotBlank()) {
            errors.add(line)
        }
    }
    return Pair(imported, errors)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    gradientColor: Color,
    onGradientColorChange: (Color) -> Unit,
    gridColumns: Int,
    onGridColumnsChange: (Int) -> Unit,
    onBack: () -> Unit,
    topBarContainerColor: Color = Color.Transparent,
    onExport: () -> Unit = {},
    onImport: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAboutSheet by remember { mutableStateOf(false) }
    var showGridColumnsDialog by remember { mutableStateOf(false) }
    var showGradientDialog by remember { mutableStateOf(false) }
    val appVersion = BuildConfig.VERSION_NAME
    val sectionCardColor = Color(0xFF141414)
    val sectionShape = RoundedCornerShape(26.dp)
    val sectionBorderColor = Color.White.copy(alpha = 0.06f)
    val listItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
    val smallButtonUnselectedColor = Color(0xFF141414)

    val listState = rememberLazyListState()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Персонализация",
                    color = colorScheme.onSurface.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            item {
                Surface(
                    shape = sectionShape,
                    color = sectionCardColor,
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp
                    ,
                    modifier = Modifier.border(1.dp, sectionBorderColor, sectionShape)
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        ListItem(
                            headlineContent = { Text("Градиентный цвет") },
                            supportingContent = {
                                Text(
                                    text = "Меняет фон приложения",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurface.copy(alpha = 0.62f)
                                )
                            },
                            colors = listItemColors,
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .background(gradientColor, CircleShape)
                                            .border(
                                                width = 1.dp,
                                                color = Color.White.copy(alpha = 0.18f),
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable { showGradientDialog = true }
                        )
                        Divider(color = colorScheme.onSurface.copy(alpha = 0.08f))
                        ListItem(
                            headlineContent = { Text("Отображение карт") },
                            supportingContent = {
                                Text(
                                    text = "Колонок: ${gridColumns.coerceIn(1, 4)}",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurface.copy(alpha = 0.62f)
                                )
                            },
                            colors = listItemColors,
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.ViewModule,
                                    contentDescription = null,
                                    tint = colorScheme.primary
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable { showGridColumnsDialog = true }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Данные",
                    color = colorScheme.onSurface.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            item {
                Surface(
                    shape = sectionShape,
                    color = sectionCardColor,
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp
                    ,
                    modifier = Modifier.border(1.dp, sectionBorderColor, sectionShape)
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        ListItem(
                            headlineContent = { Text("Экспорт") },
                            supportingContent = {
                                Text(
                                    text = "Сохранить все карточки в архив",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurface.copy(alpha = 0.62f)
                                )
                            },
                            colors = listItemColors,
                            leadingContent = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = colorScheme.primary) },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable { onExport() }
                        )
                        Divider(color = colorScheme.onSurface.copy(alpha = 0.08f))
                        ListItem(
                            headlineContent = { Text("Импорт") },
                            supportingContent = {
                                Text(
                                    text = "Восстановить все карточки из архива",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurface.copy(alpha = 0.62f)
                                )
                            },
                            colors = listItemColors,
                            leadingContent = { Icon(Icons.Default.CloudDownload, contentDescription = null, tint = colorScheme.primary) },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable { onImport() }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Поддержка",
                    color = colorScheme.onSurface.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            item {
                Surface(
                    shape = sectionShape,
                    color = sectionCardColor,
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp
                    ,
                    modifier = Modifier.border(1.dp, sectionBorderColor, sectionShape)
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        ListItem(
                            headlineContent = { Text("Связаться с разработчиком") },
                            supportingContent = {
                                Text(
                                    text = "Telegram-бот поддержки",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurface.copy(alpha = 0.62f)
                                )
                            },
                            colors = listItemColors,
                            leadingContent = { Icon(Icons.Outlined.Chat, contentDescription = null, tint = colorScheme.primary) },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable {
                                    val intent = Intent(Intent.ACTION_VIEW, "https://t.me/SeacardSupportBot".toUri())
                                    context.startActivity(intent)
                                }
                        )
                        Divider(color = colorScheme.onSurface.copy(alpha = 0.08f))
                        ListItem(
                            headlineContent = { Text("Следить за приложением") },
                            supportingContent = {
                                Text(
                                    text = "Telegram-канал разработчика",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurface.copy(alpha = 0.62f)
                                )
                            },
                            colors = listItemColors,
                            leadingContent = { Icon(Icons.Outlined.Chat, contentDescription = null, tint = colorScheme.primary) },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable {
                                    val intent = Intent(Intent.ACTION_VIEW, "https://t.me/merrcurys_software".toUri())
                                    context.startActivity(intent)
                                }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Опасная зона",
                    color = colorScheme.onSurface.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            item {
                Surface(
                    shape = sectionShape,
                    color = sectionCardColor,
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp
                    ,
                    modifier = Modifier.border(1.dp, sectionBorderColor, sectionShape)
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        ListItem(
                            headlineContent = { Text("Удалить все карточки") },
                            supportingContent = {
                                Text(
                                    text = "Действие нельзя отменить",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurface.copy(alpha = 0.62f)
                                )
                            },
                            colors = listItemColors,
                            leadingContent = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFF44336)) },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable { showDeleteDialog = true }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Дополнительно",
                    color = colorScheme.onSurface.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            item {
                Surface(
                    shape = sectionShape,
                    color = sectionCardColor,
                    tonalElevation = 2.dp,
                    shadowElevation = 0.dp
                    ,
                    modifier = Modifier.border(1.dp, sectionBorderColor, sectionShape)
                ) {
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable { showAboutSheet = true }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "О приложении",
                                modifier = Modifier.weight(1f),
                                color = colorScheme.onSurface
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить все карточки?") },
            text = { Text("Вы уверены, что хотите удалить все карточки?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        runBlocking(Dispatchers.IO) {
                            DatabaseProvider.get(context).cardDao().deleteAll()
                        }
                        ru.merrcurys.seacard.widget.SeaCardAppWidgetProvider.notifyDataChanged(context)
                        showDeleteDialog = false
                        onBack()
                    }
                ) {
                    Text("Удалить", color = Color(0xFFF44336))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            },
            containerColor = sectionCardColor,
            titleContentColor = colorScheme.onSurface,
            textContentColor = colorScheme.onSurface
        )
    }

    if (showAboutSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val sheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.42f
        val aboutSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

        ModalBottomSheet(
            onDismissRequest = { showAboutSheet = false },
            sheetState = sheetState,
            containerColor = sectionCardColor,
            shape = aboutSheetShape,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false)
        ) {
            BackHandler(onBack = { showAboutSheet = false })
            AboutBottomSheetContent(
                appVersion = appVersion,
                sheetHeight = sheetHeight,
                onPrivacyPolicyClick = {
                    val intent = Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri())
                    context.startActivity(intent)
                }
            )
        }
    }

    if (showGridColumnsDialog) {
        val current = gridColumns.coerceIn(1, 4)
        AlertDialog(
            onDismissRequest = { showGridColumnsDialog = false },
            title = { Text("Отображение карт") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..4).forEach { cols ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .noRippleClickable {
                                    onGridColumnsChange(cols)
                                    showGridColumnsDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$cols",
                                modifier = Modifier.weight(1f),
                                color = colorScheme.onSurface
                            )
                            if (cols == current) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colorScheme.primary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGridColumnsDialog = false }) {
                    Text("Закрыть")
                }
            },
            containerColor = sectionCardColor,
            titleContentColor = colorScheme.onSurface,
            textContentColor = colorScheme.onSurface
        )
    }

    if (showGradientDialog) {
        AlertDialog(
            onDismissRequest = { showGradientDialog = false },
            title = { Text("Градиентный цвет") },
            text = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    GradientColorOption.values().forEach { option ->
                        val selected = gradientColor == option.color
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(option.color, CircleShape)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) colorScheme.primary else Color.White.copy(alpha = 0.18f),
                                    shape = CircleShape
                                )
                                .noRippleClickable {
                                    onGradientColorChange(option.color)
                                    showGradientDialog = false
                                }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGradientDialog = false }) {
                    Text("Закрыть")
                }
            },
            containerColor = sectionCardColor,
            titleContentColor = colorScheme.onSurface,
            textContentColor = colorScheme.onSurface
        )
    }
}

@Composable
private fun AboutBottomSheetContent(
    appVersion: String,
    sheetHeight: androidx.compose.ui.unit.Dp,
    onPrivacyPolicyClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val mutedColor = colorScheme.onSurface.copy(alpha = 0.55f)
    val bodyColor = colorScheme.onSurface.copy(alpha = 0.88f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = sheetHeight)
            .wrapContentHeight()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 20.dp)
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Море Карт",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                letterSpacing = (-0.5).sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Ваш надёжный цифровой кошелёк для хранения скидок, бонусов и карт лояльности.",
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = mutedColor,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                AboutMetaLine(
                    icon = Icons.Outlined.Person,
                    text = "Себежко Александр Андреевич",
                    iconTint = colorScheme.primary,
                    textColor = bodyColor,
                )
                AboutMetaLine(
                    icon = Icons.Outlined.Description,
                    text = "Лицензия GNU GPL-3.0",
                    iconTint = colorScheme.primary.copy(alpha = 0.75f),
                    textColor = bodyColor.copy(alpha = 0.82f),
                )
                AboutMetaLine(
                    icon = Icons.Outlined.Info,
                    text = "Версия $appVersion",
                    iconTint = colorScheme.primary.copy(alpha = 0.75f),
                    textColor = bodyColor.copy(alpha = 0.82f),
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        val sectionCardColor = Color(0xFF141414)
        val sectionShape = RoundedCornerShape(26.dp)
        val sectionBorderColor = Color.White.copy(alpha = 0.06f)
        Surface(
            shape = sectionShape,
            color = sectionCardColor,
            tonalElevation = 2.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, sectionBorderColor, sectionShape)
                .noRippleClickable(onPrivacyPolicyClick)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PrivacyTip,
                    contentDescription = null,
                    tint = colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Политика конфиденциальности",
                    modifier = Modifier.weight(1f),
                    color = colorScheme.onSurface,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun AboutMetaLine(
    icon: ImageVector,
    text: String,
    iconTint: Color,
    textColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = textColor,
        )
    }
}

fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    this.clickable(
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick
    )
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    SeaCardTheme {
        SettingsScreen(
            gradientColor = BerlinAzure,
            onGradientColorChange = {},
            gridColumns = 2,
            onGridColumnsChange = {},
            onBack = {},
            topBarContainerColor = Color.Transparent
        )
    }
}