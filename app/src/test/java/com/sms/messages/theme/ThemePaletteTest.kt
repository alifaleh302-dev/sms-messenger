package com.sms.messages.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.sms.messages.ui.theme.DarkColors
import com.sms.messages.ui.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the palette against the trap that made the settings cards lavender.
 *
 * A Material 3 scheme has far more roles than the handful an app usually names. Any role left
 * out silently keeps Material's baseline value, and that baseline is a purple, so the mistake
 * shows up as components that look subtly off-brand rather than as a build error. These checks
 * make a missing role fail loudly instead.
 */
class ThemePaletteTest {

    /** The neutral roles that paint surfaces; every one must come from this app's palette. */
    private val neutralRoles = listOf(
        "background", "onBackground", "surface", "onSurface",
        "surfaceVariant", "onSurfaceVariant",
        "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer",
        "surfaceContainerHigh", "surfaceContainerHighest",
        "outline", "outlineVariant",
        "inverseSurface", "inverseOnSurface",
    )

    /** The roles a Material 3 component reads for elevation and separation. */
    private val containerRoles = listOf(
        "surfaceContainerLowest", "surfaceContainerLow", "surfaceContainer",
        "surfaceContainerHigh", "surfaceContainerHighest",
    )

    private fun role(scheme: ColorScheme, name: String): Color = when (name) {
        "background" -> scheme.background
        "onBackground" -> scheme.onBackground
        "surface" -> scheme.surface
        "onSurface" -> scheme.onSurface
        "surfaceVariant" -> scheme.surfaceVariant
        "onSurfaceVariant" -> scheme.onSurfaceVariant
        "surfaceContainerLowest" -> scheme.surfaceContainerLowest
        "surfaceContainerLow" -> scheme.surfaceContainerLow
        "surfaceContainer" -> scheme.surfaceContainer
        "surfaceContainerHigh" -> scheme.surfaceContainerHigh
        "surfaceContainerHighest" -> scheme.surfaceContainerHighest
        "outline" -> scheme.outline
        "outlineVariant" -> scheme.outlineVariant
        "inverseSurface" -> scheme.inverseSurface
        "inverseOnSurface" -> scheme.inverseOnSurface
        else -> error("unknown role $name")
    }

    /**
     * A neutral whose red and green channels differ is tinted, and Material's baseline tint is
     * a purple: it pushes red and blue above green. The brand's neutrals are blue-tinted, which
     * puts blue on top. Any neutral that is still baseline purple is a role nobody set.
     */
    @Test
    fun lightNeutralsAreTintedByTheBrandNotByMaterial() {
        val purple = neutralRoles.filter { isMaterialPurple(role(LightColors, it)) }
        assertEquals(
            "these light roles still look like Material's purple baseline; " +
                "add them to LightColors",
            emptyList<String>(),
            purple,
        )
    }

    @Test
    fun darkNeutralsAreTintedByTheBrandNotByMaterial() {
        val purple = neutralRoles.filter { isMaterialPurple(role(DarkColors, it)) }
        assertEquals(
            "these dark roles still look like Material's purple baseline; " +
                "add them to DarkColors",
            emptyList<String>(),
            purple,
        )
    }

    /**
     * Cards, dialogs and sheets read the container ladder, so it has to climb in the right
     * direction or they will not separate from the page behind them.
     */
    @Test
    fun lightContainerLadderClimbs() {
        val ladder = containerRoles.map { role(LightColors, it) }
        assertTrue(
            "light surface containers must get progressively darker: $ladder",
            ladder.zipWithNext().all { (a, b) -> a.luminance() >= b.luminance() },
        )
    }

    @Test
    fun darkContainerLadderClimbs() {
        val ladder = containerRoles.map { role(DarkColors, it) }
        assertTrue(
            "dark surface containers must get progressively lighter: $ladder",
            ladder.zipWithNext().all { (a, b) -> a.luminance() <= b.luminance() },
        )
    }

    /** Material's baseline neutrals are violet: red sits above green and blue sits highest. */
    private fun isMaterialPurple(c: Color): Boolean =
        c.red > c.green + 0.005f && c.blue > c.green + 0.005f

    private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
}
