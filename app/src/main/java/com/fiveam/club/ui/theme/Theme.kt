package com.fiveam.club.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.fiveam.club.data.prefs.ThemeMode

val Sun = Color(0xFFFFB547)
val Dawn = Color(0xFF7C9CFF)
val Night = Color(0xFF0B1020)
val NightSurface = Color(0xFF151B2E)
val NightSurfaceHigh = Color(0xFF1E2640)
val Success = Color(0xFF4ADE80)
val Warning = Color(0xFFFBBF24)
val Danger = Color(0xFFF87171)

private val DarkColors = darkColorScheme(
    primary = Sun,
    onPrimary = Color(0xFF231600),
    primaryContainer = Color(0xFF5C3B00),
    onPrimaryContainer = Color(0xFFFFDDB0),
    secondary = Dawn,
    onSecondary = Color(0xFF0A1A4F),
    background = Night,
    onBackground = Color(0xFFE8EAF2),
    surface = NightSurface,
    onSurface = Color(0xFFE8EAF2),
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = Color(0xFFB8BED3),
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    error = Danger,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5300),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDB0),
    onPrimaryContainer = Color(0xFF2C1700),
    secondary = Color(0xFF3B5BDB),
    background = Color(0xFFFBF8F3),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF1ECE4),
    onSurfaceVariant = Color(0xFF4F4639),
)

// UX-03: comfortable sizes for half-asleep eyes.
private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 72.sp, fontWeight = FontWeight.Bold, lineHeight = 80.sp),
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

/** UX-01/UX-02: Arabic RTL everywhere, dark by default. */
@Composable
fun FiveAmTheme(mode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}
