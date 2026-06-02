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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import kotlinx.coroutines.flow.first
import ru.merrcurys.seacard.core.design.GradientBackground
import ru.merrcurys.seacard.core.design.SeaCardTheme
import ru.merrcurys.seacard.core.design.applySeaCardSystemBarColors
import ru.merrcurys.seacard.core.rustore.RuStoreInAppUpdateController
import ru.merrcurys.seacard.core.rustore.RuStoreReviewHelper
import ru.merrcurys.seacard.core.utils.SortType
import ru.merrcurys.seacard.features.detail.CardDetailActivity
import ru.merrcurys.seacard.features.scan.ScanCardActivity
import ru.merrcurys.seacard.features.settings.SettingsActivity
import java.io.File
import ru.merrcurys.seacard.domain.entity.Card as CardModel

private const val RU_STORE_REVIEW_LOG_TAG = "RuStoreReview"

private fun mainGridCoverModel(frontPath: String): Any =
    if (frontPath.startsWith("cards/")) "file:///android_asset/$frontPath"
    else File(frontPath)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySeaCardSystemBarColors()

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

            DisposableEffect(Unit) {
                val ruStoreUpdate = RuStoreInAppUpdateController(this@MainActivity)
                ruStoreUpdate.checkOnLaunch()
                onDispose { ruStoreUpdate.dispose() }
            }

            LaunchedEffect(Unit) {
                Log.i(RU_STORE_REVIEW_LOG_TAG, "Ожидание: главный экран (!picker) и карт >= 5, сейчас карт=${cards.size}")
                snapshotFlow { !showCoverPicker && cards.size >= 5 }
                    .first { it }
                Log.i(RU_STORE_REVIEW_LOG_TAG, "Условие выполнено, вызываем RuStoreReviewHelper")
                RuStoreReviewHelper.tryLaunchReview(this@MainActivity)
            }

            SeaCardTheme {
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
                            viewModel.updateCardUsage(card.id)
                            cardDetailLauncher.launch(Intent(context, CardDetailActivity::class.java).apply { putExtra("card_id", card.id) })
                        },
                        onSettingsClick = { settingsLauncher.launch(Intent(context, SettingsActivity::class.java)) },
                        onSortTypeChange = { viewModel.setSortType(it) },
                        onDeleteCards = { viewModel.deleteCards(it) }
                    )
                }
            }
        }
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
    onDeleteCards: (List<CardModel>) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    GradientBackground(gradientColor = gradientColor) {
        val searchQueryState = rememberSaveable(saver = TextFieldState.Saver) { TextFieldState() }
        var showSearch by rememberSaveable { mutableStateOf(false) }
        var showFilterMenu by rememberSaveable { mutableStateOf(false) }
        var selectionMode by rememberSaveable { mutableStateOf(false) }
        var selectedCards by retain { mutableStateOf<Set<CardModel>>(emptySet()) }
        val focusRequester = retain { FocusRequester() }

        fun isColorDark(color: Int): Boolean {
            val red = (color shr 16) and 0xFF
            val green = (color shr 8) and 0xFF
            val blue = color and 0xFF
            val brightness = (red * 299 + green * 587 + blue * 114) / 1000
            return brightness < 128
        }

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
                                        .padding(end = 16.dp)
                                        .focusRequester(focusRequester),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = colorScheme.onSurface,
                                        unfocusedTextColor = colorScheme.onSurface,
                                        focusedBorderColor = colorScheme.primary,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    placeholder = {
                                        Text("Поиск карт...")
                                    },
                                    shape = RoundedCornerShape(100.dp),
                                    lineLimits = TextFieldLineLimits.SingleLine,
                                    contentPadding = OutlinedTextFieldDefaults.contentPadding(
                                        start = 24.dp,
                                        end = 8.dp
                                    ),
                                    textStyle = TextStyle(
                                        fontSize = 20.sp,
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
                                onDeleteCards(selectedCards.toList())
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
                        .padding(innerPadding)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
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
                    } else if (filteredCards.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.align(Alignment.Center).offset(y = (-64).dp)
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
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridColumns.coerceIn(1, 4)),
                            contentPadding = PaddingValues(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            flingBehavior = ScrollableDefaults.flingBehavior(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredCards, key = { it.id }) { card ->
                                val isSelected = selectedCards.contains(card)
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (card.coverAsset != null) Color.Transparent else Color(card.color),
                                        contentColor = if (card.coverAsset != null) Color.Unspecified else if (isColorDark(card.color)) Color.White else Color.Black
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .aspectRatio(1.574f)
                                        .fillMaxWidth()
                                        .then(
                                            if (isSelected) Modifier
                                                .border(
                                                    width = 3.dp,
                                                    color = colorScheme.primary,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                            else Modifier
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                if (selectionMode) {
                                                    selectedCards = if (isSelected) selectedCards - card else selectedCards + card
                                                    if (selectedCards.isEmpty()) selectionMode = false
                                                } else {
                                                    onCardClick(card)
                                                }
                                            },
                                            onLongClick = {
                                                if (!selectionMode) {
                                                    selectionMode = true
                                                    selectedCards = setOf(card)
                                                }
                                            }
                                        )
                                ) {
                                    val currentContext = LocalContext.current
                                    val frontPath = card.frontCoverPath
                                    key(frontPath) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .matchParentSize()
                                                        .background(Color.Black.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp))
                                                )
                                            }
                                            if (frontPath != null) {
                                                SubcomposeAsyncImage(
                                                    model = ImageRequest.Builder(currentContext)
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
                                                            color = if (isColorDark(card.color)) Color.White else Color.Black,
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
                                                    color = if (isColorDark(card.color)) Color.White else Color.Black,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
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
        MainScreen(cards = emptyList(), currentSortType = SortType.ADD_TIME, gridColumns = 2, gradientColor = Color(0xFF000000), onAddCard = {}, onCardClick = {}, onSettingsClick = {}, onSortTypeChange = {}, onDeleteCards = {})
    }
}