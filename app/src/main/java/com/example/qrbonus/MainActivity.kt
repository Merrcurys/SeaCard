package com.example.qrbonus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.qrbonus.ui.theme.QRBonusTheme
import com.example.qrbonus.ui.theme.BlackBackground

class MainActivity : ComponentActivity() {
    private val scanCardLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            recreate()
        }
    }
    
    private val cardDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val context = this
            var isDark by remember { mutableStateOf(loadThemePref(context)) }
            var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
            
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                isDark = loadThemePref(context)
            }
            
            // Функция загрузки карт
            fun loadCards() {
                val prefs = getSharedPreferences("cards", Context.MODE_PRIVATE)
                val cardSet = prefs.getStringSet("card_list", setOf()) ?: setOf()
                cards = cardSet.mapNotNull { cardString ->
                    val parts = cardString.split("|")
                    when (parts.size) {
                        2 -> Card(parts[0], parts[1], "barcode") // Старый формат
                        3 -> Card(parts[0], parts[1], parts[2]) // Новый формат с типом кода
                        else -> null
                    }
                }
            }
            
            LaunchedEffect(isDark) {
                val window = this@MainActivity.window
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDark
            }
            
            LaunchedEffect(Unit) {
                isDark = loadThemePref(context)
                loadCards()
            }
            
            QRBonusTheme(darkTheme = isDark) {
                MainScreen(
                    cards = cards,
                    onAddCard = {
                        val intent = Intent(this@MainActivity, ScanCardActivity::class.java)
                        scanCardLauncher.launch(intent)
                    },
                    onCardClick = { card ->
                        val intent = Intent(this@MainActivity, CardDetailActivity::class.java).apply {
                            putExtra("card_name", card.name)
                            putExtra("card_code", card.code)
                            putExtra("code_type", card.type)
                        }
                        cardDetailLauncher.launch(intent)
                    },
                    onSettingsClick = {
                        launcher.launch(Intent(context, SettingsActivity::class.java))
                    }
                )
            }
        }
    }
    
    private fun loadThemePref(context: Context): Boolean {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("dark_theme", true)
    }
}

data class Card(val name: String, val code: String, val type: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    cards: List<Card>,
    onAddCard: () -> Unit,
    onCardClick: (Card) -> Unit,
    onSettingsClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background == BlackBackground
    val topBarColor = if (isDark) BlackBackground else Color(0xFFF5F5F5)
    
    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Карты", color = colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                },
                navigationIcon = {},
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Поиск", tint = colorScheme.onSurface)
                    }
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.FilterAlt, contentDescription = "Фильтр", tint = colorScheme.onSurface)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки", tint = colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor)
            )
        },
        floatingActionButton = {
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
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
                    .padding(innerPadding)
            ) {
                if (cards.isEmpty()) {
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
                            text = "Вы еще не добавили\nни одной карты",
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
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(cards) { card ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = colorScheme.secondary,
                                    contentColor = colorScheme.onSecondary
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier
                                    .height(120.dp)
                                    .fillMaxWidth()
                                    .clickable { onCardClick(card) }
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(card.name, color = colorScheme.onSecondary, fontWeight = FontWeight.Bold)
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
    QRBonusTheme {
        MainScreen(cards = emptyList(), onAddCard = {}, onCardClick = {}, onSettingsClick = {})
    }
}