package com.example.seacard

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.seacard.ui.theme.SeaCardTheme
import com.example.seacard.ui.theme.BlackBackground
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.ArrowBack
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

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = this
            var isDark by remember { mutableStateOf(loadThemePref(context)) }
            LaunchedEffect(isDark) {
                val window = this@SettingsActivity.window
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDark
            }
            LaunchedEffect(Unit) {
                isDark = loadThemePref(context)
            }
            SeaCardTheme(darkTheme = isDark) {
                SettingsScreen(
                    isDarkTheme = isDark,
                    onThemeChange = { dark ->
                        isDark = dark
                        saveThemePref(context, dark)
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun loadThemePref(context: Context): Boolean {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return prefs.getBoolean("dark_theme", true)
    }

    private fun saveThemePref(context: Context, dark: Boolean) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("dark_theme", dark) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(isDarkTheme: Boolean, onThemeChange: (Boolean) -> Unit, onBack: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val topBarColor = if (isDarkTheme) BlackBackground else Color(0xFFF5F5F5)
    var showDeleteDialog by remember { mutableStateOf(false) }
    val appVersion = remember { "1.0.0" } // TODO: BuildConfig.VERSION_NAME

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopAppBar(
                title = { Text("Настройки", color = colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor)
            )
            Spacer(modifier = Modifier.height(32.dp))
            // Кастомный тумблер темы
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = if (isDarkTheme) colorScheme.onPrimary else colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Тема приложения",
                        color = if (isDarkTheme) colorScheme.onSurface else colorScheme.onPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp
                    )
                    // Кастомный тумблер
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .width(56.dp)
                            .background(
                                if (isDarkTheme) colorScheme.primary.copy(alpha = 0.7f) else colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onThemeChange(!isDarkTheme) },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        androidx.compose.animation.AnimatedContent(targetState = isDarkTheme, label = "") { checked ->
                            Box(
                                modifier = Modifier
                                    .padding(start = if (checked) 24.dp else 4.dp, end = if (checked) 4.dp else 24.dp)
                                    .size(24.dp)
                                    .background(
                                        if (checked) colorScheme.primary else colorScheme.onPrimary,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
            // Кнопка Telegram
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, "https://t.me/merrcurys".toUri())
                    context.startActivity(intent)
                },
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = if (isDarkTheme) colorScheme.onPrimary else colorScheme.primary,
                    contentColor = if (isDarkTheme) colorScheme.onSurface else colorScheme.onPrimary
                ),
                elevation = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                Text("Связаться с разработчиком", fontWeight = FontWeight.Medium)
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
    SeaCardTheme(darkTheme = true) {
        SettingsScreen(
            isDarkTheme = true,
            onThemeChange = {},
            onBack = {})
    }
} 