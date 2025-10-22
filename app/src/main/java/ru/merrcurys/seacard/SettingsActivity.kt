package ru.merrcurys.seacard

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.merrcurys.seacard.ui.theme.SeaCardTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.composed
import androidx.core.view.WindowCompat
import androidx.core.content.edit
import androidx.compose.ui.tooling.preview.Preview
import android.content.Intent
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import ru.merrcurys.seacard.ui.theme.GradientBackground
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import android.widget.Toast
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import java.io.OutputStreamWriter
import java.io.InputStreamReader
import java.io.BufferedReader
import androidx.compose.foundation.layout.navigationBarsPadding
import ru.merrcurys.seacard.ui.theme.BerlinAzure
import ru.merrcurys.seacard.ui.theme.GradientColorOption
import ru.merrcurys.seacard.ui.theme.rememberGradientState

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var exportCards: (() -> Unit)? = null
        var importCards: (() -> Unit)? = null
        val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
            if (uri != null) {
                try {
                    val prefs = getSharedPreferences("cards", Context.MODE_PRIVATE)
                    val cardSet = prefs.getStringSet("card_list", setOf()) ?: setOf()
                    contentResolver.openOutputStream(uri)?.use { out ->
                        OutputStreamWriter(out).use { writer ->
                            cardSet.forEach { writer.write(it + "\n") }
                        }
                    }
                    Toast.makeText(this, "Карточки экспортированы", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка экспорта: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
        val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                val prefs = getSharedPreferences("cards", Context.MODE_PRIVATE)
                val cardSet = prefs.getStringSet("card_list", setOf())?.toMutableSet() ?: mutableSetOf()
                val assetList = assets.list("cards")?.toSet() ?: emptySet()
                val importedNames = mutableListOf<String>()
                val errors = mutableListOf<String>()
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        BufferedReader(InputStreamReader(input)).useLines { lines ->
                            // Собираем уже существующие карточки по ключу (name, code, type)
                            val existingKeys = cardSet.map { s ->
                                val p = s.split("|")
                                Triple(p.getOrNull(0), p.getOrNull(1), p.getOrNull(2))
                            }.toMutableSet()
                            lines.forEach { line ->
                                val parts = line.split("|")
                                if (parts.size >= 2) {
                                    val name = parts[0]
                                    val code = parts[1]
                                    val type = parts.getOrNull(2) ?: "barcode"
                                    val cardKey = Triple(name, code, type)
                                    if (existingKeys.contains(cardKey)) {
                                        // Уже есть такая карта — пропускаем
                                        return@forEach
                                    }
                                    val coverAsset = if (parts.size >= 7) parts[6] else null
                                    val fixedCover = if (coverAsset != null && coverAsset.startsWith("cards/") && assetList.contains(coverAsset.removePrefix("cards/"))) {
                                        coverAsset
                                    } else {
                                        val found = CoverNames.coverNameMap.entries.find { it.value.equals(name, ignoreCase = true) }?.key
                                        if (found != null) "cards/$found" else null
                                    }
                                    val newLine = if (fixedCover != null) {
                                        parts.take(6).joinToString("|") + "|" + fixedCover
                                    } else if (parts.size >= 7) {
                                        parts.take(6).joinToString("|")
                                    } else {
                                        line
                                    }
                                    try {
                                        cardSet.add(newLine)
                                        existingKeys.add(cardKey)
                                        importedNames.add(name)
                                    } catch (e: Exception) {
                                        errors.add(name)
                                    }
                                } else {
                                    errors.add(line)
                                }
                            }
                        }
                    }
                    prefs.edit { putStringSet("card_list", cardSet) }
                    if (errors.isEmpty()) {
                        Toast.makeText(this, "Все карты были скопированы", Toast.LENGTH_LONG).show()
                    } else {
                        errors.forEach { name ->
                            Toast.makeText(this, "Ошибка при копировании карты $name", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Ошибка импорта: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
        exportCards = { exportLauncher.launch("seacard_cards.txt") }
        importCards = { importLauncher.launch("text/plain") }
        setContent {
            val context = this
            // Always use dark theme
            val gradientState = rememberGradientState(context)
            LaunchedEffect(Unit) {
                val window = this@SettingsActivity.window
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
            }
            SeaCardTheme {
                GradientBackground(gradientColor = gradientState.gradientColor) {
                    SettingsScreen(
                        gradientColor = gradientState.gradientColor,
                        onGradientColorChange = { color ->
                            gradientState.updateGradientColor(color)
                            saveGradientColorPref(context, color)
                        },
                        onBack = { finish() },
                        topBarContainerColor = Color.Transparent,
                        onExport = { exportCards?.invoke() },
                        onImport = { importCards?.invoke() }
                    )
                }
            }
        }
    }

    private fun saveGradientColorPref(context: Context, color: Color) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit { putInt("gradient_color", color.hashCode()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    gradientColor: Color,
    onGradientColorChange: (Color) -> Unit,
    onBack: () -> Unit,
    topBarContainerColor: Color = Color.Transparent,
    onExport: () -> Unit = {},
    onImport: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    val appVersion = BuildConfig.VERSION_NAME

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopAppBar(
                title = { Text("Настройки", color = colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            // Выбор градиентного цвета
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = colorScheme.onPrimary,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Градиентный цвет",
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Цветовые варианты
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        GradientColorOption.values().forEach { option ->
                            val isSelected = gradientColor == option.color
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = option.color,
                                        shape = CircleShape
                                    )
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { onGradientColorChange(option.color) }
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            // Кнопка Telegram
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://t.me/SeacardSupportBot".toUri())
                    context.startActivity(intent)
                },
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = colorScheme.onPrimary,
                    contentColor = colorScheme.onSurface
                ),
                elevation = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text("Связаться с разработчиком", fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(24.dp))
            // Кнопки экспорта и импорта карточек на одной линии
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onExport,
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = colorScheme.onPrimary,
                        contentColor = colorScheme.onSurface
                    ),
                    elevation = null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = null,
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Экспорт", fontWeight = FontWeight.Medium)
                }
                Button(
                    onClick = onImport,
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = colorScheme.onPrimary,
                        contentColor = colorScheme.onSurface
                    ),
                    elevation = null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Импорт", fontWeight = FontWeight.Medium)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            // Кнопка удалить все карточки
            Button(
                onClick = { showDeleteDialog = true },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Удалить все карточки", color = Color.White, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.weight(1f))
            // Версия приложения
            Text(
                text = "Версия приложения: $appVersion",
                color = colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
                    .navigationBarsPadding()
            )
        }
        // Диалог подтверждения удаления
        if (showDeleteDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Удалить все карточки?") },
                text = { Text("Вы уверены что хотите удалить все карточки?") },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            // Удаляем все карточки
                            val prefs = context.getSharedPreferences("cards", Context.MODE_PRIVATE)
                            prefs.edit { remove("card_list") }
                            showDeleteDialog = false
                            onBack()
                        }
                    ) {
                        Text("Удалить", color = Color.Red)
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Отмена")
                    }
                },
                containerColor = colorScheme.surface,
                titleContentColor = colorScheme.onSurface,
                textContentColor = colorScheme.onSurface
            )
        }
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
            onBack = {},
            topBarContainerColor = Color.Transparent
        )
    }
}