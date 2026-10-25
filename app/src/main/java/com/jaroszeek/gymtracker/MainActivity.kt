package com.jaroszeek.gymtracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.core.notifications.WorkoutService
import com.jaroszeek.gymtracker.core.update.UpdateManager
import com.jaroszeek.gymtracker.data.health.HealthSync
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutManager
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.designsystem.theme.ThemeSettings
import com.jaroszeek.gymtracker.domain.settings.WeightUnit
import com.jaroszeek.gymtracker.feature.health.PrivacyPolicyScreen
import com.jaroszeek.gymtracker.feature.routines.LocalWeightUnit
import com.jaroszeek.gymtracker.navigation.GymTrackerRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var workouts: ActiveWorkoutManager

    @Inject lateinit var healthSync: HealthSync

    @Inject lateinit var preferences: AppPreferences

    @Inject lateinit var updates: UpdateManager

    /** How the app looks (A5.9); null until read, while the splash screen stays. */
    private lateinit var look: StateFlow<Look?>

    /** Bumped by every tap on the workout notification; the root then opens the workout. */
    private var openWorkoutRequests by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        look = combine(preferences.theme, preferences.weightUnit, ::Look).stateIn(lifecycleScope, SharingStarted.Eagerly, null)
        keepSplashUntilLookIsRead()
        // Health Connect opens only the privacy policy, through the activity-alias (A6)
        if (intent?.action == Intent.ACTION_VIEW_PERMISSION_USAGE) {
            setLookedContent { GlassScaffold { PrivacyPolicyScreen(onBack = ::finish) } }
            return
        }
        if (savedInstanceState == null && intent?.action == ACTION_OPEN_WORKOUT) openWorkoutRequests++
        addOnNewIntentListener { if (it.action == ACTION_OPEN_WORKOUT) openWorkoutRequests++ }
        lifecycleScope.launch {
            // The service starts only from the foreground (A7): on "Start", or on coming back with a workout restored after process death
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                workouts.workout.map { it != null }.distinctUntilChanged().filter { it }.collect { WorkoutService.start(this@MainActivity) }
            }
        }
        lifecycleScope.launch {
            // The system's confirmation of an update (A5.11) can only start from a visible activity
            repeatOnLifecycle(Lifecycle.State.STARTED) { updates.userActions.collect(::startActivity) }
        }
        setLookedContent { GymTrackerRoot(openWorkoutRequests) }
    }

    private fun setLookedContent(content: @Composable () -> Unit) = setContent {
        val current = look.collectAsStateWithLifecycle().value ?: return@setContent
        GymTrackerTheme(current.theme) {
            CompositionLocalProvider(LocalWeightUnit provides current.unit, content = content)
        }
    }

    /** The first frame waits for the saved theme, so it does not flash in the default colors. */
    private fun keepSplashUntilLookIsRead() {
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean = (look.value != null).also { ready ->
                    if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                }
            },
        )
    }

    private data class Look(val theme: ThemeSettings, val unit: WeightUnit)

    override fun onStart() {
        super.onStart()
        // Sync on open (A6) and the daily update check (A5.11); the privacy policy alone, opened by Health Connect, does not count
        if (intent?.action != Intent.ACTION_VIEW_PERMISSION_USAGE) {
            healthSync.onAppStart()
            updates.checkIfDue()
        }
    }

    companion object {
        const val ACTION_OPEN_WORKOUT = "com.jaroszeek.gymtracker.action.OPEN_WORKOUT"
    }
}
