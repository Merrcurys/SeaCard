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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import com.example.seacard.ui.theme.GradientBackground

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
                GradientBackground(darkTheme = isDark) {
                    SettingsScreen(
                        isDarkTheme = isDark,
                        onThemeChange = { dark ->
                            isDark = dark
                            saveThemePref(context, dark)
                        },
                        onBack = { finish() },
                        topBarContainerColor = Color.Transparent
                    )
                }
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
fun SettingsScreen(isDarkTheme: Boolean, onThemeChange: (Boolean) -> Unit, onBack: () -> Unit, topBarContainerColor: Color = Color.Transparent) {
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
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
            Spacer(modifier = Modifier.height(32.dp))
            // Тумблер смены темы
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
                    val switchWidth = 64.dp
                    val switchHeight = 36.dp
                    val thumbSize = 32.dp
                    val padding = 2.dp
                    val thumbOffset by animateDpAsState(
                        targetValue = if (isDarkTheme) switchWidth - thumbSize - padding else padding,
                        animationSpec = tween(durationMillis = 300), label = "thumbOffset"
                    )
                    val trackColor by animateColorAsState(
                        targetValue = colorScheme.surfaceVariant,
                        animationSpec = tween(durationMillis = 300), label = "trackColor"
                    )
                    val iconColor = if (isDarkTheme) Color.White else Color(0xFF222222)
                    Box(
                        modifier = Modifier
                            .width(switchWidth)
                            .height(switchHeight)
                            .background(trackColor, shape = RoundedCornerShape(18.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onThemeChange(!isDarkTheme) },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        // Солнце
                        Icon(
                            imageVector = Icons.Filled.LightMode,
                            contentDescription = "Светлая тема",
                            tint = iconColor,
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.CenterStart)
                                .padding(start = 6.dp)
                        )
                        // Луна
                        Icon(
                            imageVector = Icons.Filled.DarkMode,
                            contentDescription = "Темная тема",
                            tint = iconColor,
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.CenterEnd)
                                .padding(end = 6.dp)
                        )
                        // Кружок
                        Box(
                            modifier = Modifier
                                .offset(x = thumbOffset)
                                .size(thumbSize)
                                .background(
                                    if (isDarkTheme) colorScheme.primary else colorScheme.onPrimary,
                                    shape = CircleShape
                                )
                                .border(1.dp, colorScheme.onSurface.copy(alpha = 0.1f), CircleShape)
                        )
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
            onBack = {},
            topBarContainerColor = Color.Transparent
        )
    }
} 