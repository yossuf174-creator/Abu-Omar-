package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MessengerBlue,
    secondary = BubbleOtherDark,
    background = MessengerBgDark,
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    onSecondary = DarkText,
    onBackground = DarkText,
    onSurface = DarkText,
    secondaryContainer = Color(0xFF242526)
)

private val LightColorScheme = lightColorScheme(
    primary = MessengerBlue,
    secondary = BubbleOtherLight,
    background = MessengerBgLight,
    surface = Color(0xFFF0F2F5),
    onPrimary = Color.White,
    onSecondary = LightText,
    onBackground = LightText,
    onSurface = LightText,
    secondaryContainer = Color(0xFFE4E6EB)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
