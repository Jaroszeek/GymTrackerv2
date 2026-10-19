package com.jaroszeek.gymtracker.navigation

import androidx.compose.runtime.mutableStateOf
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class TabNavigatorTest {

    private data object Detail : NavKey

    private fun navigator() = TabNavigator(
        stacks = TopLevelTab.entries.associateWith { mutableListOf(it.root) },
        tab = mutableStateOf(TopLevelTab.Home),
    )

    @Test
    fun startStaysBelowOtherTabs() {
        val navigator = navigator()
        navigator.select(TopLevelTab.Progress)
        assertEquals(listOf(TopLevelTab.Home, TopLevelTab.Progress), navigator.visibleTabs)
    }

    @Test
    fun backFromTabRootReturnsToStart() {
        val navigator = navigator()
        navigator.select(TopLevelTab.Workout)
        navigator.back()
        assertEquals(TopLevelTab.Home, navigator.currentTab)
    }

    @Test
    fun backPopsWithinTabFirst() {
        val navigator = navigator()
        navigator.select(TopLevelTab.Exercises)
        navigator.navigate(Detail)
        navigator.back()
        assertEquals(TopLevelTab.Exercises, navigator.currentTab)
        assertEquals(listOf<NavKey>(ExercisesKey), navigator.stacks.getValue(TopLevelTab.Exercises))
    }

    @Test
    fun reselectingTabReturnsToItsRoot() {
        val navigator = navigator()
        navigator.select(TopLevelTab.Profile)
        navigator.navigate(Detail)
        navigator.navigate(Detail)
        navigator.select(TopLevelTab.Profile)
        assertEquals(listOf<NavKey>(ProfileKey), navigator.stacks.getValue(TopLevelTab.Profile))
    }

    @Test
    fun openBringsTheWorkoutToTheTopFromAnyTab() {
        val navigator = navigator()
        navigator.select(TopLevelTab.Workout)
        navigator.navigate(ActiveWorkoutKey)
        navigator.navigate(Detail)
        navigator.select(TopLevelTab.Exercises)

        navigator.open(TopLevelTab.Workout, ActiveWorkoutKey)

        assertEquals(TopLevelTab.Workout, navigator.currentTab)
        assertEquals(listOf(WorkoutKey, Detail, ActiveWorkoutKey), navigator.stacks.getValue(TopLevelTab.Workout))
        assertEquals(true, navigator.isOnTop(ActiveWorkoutKey))
        // Opening it again changes nothing
        navigator.open(TopLevelTab.Workout, ActiveWorkoutKey)
        assertEquals(listOf(WorkoutKey, Detail, ActiveWorkoutKey), navigator.stacks.getValue(TopLevelTab.Workout))
    }

    @Test
    fun tabsKeepTheirStacks() {
        val navigator = navigator()
        navigator.select(TopLevelTab.Exercises)
        navigator.navigate(Detail)
        navigator.select(TopLevelTab.Progress)
        navigator.select(TopLevelTab.Exercises)
        assertEquals(listOf(ExercisesKey, Detail), navigator.stacks.getValue(TopLevelTab.Exercises))
    }
}
