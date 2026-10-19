package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

/** Layer that glass samples; null inside the content, because glass cannot sample the layer it lives in. */
internal val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

private val LocalOverlayHost = staticCompositionLocalOf<OverlayHost?> { null }

private class OverlayHost {
    val entries = mutableStateListOf<State<@Composable () -> Unit>>()
}

/**
 * Root of a glass screen. [content] (with the aurora behind it) is the layer that glass blurs;
 * [chrome] (bars, mini bar) and overlays ([GlassDialog], [GlassBottomSheet]) float above it.
 */
@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    chrome: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val host = remember { OverlayHost() }
    // With "Ogranicz przezroczystość" nothing samples the content, so it is not recorded into a layer either
    val sampled = !GtTheme.reduceTransparency
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (sampled) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            AuroraBackground(Modifier.fillMaxSize())
            CompositionLocalProvider(LocalOverlayHost provides host, content = content)
        }
        StatusBarScrim()
        CompositionLocalProvider(LocalBackdrop provides backdrop.takeIf { sampled }, LocalOverlayHost provides host) {
            chrome()
            host.entries.forEach { entry -> key(entry) { entry.value() } }
        }
    }
}

/** Shows [content] at the [GlassScaffold] level, above the bars, while this composable is in composition. */
@Composable
internal fun Overlay(content: @Composable () -> Unit) {
    val host = checkNotNull(LocalOverlayHost.current) { "Overlay must be placed inside GlassScaffold" }
    val latest = rememberUpdatedState(content)
    DisposableEffect(host) {
        host.entries += latest
        onDispose { host.entries -= latest }
    }
}

// Content scrolled under the status bar must not blend with the clock and system icons
@Composable
private fun StatusBarScrim() {
    val background = GtTheme.colors.background
    Box(
        Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars.add(WindowInsets(top = GtSpacing.xxl)))
            .background(Brush.verticalGradient(listOf(background.copy(alpha = 0.85f), Color.Transparent))),
    )
}

/** Height of chrome floating above the navbar (the workout mini bar); [navBarContentPadding] adds it. */
val LocalChromeAboveNavBar = compositionLocalOf { 0.dp }

/** Bottom padding that keeps scrolled content clear of the floating navbar (and what floats above it). */
@Composable
fun navBarContentPadding(): PaddingValues {
    val system = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(bottom = system + GtSizes.NavBarHeight + GtSizes.NavBarGap * 2 + GtSpacing.s + LocalChromeAboveNavBar.current)
}
