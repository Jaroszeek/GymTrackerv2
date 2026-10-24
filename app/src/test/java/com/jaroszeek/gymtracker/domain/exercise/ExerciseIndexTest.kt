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
    fun commonExercisesComeFirstWithinATier() {
        val benchDips = exercise("Bench Dips", primary = setOf(Muscle.Triceps), equipment = Equipment.BodyOnly)
        val withCommon = ExerciseIndex(listOf(bench, dumbbellBench, benchDips), common = setOf(bench.id))
        assertEquals(listOf(bench, benchDips, dumbbellBench), withCommon.search(ExerciseFilter("bench")))
    }

    @Test
    fun exactNameThenWordPrefixesThenTheRest() {
        val exact = exercise("Bench Press")
        // In the rest: "press" only inside a word of the name; "bench" only in the alias
        val inside = exercise("Benchpress Machine")
        val alias = exercise("Smith Machine Press", listOf("bench press na suwnicy"))
        val ranked = ExerciseIndex(listOf(alias, inside, bench, exact))
        assertEquals(listOf(exact, bench, inside, alias), ranked.search(ExerciseFilter("Bench press")))
    }

    @Test
    fun exactNameMatchBeatsCommonExercises() {
        val exact = exercise("Bench Press")
        val withCommon = ExerciseIndex(listOf(bench, dumbbellBench, exact), common = setOf(bench.id, dumbbellBench.id))
        assertEquals(listOf(exact, bench, dumbbellBench), withCommon.search(ExerciseFilter("bench press")))
    }

    @Test
    fun aliasOnlyMatchesComeLastEvenWhenCommon() {
        val custom = exercise("Wyciskanie na skosie")
        val withCommon = ExerciseIndex(listOf(bench, dumbbellBench, custom), common = setOf(bench.id))
        assertEquals(listOf(custom, bench, dumbbellBench), withCommon.search(ExerciseFilter("wyciskanie")))
    }

    @Test
    fun polishDiacriticsRankLikePlainLetters() {
        val dumbbellRow = exercise("Wiosłowanie hantlą", primary = setOf(Muscle.MiddleBack), equipment = Equipment.Dumbbell)
        val barbellRow = exercise("Bent Over Barbell Row", listOf("wiosłowanie sztangą"), primary = setOf(Muscle.MiddleBack))
        val rows = ExerciseIndex(listOf(barbellRow, dumbbellRow), common = setOf(barbellRow.id))
        assertEquals(listOf(dumbbellRow, barbellRow), rows.search(ExerciseFilter("wiosłowanie")))
        assertEquals(listOf(dumbbellRow, barbellRow), rows.search(ExerciseFilter("WIOSLOWANIE")))
        assertEquals(listOf(dumbbellRow), rows.search(ExerciseFilter("wioslowanie hantla")))
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

    @Test
    fun orderedMusclesListEveryMuscleOnce() {
        assertEquals(Muscle.entries.toSet(), BodyPart.orderedMuscles.toSet())
        assertEquals(Muscle.entries.size, BodyPart.orderedMuscles.size)
    }
}
