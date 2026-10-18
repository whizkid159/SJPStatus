package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SindhEmeraldLight,
    onPrimary = OnSindhMintContainer,
    primaryContainer = SindhEmeraldDark,
    onPrimaryContainer = SindhMintContainer,
    secondary = WarmGoldLight,
    onSecondary = OnWarmGoldContainer,
    secondaryContainer = Color(0xFF78350F),
    onSecondaryContainer = WarmGoldContainer,
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF082F49),
    background = DarkBackground,
    onBackground = Color(0xFFF1F5F9),
    surface = DarkSurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

private val LightColorScheme = lightColorScheme(
    primary = SindhEmerald,
    onPrimary = Color.White,
    primaryContainer = SindhMintContainer,
    onPrimaryContainer = OnSindhMintContainer,
    secondary = WarmGoldAccent,
    onSecondary = Color.White,
    secondaryContainer = WarmGoldContainer,
    onSecondaryContainer = OnWarmGoldContainer,
    tertiary = SlateNavy,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = SlateNavy,
    surface = LightSurface,
    onSurface = SlateNavy,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
