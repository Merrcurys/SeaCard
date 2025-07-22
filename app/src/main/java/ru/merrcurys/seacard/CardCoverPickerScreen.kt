package ru.merrcurys.seacard

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.IOException
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.ui.layout.ContentScale
import ru.merrcurys.seacard.CoverNames.coverNameMap
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import ru.merrcurys.seacard.ui.theme.BlackBackground
import ru.merrcurys.seacard.ui.theme.GradientBackground
import kotlin.text.substringBeforeLast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardCoverPickerScreen(
    onCoverSelected: (String?) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val assetManager = context.assets
    var coverList by remember { mutableStateOf(listOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    var sortAsc by remember { mutableStateOf(true) }
    val isDark = MaterialTheme.colorScheme.background == BlackBackground
    GradientBackground(darkTheme = isDark) {
        BackHandler(onBack = onBack)
        LaunchedEffect(Unit) {
            try {
                coverList = assetManager.list("cards")?.filter { it.endsWith(".webp") } ?: emptyList()
            } catch (e: IOException) {
                coverList = emptyList()
            }
        }
        val filteredCovers = remember(coverList, searchQuery, sortAsc) {
            fun normalize(text: String): String {
                return text
                    .replace("'", "")
                    .replace("’", "")
                    .replace("`", "")
                    .replace("ё", "е", ignoreCase = true)
                    .replace("Ё", "Е", ignoreCase = true)
                    .lowercase()
            }
            val normQuery = normalize(searchQuery)
            coverList
                .filter { file ->
                    val name = coverNameMap[file] ?: file.substringBeforeLast('.')
                    normalize(name).contains(normQuery)
                }
                .sortedWith(
                    if (sortAsc)
                        compareBy(String.CASE_INSENSITIVE_ORDER) { coverNameMap[it] ?: it.substringBeforeLast('.') }
                    else
                        compareBy(String.CASE_INSENSITIVE_ORDER.reversed()) { coverNameMap[it] ?: it.substringBeforeLast('.') }
                )
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Выберите карту",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { sortAsc = !sortAsc }) {
                                Icon(
                                    imageVector = if (sortAsc) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                    contentDescription = if (sortAsc) "Сортировать А-Я" else "Сортировать Я-А",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Поиск по названию") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 0.dp)
                            .padding(bottom = 12.dp),
                        singleLine = true
                    )
                    if (filteredCovers.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .offset(y = (-48).dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Wallet,
                                contentDescription = "Нет карт",
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = "Карта не нашлась, попробуйте добавить вручную",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 30.sp,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().padding(bottom = 70.dp)) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(filteredCovers) { coverName ->
                                    val assetPath = "cards/$coverName"
                                    val imageBitmap: ImageBitmap? = try {
                                        val input = assetManager.open(assetPath)
                                        val bmp = BitmapFactory.decodeStream(input)
                                        input.close()
                                        bmp?.asImageBitmap()
                                    } catch (e: Exception) { null }
                                    val displayName = coverNameMap[coverName] ?: coverName.substringBeforeLast('.')
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .height(100.dp)
                                            .fillMaxWidth()
                                            .clickable { onCoverSelected(assetPath) },
                                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                                    ) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (imageBitmap != null) {
                                                Image(
                                                    bitmap = imageBitmap,
                                                    contentDescription = displayName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Text("Ошибка", color = Color.Red)
                                            }
                                            // Градиентная подложка и текст
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth()
                                                    .height(38.dp)
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colors = listOf(Color.Transparent, Color(0xCC000000)),
                                                            startY = 0f,
                                                            endY = 100f
                                                        ),
                                                        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                                                    )
                                            ) {
                                                Text(
                                                    text = displayName,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .align(Alignment.Center)
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                    textAlign = TextAlign.Center,
                                                    style = TextStyle(
                                                        shadow = Shadow(
                                                            color = Color.Black.copy(alpha = 0.7f),
                                                            offset = Offset(0f, 1.5f),
                                                            blurRadius = 3f
                                                        )
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = { onCoverSelected(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Text("Добавить вручную")
                }
            }
        }
    }
} 