package com.jaroszeek.gymtracker.data.exercise

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Polish instructions of the built-in exercises by their source id (D-102). They live in the assets,
 * not in the database, so installs seeded with the English text show them too, without a migration.
 */
@Singleton
class ExerciseTranslations internal constructor(load: () -> Map<String, List<String>>) {
    @Inject
    constructor(@ApplicationContext context: Context) : this({
        context.assets.open(INSTRUCTIONS_PL).bufferedReader().use { Json.decodeFromString<Map<String, List<String>>>(it.readText()) }
    })

    private val instructions by lazy(load)

    /** Off the main thread: the first call reads the file. Null for custom exercises and missing ids. */
    fun instructions(externalId: String?): List<String>? = externalId?.let { instructions[it] }

    private companion object {
        const val INSTRUCTIONS_PL = "exercises/instructions_pl.json"
    }
}
