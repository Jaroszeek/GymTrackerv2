package com.jaroszeek.gymtracker.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.jaroszeek.gymtracker.designsystem.component.GlassNavBar
import com.jaroszeek.gymtracker.designsystem.component.GlassNavItem
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.HideOnScrollState
import com.jaroszeek.gymtracker.designsystem.theme.GtMotion
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.feature.placeholder.PlaceholderScreen

private val NavItems = TopLevelTab.entries.map { GlassNavItem(it.label, it.icon, it.selectedIcon) }

/** App shell: content under the glass navbar, one back stack per tab. */
@Composable
fun GymTrackerRoot() {
    val navigator = rememberTabNavigator()
    val density = LocalDensity.current
    val hideOnScroll = remember(density) { HideOnScrollState(threshold = with(density) { GtSizes.HideBarThreshold.toPx() }) }

    // Every stack is decorated (keeps its saved state); only the visible ones go on screen
    val decorated = TopLevelTab.entries.associateWith { tab ->
        rememberDecoratedNavEntries(
            backStack = navigator.stacks.getValue(tab),
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = ::entryFor,
        )
    }

    GlassScaffold(
        modifier = Modifier.nestedScroll(hideOnScroll.connection),
        chrome = {
            AnimatedVisibility(
                visible = hideOnScroll.visible,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                GlassNavBar(
                    items = NavItems,
                    selectedIndex = navigator.currentTab.ordinal,
                    onSelect = { index ->
                        navigator.select(TopLevelTab.entries[index])
                        hideOnScroll.show()
                    },
                )
            }
        },
    ) {
        NavDisplay(
            entries = navigator.visibleTabs.flatMap { decorated.getValue(it) },
            onBack = navigator::back,
            transitionSpec = { forward() },
            popTransitionSpec = { backward() },
            predictivePopTransitionSpec = { backward() },
        )
    }
}

private fun entryFor(key: NavKey): NavEntry<NavKey> = NavEntry(key) {
    val tab = TopLevelTab.entries.first { it.root == key }
    PlaceholderScreen(tab)
}

// Transitions as in vynox: a short slide with a fade
private fun forward(): ContentTransform =
    (slideInHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 5 } + fadeIn()) togetherWith fadeOut(tween(GtMotion.FADE_OUT_MILLIS))

private fun backward(): ContentTransform =
    fadeIn(tween(GtMotion.FADE_IN_MILLIS)) togetherWith (slideOutHorizontally(spring(stiffness = Spring.StiffnessMediumLow)) { it / 5 } + fadeOut())
