package com.sms.messages.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sms.messages.data.ThemeMode

private val Brand = Color(0xFF1B6EF3)
private val BrandDark = Color(0xFF9CC4FF)
private val Accent = Color(0xFF00A67D)
private val AccentDark = Color(0xFF6FDDB6)
private val Surface = Color(0xFFF7F8FB)
private val SurfaceDark = Color(0xFF10141A)
private val BubbleOut = Color(0xFF1B6EF3)
private val BubbleOutDark = Color(0xFF2A5FA8)

val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E4FF),
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Accent,
    onSecondary = Color.White,
    background = Surface,
    onBackground = Color(0xFF14171C),
    surface = Color.White,
    onSurface = Color(0xFF14171C),
    surfaceVariant = Color(0xFFE6E9EF),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFFC3C7CF),
)

val DarkColors = darkColorScheme(
    primary = BrandDark,
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF00458A),
    onPrimaryContainer = Color(0xFFD7E4FF),
    secondary = AccentDark,
    onSecondary = Color(0xFF003825),
    background = SurfaceDark,
    onBackground = Color(0xFFE3E6EB),
    surface = Color(0xFF181D24),
    onSurface = Color(0xFFE3E6EB),
    surfaceVariant = Color(0xFF2A2F38),
    onSurfaceVariant = Color(0xFFC3C7CF),
    outline = Color(0xFF8D9199),
)

/** Bubble colours reused by the conversation list and the chat screen. */
object BubbleColors {
    val outgoingLight = BubbleOut
    val outgoingDark = BubbleOutDark
}

@Composable
fun SmsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark ->
            dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colors,
        typography = SmsTypography,
        shapes = SmsShapes,
        content = content,
    )
}
