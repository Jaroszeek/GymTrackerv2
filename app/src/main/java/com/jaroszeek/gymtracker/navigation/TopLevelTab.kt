package com.jaroszeek.gymtracker.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.jaroszeek.gymtracker.R
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data object ExercisesKey : NavKey

@Serializable
data object WorkoutKey : NavKey

@Serializable
data object ProgressKey : NavKey

@Serializable
data object ProfileKey : NavKey

@Serializable
data class ExerciseDetailKey(val id: Long) : NavKey

/** [id] null creates a new routine. */
@Serializable
data class RoutineEditorKey(val id: Long?) : NavKey

@Serializable
data object TemplatesKey : NavKey

/** [id] null creates a new custom exercise. */
@Serializable
data class ExerciseEditorKey(val id: Long?) : NavKey

/** Navbar tabs in display order; Trening is the emphasized center (D-010). */
enum class TopLevelTab(
    val root: NavKey,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
) {
    Home(HomeKey, R.string.nav_home, R.drawable.ic_home, R.drawable.ic_home_filled),
    Exercises(ExercisesKey, R.string.nav_exercises, R.drawable.ic_menu_book, R.drawable.ic_menu_book_filled),
    Workout(WorkoutKey, R.string.nav_workout, R.drawable.ic_exercise, R.drawable.ic_exercise_filled),
    Progress(ProgressKey, R.string.nav_progress, R.drawable.ic_monitoring, R.drawable.ic_monitoring_filled),
    Profile(ProfileKey, R.string.nav_profile, R.drawable.ic_person, R.drawable.ic_person_filled),
}
