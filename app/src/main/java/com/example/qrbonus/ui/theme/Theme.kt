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
    primary = FabColor,
    onPrimary = FabTextColor,
    secondary = CardWhite,
    onSecondary = CardTextBlack,
    background = BlackBackground,
    onBackground = MainTextWhite,
    surface = CardWhite,
    onSurface = CardTextBlack,
)

private val LightColorScheme = lightColorScheme(
    primary = FabColor,
    onPrimary = FabTextColor,
    secondary = CardWhite,
    onSecondary = CardTextBlack,
    background = BlackBackground,
    onBackground = MainTextWhite,
    surface = CardWhite,
    onSurface = CardTextBlack,
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