package com.example.qrbonus.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import com.example.qrbonus.ui.theme.BlackBackground
import com.example.qrbonus.ui.theme.CardWhite
import com.example.qrbonus.ui.theme.CardTextBlack
import com.example.qrbonus.ui.theme.MainTextWhite
import com.example.qrbonus.ui.theme.FabColor
import com.example.qrbonus.ui.theme.FabTextColor

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF1C1C1C),
    secondary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF1C1C1C),
    background = Color(0xFF111111),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF232323),
    onSurface = Color(0xFFFFFFFF),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF444444),
    onPrimary = Color(0xFFF5F5F5),
    secondary = Color(0xFFE0E0E0),
    onSecondary = Color(0xFF232323),
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF232323),
    surface = Color(0xFFEAEAEA),
    onSurface = Color(0xFF232323),
)

@Composable
fun QRBonusTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}