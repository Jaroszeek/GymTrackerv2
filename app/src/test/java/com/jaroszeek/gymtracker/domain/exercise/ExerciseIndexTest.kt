package com.jaroszeek.gymtracker.domain.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseIndexTest {

    private var nextId = 1L

    private fun exercise(
        name: String,
        aliases: List<String> = emptyList(),
        primary: Set<Muscle> = setOf(Muscle.Chest),
        equipment: Equipment? = Equipment.Barbell,
    ) = Exercise(
        id = nextId++,
        name = name,
        aliases = aliases,
        category = ExerciseCategory.Strength,
        equipment = equipment,
        level = null,
        mechanic = null,
        primaryMuscles = primary,
        secondaryMuscles = emptySet(),
        instructions = emptyList(),
        images = emptyList(),
        source = ExerciseSource.Builtin,
    )

    private val bench = exercise("Barbell Bench Press - Medium Grip", listOf("wyciskanie sztangi leżąc"))
    private val dumbbellBench = exercise("Dumbbell Bench Press", listOf("wyciskanie hantli leżąc"), equipment = Equipment.Dumbbell)
    private val deadlift = exercise("Barbell Deadlift", listOf("martwy ciąg"), primary = setOf(Muscle.LowerBack, Muscle.Hamstrings))
    private val curl = exercise("EZ-Bar Curl", primary = setOf(Muscle.Biceps), equipment = Equipment.EzBar)
    private val plank = exercise("Plank", listOf("deska"), primary = setOf(Muscle.Abdominals), equipment = null)
    private val index = ExerciseIndex(listOf(deadlift, plank, curl, dumbbellBench, bench))

    private fun search(query: String = "", bodyParts: Set<BodyPart> = emptySet(), equipment: Set<EquipmentGroup> = emptySet()) =
        index.search(ExerciseFilter(query, bodyParts, equipment))

    @Test
    fun normalizeDropsCaseDiacriticsAndPunctuation() {
        assertEquals("zazolc gesla jazn", ExerciseIndex.normalize("Zażółć  gęślą-jaźń!"))
        assertEquals("martwy ciag", ExerciseIndex.normalize("MARTWY CIĄG"))
        assertEquals("lydki", ExerciseIndex.normalize("Łydki"))
    }

    @Test
    fun emptyFilterReturnsEverythingAlphabetically() {
        assertEquals(listOf(bench, deadlift, dumbbellBench, curl, plank), search())
    }

    @Test
    fun findsByPolishAliasWithoutDiacritics() {
        assertEquals(listOf(deadlift), search("martwy ciag"))
        assertEquals(listOf(plank), search("DESKA"))
    }

    @Test
    fun everyWordMustMatchInAnyOrder() {
        assertEquals(listOf(dumbbellBench), search("lezac hantli"))
        assertEquals(listOf(bench, dumbbellBench), search("wyciskanie"))
        assertTrue(search("wyciskanie nogami").isEmpty())
    }

    @Test
    fun namesStartingWithQueryComeFirst() {
        val benchDips = exercise("Bench Dips", primary = setOf(Muscle.Triceps), equipment = Equipment.BodyOnly)
        val withDips = ExerciseIndex(listOf(bench, dumbbellBench, benchDips))
        assertEquals(listOf(benchDips, bench, dumbbellBench), withDips.search(ExerciseFilter("bench")))
        // No name starts with "press", so the order stays alphabetical
        assertEquals(listOf(bench, dumbbellBench), withDips.search(ExerciseFilter("press")))
    }

    @Test
    fun bodyPartMatchesPrimaryMusclesOnly() {
        assertEquals(listOf(deadlift), search(bodyParts = setOf(BodyPart.Back)))
        assertEquals(listOf(deadlift), search(bodyParts = setOf(BodyPart.Legs)))
        assertEquals(listOf(curl, plank), search(bodyParts = setOf(BodyPart.Arms, BodyPart.Abs)))
    }

    @Test
    fun equipmentGroupsIncludeEzBarAndMissingEquipment() {
        assertEquals(listOf(bench, deadlift, curl), search(equipment = setOf(EquipmentGroup.Barbell)))
        assertEquals(listOf(plank), search(equipment = setOf(EquipmentGroup.Other)))
    }

    @Test
    fun filtersCombineWithAnd() {
        assertEquals(listOf(bench), search("wyciskanie", bodyParts = setOf(BodyPart.Chest), equipment = setOf(EquipmentGroup.Barbell)))
        assertTrue(search("deska", equipment = setOf(EquipmentGroup.Barbell)).isEmpty())
    }
}
