package com.jaroszeek.gymtracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jaroszeek.gymtracker.core.notifications.WorkoutService
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutManager
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.navigation.GymTrackerRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var workouts: ActiveWorkoutManager

    /** Bumped by every tap on the workout notification; the root then opens the workout. */
    private var openWorkoutRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.action == ACTION_OPEN_WORKOUT) openWorkoutRequests++
        addOnNewIntentListener { if (it.action == ACTION_OPEN_WORKOUT) openWorkoutRequests++ }
        lifecycleScope.launch {
            // The service starts only from the foreground (A7): on "Start", or on coming back with a workout restored after process death
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                workouts.workout.map { it != null }.distinctUntilChanged().filter { it }.collect { WorkoutService.start(this@MainActivity) }
            }
        }
        setContent {
            GymTrackerTheme {
                GymTrackerRoot(openWorkoutRequests)
            }
        }
    }

    companion object {
        const val ACTION_OPEN_WORKOUT = "com.jaroszeek.gymtracker.action.OPEN_WORKOUT"
    }
}
