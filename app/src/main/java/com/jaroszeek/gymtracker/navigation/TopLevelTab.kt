package com.jaroszeek.gymtracker.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.jaroszeek.gymtracker.R
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data object ExercisesRoute : NavKey

@Serializable
data object WorkoutRoute : NavKey

@Serializable
data object ProgressRoute : NavKey

@Serializable
data object ProfileRoute : NavKey

/** Navbar tabs in display order; Trening is the emphasized center (D-010). */
enum class TopLevelTab(
    val root: NavKey,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
) {
    Home(HomeRoute, R.string.nav_home, R.drawable.ic_home, R.drawable.ic_home_filled),
    Exercises(ExercisesRoute, R.string.nav_exercises, R.drawable.ic_menu_book, R.drawable.ic_menu_book_filled),
    Workout(WorkoutRoute, R.string.nav_workout, R.drawable.ic_exercise, R.drawable.ic_exercise_filled),
    Progress(ProgressRoute, R.string.nav_progress, R.drawable.ic_monitoring, R.drawable.ic_monitoring_filled),
    Profile(ProfileRoute, R.string.nav_profile, R.drawable.ic_person, R.drawable.ic_person_filled),
}
