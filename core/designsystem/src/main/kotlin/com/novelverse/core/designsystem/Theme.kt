package com.novelverse.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.novelverse.core.model.AppTheme

private val LightColors = lightColorScheme(
    primary = Color(0xFF416653), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E8D9), onPrimaryContainer = Color(0xFF143727),
    background = Color(0xFFFAF8F3), surface = Color(0xFFFAF8F3),
    surfaceVariant = Color(0xFFECEEE6), onSurfaceVariant = Color(0xFF454B43),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5D1B5), onPrimary = Color(0xFF143727),
    primaryContainer = Color(0xFF2C4E3C), onPrimaryContainer = Color(0xFFD3E8D9),
    background = Color(0xFF131713), surface = Color(0xFF131713),
)
private val ReaderTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 34.sp, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 30.sp),
)

@Composable
fun NovelVerseTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val dark = theme == AppTheme.DARK || theme == AppTheme.OLED || (theme == AppTheme.SYSTEM && isSystemInDarkTheme())
    val colors = when {
        theme == AppTheme.OLED -> DarkColors.copy(background = Color.Black, surface = Color.Black)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = ReaderTypography, content = content)
}
