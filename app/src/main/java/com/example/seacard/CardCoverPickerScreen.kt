package com.example.seacard

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
import androidx.compose.ui.draw.clip
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
import com.example.seacard.CoverNames.coverNameMap
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset

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
    LaunchedEffect(Unit) {
        try {
            coverList = assetManager.list("cards")?.filter { it.endsWith(".webp") } ?: emptyList()
        } catch (e: IOException) {
            coverList = emptyList()
        }
    }
    BackHandler(onBack = onBack)
    val filteredCovers = remember(coverList, searchQuery, sortAsc) {
        coverList
            .filter { file ->
                val name = coverNameMap[file] ?: file.substringBeforeLast('.')
                name.contains(searchQuery, ignoreCase = true)
            }
            .sortedWith(compareBy(if (sortAsc) String.CASE_INSENSITIVE_ORDER else String.CASE_INSENSITIVE_ORDER.reversed()) { coverNameMap[it] ?: it.substringBeforeLast('.') })
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Поиск по названию") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true
            )
            if (filteredCovers.isEmpty()) {
                // Show empty state with icon and message
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredCovers) { coverName ->
                        val assetPath = "cards/$coverName"
                        val imageBitmap: ImageBitmap? = try {
                            val input = assetManager.open(assetPath)
                            val bmp = android.graphics.BitmapFactory.decodeStream(input)
                            input.close()
                            bmp?.asImageBitmap()
                        } catch (e: Exception) { null }
                        val displayName = coverNameMap[coverName] ?: coverName.substringBeforeLast('.')
                        Box(
                            modifier = Modifier
                                .height(100.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.LightGray)
                                .clickable { onCoverSelected(assetPath) },
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
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onCoverSelected(null) }, modifier = Modifier.fillMaxWidth()) {
                Text("Добавить вручную")
            }
        }
    }
} 