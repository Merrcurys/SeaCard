package com.example.qrbonus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qrbonus.ui.theme.QRBonusTheme
import com.example.qrbonus.ui.theme.CardWhite
import com.example.qrbonus.ui.theme.CardTextBlack
import com.example.qrbonus.ui.theme.FabColor
import com.example.qrbonus.ui.theme.FabTextColor
import com.example.qrbonus.ui.theme.MainTextWhite
import com.example.qrbonus.ui.theme.BlackBackground
import androidx.compose.foundation.shape.RoundedCornerShape

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QRBonusTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val cards = remember { mutableStateListOf<String>() } // MVP: просто список строк
    Scaffold(
        containerColor = BlackBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text("Карты", color = MainTextWhite, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                },
                navigationIcon = {},
                actions = {
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Поиск", tint = MainTextWhite)
                    }
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.FilterAlt, contentDescription = "Фильтр", tint = MainTextWhite)
                    }
                    IconButton(onClick = { /* TODO */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = MainTextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BlackBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO: добавить карту */ },
                containerColor = FabColor,
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_input_add),
                        contentDescription = "Добавить карту",
                        tint = FabTextColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Добавить карту", color = FabTextColor)
                }
            }
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BlackBackground)
                    .padding(innerPadding)
            ) {
                if (cards.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.offset(y = (-64).dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Wallet,
                                contentDescription = "Нет карт",
                                tint = MainTextWhite.copy(alpha = 0.18f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Вы еще не добавили\nни одной карты",
                                color = MainTextWhite.copy(alpha = 0.7f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 30.sp,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
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
                                    containerColor = CardWhite,
                                    contentColor = CardTextBlack
                                ),
                                modifier = Modifier
                                    .height(120.dp)
                                    .fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(card, color = CardTextBlack, fontWeight = FontWeight.Bold)
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
        MainScreen()
    }
}