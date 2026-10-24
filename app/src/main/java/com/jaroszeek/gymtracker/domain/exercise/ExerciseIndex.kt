package com.jaroszeek.gymtracker.domain.exercise

import java.text.Normalizer

data class ExerciseFilter(
    val query: String = "",
    val bodyParts: Set<BodyPart> = emptySet(),
    val equipment: Set<EquipmentGroup> = emptySet(),
) {
    val isEmpty: Boolean get() = query.isBlank() && bodyParts.isEmpty() && equipment.isEmpty()
}

/**
 * In-memory search over the whole exercise list (~900 rows, so no FTS or SQL LIKE needed).
 * Every query word must appear in the name or a Polish alias, ignoring case and diacritics.
 * Within a chip group filters are OR, between groups AND.
 * Results come in tiers: the exact name, then names where every query word starts a word, then the
 * rest (a word matched only inside a word or only through an alias). Within a tier the [common]
 * exercises (the templates' and those in the user's routines and history) come first, then names
 * starting with the query, then alphabetical. Without a query the list is alphabetical.
 * Normalized texts are computed once per list, not per keystroke.
 */
class ExerciseIndex(exercises: List<Exercise>, common: Set<Long> = emptySet()) {
    private class Entry(val exercise: Exercise, val name: String, val text: String, val common: Boolean) {
        val words = name.split(' ')
    }

    private val entries = exercises
        .map { Entry(it, normalize(it.name), normalize((listOf(it.name) + it.aliases).joinToString(" ")), it.id in common) }
        .sortedBy { it.name }

    val size: Int get() = entries.size

    fun search(filter: ExerciseFilter): List<Exercise> {
        val query = normalize(filter.query)
        val tokens = query.split(' ').filter { it.isNotEmpty() }
        val muscles = filter.bodyParts.flatMap { it.muscles }.toSet()
        val matches = entries.filter { entry ->
            val exercise = entry.exercise
            (muscles.isEmpty() || exercise.primaryMuscles.any { it in muscles }) &&
                (filter.equipment.isEmpty() || exercise.equipmentGroup in filter.equipment) &&
                tokens.all { it in entry.text }
        }
        if (tokens.isEmpty()) return matches.map { it.exercise }

        fun tier(entry: Entry) = when {
            entry.name == query -> 0
            tokens.all { token -> entry.words.any { it.startsWith(token) } } -> 1
            else -> 2
        }
        // sortedWith is stable, so the alphabetical order holds within equal ranks
        return matches
            .sortedWith(compareBy<Entry>({ tier(it) }, { !it.common }, { !it.name.startsWith(query) }))
            .map { it.exercise }
    }

    companion object {
        /** Lowercase, no diacritics (including "ł", which has no decomposition), words separated by single spaces. */
        fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
                .replace(CombiningMarks, "")
                .replace('ł', 'l')
                .replace(NonAlphanumeric, " ")
                .trim()

        private val CombiningMarks = Regex("\\p{Mn}+")
        private val NonAlphanumeric = Regex("[^a-z0-9]+")
    }
}

val Exercise.equipmentGroup: EquipmentGroup get() = equipment?.group ?: EquipmentGroup.Other
