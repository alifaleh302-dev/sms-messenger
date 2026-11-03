package com.sms.messages.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
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
    secondaryContainer = Color(0xFFD3F3E7),
    onSecondaryContainer = Color(0xFF00382A),
    tertiary = Color(0xFF00707C),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFAFECF5),
    onTertiaryContainer = Color(0xFF002F35),
    background = Surface,
    onBackground = Color(0xFF14171C),
    surface = Color.White,
    onSurface = Color(0xFF14171C),
    surfaceVariant = Color(0xFFE6E9EF),
    onSurfaceVariant = Color(0xFF44474E),
    // `outline` draws the boundary of things that are not text: the tab chips, a reply
    // quote, an unselected control. At the old value it sat at 1.6:1 against the page,
    // which is under the 3:1 that WCAG asks of a UI boundary, so chips read as empty
    // space. This value clears 3:1 on both the page and a white card.
    outline = Color(0xFF858D9C),
    outlineVariant = Color(0xFFD5DAE3),
    // The container ladder is what Cards, dialogs, sheets and menus paint themselves
    // with. Leaving it out is not neutral: those components fall back to Material's
    // baseline palette, which is a lavender that does not belong to this app.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F6FA),
    surfaceContainer = Color(0xFFEEF1F7),
    surfaceContainerHigh = Color(0xFFE8ECF3),
    surfaceContainerHighest = Color(0xFFE2E7EF),
    inverseSurface = Color(0xFF2E3138),
    inverseOnSurface = Color(0xFFF1F3F7),
    inversePrimary = BrandDark,
    scrim = Color.Black,
)

val DarkColors = darkColorScheme(
    primary = BrandDark,
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF00458A),
    onPrimaryContainer = Color(0xFFD7E4FF),
    secondary = AccentDark,
    onSecondary = Color(0xFF003825),
    secondaryContainer = Color(0xFF00503A),
    onSecondaryContainer = Color(0xFF6FDDB6),
    tertiary = Color(0xFF6FD6E4),
    onTertiary = Color(0xFF00363C),
    tertiaryContainer = Color(0xFF004E57),
    onTertiaryContainer = Color(0xFFAFECF5),
    background = SurfaceDark,
    onBackground = Color(0xFFE3E6EB),
    surface = Color(0xFF181D24),
    onSurface = Color(0xFFE3E6EB),
    surfaceVariant = Color(0xFF2A2F38),
    onSurfaceVariant = Color(0xFFC3C7CF),
    outline = Color(0xFF737B87),
    outlineVariant = Color(0xFF454C57),
    surfaceContainerLowest = Color(0xFF0C1015),
    surfaceContainerLow = Color(0xFF1C222A),
    surfaceContainer = Color(0xFF20262E),
    surfaceContainerHigh = Color(0xFF252B34),
    surfaceContainerHighest = Color(0xFF2A313A),
    inverseSurface = Color(0xFFE3E6EB),
    inverseOnSurface = Color(0xFF2A2F38),
    inversePrimary = Brand,
    scrim = Color.Black,
)

/**
 * The outgoing bubble fill and the colour drawn on top of it.
 *
 * A bubble is not an accent: in dark mode Material's `primary` is a pale blue meant for
 * buttons on a dark page, and filling a bubble with it reads as washed out and inverts the
 * light theme, where the bubble is the strong brand blue. The bubble keeps its own pair so
 * both modes look like the same product.
 */
data class BubblePalette(val outgoing: Color, val onOutgoing: Color)

val LocalBubblePalette = staticCompositionLocalOf {
    BubblePalette(BubbleOut, Color.White)
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
    ) {
        // The screens below the nav host do not each paint a background, so without this
        // they would fall through to the AppCompat window colour, which is not the brand
        // surface. Painting it once here keeps every screen on the same page colour.
        Surface(color = colors.background) {
            CompositionLocalProvider(
                LocalBubblePalette provides if (dark) {
                    BubblePalette(BubbleOutDark, Color.White)
                } else {
                    BubblePalette(BubbleOut, Color.White)
                },
                content = content,
            )
        }
    }
}
