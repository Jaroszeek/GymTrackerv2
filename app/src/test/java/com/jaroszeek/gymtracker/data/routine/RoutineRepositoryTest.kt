package com.jaroszeek.gymtracker.data.routine

import com.jaroszeek.gymtracker.data.exercise.DeleteResult
import com.jaroszeek.gymtracker.data.exercise.ExerciseRepository
import com.jaroszeek.gymtracker.domain.exercise.Exercise
import com.jaroszeek.gymtracker.domain.exercise.ExerciseDraft
import com.jaroszeek.gymtracker.domain.exercise.Muscle
import com.jaroszeek.gymtracker.domain.routine.RoutineDraft
import com.jaroszeek.gymtracker.domain.routine.RoutineTargets
import com.jaroszeek.gymtracker.domain.routine.toDraft
import com.jaroszeek.gymtracker.testing.asset
import com.jaroszeek.gymtracker.testing.inMemoryDatabase
import com.jaroszeek.gymtracker.testing.seeder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineRepositoryTest {

    private val database = inMemoryDatabase()
    private val exercises = ExerciseRepository(database, seeder(database), Dispatchers.IO)
    private val clock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneOffset.UTC)
    private val routines = RoutineRepository(database, exercises, clock, Dispatchers.IO)

    @After
    fun close() = database.close()

    private suspend fun exercise(name: String): Exercise = exercises.observeAll().first().single { it.name == name }

    @Test
    fun savedRoutineComesBackWithBlocksVariantsAndTargets() = runTest {
        val squat = exercise("Barbell Squat")
        val hack = exercise("Hack Squat")
        val rdl = exercise("Romanian Deadlift")
        var draft = RoutineDraft(name = " Legs ").addBlock(squat).addBlock(rdl)
        val block = draft.blocks.first()
        draft = draft.addVariant(block.id, hack)
            .updateTargets(block.id, block.variants.single().id, RoutineTargets(4, 6, 8, 100.0, 180))

        val routine = routines.observe(routines.save(null, draft)).first()!!

        assertEquals("Legs", routine.name)
        assertEquals(listOf(listOf(squat, hack), listOf(rdl)), routine.blocks.map { b -> b.variants.map { it.exercise } })
        assertEquals(RoutineTargets(4, 6, 8, 100.0, 180), routine.blocks.first().variants.first().targets)
        assertEquals(clock.instant(), routine.createdAt)
        assertEquals(4 + RoutineTargets.DEFAULT_SETS, routine.totalSets)
    }

    @Test
    fun editingKeepsTheIdsOfSavedBlocksAndVariants() = runTest {
        val bench = exercise("Barbell Bench Press - Medium Grip")
        val incline = exercise("Incline Dumbbell Press")
        val fly = exercise("Butterfly")
        val id = routines.save(null, RoutineDraft(name = "Push").addBlock(bench).addBlock(incline).addBlock(fly))
        val saved = routines.observe(id).first()!!

        // Rename, drop the middle block, move the fly first, add a variant and a new block
        var draft = saved.toDraft().rename("Push A").removeVariant(saved.blocks[1].id, saved.blocks[1].variants.single().id).moveBlock(1, 0)
        draft = draft.addVariant(saved.blocks[0].id, incline).addBlock(exercise("Triceps Pushdown"))
        routines.save(id, draft)
        val edited = routines.observe(id).first()!!

        assertEquals("Push A", edited.name)
        assertEquals(listOf(saved.blocks[2].id, saved.blocks[0].id), edited.blocks.take(2).map { it.id })
        assertEquals(saved.blocks[0].variants.single().id, edited.blocks[1].variants.first().id)
        assertEquals(listOf(bench, incline), edited.blocks[1].variants.map { it.exercise })
        assertEquals(3, edited.blocks.size)
        assertTrue(database.routineDao().blockIds(id).none { it == saved.blocks[1].id })
    }

    @Test
    fun duplicateCopiesEverythingUnderNewIds() = runTest {
        val id = routines.save(null, RoutineDraft(name = "Pull").addBlock(exercise("Pullups")).addBlock(exercise("Face Pull")))
        val original = routines.observe(id).first()!!
        val copy = routines.observe(routines.duplicate(id, "Pull (kopia)")).first()!!

        assertEquals("Pull (kopia)", copy.name)
        assertEquals(original.blocks.map { b -> b.variants.map { it.exercise to it.targets } }, copy.blocks.map { b -> b.variants.map { it.exercise to it.targets } })
        assertNotEquals(original.blocks.map { it.id }, copy.blocks.map { it.id })
    }

    @Test
    fun deletingARoutineRemovesItsBlocks() = runTest {
        val id = routines.save(null, RoutineDraft(name = "Legs").addBlock(exercise("Leg Press")))
        routines.delete(id)
        assertNull(routines.observe(id).first())
        assertTrue(database.routineDao().blockIds(id).isEmpty())
    }

    @Test
    fun exerciseUsedInARoutineCannotBeDeleted() = runTest {
        val customId = exercises.saveCustom(null, ExerciseDraft(name = "Landmine Press", primaryMuscles = setOf(Muscle.Shoulders)))
        val custom = exercises.observe(customId).first()!!
        val routineId = routines.save(null, RoutineDraft(name = "Shoulders").addBlock(custom))

        assertEquals(DeleteResult.InUse, exercises.deleteCustom(customId))

        routines.delete(routineId)
        assertEquals(DeleteResult.Deleted, exercises.deleteCustom(customId))
    }

    @Test
    fun everyTemplateResolvesAndCopies() = runTest {
        val templates = TemplateRepository(database, exercises, routines, Dispatchers.IO) { asset("templates/templates.json") }.templates()

        assertEquals(listOf("ppl", "upper_lower", "fbw"), templates.map { it.id })
        assertEquals(listOf(3, 2, 2), templates.map { it.routines.size })
        assertTrue(templates.all { template -> template.routines.all { it.isValid } })

        val ids = TemplateRepository(database, exercises, routines, Dispatchers.IO) { asset("templates/templates.json") }.copy(templates.first())
        assertEquals(listOf("Push", "Pull", "Legs"), ids.map { routines.observe(it).first()!!.name })
    }
}
