package com.jaroszeek.gymtracker.designsystem.theme

import android.app.Activity
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LocalGtColors = staticCompositionLocalOf { gtColors(GtPalette.Volt, dark = true) }
private val LocalGtTypography = staticCompositionLocalOf { GtTypography() }
private val LocalReduceTransparency = staticCompositionLocalOf { false }
private val LocalReducedMotion = staticCompositionLocalOf { false }

object GtTheme {
    val colors: GtColors
        @Composable @ReadOnlyComposable get() = LocalGtColors.current

    val typography: GtTypography
        @Composable @ReadOnlyComposable get() = LocalGtTypography.current

    /** "Ogranicz przezroczystość": glass becomes opaque, without backdrop blur. */
    val reduceTransparency: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceTransparency.current

    /** System animations are off; decorative motion (aurora drift) stops. */
    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current
}

@Composable
fun GymTrackerTheme(settings: ThemeSettings = ThemeSettings(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = settings.palette.forcedDark ?: when (settings.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val mono = settings.palette.forcedDark != null
    val colors = remember(settings, dark) {
        val dynamic = if (settings.dynamicColor && !mono) {
            val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            listOf(scheme.primary, scheme.tertiary, scheme.secondary)
        } else {
            null
        }
        gtColors(settings.palette, dark, settings.amoled, dynamic)
    }
    val typography = remember { GtTypography() }
    val reducedMotion = remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !colors.isDark
                isAppearanceLightNavigationBars = !colors.isDark
            }
        }
    }

    MaterialTheme(colorScheme = colors.toMaterial(), typography = typography.toMaterial()) {
        CompositionLocalProvider(
            LocalGtColors provides colors,
            LocalGtTypography provides typography,
            LocalReduceTransparency provides settings.reduceTransparency,
            LocalReducedMotion provides reducedMotion,
            // Glass surfaces are not M3 Surfaces; without this, text defaults to near-black
            LocalContentColor provides colors.textPrimary,
            LocalTextStyle provides typography.body,
            content = content,
        )
    }
}

private fun GtColors.toMaterial() = (if (isDark) darkColorScheme() else lightColorScheme()).copy(
    primary = accent,
    onPrimary = onAccent,
    background = background,
    onBackground = textPrimary,
    surface = surface,
    onSurface = textPrimary,
    surfaceVariant = surfaceRaised,
    onSurfaceVariant = textSecondary,
    outline = hairline,
    error = danger,
)
