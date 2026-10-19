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
 * Within a chip group filters are OR, between groups AND; names starting with the query come first.
 * Normalized texts are computed once per list, not per keystroke.
 */
class ExerciseIndex(exercises: List<Exercise>) {
    private class Entry(val exercise: Exercise, val name: String, val text: String)

    private val entries = exercises
        .map { Entry(it, normalize(it.name), normalize((listOf(it.name) + it.aliases).joinToString(" "))) }
        .sortedBy { it.name }

    val size: Int get() = entries.size

    fun search(filter: ExerciseFilter): List<Exercise> {
        val tokens = normalize(filter.query).split(' ').filter { it.isNotEmpty() }
        val muscles = filter.bodyParts.flatMap { it.muscles }.toSet()
        val matches = entries.filter { entry ->
            val exercise = entry.exercise
            (muscles.isEmpty() || exercise.primaryMuscles.any { it in muscles }) &&
                (filter.equipment.isEmpty() || exercise.equipmentGroup in filter.equipment) &&
                tokens.all { it in entry.text }
        }
        val first = tokens.firstOrNull()
        // sortedBy is stable, so the alphabetical order holds within both groups
        val ranked = if (first == null) matches else matches.sortedBy { !it.name.startsWith(first) }
        return ranked.map { it.exercise }
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
