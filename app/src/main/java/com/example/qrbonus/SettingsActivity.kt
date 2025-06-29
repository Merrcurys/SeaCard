package com.example.qrbonus

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.WbSunny
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
import com.example.qrbonus.ui.theme.QRBonusTheme
import com.example.qrbonus.ui.theme.BlackBackground
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
            QRBonusTheme(darkTheme = isDark) {
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
    val sunButtonColor = if (isDarkTheme) colorScheme.secondary else Color(0xFFFDFDFD)
    val moonButtonColor = Color(0xFF232323)
    val moonIconColor = Color.White
    val sunIconColor = colorScheme.onSecondary
    val topBarColor = if (isDarkTheme) BlackBackground else Color(0xFFF5F5F5)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column {
            TopAppBar(
                title = { Text("Настройки", color = colorScheme.onSurface, fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = topBarColor)
            )
            Spacer(modifier = Modifier.height(48.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = sunButtonColor,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(100.dp)
                        .noRippleClickable { onThemeChange(false) }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Filled.WbSunny,
                            contentDescription = "Светлая тема",
                            tint = sunIconColor,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = moonButtonColor,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .size(100.dp)
                        .noRippleClickable { onThemeChange(true) }
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Filled.Brightness2,
                            contentDescription = "Тёмная тема",
                            tint = moonIconColor,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }
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
    QRBonusTheme(darkTheme = true) {
        SettingsScreen(
            isDarkTheme = true,
            onThemeChange = {},
            onBack = {})
    }
} 