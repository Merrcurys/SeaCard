package ru.merrcurys.seacard.features.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.floor
import kotlin.math.roundToInt
import ru.merrcurys.seacard.core.design.GradientBackground
import ru.merrcurys.seacard.core.design.SeaCardTheme
import ru.merrcurys.seacard.core.design.applySeaCardSystemBarColors
import ru.merrcurys.seacard.core.play.PlayInAppUpdateController
import ru.merrcurys.seacard.core.play.PlayReviewHelper
import ru.merrcurys.seacard.core.rustore.RuStoreInAppUpdateController
import ru.merrcurys.seacard.core.rustore.RuStoreReviewHelper
import ru.merrcurys.seacard.core.store.AppInstallSource
import ru.merrcurys.seacard.core.utils.SortType
import ru.merrcurys.seacard.features.detail.CardDetailActivity
import ru.merrcurys.seacard.features.scan.ScanCardActivity
import ru.merrcurys.seacard.features.settings.SettingsActivity
import java.io.File
import ru.merrcurys.seacard.domain.entity.Card as CardModel

private const val IN_APP_REVIEW_LOG_TAG = "InAppReview"

private fun mainGridCoverModel(frontPath: String): Any =
    if (frontPath.startsWith("cards/")) "file:///android_asset/$frontPath"
    else File(frontPath)

private fun isColorDark(color: Int): Boolean {
    val red = (color shr 16) and 0xFF
    val green = (color shr 8) and 0xFF
    val blue = color and 0xFF
    val brightness = (red * 299 + green * 587 + blue * 114) / 1000
    return brightness < 128
}

/**
 * Индекс ячейки сетки под центром перетаскиваемой карточки.
 * Считается через геометрию видимых ячеек, поэтому устойчиво к прокрутке.
 */
private fun computeTargetIndex(
    visibleItems: List<LazyGridItemInfo>,
    draggingKey: Any?,
    center: Offset,
    itemSize: IntSize,
    columns: Int,
    spacingPx: Float,
    listSize: Int,
    fallback: Int
): Int {
    if (columns <= 0 || visibleItems.isEmpty() || itemSize.width <= 0 || itemSize.height <= 0 || listSize <= 0) {
        return fallback
    }
    val columnPitch = itemSize.width + spacingPx
    val rowPitch = itemSize.height + spacingPx
    val reference = visibleItems.firstOrNull { it.key != draggingKey } ?: visibleItems.first()
    val referenceColumn = reference.index % columns
    val referenceRow = reference.index / columns
    val originX = reference.offset.x - referenceColumn * columnPitch
    val originY = reference.offset.y - referenceRow * rowPitch
    val column = floor((center.x - originX) / columnPitch).toInt().coerceIn(0, columns - 1)
    val row = floor((center.y - originY) / rowPitch).toInt().coerceAtLeast(0)
    return (row * columns + column).coerceIn(0, listSize - 1)
}

/** Стабильный (один и тот же) Modifier: позволяет GridCardItem пропускать рекомпозицию. */
private val GridItemFillModifier = Modifier.fillMaxSize()

@Composable
private fun GridCardItem(
    card: CardModel,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(12.dp)
    val dark = isColorDark(card.color)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (card.coverAsset != null) Color.Transparent else Color(card.color),
            contentColor = if (card.coverAsset != null) Color.Unspecified else if (dark) Color.White else Color.Black
        ),
        shape = cardShape,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.12f), shape = cardShape)
                )
            }
            CardCover(card = card, dark = dark)
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Выбрано",
                    tint = colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                )
            }
        }
    }
}

@Composable
private fun CardCover(card: CardModel, dark: Boolean) {
    val context = LocalContext.current
    val frontPath = card.frontCoverPath
    key(frontPath) {
        if (frontPath != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(mainGridCoverModel(frontPath))
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                    is AsyncImagePainter.State.Loading,
                    is AsyncImagePainter.State.Empty -> Unit
                    is AsyncImagePainter.State.Error -> Text(
                        text = card.name,
                        color = if (dark) Color.White else Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        } else {
            Text(
                text = card.name,
                color = if (dark) Color.White else Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

class MainActivity : ComponentActivity() {

    private var playUpdateController: PlayInAppUpdateController? = null
    private var ruStoreUpdateController: RuStoreInAppUpdateController? = null
    private val installSource: AppInstallSource by lazy { AppInstallSource.detect(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySeaCardSystemBarColors()

        when (installSource) {
            AppInstallSource.GOOGLE_PLAY ->
                playUpdateController = PlayInAppUpdateController(this).also { it.checkOnLaunch() }
            AppInstallSource.RU_STORE ->
                ruStoreUpdateController = RuStoreInAppUpdateController(this).also { it.checkOnLaunch() }
            AppInstallSource.UNKNOWN -> Unit
        }

        setContent {
            val viewModel: MainViewModel = viewModel()
            val cards by viewModel.cards.collectAsStateWithLifecycle(initialValue = emptyList())
            val cardsFromDbReady by viewModel.cardsFromDbReady.collectAsStateWithLifecycle(initialValue = false)
            val currentSortType by viewModel.sortType.collectAsStateWithLifecycle()
            val showCoverPicker by viewModel.showCoverPicker.collectAsStateWithLifecycle()
            val gridColumns by viewModel.gridColumns.collectAsStateWithLifecycle()
            val gradientColor by viewModel.gradientColor.collectAsStateWithLifecycle()

            val scanCardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
            val cardDetailLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
            val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
            val context = this@MainActivity

            LaunchedEffect(installSource) {
                if (installSource == AppInstallSource.UNKNOWN) return@LaunchedEffect

                Log.i(
                    IN_APP_REVIEW_LOG_TAG,
                    "Ожидание: главный экран (!picker) и карт >= 5, магазин=$installSource, сейчас карт=${cards.size}",
                )
                snapshotFlow { !showCoverPicker && cards.size >= 5 }
                    .first { it }
                Log.i(IN_APP_REVIEW_LOG_TAG, "Условие выполнено, запрос отзыва ($installSource)")
                when (installSource) {
                    AppInstallSource.GOOGLE_PLAY -> PlayReviewHelper.tryLaunchReview(this@MainActivity)
                    AppInstallSource.RU_STORE -> RuStoreReviewHelper.tryLaunchReview(this@MainActivity)
                    AppInstallSource.UNKNOWN -> Unit
                }
            }

            SeaCardTheme {
                GradientBackground(gradientColor = gradientColor) {
                    if (showCoverPicker) {
                        CardCoverPickerScreen(
                            onCoverSelected = { coverAsset: String? ->
                                viewModel.setShowCoverPicker(false)
                                val intent = Intent(context, ScanCardActivity::class.java)
                                if (coverAsset != null) intent.putExtra("cover_asset", coverAsset)
                                scanCardLauncher.launch(intent)
                            },
                            onBack = { viewModel.setShowCoverPicker(false) }
                        )
                    } else {
                        MainScreen(
                            cards = cards,
                            cardsFromDbReady = cardsFromDbReady,
                            currentSortType = currentSortType,
                            gridColumns = gridColumns,
                            gradientColor = gradientColor,
                            onAddCard = { viewModel.setShowCoverPicker(true) },
                            onCardClick = { card ->
                                // Счётчик использования увеличивается при открытии CardDetailActivity
                                // (в т.ч. при клике по карточке из виджета).
                                cardDetailLauncher.launch(Intent(context, CardDetailActivity::class.java).apply { putExtra("card_id", card.id) })
                            },
                            onSettingsClick = { settingsLauncher.launch(Intent(context, SettingsActivity::class.java)) },
                            onSortTypeChange = { viewModel.setSortType(it) },
                            onReorderCards = { viewModel.reorderCards(it) },
                            onDeleteCards = { viewModel.deleteCards(it) }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        playUpdateController?.dispose()
        ruStoreUpdateController?.dispose()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    cards: List<CardModel>,
    cardsFromDbReady: Boolean = true,
    currentSortType: SortType,
    gridColumns: Int,
    gradientColor: Color,
    onAddCard: () -> Unit,
    onCardClick: (CardModel) -> Unit,
    onSettingsClick: () -> Unit,
    onSortTypeChange: (SortType) -> Unit,
    onReorderCards: (List<CardModel>) -> Unit,
    onDeleteCards: (List<CardModel>) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    GradientBackground(gradientColor = gradientColor) {
        val searchQueryState = rememberSaveable(saver = TextFieldState.Saver) { TextFieldState() }
        var showSearch by rememberSaveable { mutableStateOf(false) }
        var showFilterMenu by rememberSaveable { mutableStateOf(false) }
        var selectionMode by rememberSaveable { mutableStateOf(false) }
        // Храним идентификаторы: после drag меняется sortOrder и объекты CardModel пересоздаются.
        var selectedCards by retain { mutableStateOf<Set<Long>>(emptySet()) }
        val focusRequester = retain { FocusRequester() }

        val filteredCards = remember(cards, searchQueryState.text) {
            fun normalize(text: String): String {
                return text
                    .replace("'", "")
                    .replace("’", "")
                    .replace("`", "")
                    .replace("ё", "е", ignoreCase = true)
                    .replace("Ё", "Е", ignoreCase = true)
                    .lowercase()
            }
            val query = searchQueryState.text.toString()
            if (query.isBlank()) {
                cards
            } else {
                val normQuery = normalize(query)
                cards.filter { card ->
                    normalize(card.name).contains(normQuery)
                }
            }
        }

        val density = LocalDensity.current
        val haptics = LocalHapticFeedback.current
        val gridState = rememberLazyGridState()
        val spacingPx = with(density) { 8.dp.toPx() }

        var dragOrder by remember { mutableStateOf<List<CardModel>?>(null) }
        var draggingId by remember { mutableStateOf<Long?>(null) }
        var draggingIndex by remember { mutableIntStateOf(-1) }
        var dragPointer by remember { mutableStateOf(Offset.Zero) }
        var dragStartPointer by remember { mutableStateOf(Offset.Zero) }
        var dragGrabOffset by remember { mutableStateOf(Offset.Zero) }
        var dragItemSize by remember { mutableStateOf(IntSize.Zero) }
        var dragCenter by remember { mutableStateOf(Offset.Zero) }
        var lastDragEndedAt by remember { mutableStateOf(0L) }

        val displayCards = dragOrder ?: filteredCards
        // Перетаскивание доступно и в режиме выбора: оно само его включает.
        val dragEnabled = cardsFromDbReady && searchQueryState.text.isBlank()

        val latestFilteredCards by rememberUpdatedState(filteredCards)
        val latestCards by rememberUpdatedState(cards)
        val latestColumns by rememberUpdatedState(gridColumns)
        val latestSpacingPx by rememberUpdatedState(spacingPx)
        val latestDragEnabled by rememberUpdatedState(dragEnabled)

        // Снимаем закреплённый локальный порядок, когда БД отдала ровно тот же список,
        // либо когда набор карт изменился (добавили/удалили) — иначе в сетке останется
        // «призрак» удалённой карты со старой обложкой.
        LaunchedEffect(cards, dragOrder) {
            val pinned = dragOrder ?: return@LaunchedEffect
            if (draggingId != null) return@LaunchedEffect
            val pinnedIds = pinned.map { it.id }
            val cardIds = cards.map { it.id }
            if (pinnedIds == cardIds || pinnedIds.toSet() != cardIds.toSet()) {
                dragOrder = null
            }
        }

        // Автопрокрутка при перетаскивании к верхнему/нижнему краю сетки.
        LaunchedEffect(draggingId) {
            if (draggingId == null) return@LaunchedEffect
            val threshold = with(density) { 72.dp.toPx() }
            while (isActive) {
                val viewportHeight = gridState.layoutInfo.viewportSize.height
                if (viewportHeight > 0) {
                    val centerY = dragCenter.y
                    val dy = when {
                        centerY < threshold -> -((threshold - centerY) * 0.04f).coerceIn(0f, 14f)
                        centerY > viewportHeight - threshold ->
                            ((centerY - (viewportHeight - threshold)) * 0.04f).coerceIn(0f, 14f)
                        else -> 0f
                    }
                    if (dy != 0f) gridState.scrollBy(dy)
                }
                withFrameNanos { }
            }
        }

        fun stopDragging() {
            draggingId = null
            draggingIndex = -1
            dragPointer = Offset.Zero
            dragStartPointer = Offset.Zero
            dragGrabOffset = Offset.Zero
            dragItemSize = IntSize.Zero
            dragCenter = Offset.Zero
        }

        BackHandler(enabled = selectionMode) {
            selectionMode = false
            selectedCards = emptySet()
        }

        BackHandler(enabled = showSearch) {
            searchQueryState.clearText()
            showSearch = false
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = {
                        if (selectionMode) {
                            Text("Выбрано: ${selectedCards.size}", color = colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        } else if (showSearch) {
                            LaunchedEffect(Unit) {
                                focusRequester.requestFocus()
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        searchQueryState.clearText()
                                        showSearch = false
                                    }
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Закрыть поиск",
                                        tint = colorScheme.onSurface
                                    )
                                }

                                OutlinedTextField(
                                    state = searchQueryState,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .padding(end = 16.dp)
                                        .focusRequester(focusRequester),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = colorScheme.onSurface,
                                        unfocusedTextColor = colorScheme.onSurface,
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        disabledBorderColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    placeholder = {
                                        Text(
                                            text = "Поиск карт...",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                    contentPadding = OutlinedTextFieldDefaults.contentPadding(
                                        start = 8.dp,
                                        end = 8.dp,
                                        top = 8.dp,
                                        bottom = 8.dp
                                    ),
                                    textStyle = TextStyle(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    trailingIcon = {
                                        if (searchQueryState.text.isNotEmpty()) {
                                            IconButton(onClick = { searchQueryState.clearText() }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Очистить", tint = colorScheme.onSurface.copy(alpha = 0.7f))
                                            }
                                        }
                                    }
                                )
                            }
                        } else {
                            Text("Карты", color = colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        }
                    },
                    navigationIcon = {},
                    actions = {
                        if (selectionMode) {
                            IconButton(onClick = {
                                dragOrder = null
                                onDeleteCards(displayCards.filter { it.id in selectedCards })
                                selectedCards = emptySet()
                                selectionMode = false
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить",
                                    tint = colorScheme.onSurface
                                )
                            }
                        } else {
                            if (!showSearch) {
                                IconButton(
                                    onClick = { showSearch = !showSearch }
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "Поиск",
                                        tint = colorScheme.onSurface
                                    )
                                }
                            }
                            Box {
                                IconButton(onClick = { showFilterMenu = true }) {
                                    Icon(
                                        Icons.Default.FilterAlt,
                                        contentDescription = "Фильтр",
                                        tint = colorScheme.onSurface
                                    )
                                }
                                DropdownMenu(
                                    expanded = showFilterMenu,
                                    onDismissRequest = { showFilterMenu = false },
                                    modifier = Modifier.background(colorScheme.surface)
                                ) {
                                    SortType.entries.forEach { sortType ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = sortType.displayName,
                                                    color = if (currentSortType == sortType) colorScheme.primary else colorScheme.onSurface
                                                )
                                            },
                                            onClick = {
                                                dragOrder = null
                                                onSortTypeChange(sortType)
                                                showFilterMenu = false
                                            },
                                            leadingIcon = {
                                                if (currentSortType == sortType) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Выбрано",
                                                        tint = colorScheme.primary
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = onSettingsClick) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "Настройки",
                                    tint = colorScheme.onSurface
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            floatingActionButton = {
                if (!selectionMode && cardsFromDbReady) {
                    val addFabShape = RoundedCornerShape(28.dp)
                    Surface(
                        onClick = onAddCard,
                        modifier = Modifier
                            .semantics { contentDescription = "Добавить карту" }
                            .height(52.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.14f), addFabShape),
                        shape = addFabShape,
                        color = Color(0xE61C1C20),
                        contentColor = Color.White,
                        tonalElevation = 2.dp,
                        shadowElevation = 10.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = "Добавить карту",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                letterSpacing = 0.25.sp
                            )
                        }
                    }
                }
            },
            content = { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding())
                        .consumeWindowInsets(innerPadding)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // Не выходим из режима выбора сразу после завершения перетаскивания.
                            if (System.currentTimeMillis() - lastDragEndedAt < 250L) return@clickable
                            if (showSearch) {
                                showSearch = false
                                searchQueryState.clearText()
                            }
                            if (selectionMode) {
                                selectionMode = false
                                selectedCards = emptySet()
                            }
                        }
                ) {
                    if (!cardsFromDbReady) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(bottom = innerPadding.calculateBottomPadding())
                                .offset(y = (-64).dp)
                                .semantics { contentDescription = "Загружаем ваши карты из хранилища" }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Wallet,
                                contentDescription = null,
                                tint = colorScheme.onBackground.copy(alpha = 0.18f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Загружаем ваши карты из хранилища",
                                color = colorScheme.onBackground.copy(alpha = 0.7f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 30.sp,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (displayCards.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(bottom = innerPadding.calculateBottomPadding())
                                .offset(y = (-64).dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Wallet,
                                contentDescription = "Нет карт",
                                tint = colorScheme.onBackground.copy(alpha = 0.18f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = if (searchQueryState.text.isBlank()) {
                                    "Вы еще не добавили\nни одной карты"
                                } else {
                                    "Карты не найдены"
                                },
                                color = colorScheme.onBackground.copy(alpha = 0.7f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 30.sp,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    // Жест висит на стабильном контейнере: ячейка под пальцем
                                    // заменяется плейсхолдером, но узел жеста не пересоздаётся.
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { position ->
                                            if (!latestDragEnabled) return@detectDragGesturesAfterLongPress
                                            val hit = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                                val left = info.offset.x.toFloat()
                                                val top = info.offset.y.toFloat()
                                                position.x >= left && position.x < left + info.size.width &&
                                                    position.y >= top && position.y < top + info.size.height
                                            } ?: return@detectDragGesturesAfterLongPress
                                            val id = hit.key as? Long ?: return@detectDragGesturesAfterLongPress
                                            // Drag-and-Drop автоматически включает режим «Выбрать карты».
                                            selectionMode = true
                                            if (dragOrder == null) dragOrder = latestFilteredCards
                                            draggingId = id
                                            draggingIndex = hit.index
                                            dragItemSize = hit.size
                                            dragGrabOffset = position - Offset(hit.offset.x.toFloat(), hit.offset.y.toFloat())
                                            dragPointer = position
                                            dragStartPointer = position
                                            dragCenter = position - dragGrabOffset +
                                                Offset(hit.size.width / 2f, hit.size.height / 2f)
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, _ ->
                                            if (draggingId == null) return@detectDragGesturesAfterLongPress
                                            change.consume()
                                            dragPointer = change.position
                                            val current = dragOrder ?: return@detectDragGesturesAfterLongPress
                                            val center = dragPointer - dragGrabOffset +
                                                Offset(dragItemSize.width / 2f, dragItemSize.height / 2f)
                                            dragCenter = center
                                            val target = computeTargetIndex(
                                                visibleItems = gridState.layoutInfo.visibleItemsInfo,
                                                draggingKey = draggingId,
                                                center = center,
                                                itemSize = dragItemSize,
                                                columns = latestColumns,
                                                spacingPx = latestSpacingPx,
                                                listSize = current.size,
                                                fallback = draggingIndex
                                            )
                                            if (target != draggingIndex && draggingIndex in current.indices) {
                                                val mutable = current.toMutableList()
                                                val moved = mutable.removeAt(draggingIndex)
                                                mutable.add(target.coerceIn(0, mutable.size), moved)
                                                dragOrder = mutable
                                                draggingIndex = target
                                            }
                                        },
                                        onDragEnd = {
                                            if (draggingId == null) return@detectDragGesturesAfterLongPress
                                            val finished = dragOrder
                                            stopDragging()
                                            lastDragEndedAt = System.currentTimeMillis()
                                            if (finished != null && finished.map { it.id } != latestCards.map { it.id }) {
                                                onReorderCards(finished)
                                            } else {
                                                dragOrder = null
                                            }
                                        },
                                        onDragCancel = {
                                            if (draggingId == null) return@detectDragGesturesAfterLongPress
                                            stopDragging()
                                            lastDragEndedAt = System.currentTimeMillis()
                                            dragOrder = null
                                        }
                                    )
                                }
                        ) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridColumns.coerceIn(1, 4)),
                                state = gridState,
                                userScrollEnabled = draggingId == null,
                                contentPadding = PaddingValues(
                                    start = 8.dp,
                                    top = 8.dp,
                                    end = 8.dp,
                                    bottom = innerPadding.calculateBottomPadding() + 80.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                flingBehavior = ScrollableDefaults.flingBehavior(),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(displayCards, key = { it.id }) { card ->
                                    val isSelected = card.id in selectedCards
                                    val isDragging = card.id == draggingId
                                    // Клик/размер вешаем на обёртку, а сам GridCardItem получает
                                    // стабильный Modifier — тогда при перестановке карточки не
                                    // перерисовываются (и не перезагружают обложку из Coil).
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1.574f)
                                            .fillMaxWidth()
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    width = 3.dp,
                                                    color = colorScheme.primary,
                                                    shape = RoundedCornerShape(12.dp)
                                                ) else Modifier
                                            )
                                            .combinedClickable(
                                                onClick = {
                                                    // Отсекаем ложный клик после перетаскивания.
                                                    if (System.currentTimeMillis() - lastDragEndedAt < 250L) return@combinedClickable
                                                    if (selectionMode) {
                                                        selectedCards = if (isSelected) selectedCards - card.id else selectedCards + card.id
                                                        if (selectedCards.isEmpty()) selectionMode = false
                                                    } else {
                                                        dragOrder = null
                                                        onCardClick(card)
                                                    }
                                                }
                                            )
                                    ) {
                                        if (isDragging) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .border(
                                                        width = 1.dp,
                                                        color = colorScheme.primary.copy(alpha = 0.5f),
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                                    .background(
                                                        color = Color.White.copy(alpha = 0.06f),
                                                        shape = RoundedCornerShape(12.dp)
                                                    )
                                            )
                                        } else {
                                            GridCardItem(
                                                card = card,
                                                isSelected = isSelected,
                                                modifier = GridItemFillModifier
                                            )
                                        }
                                    }
                                }
                            }

                            val draggingCard = draggingId?.let { id -> displayCards.firstOrNull { it.id == id } }
                            if (draggingCard != null && dragItemSize.width > 0) {
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            val topLeft = dragPointer - dragGrabOffset
                                            IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt())
                                        }
                                        .size(
                                            width = with(density) { dragItemSize.width.toDp() },
                                            height = with(density) { dragItemSize.height.toDp() }
                                        )
                                        .graphicsLayer {
                                            // Читаем состояние только в layer-фазе, чтобы движение пальца
                                            // не вызывало рекомпозицию всего экрана.
                                            val width = dragItemSize.width.coerceAtLeast(1)
                                            rotationZ = ((dragPointer.x - dragStartPointer.x) / width * 8f).coerceIn(-8f, 8f)
                                            scaleX = 1.06f
                                            scaleY = 1.06f
                                            shadowElevation = 24.dp.toPx()
                                            alpha = 0.98f
                                        }
                                ) {
                                    GridCardItem(
                                        card = draggingCard,
                                        isSelected = false,
                                        modifier = GridItemFillModifier
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    SeaCardTheme {
        MainScreen(cards = emptyList(), currentSortType = SortType.ADD_TIME, gridColumns = 2, gradientColor = Color(0xFF000000), onAddCard = {}, onCardClick = {}, onSettingsClick = {}, onSortTypeChange = {}, onReorderCards = {}, onDeleteCards = {})
    }
}