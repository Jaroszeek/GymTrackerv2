package com.jaroszeek.gymtracker.feature.workout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.core.notifications.LiveUpdateChecks
import com.jaroszeek.gymtracker.core.notifications.WorkoutNotifications
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.data.exercise.ExerciseSeeder
import com.jaroszeek.gymtracker.data.routine.RoutineRepository
import com.jaroszeek.gymtracker.data.routine.TemplateRepository
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutManager
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutStore
import com.jaroszeek.gymtracker.data.workout.WorkoutRepository
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.RoutineDraft
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.feature.exercises.ExercisesViewModel
import com.jaroszeek.gymtracker.feature.routines.RoutinesRoute
import com.jaroszeek.gymtracker.feature.routines.RoutinesViewModel
import java.io.File
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole workout through the UI: start from the routine card, enter a weight, tick sets off,
 * the rest overlay (+30 s, skip, close), finish, summary, apply the new targets, saved to history.
 * Real repositories on an in-memory database; the real seed.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val file = File(context.cacheDir, "workout-flow-test.json")
    private lateinit var database: GtDatabase
    private lateinit var exercises: ExerciseRepository
    private lateinit var routines: RoutineRepository
    private lateinit var workouts: WorkoutRepository
    private lateinit var manager: ActiveWorkoutManager
    private lateinit var preferences: AppPreferences
    private var routineId = 0L

    @Before
    fun setUp() {
        file.delete()
        database = Room.inMemoryDatabaseBuilder<GtDatabase>(context).setDriver(AndroidSQLiteDriver()).build()
        exercises = ExerciseRepository(database, ExerciseSeeder(database, Dispatchers.IO, context), Dispatchers.IO)
        routines = RoutineRepository(database, exercises, Clock.systemUTC(), Dispatchers.IO)
        workouts = WorkoutRepository(database, Dispatchers.IO)
        preferences = AppPreferences(PreferenceDataStoreFactory.create(scope = scope) { File(context.cacheDir, "workout-flow-test.preferences_pb").apply { delete() } })
        manager = ActiveWorkoutManager(ActiveWorkoutStore(file), workouts, preferences, Clock.systemUTC(), scope, Dispatchers.IO)
        routineId = runBlocking {
            val bench = exercises.observeAll().first().single { it.name == BENCH }
            var draft = RoutineDraft(name = "Push").addBlock(bench)
            val block = draft.blocks.single()
            draft = draft.updateTargets(block.id, block.variants.single().id, RoutineTargets(2, 8, 12, 60.0, restSeconds = 60))
            routines.save(null, draft)
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
        file.delete()
    }

    @Test
    fun startTickOffRestFinishAndApplyTargets() {
        val routinesViewModel = RoutinesViewModel(routines, manager)
        val checks = LiveUpdateChecks(context, manager, WorkoutNotifications(context))
        val workoutViewModel = WorkoutViewModel(manager, exercises, routines, checks, preferences, Dispatchers.Default)
        // The background issues card (permissions differ per device) would push the exercises off screen
        runBlocking { preferences.setHiddenBackgroundIssues(BackgroundIssues(notificationsOff = true, exactAlarmsOff = true, batteryLimited = true).keys) }
        val pickerViewModel = ExercisesViewModel(exercises, TemplateRepository(database, exercises, routines, Dispatchers.IO, context), Dispatchers.Default)
        var onWorkout by mutableStateOf(false)
        compose.setContent {
            GymTrackerTheme {
                GlassScaffold {
                    if (onWorkout) {
                        WorkoutRoute(workoutViewModel, pickerViewModel, onClose = { onWorkout = false }, onDiagnostics = {})
                    } else {
                        RoutinesRoute(onEdit = {}, onNew = {}, onTemplates = {}, onOpenWorkout = { onWorkout = true }, viewModel = routinesViewModel)
                    }
                }
            }
        }

        // Start from the routine card
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Start").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Start").performClick()
        // The exercise name can show a frame before its set rows
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithContentDescription("Zwiększ: Ciężar").fetchSemanticsNodes().isNotEmpty() }

        // 60 → 62.5 kg on the first set; the second follows
        compose.onAllNodesWithContentDescription("Zwiększ: Ciężar").onFirst().performClick()
        compose.onNodeWithContentDescription("Odhacz serię 1").performClick()

        // The rest starts: 60 s from the targets, +30 s, then skip
        compose.onNodeWithText("Następne: $BENCH • seria 2").assertIsDisplayed()
        compose.onNodeWithText("+30 s").performClick()
        compose.waitForIdle()
        assertEquals(90_000L, manager.workout.value!!.rest!!.let { it.endsAt - it.startsAt })
        compose.onNodeWithText("Pomiń").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Pomiń").fetchSemanticsNodes().isEmpty() }

        // The second set, ticked off from its one-line row with the copied weight; closing keeps the rest running
        compose.onNodeWithContentDescription("Odhacz serię 2").performClick()
        compose.onNodeWithText("To była ostatnia seria").assertIsDisplayed()
        compose.onNodeWithContentDescription("Zamknij").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("To była ostatnia seria").fetchSemanticsNodes().isEmpty() }

        // Finish
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Zakończ trening"))
        compose.onNodeWithText("Zakończ trening").performClick()
        compose.onNodeWithText("Trening trafi do historii.").assertIsDisplayed()
        compose.onNodeWithText("Zakończ").performClick()

        // Summary and the target change: 2 × 8–12 at 60 kg → 62.5 kg
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Trening zakończony").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Serie robocze").assertIsDisplayed()
        compose.onNodeWithText("Zastosuj jako nowe cele").performClick()
        compose.onAllNodesWithText("Gotowe").onLast().performClick()
        compose.waitUntil(TIMEOUT) {
            runBlocking { routines.observe(routineId).first()!!.blocks.single().variants.single().targets.weightKg } == 62.5
        }

        // Saved to history, nothing left to restore
        runBlocking {
            assertEquals(1, database.workoutDao().workouts().size)
            assertEquals(List(2) { PerformedSet(62.5, 8) }, workouts.lastSession(exercises.observeAll().first().single { it.name == BENCH }.id))
        }
        assertFalse(file.exists())

        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Gotowe").fetchSemanticsNodes().size == 1 }
        compose.onNodeWithText("Gotowe").performClick()
        compose.waitUntil(TIMEOUT) { !onWorkout }
    }

    private companion object {
        const val BENCH = "Barbell Bench Press - Medium Grip"
        const val TIMEOUT = 10_000L
    }
}
