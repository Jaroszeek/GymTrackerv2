package com.jaroszeek.gymtracker.domain.progress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseProgressTest {

    private fun session(time: Long, maxWeight: Double?, setVolume: Double?, volume: Double, maxReps: Int = 5) =
        ExerciseSession(time, time, maxWeight, maxWeight?.let { OneRepMax.epley(it, maxReps) }, setVolume, volume, maxReps)

    @Test
    fun noHistoryHasNoRecords() {
        val progress = ExerciseProgress(emptyList(), emptyList())
        val records = progress.records()
        assertTrue(records.isEmpty)
        assertNull(records.e1rm)
        assertNull(records.e1rmFrom)
        assertNull(records.sessionVolume)
        assertEquals(PersonalBests(), progress.bestsBefore(Long.MAX_VALUE))
    }

    @Test
    fun anEqualResultLaterKeepsTheDateItWasFirstReached() {
        val progress = ExerciseProgress(
            sessions = listOf(session(1, 100.0, 500.0, 1_500.0), session(2, 100.0, 500.0, 1_500.0)),
            repsDone = listOf(RepsDone(RepBest(100.0, 5), 2), RepsDone(RepBest(100.0, 5), 1)),
        )
        val records = progress.records()
        assertEquals(Dated(100.0, 1L), records.weight)
        assertEquals(Dated(OneRepMax.epley(100.0, 5)!!, 1L), records.e1rm)
        assertEquals(Dated(500.0, 1L), records.setVolume)
        assertEquals(Dated(1_500.0, 1L), records.sessionVolume)
        assertEquals(listOf(Dated(RepBest(100.0, 5), 1L)), records.reps)
    }

    @Test
    fun theBestE1rmComesFromTheSetWithTheHighestEstimate() {
        // 90 × 9 estimates 117 kg, more than the heavier 100 × 5 (116,7 kg); over 12 reps counts for reps only
        val progress = ExerciseProgress(
            sessions = listOf(session(1, 100.0, 810.0, 1_310.0)),
            repsDone = listOf(RepsDone(RepBest(100.0, 5), 1), RepsDone(RepBest(90.0, 9), 1), RepsDone(RepBest(60.0, 20), 1)),
        )
        val records = progress.records()
        assertEquals(RepBest(90.0, 9), records.e1rmFrom)
        assertEquals(OneRepMax.epley(90.0, 9)!!, records.e1rm!!.value, 1e-9)
        assertEquals(listOf(RepBest(100.0, 5), RepBest(90.0, 9), RepBest(60.0, 20)), records.reps.map { it.value })
    }

    @Test
    fun bodyweightWorkHasRepsRecordsOnly() {
        val progress = ExerciseProgress(
            sessions = listOf(session(1, null, null, 0.0, maxReps = 12), session(2, null, null, 0.0, maxReps = 15)),
            repsDone = listOf(RepsDone(RepBest(null, 12), 1), RepsDone(RepBest(null, 15), 2)),
        )
        val records = progress.records()
        assertFalse(records.isEmpty)
        assertNull(records.weight)
        assertNull(records.e1rm)
        assertNull(records.setVolume)
        assertNull(records.sessionVolume)
        assertEquals(listOf(Dated(RepBest(null, 15), 2L)), records.reps)
    }

    @Test
    fun bestsBeforeASessionLeaveItAndLaterOnesOut() {
        val progress = ExerciseProgress(
            sessions = listOf(session(1, 100.0, 500.0, 1_500.0), session(2, 110.0, 550.0, 1_650.0)),
            repsDone = listOf(RepsDone(RepBest(100.0, 5), 1), RepsDone(RepBest(100.0, 8), 2), RepsDone(RepBest(110.0, 5), 2)),
        )
        val before = progress.bestsBefore(2)
        assertEquals(PersonalBests(100.0, OneRepMax.epley(100.0, 5), 500.0, 1_500.0, listOf(RepBest(100.0, 5))), before)
        assertEquals(listOf(RepBest(100.0, 8), RepBest(110.0, 5)), progress.bestsBefore(Long.MAX_VALUE).reps)
    }
}
