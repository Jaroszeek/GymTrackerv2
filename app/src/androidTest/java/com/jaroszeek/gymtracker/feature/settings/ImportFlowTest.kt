package com.jaroszeek.gymtracker.feature.settings

import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.database.RoutineEntity
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.data.backup.BackupFiles
import com.jaroszeek.gymtracker.data.backup.BackupManager
import com.jaroszeek.gymtracker.data.exercise.ExerciseSeeder
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutManager
import com.jaroszeek.gymtracker.data.workout.ActiveWorkoutStore
import com.jaroszeek.gymtracker.data.workout.WorkoutRepository
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Import through the settings: pick a file, the summary dialog, confirm, and the backup is the data
 * after the swap at the next start. The real view model and backup manager on files in the cache.
 */
@RunWith(AndroidJUnit4::class)
class ImportFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val root = File(context.cacheDir, "import-flow-test")
    private val files = BackupFiles(File(root, "databases/gymtracker.db"), File(root, "backup"))
    private val exported = File(root, "GymTracker-export.db")
    private val databases = mutableListOf<GtDatabase>()
    private lateinit var live: GtDatabase
    private lateinit var viewModel: SettingsViewModel
    @Volatile private var restarted = false

    private fun open(file: File): GtDatabase =
        Room.databaseBuilder<GtDatabase>(context, file.path).setDriver(AndroidSQLiteDriver()).build().also { databases += it }

    @Before
    fun setUp() {
        root.deleteRecursively()
        files.database.parentFile!!.mkdirs()
        live = open(files.database)
        val backup = BackupManager(
            database = live,
            files = files,
            driver = AndroidSQLiteDriver(),
            openDatabase = ::open,
            seedExercises = { ExerciseSeeder(it, Dispatchers.IO, context).ensureSeeded() },
            emptyWorkoutName = "Trening",
            io = Dispatchers.IO,
            restartApp = { restarted = true },
        )
        runBlocking {
            ExerciseSeeder(live, Dispatchers.IO, context).ensureSeeded()
            live.routineDao().insertRoutine(RoutineEntity(name = "Legs", createdAt = 1, updatedAt = 1))
            exported.outputStream().use { backup.export(it) }
            // Made after the export: the import replaces it
            live.routineDao().insertRoutine(RoutineEntity(name = "Arms", createdAt = 2, updatedAt = 2))
        }
        val preferences = AppPreferences(PreferenceDataStoreFactory.create(scope = scope) { File(root, "test.preferences_pb") })
        val workout = ActiveWorkoutManager(ActiveWorkoutStore(File(root, "workout.json")), WorkoutRepository(live, Dispatchers.IO), preferences, Clock.systemUTC(), scope, Dispatchers.IO)
        viewModel = SettingsViewModel(preferences, backup, workout, context, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        scope.cancel()
        databases.forEach { it.close() }
        root.deleteRecursively()
    }

    @Test
    fun importShowsTheSummaryAndReplacesTheDataAfterConfirming() {
        show(exported)

        compose.onNodeWithText("Importuj kopię").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Zastąpić dane kopią?").fetchSemanticsNodes().isNotEmpty() }
        // Only "1 …": the plural rules follow the device locale, and a test device may not be Polish
        compose.onNodeWithText("1 rutyna, ", substring = true).assertExists()
        compose.onNodeWithText("Zastąp").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("Uruchamiam ponownie…").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(TIMEOUT) { restarted }

        live.close()
        assertTrue(BackupManager.applyPending(files))
        val imported = open(files.database)
        assertEquals(listOf("Legs"), runBlocking { imported.routineDao().observeAll().first().map { it.routine.name } })
    }

    @Test
    fun aFileThatIsNotABackupIsRejectedWithoutChanges() {
        val notes = File(root, "notes.txt").apply { writeText("not a database") }
        show(notes)

        compose.onNodeWithText("Importuj kopię").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("To nie jest kopia GymTrackera. Dane się nie zmieniły.").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("OK").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodesWithText("To nie jest kopia GymTrackera. Dane się nie zmieniły.").fetchSemanticsNodes().isEmpty() }

        assertEquals(listOf("Legs", "Arms"), runBlocking { live.routineDao().observeAll().first().map { it.routine.name } })
        assertTrue(!files.pending.exists())
    }

    /** The settings, where picking a file to import picks [file]. */
    private fun show(file: File) {
        compose.setContent {
            GymTrackerTheme {
                GlassScaffold {
                    val state = viewModel.state.collectAsStateWithLifecycle().value ?: return@GlassScaffold
                    SettingsScreen(
                        state = state,
                        version = "0.1.0",
                        actions = SettingsActions(
                            onImport = { viewModel.onImport(Uri.fromFile(file)) },
                            onConfirmImport = viewModel::onConfirmImport,
                            onDismissBackup = viewModel::onDismissBackup,
                        ),
                    )
                }
            }
        }
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
