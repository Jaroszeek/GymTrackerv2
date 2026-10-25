package com.jaroszeek.gymtracker.data.exercise

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import com.jaroszeek.gymtracker.domain.settings.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json

/**
 * Polish instructions of the built-in exercises by their source id (D-102). They live in the assets,
 * not in the database, so installs seeded with the English text show them too, without a migration.
 * The English app keeps the source text (D-106).
 */
@Singleton
class ExerciseTranslations internal constructor(
    /** Whether the app is in Polish, now and after every change of its language. */
    val polish: Flow<Boolean> = flowOf(true),
    load: () -> Map<String, List<String>>,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        polishOf(context),
        { context.assets.open(INSTRUCTIONS_PL).bufferedReader().use { Json.decodeFromString<Map<String, List<String>>>(it.readText()) } },
    )

    private val instructions by lazy(load)

    /** Off the main thread: the first call reads the file. Null for custom exercises and missing ids. */
    fun instructions(externalId: String?): List<String>? = externalId?.let { instructions[it] }

    private companion object {
        const val INSTRUCTIONS_PL = "exercises/instructions_pl.json"

        fun Configuration.isPolish() = locales[0].language == AppLanguage.Polish.tag

        // An open screen keeps its view model when the language changes, so its exercise follows the change
        fun polishOf(context: Context): Flow<Boolean> = callbackFlow {
            val callbacks = object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    trySend(newConfig.isPolish())
                }

                @Deprecated("Deprecated in Java")
                override fun onLowMemory() = Unit
            }
            context.registerComponentCallbacks(callbacks)
            send(context.resources.configuration.isPolish())
            awaitClose { context.unregisterComponentCallbacks(callbacks) }
        }.distinctUntilChanged()
    }
}
