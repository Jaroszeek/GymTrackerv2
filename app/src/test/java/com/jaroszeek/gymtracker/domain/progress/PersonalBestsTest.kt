package com.jaroszeek.gymtracker.domain.progress

import com.jaroszeek.gymtracker.domain.progress.RecordType.E1rm
import com.jaroszeek.gymtracker.domain.progress.RecordType.Reps
import com.jaroszeek.gymtracker.domain.progress.RecordType.SessionVolume
import com.jaroszeek.gymtracker.domain.progress.RecordType.SetVolume
import com.jaroszeek.gymtracker.domain.progress.RecordType.Weight
import com.jaroszeek.gymtracker.domain.routine.PerformedSet
import com.jaroszeek.gymtracker.domain.routine.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalBestsTest {

    private fun set(weight: Double?, reps: Int, type: SetType = SetType.Working) = PerformedSet(weight, reps, type)

    /** Bests after the given sets, each set a session of its own. */
    private fun bestsOf(vararg sets: PerformedSet) = sets.fold(PersonalBests()) { bests, set ->
        bests.plus(set).copy(sessionVolumeKg = maxOf(bests.sessionVolumeKg ?: 0.0, set.volumeKg).takeIf { it > 0 })
    }

    @Test
    fun theFirstSessionOnlySetsTheBaseline() {
        assertEquals(List(3) { emptySet<RecordType>() }, PersonalBests().sessionRecords(listOf(set(60.0, 10), set(70.0, 8), set(80.0, 6))))
    }

    @Test
    fun aHeavierWeightIsARecordButAnEqualOneIsNot() {
        val bests = bestsOf(set(100.0, 5))
        assertEquals(setOf(Weight), bests.recordsOf(set(102.5, 1)))
        assertEquals(emptySet<RecordType>(), bests.recordsOf(set(100.0, 5)))
    }

    @Test
    fun moreRepsWithALighterWeightBeatTheE1rmVolumeAndReps() {
        // 100 × 5 = e1RM 116.7, volume 500; 95 × 8 = e1RM 120.3, volume 760
        assertEquals(setOf(E1rm, SetVolume, Reps), bestsOf(set(100.0, 5)).recordsOf(set(95.0, 8)))
    }

    @Test
    fun repsCountOnlyAgainstSetsWithAtLeastTheSameWeight() {
        val bests = bestsOf(set(100.0, 5), set(90.0, 8))
        assertEquals(emptySet<RecordType>(), bests.recordsOf(set(90.0, 8)))
        assertEquals(setOf(Reps), bests.recordsOf(set(90.0, 9)) - setOf(E1rm, SetVolume))
        // 80 × 8 is not more than 90 × 8
        assertEquals(emptySet<RecordType>(), bests.recordsOf(set(80.0, 8)))
        // Nothing was as heavy: a weight record, not a reps record
        assertEquals(setOf(Weight), bests.recordsOf(set(105.0, 3)))
        assertEquals(8, bests.mostReps(85.0))
        assertNull(bests.mostReps(110.0))
    }

    @Test
    fun warmupsNeitherSetNorChangeRecords() {
        val bests = bestsOf(set(100.0, 5))
        assertEquals(emptySet<RecordType>(), bests.recordsOf(set(140.0, 5, SetType.Warmup)))
        assertEquals(bests, bests.plus(set(140.0, 5, SetType.Warmup)))
    }

    @Test
    fun dropSetsAndSetsToFailureCount() {
        val bests = bestsOf(set(100.0, 5))
        assertEquals(setOf(Weight), bests.recordsOf(set(105.0, 1, SetType.Failure)))
        assertEquals(setOf(Reps), bests.recordsOf(set(100.0, 6, SetType.DropSet)) - setOf(E1rm, SetVolume))
    }

    @Test
    fun bodyweightOnlyBeatsReps() {
        val bests = bestsOf(set(null, 12))
        assertEquals(setOf(Reps), bests.recordsOf(set(null, 13)))
        // A weight of 0 is bodyweight too
        assertEquals(setOf(Reps), bests.recordsOf(set(0.0, 13)))
        assertEquals(emptySet<RecordType>(), bests.recordsOf(set(null, 12)))
        // The first set with added weight has nothing to beat
        assertEquals(emptySet<RecordType>(), bests.recordsOf(set(10.0, 8)))
    }

    @Test
    fun overTwelveRepsThereIsNoE1rmRecord() {
        // 60 × 20 would be an Epley of 100, above 80 × 5 (93.3), but over 12 reps there is no estimate
        val bests = bestsOf(set(80.0, 5))
        assertEquals(setOf(SetVolume, Reps), bests.recordsOf(set(60.0, 20)))
    }

    @Test
    fun setsInTheSessionRaiseTheBar() {
        val records = bestsOf(set(100.0, 5)).sessionRecords(listOf(set(102.5, 5), set(102.5, 5), set(105.0, 3)))
        assertTrue(Weight in records[0])
        assertEquals(emptySet<RecordType>(), records[1])
        assertEquals(setOf(Weight), records[2])
    }

    @Test
    fun theSessionVolumeRecordGoesToTheSetThatPassesTheOldBest() {
        // Old best session: 1 200 kg; the third set takes this one from 800 to 1 280 kg
        val bests = PersonalBests(weightKg = 100.0, e1rmKg = 116.7, setVolumeKg = 500.0, sessionVolumeKg = 1_200.0, reps = listOf(RepBest(100.0, 5)))
        val records = bests.sessionRecords(listOf(set(80.0, 5), set(80.0, 5), set(80.0, 6), set(80.0, 1), set(80.0, 5)))
        assertEquals(listOf(false, false, true, false, false), records.map { SessionVolume in it })
    }

    @Test
    fun equallingTheSessionVolumeIsNoRecord() {
        val bests = PersonalBests(weightKg = 100.0, setVolumeKg = 500.0, sessionVolumeKg = 1_000.0, reps = listOf(RepBest(100.0, 5)))
        assertEquals(listOf(emptySet<RecordType>(), emptySet()), bests.sessionRecords(listOf(set(100.0, 5), set(100.0, 5))))
    }

    @Test
    fun warmupsDoNotAddToTheSessionVolume() {
        val bests = PersonalBests(weightKg = 100.0, setVolumeKg = 500.0, sessionVolumeKg = 600.0, reps = listOf(RepBest(100.0, 5)))
        val records = bests.sessionRecords(listOf(set(100.0, 5, SetType.Warmup), set(50.0, 2)))
        assertEquals(listOf(emptySet<RecordType>(), emptySet()), records)
    }

    @Test
    fun plusKeepsTheMostRepsPerWeight() {
        val bests = PersonalBests().plus(set(100.0, 5)).plus(set(100.0, 3)).plus(set(100.0, 6)).plus(set(null, 10)).plus(set(0.0, 12))
        assertEquals(listOf(RepBest(100.0, 6), RepBest(null, 12)), bests.reps)
        assertEquals(100.0, bests.weightKg!!, 0.0)
        assertEquals(600.0, bests.setVolumeKg!!, 0.0)
        assertEquals(120.0, bests.e1rmKg!!, 0.001)
    }

    @Test
    fun theFrontierLeavesOutWeightsBeatenByHeavierOnes() {
        val bests = listOf(
            RepBest(80.0, 8), RepBest(100.0, 5), RepBest(90.0, 8), RepBest(70.0, 12), RepBest(90.0, 6),
            RepBest(null, 10), RepBest(null, 15), RepBest(60.0, 12),
        )
        assertEquals(listOf(RepBest(100.0, 5), RepBest(90.0, 8), RepBest(70.0, 12), RepBest(null, 15)), bests.frontier())
        assertEquals(emptyList<RepBest>(), emptyList<RepBest>().frontier())
    }
}
