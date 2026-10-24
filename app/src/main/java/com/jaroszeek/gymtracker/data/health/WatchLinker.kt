package com.jaroszeek.gymtracker.data.health

import android.util.Log
import com.jaroszeek.gymtracker.core.di.ApplicationScope
import com.jaroszeek.gymtracker.data.workout.WorkoutRepository
import com.jaroszeek.gymtracker.domain.health.autoLinks
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

/**
 * Links each finished workout with the watch session recorded alongside it ([autoLinks]) by itself,
 * whenever workouts or imported sessions change; this replaces the proposal of D-062. Unlinking by hand
 * marks the workout dismissed, so it is not linked again.
 */
@Singleton
class WatchLinker @Inject constructor(
    private val workouts: WorkoutRepository,
    private val health: HealthRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Called once by the application. */
    fun start() {
        // A link takes the workout out of the unlinked ones, so the next emission has nothing left to do.
        // The three flows emit one by one, so a link may be worked out from an outdated mix; the
        // repository then skips it and the newer emission gets it right.
        combine(workouts.observeUnlinked(), health.observeSessions(), workouts.observeLinkedSessions(), ::autoLinks)
            .onEach { links -> links.forEach { (id, sessionId) -> workouts.autoLinkHealthSession(id, sessionId) } }
            .retryWhen { e, _ ->
                Log.e(TAG, "Linking workouts with watch sessions failed", e)
                delay(RETRY_DELAY_MILLIS)
                true
            }
            .launchIn(scope)
    }

    private companion object {
        const val TAG = "WatchLinker"
        const val RETRY_DELAY_MILLIS = 30_000L
    }
}
