package ru.merrcurys.seacard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize

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

// Градиенты
enum class GradientColorOption(val color: Color) {
    BLUE(BerlinAzure),
    GREEN(GreenGradient),
    PURPLE(PurpleGradient),
    RED(RedGradient),
    ORANGE(OrangeGradient)
}

@Composable
fun SeaCardTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun GradientBackground(
    gradientColor: Color = BerlinAzure,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val gradient = Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to gradientColor,
                0.6f to BlackBackground,
                1.0f to BlackBackground
            )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(gradient)
        ) {}
        content()
    }
}

@Composable
fun DynamicGradientBackground(
    colors: List<Color>,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val gradient = if (colors.size > 1) {
            Brush.verticalGradient(colors)
        } else {
            null
        }
        if (gradient != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(gradient)
            ) {}
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(colors.firstOrNull() ?: Color.White)
            ) {}
        }
        content()
    }
}