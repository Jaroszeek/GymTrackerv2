package com.jaroszeek.gymtracker.data.exercise

import android.content.Context
import androidx.room3.withWriteTransaction
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Fills the exercise table from the bundled seed files once. Idempotent: it only runs when no
 * seeded exercise exists, and everything goes in one transaction, so an interrupted seed rolls
 * back and simply runs again on the next start.
 */
@Singleton
class ExerciseSeeder internal constructor(
    private val database: GtDatabase,
    private val io: CoroutineDispatcher,
    private val loadSeed: () -> List<SeedExercise>,
) {
    @Inject
    constructor(
        database: GtDatabase,
        @IoDispatcher io: CoroutineDispatcher,
        @ApplicationContext context: Context,
    ) : this(database, io, { readAssets(context) })

    private val mutex = Mutex()

    @Volatile
    private var seeded = false

    suspend fun ensureSeeded() {
        if (seeded) return
        mutex.withLock {
            if (seeded) return
            withContext(io) {
                val dao = database.exerciseDao()
                if (dao.countSeeded() == 0) {
                    val seed = loadSeed()
                    database.withWriteTransaction {
                        seed.forEach { exercise ->
                            val id = dao.insert(exercise.entity)
                            dao.insertMuscles(exercise.muscles(id))
                        }
                    }
                }
            }
            seeded = true
        }
    }

    private companion object {
        fun readAssets(context: Context): List<SeedExercise> {
            fun read(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }
            return ExerciseSeedParser.parse(
                freeExerciseDb = read(ExerciseSeedParser.FREE_EXERCISE_DB),
                nippard = read(ExerciseSeedParser.NIPPARD),
                aliasesPl = read(ExerciseSeedParser.ALIASES_PL),
            )
        }
    }
}
