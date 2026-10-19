package com.jaroszeek.gymtracker.data.exercise

import com.jaroszeek.gymtracker.core.database.ExerciseEntity
import com.jaroszeek.gymtracker.core.database.ExerciseMuscleEntity
import com.jaroszeek.gymtracker.domain.exercise.Equipment
import com.jaroszeek.gymtracker.domain.exercise.ExerciseCategory
import com.jaroszeek.gymtracker.domain.exercise.ExerciseSource
import com.jaroszeek.gymtracker.domain.exercise.Level
import com.jaroszeek.gymtracker.domain.exercise.Mechanic
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One exercise as written in free-exercise-db (and in our `nippard.json`, which uses the same format). */
@Serializable
internal data class SeedExerciseJson(
    val id: String,
    val name: String,
    val force: String? = null,
    val level: String? = null,
    val mechanic: String? = null,
    val equipment: String? = null,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val instructions: List<String>,
    val category: String,
    val images: List<String>,
)

/** A seed row ready to insert; muscles get the exercise id after the insert. */
class SeedExercise(val entity: ExerciseEntity, private val primary: List<String>, private val secondary: List<String>) {
    fun muscles(exerciseId: Long): List<ExerciseMuscleEntity> =
        primary.map { ExerciseMuscleEntity(exerciseId, it, isPrimary = true) } +
            secondary.map { ExerciseMuscleEntity(exerciseId, it, isPrimary = false) }
}

internal object ExerciseSeedParser {
    private val json = Json { ignoreUnknownKeys = true }

    const val FREE_EXERCISE_DB = "exercises/free-exercise-db.json"
    const val NIPPARD = "exercises/nippard.json"
    const val ALIASES_PL = "exercises/aliases_pl.json"

    /**
     * Fails fast on any value the domain enums do not know, so a bad seed file breaks the
     * seed test instead of reaching users.
     */
    fun parse(freeExerciseDb: String, nippard: String, aliasesPl: String): List<SeedExercise> {
        val aliases = json.decodeFromString<Map<String, List<String>>>(aliasesPl)
        val sources = json.decodeFromString<List<SeedExerciseJson>>(freeExerciseDb).map { it to ExerciseSource.Builtin } +
            json.decodeFromString<List<SeedExerciseJson>>(nippard).map { it to ExerciseSource.Nippard }
        val ids = sources.map { it.first.id }.toSet()
        require(ids.size == sources.size) { "Duplicate exercise ids in the seed files" }
        require(ids.containsAll(aliases.keys)) { "Aliases for unknown ids: ${aliases.keys - ids}" }
        return sources.map { (exercise, source) -> exercise.toSeed(source, aliases[exercise.id].orEmpty()) }
    }

    private fun SeedExerciseJson.toSeed(source: ExerciseSource, aliasesPl: List<String>): SeedExercise {
        // Validate every key against the domain vocabulary
        ExerciseCategory.fromKey(category)
        equipment?.let(Equipment::fromKey)
        level?.let(Level::fromKey)
        mechanic?.let(Mechanic::fromKey)
        (primaryMuscles + secondaryMuscles).forEach(Muscle::fromKey)
        val entity = ExerciseEntity(
            externalId = id,
            name = name,
            aliasesPl = aliasesPl,
            category = category,
            equipment = equipment,
            level = level,
            mechanic = mechanic,
            force = force,
            instructions = instructions,
            images = images,
            source = source.key,
        )
        // A few source exercises list a muscle as both primary and secondary; primary wins
        return SeedExercise(entity, primaryMuscles.distinct(), secondaryMuscles.distinct() - primaryMuscles.toSet())
    }
}
