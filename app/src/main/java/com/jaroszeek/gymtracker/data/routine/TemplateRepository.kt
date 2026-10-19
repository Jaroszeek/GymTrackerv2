package com.jaroszeek.gymtracker.data.routine

import android.content.Context
import com.jaroszeek.gymtracker.core.database.GtDatabase
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.routine.RoutineBlock
import com.jaroszeek.gymtracker.domain.routine.RoutineDraft
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.routine.RoutineVariant
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A ready-made program: one or more routines that are copied into the user's routines together. */
data class RoutineTemplate(val id: String, val name: String, val routines: List<RoutineDraft>)

@Singleton
class TemplateRepository internal constructor(
    private val database: GtDatabase,
    private val exercises: ExerciseRepository,
    private val routines: RoutineRepository,
    private val io: CoroutineDispatcher,
    private val readTemplates: () -> String,
) {
    @Inject
    constructor(
        database: GtDatabase,
        exercises: ExerciseRepository,
        routines: RoutineRepository,
        @IoDispatcher io: CoroutineDispatcher,
        @ApplicationContext context: Context,
    ) : this(database, exercises, routines, io, { context.assets.open(ASSET).bufferedReader().use { it.readText() } })

    suspend fun templates(): List<RoutineTemplate> = withContext(io) {
        // Exercises must be seeded before template references can resolve
        val byId = exercises.observeAll().first().associateBy { it.id }
        val byExternalId = database.exerciseDao().externalIds().associate { it.externalId to byId.getValue(it.id) }
        TemplateParser.parse(readTemplates(), byExternalId)
    }

    /** Adds every routine of the template to the user's routines; returns their ids. */
    suspend fun copy(template: RoutineTemplate): List<Long> = template.routines.map { routines.save(null, it) }

    private companion object {
        const val ASSET = "templates/templates.json"
    }
}

internal object TemplateParser {
    @Serializable
    private data class TemplateJson(val id: String, val name: String, val routines: List<RoutineJson>)

    @Serializable
    private data class RoutineJson(val name: String, val blocks: List<BlockJson>)

    @Serializable
    private data class BlockJson(val variants: List<VariantJson>)

    @Serializable
    private data class VariantJson(val exercise: String, val sets: Int, val repsMin: Int, val repsMax: Int, val restSeconds: Int? = null)

    /** Fails on an exercise id that is not in the database, so a broken template breaks the test, not the app. */
    fun parse(json: String, exercises: Map<String, Exercise>): List<RoutineTemplate> =
        Json.decodeFromString<List<TemplateJson>>(json).map { template ->
            var nextId = -1L
            RoutineTemplate(
                id = template.id,
                name = template.name,
                routines = template.routines.map { routine ->
                    RoutineDraft(
                        name = routine.name,
                        blocks = routine.blocks.map { block ->
                            RoutineBlock(
                                id = nextId--,
                                variants = block.variants.map { variant ->
                                    RoutineVariant(
                                        id = nextId--,
                                        exercise = exercises[variant.exercise] ?: error("Unknown exercise ${variant.exercise} in template ${template.id}"),
                                        targets = RoutineTargets(variant.sets, variant.repsMin, variant.repsMax, weightKg = null, variant.restSeconds),
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }
}
