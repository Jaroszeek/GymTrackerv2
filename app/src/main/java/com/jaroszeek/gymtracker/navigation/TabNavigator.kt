package com.jaroszeek.gymtracker.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * One back stack per navbar tab (D-003). Back from a tab's first screen returns to Start,
 * back from Start's first screen leaves the app; re-selecting a tab returns to its first screen.
 */
@Stable
class TabNavigator(
    val stacks: Map<TopLevelTab, MutableList<NavKey>>,
    tab: MutableState<TopLevelTab>,
) {
    var currentTab by tab
        private set

    /** Stacks on screen, bottom first: Start stays below the current tab, so back walks into it. */
    val visibleTabs: List<TopLevelTab>
        get() = if (currentTab == TopLevelTab.Home) listOf(TopLevelTab.Home) else listOf(TopLevelTab.Home, currentTab)

    fun select(tab: TopLevelTab) {
        if (tab == currentTab) {
            val stack = stacks.getValue(tab)
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
        } else {
            currentTab = tab
        }
    }

    fun navigate(key: NavKey) {
        stacks.getValue(currentTab).add(key)
    }

    fun back() {
        val stack = stacks.getValue(currentTab)
        when {
            stack.size > 1 -> stack.removeAt(stack.lastIndex)
            currentTab != TopLevelTab.Home -> currentTab = TopLevelTab.Home
        }
    }
}

@Composable
fun rememberTabNavigator(): TabNavigator {
    val stacks = TopLevelTab.entries.associateWith { rememberNavBackStack(it.root) }
    val tab = rememberSaveable { mutableStateOf(TopLevelTab.Home) }
    return remember(stacks, tab) { TabNavigator(stacks, tab) }
}
