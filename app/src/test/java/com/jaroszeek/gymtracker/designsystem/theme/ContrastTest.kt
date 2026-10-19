package com.jaroszeek.gymtracker.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every text color must reach WCAG AA (4.5:1) on every surface it sits on, in every theme. */
class ContrastTest {

    private val themes = GtPalette.entries.flatMap { palette ->
        listOf(false, true).flatMap { dark ->
            listOf(false, true).map { amoled -> "$palette dark=$dark amoled=$amoled" to gtColors(palette, dark, amoled) }
        }
    }

    @Test
    fun textOnOpaqueSurfaces() = check { c ->
        val texts = mapOf("textPrimary" to c.textPrimary, "textSecondary" to c.textSecondary, "textTertiary" to c.textTertiary)
        val surfaces = mapOf("background" to c.background, "surface" to c.surface, "surfaceRaised" to c.surfaceRaised)
        texts.flatMap { (t, text) -> surfaces.map { (s, surface) -> "$t on $s" to contrastRatio(text, surface) } }
    }

    @Test
    fun statusColorsOnCards() = check { c ->
        listOf("danger" to contrastRatio(c.danger, c.surface), "success" to contrastRatio(c.success, c.surface))
    }

    @Test
    fun accentPairs() = check { c ->
        val washOnSurface = c.accent.copy(alpha = GtAlpha.ACCENT_WASH).compositeOver(c.surface)
        val washOnRaised = c.accent.copy(alpha = GtAlpha.ACCENT_WASH).compositeOver(c.surfaceRaised)
        listOf(
            "onAccent on accent" to contrastRatio(c.onAccent, c.accent),
            "accentText on background" to contrastRatio(c.accentText, c.background),
            "accentText on surface" to contrastRatio(c.accentText, c.surface),
            "accentText on accent wash (surface)" to contrastRatio(c.accentText, washOnSurface),
            "accentText on accent wash (raised)" to contrastRatio(c.accentText, washOnRaised),
        )
    }

    // Glass sits over the aurora; the worst case is the center of a blob right behind it
    @Test
    fun textOnGlassOverAurora() = check { c ->
        c.aurora.flatMap { tone ->
            val behind = tone.copy(alpha = c.auroraAlpha).compositeOver(c.background)
            val glass = c.glassTint.compositeOver(behind)
            val droplet = c.accent.copy(alpha = GtAlpha.ACCENT_WASH).compositeOver(glass)
            listOf(
                "textPrimary on glass ($tone)" to contrastRatio(c.textPrimary, glass),
                "textSecondary on glass ($tone)" to contrastRatio(c.textSecondary, glass),
                "textPrimary on navbar droplet ($tone)" to contrastRatio(c.textPrimary, droplet),
            )
        }
    }

    @Test
    fun contentColorPicksReadableInk() {
        assertEquals(Color.White, contentColorFor(Color(0xFF161616)))
        assertTrue(contentColorFor(Color(0xFFC6F135)) != Color.White)
    }

    private fun check(pairs: (GtColors) -> List<Pair<String, Float>>) {
        val failures = themes.flatMap { (name, colors) ->
            pairs(colors).filter { it.second < MIN_CONTRAST }.map { "$name: ${it.first} = ${"%.2f".format(it.second)}" }
        }
        assertTrue("Contrast below $MIN_CONTRAST:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private companion object {
        const val MIN_CONTRAST = 4.5f
    }
}
