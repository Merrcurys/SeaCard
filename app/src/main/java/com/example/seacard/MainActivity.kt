package com.example.seacard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.seacard.ui.theme.SeaCardTheme
import com.example.seacard.ui.theme.BlackBackground
import androidx.compose.foundation.interaction.MutableInteractionSource
import java.util.*
import androidx.core.content.edit
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import java.io.IOException
import androidx.compose.ui.draw.clip
import com.example.seacard.CardCoverPickerScreen

enum class SortType(val displayName: String) {
    ADD_TIME("По времени добавления"),
    NAME_ASC("По названию (А-Я)"),
    NAME_DESC("По названию (Я-А)"),
    USAGE_FREQ("По частоте использования")
}

data class Card(
    val name: String, 
    val code: String, 
    val type: String,
    val addTime: Long = System.currentTimeMillis(),
    val usageCount: Int = 0,
    val color: Int = 0xFFFFFFFF.toInt(), // Белый цвет по умолчанию
    val coverAsset: String? = null // Путь к обложке, если есть
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val context = this
            var isDark by remember { mutableStateOf(loadThemePref(context)) }
            var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
            var currentSortType by remember { mutableStateOf(loadSortTypePref(context)) }
            var showCoverPicker by remember { mutableStateOf(false) }
            var pendingCoverAsset by remember { mutableStateOf<String?>(null) }

            // Функция загрузки карт
            fun loadCards() {
                val prefs = getSharedPreferences("cards", Context.MODE_PRIVATE)
                val cardSet = prefs.getStringSet("card_list", setOf()) ?: setOf()
                cards = cardSet.mapNotNull { cardString ->
                    val parts = cardString.split("|")
                    when (parts.size) {
                        2 -> Card(parts[0], parts[1], "barcode") // Старый формат
                        3 -> Card(parts[0], parts[1], parts[2]) // Новый формат с типом кода
                        5 -> Card(parts[0], parts[1], parts[2], parts[3].toLongOrNull() ?: System.currentTimeMillis(), parts[4].toIntOrNull() ?: 0) // Формат с временем и частотой
                        6 -> Card(parts[0], parts[1], parts[2], parts[3].toLongOrNull() ?: System.currentTimeMillis(), parts[4].toIntOrNull() ?: 0, parts[5].toIntOrNull() ?: 0xFFFFFFFF.toInt()) // Формат с цветом
                        7 -> Card(parts[0], parts[1], parts[2], parts[3].toLongOrNull() ?: System.currentTimeMillis(), parts[4].toIntOrNull() ?: 0, parts[5].toIntOrNull() ?: 0xFFFFFFFF.toInt(), parts[6].takeIf { it.isNotBlank() }) // Формат с coverAsset
                        else -> null
                    }
                }.sortedWith(getSortComparator(currentSortType))
            }

            val scanCardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    loadCards()
                }
            }

            val cardDetailLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    loadCards()
                }
            }

            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                isDark = loadThemePref(context)
                loadCards()
            }
            
            // Функция обновления частоты использования карты
            fun updateCardUsage(cardName: String) {
                val prefs = getSharedPreferences("cards", Context.MODE_PRIVATE)
                val cardSet = prefs.getStringSet("card_list", setOf())?.toMutableSet() ?: mutableSetOf()
                
                val updatedCardSet = cardSet.map { cardString ->
                    val parts = cardString.split("|")
                    if (parts[0] == cardName) {
                        when (parts.size) {
                            2 -> "${parts[0]}|${parts[1]}|barcode|${System.currentTimeMillis()}|1|${0xFFFFFFFF.toInt()}"
                            3 -> "${parts[0]}|${parts[1]}|${parts[2]}|${System.currentTimeMillis()}|1|${0xFFFFFFFF.toInt()}"
                            5 -> "${parts[0]}|${parts[1]}|${parts[2]}|${parts[3]}|${(parts[4].toIntOrNull() ?: 0) + 1}|${0xFFFFFFFF.toInt()}"
                            6 -> "${parts[0]}|${parts[1]}|${parts[2]}|${parts[3]}|${(parts[4].toIntOrNull() ?: 0) + 1}|${parts[5]}"
                            else -> cardString
                        }
                    } else {
                        cardString
                    }
                }.toSet()
                
                prefs.edit { putStringSet("card_list", updatedCardSet) }
                loadCards()
            }
            
            LaunchedEffect(isDark) {
                val window = this@MainActivity.window
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDark
            }
            
            LaunchedEffect(Unit) {
                isDark = loadThemePref(context)
                loadCards()
            }
            
            SeaCardTheme(darkTheme = isDark) {
                if (showCoverPicker) {
                    CardCoverPickerScreen(
                        onCoverSelected = { coverAsset: String? ->
                            showCoverPicker = false
                            pendingCoverAsset = coverAsset
                            // Запустить сканер, передав coverAsset
                            val intent = Intent(this@MainActivity, ScanCardActivity::class.java)
                            if (coverAsset != null) intent.putExtra("cover_asset", coverAsset as String)
                            scanCardLauncher.launch(intent)
                        },
                        onBack = {
                            showCoverPicker = false
                        }
                    )
                } else {
                    MainScreen(
                        cards = cards,
                        currentSortType = currentSortType,
                        onAddCard = {
                            showCoverPicker = true
                        },
                        onCardClick = { card ->
                            updateCardUsage(card.name)
                            val intent = Intent(this@MainActivity, CardDetailActivity::class.java).apply {
                                putExtra("card_name", card.name)
                                putExtra("card_code", card.code)
                                putExtra("code_type", card.type)
                                putExtra("card_color", card.color)
                                putExtra("cover_asset", card.coverAsset)
                            }
                            cardDetailLauncher.launch(intent)
                        },
                        onSettingsClick = {
                            launcher.launch(Intent(context, SettingsActivity::class.java))
                        },
                        onSortTypeChange = { newSortType ->
                            currentSortType = newSortType
                            saveSortTypePref(context, newSortType)
                            loadCards()
                        },
                        onDeleteCards = { cardsToDelete ->
                            val prefs = getSharedPreferences("cards", Context.MODE_PRIVATE)
                            val cardSet = prefs.getStringSet("card_list", setOf())?.toMutableSet() ?: mutableSetOf()
                            val updatedCardSet = cardSet.filterNot { cardString ->
                                val parts = cardString.split("|")
                                cardsToDelete.any { card ->
                                    parts[0] == card.name
                                }
                            }.toSet()
                            prefs.edit { putStringSet("card_list", updatedCardSet) }
                            loadCards()
                        }
                    )
                }
            }
        }
    }
    
    private fun loadThemePref(context: Context): Boolean {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("dark_theme", true)
    }
    
    private fun loadSortTypePref(context: Context): SortType {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val sortTypeName = prefs.getString("sort_type", SortType.ADD_TIME.name)
        return try {
            SortType.valueOf(sortTypeName ?: SortType.ADD_TIME.name)
        } catch (e: IllegalArgumentException) {
            SortType.ADD_TIME
        }
    }
    
    private fun saveSortTypePref(context: Context, sortType: SortType) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit { putString("sort_type", sortType.name) }
    }
    
    private fun getSortComparator(sortType: SortType): Comparator<Card> {
        return when (sortType) {
            SortType.ADD_TIME -> compareByDescending { it.addTime }
            SortType.NAME_ASC -> compareBy { it.name.lowercase() }
            SortType.NAME_DESC -> compareByDescending { it.name.lowercase() }
            SortType.USAGE_FREQ -> compareByDescending { it.usageCount }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    cards: List<Card>,
    currentSortType: SortType,
    onAddCard: () -> Unit,
    onCardClick: (Card) -> Unit,
    onSettingsClick: () -> Unit,
    onSortTypeChange: (SortType) -> Unit,
    onDeleteCards: (List<Card>) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background == BlackBackground
    val topBarColor = if (isDark) BlackBackground else Color(0xFFF5F5F5)
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var selectedCards by remember { mutableStateOf<Set<Card>>(emptySet()) }
    var selectionMode by remember { mutableStateOf(false) }
    
    // Функция для определения темного цвета
    fun isColorDark(color: Int): Boolean {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        val brightness = (red * 299 + green * 587 + blue * 114) / 1000
        return brightness < 128
    }
    
    val filteredCards = remember(cards, searchQuery) {
        if (searchQuery.isBlank()) {
            cards
        } else {
            cards.filter { card ->
                card.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    // Обработка системной кнопки "назад" для сброса выбора
    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedCards = emptySet()
    }
    
    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    if (selectionMode) {
                        Text("Выбрано: ${selectedCards.size}", color = colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    } else if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск карт...") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colorScheme.onSurface,
                                unfocusedTextColor = colorScheme.onSurface,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Очистить", tint = colorScheme.onSurface.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        )
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
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = colorScheme.onSurface)
                        }
                    } else {
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Default.Search, contentDescription = "Поиск", tint = colorScheme.onSurface)
                        }
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(Icons.Default.FilterAlt, contentDescription = "Фильтр", tint = colorScheme.onSurface)
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
                            Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = colorScheme.onSurface)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor)
            )
        },
        floatingActionButton = {
            if (!selectionMode) {
                FloatingActionButton(
                    onClick = onAddCard,
                    containerColor = colorScheme.primary,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Добавить карту",
                            tint = colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Добавить карту", color = colorScheme.onPrimary)
                    }
                }
            }
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
                    .padding(innerPadding)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { 
                        if (showSearch) {
                            showSearch = false
                            searchQuery = ""
                        }
                        if (selectionMode) {
                            selectionMode = false
                            selectedCards = emptySet()
                        }
                    }
            ) {
                if (filteredCards.isEmpty()) {
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
                            text = if (searchQuery.isBlank()) {
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
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredCards) { card ->
                            val isSelected = selectedCards.contains(card)
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (card.coverAsset != null) Color.Transparent else Color(card.color),
                                    contentColor = if (card.coverAsset != null) Color.Unspecified else if (isColorDark(card.color)) Color.White else Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(100.dp)
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
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Затемнение поверх цвета карточки, если выбрана
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .background(Color.Black.copy(alpha = 0.12f), shape = RoundedCornerShape(12.dp))
                                        )
                                    }
                                    if (card.coverAsset != null) {
                                        val context = LocalContext.current
                                        val assetManager = context.assets
                                        val imageBitmap: ImageBitmap? = remember(card.coverAsset) {
                                            try {
                                                val input = assetManager.open(card.coverAsset)
                                                val bmp = android.graphics.BitmapFactory.decodeStream(input)
                                                input.close()
                                                bmp?.asImageBitmap()
                                            } catch (e: Exception) { null }
                                        }
                                        if (imageBitmap != null) {
                                            Image(
                                                bitmap = imageBitmap,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = card.name,
                                            color = if (isColorDark(card.color)) Color.White else Color.Black,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
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
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    SeaCardTheme {
        MainScreen(cards = emptyList(), currentSortType = SortType.ADD_TIME, onAddCard = {}, onCardClick = {}, onSettingsClick = {}, onSortTypeChange = {}, onDeleteCards = {})
    }
}