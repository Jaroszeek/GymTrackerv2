package com.jaroszeek.gymtracker.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

enum class ThemeMode { System, Light, Dark }

/** Curated palettes. Onyx and Porcelain are monochrome themes with a fixed brightness. */
enum class GtPalette(val forcedDark: Boolean? = null) {
    Volt, Ember, Rose, Aqua, Azure, Amber,
    Onyx(forcedDark = true),
    Porcelain(forcedDark = false),
}

@Immutable
data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.System,
    val palette: GtPalette = GtPalette.Volt,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = false,
    val reduceTransparency: Boolean = false,
)

/**
 * Semantic colors of one theme.
 *
 * [accent] is the vivid fill (buttons, indicators) paired with [onAccent];
 * [accentText] is the accent for text and icons on [background] and [surface].
 */
@Immutable
data class GtColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val glassTint: Color,
    val hairline: Color,
    val specularTop: Color,
    val specularBottom: Color,
    val sheen: Color,
    val shadow: Color,
    val scrim: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val danger: Color,
    val success: Color,
    val aurora: List<Color>,
    val auroraAlpha: Float,
)

private data class Base(
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val glassTint: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val danger: Color,
    val success: Color,
)

private val DarkBase = Base(
    background = Color(0xFF0A0B0E),
    surface = Color(0xFF14161B),
    surfaceRaised = Color(0xFF1D2027),
    glassTint = Color(0xA81A1D24),
    textPrimary = Color(0xFFF2F4F7),
    textSecondary = Color(0xFFA8AEBA),
    textTertiary = Color(0xFF858C99),
    danger = Color(0xFFFF7A7A),
    success = Color(0xFF4ADE80),
)

private val AmoledBase = DarkBase.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF0F1013),
    surfaceRaised = Color(0xFF181A1F),
    glassTint = Color(0xB0121418),
)

private val LightBase = Base(
    background = Color(0xFFF3F2EE),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFBFAF7),
    glassTint = Color(0xB8FFFFFF),
    textPrimary = Color(0xFF121418),
    textSecondary = Color(0xFF4A505B),
    textTertiary = Color(0xFF626873),
    danger = Color(0xFFC62828),
    success = Color(0xFF1B7A3A),
)

private val OnyxBase = DarkBase.copy(
    background = Color(0xFF0B0B0B),
    surface = Color(0xFF161616),
    surfaceRaised = Color(0xFF1F1F1F),
    glassTint = Color(0xA81A1A1A),
    textPrimary = Color(0xFFF5F5F5),
    textSecondary = Color(0xFFADADAD),
    textTertiary = Color(0xFF8A8A8A),
)

private val OnyxAmoledBase = OnyxBase.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF101010),
    surfaceRaised = Color(0xFF191919),
    glassTint = Color(0xB0121212),
)

private val PorcelainBase = LightBase.copy(
    background = Color(0xFFF4F3F0),
    surfaceRaised = Color(0xFFFAFAF9),
    textPrimary = Color(0xFF111111),
    textSecondary = Color(0xFF4D4D4D),
    textTertiary = Color(0xFF666666),
)

/** Vivid fill, darker variant for accent text on light themes, aurora tones. */
private data class Accent(val vivid: Color, val lightText: Color, val aurora: List<Color>)

private val Accents = mapOf(
    GtPalette.Volt to Accent(Color(0xFFC6F135), Color(0xFF3F6B00), listOf(Color(0xFFC6F135), Color(0xFF3DDC97), Color(0xFF7BD3F7))),
    GtPalette.Ember to Accent(Color(0xFFFF6A3D), Color(0xFFB23A0E), listOf(Color(0xFFFF6A3D), Color(0xFFFF3D6E), Color(0xFFFFB020))),
    GtPalette.Rose to Accent(Color(0xFFFF5C8A), Color(0xFFB21D50), listOf(Color(0xFFFF5C8A), Color(0xFFB45CFF), Color(0xFFFF8A5C))),
    GtPalette.Aqua to Accent(Color(0xFF3EE6C8), Color(0xFF0B6B63), listOf(Color(0xFF3EE6C8), Color(0xFF3DA9FC), Color(0xFF8AF5A0))),
    GtPalette.Azure to Accent(Color(0xFF4DA3FF), Color(0xFF1A5BC4), listOf(Color(0xFF4DA3FF), Color(0xFF7C5CFF), Color(0xFF3EE6C8))),
    GtPalette.Amber to Accent(Color(0xFFFFB020), Color(0xFF8A5000), listOf(Color(0xFFFFB020), Color(0xFFFF6A3D), Color(0xFFF5E050))),
    GtPalette.Onyx to Accent(Color(0xFFEDEDED), Color(0xFFEDEDED), listOf(Color(0xFFFFFFFF), Color(0xFF9AA0A6), Color(0xFF5F6368))),
    GtPalette.Porcelain to Accent(Color(0xFF161616), Color(0xFF161616), listOf(Color(0xFF8A8A8A), Color(0xFFB0B0B0), Color(0xFF6B6B6B))),
)

/**
 * Builds the colors for [palette]. [dynamicAccent] (Material You) replaces the palette accent;
 * it is expected to be the scheme's `primary`, already contrasting with this brightness.
 */
fun gtColors(
    palette: GtPalette,
    dark: Boolean,
    amoled: Boolean = false,
    dynamicAccent: List<Color>? = null,
): GtColors {
    val isDark = palette.forcedDark ?: dark
    val base = when (palette) {
        GtPalette.Onyx -> if (amoled) OnyxAmoledBase else OnyxBase
        GtPalette.Porcelain -> PorcelainBase
        else -> when {
            isDark && amoled -> AmoledBase
            isDark -> DarkBase
            else -> LightBase
        }
    }
    val accent = Accents.getValue(palette)
    val vivid = dynamicAccent?.first() ?: accent.vivid
    val accentText = when {
        dynamicAccent != null -> vivid
        isDark -> accent.vivid
        else -> accent.lightText
    }
    val ink = if (isDark) Color.White else Color.Black
    return GtColors(
        isDark = isDark,
        background = base.background,
        surface = base.surface,
        surfaceRaised = base.surfaceRaised,
        glassTint = base.glassTint,
        hairline = ink.copy(alpha = if (isDark) 0.10f else 0.08f),
        specularTop = if (isDark) Color.White.copy(alpha = 0.26f) else Color.White,
        specularBottom = ink.copy(alpha = if (isDark) 0.04f else 0.07f),
        sheen = Color.White.copy(alpha = if (isDark) 0.07f else 0.45f),
        shadow = Color.Black.copy(alpha = if (isDark) 0.45f else 0.10f),
        scrim = Color.Black.copy(alpha = if (isDark) 0.45f else 0.22f),
        textPrimary = base.textPrimary,
        textSecondary = base.textSecondary,
        textTertiary = base.textTertiary,
        accent = vivid,
        onAccent = contentColorFor(vivid),
        accentText = accentText,
        danger = base.danger,
        success = base.success,
        aurora = dynamicAccent ?: accent.aurora,
        auroraAlpha = if (isDark) 0.20f else 0.16f,
    )
}

private val InkDark = Color(0xFF0A0B0E)

/** Near-black or white, whichever contrasts more with [fill] (light accents need dark text). */
fun contentColorFor(fill: Color): Color =
    if (contrastRatio(InkDark, fill) >= contrastRatio(Color.White, fill)) InkDark else Color.White

/** WCAG 2 contrast ratio of two opaque colors. */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return maxOf(la, lb) / minOf(la, lb)
}
