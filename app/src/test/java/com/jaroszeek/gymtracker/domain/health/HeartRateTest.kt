package com.jaroszeek.gymtracker.domain.health

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateTest {

    /** Samples as (second, bpm). */
    private fun samples(vararg points: Pair<Int, Int>) = points.map { (second, bpm) -> HeartRateSample(second * 1_000L, bpm) }

    @Test
    fun theAverageIsWeightedByTheTimeUntilTheNextSample() {
        // 10 s at 100 and 30 s at 160: 145, where the plain mean would be 127
        val summary = summarize(samples(0 to 100, 10 to 160, 40 to 120), maxHeartRate = null)!!
        assertEquals(145, summary.averageBpm)
        assertEquals(160, summary.maxBpm)
    }

    @Test
    fun theLastSampleHasNoDuration() {
        assertEquals(100, summarize(samples(0 to 100, 60 to 200), maxHeartRate = null)!!.averageBpm)
    }

    @Test
    fun aGapOverTwoMinutesCountsAsNoTime() {
        // 60 s at 100, 121 s missing after 150, 30 s at 80: (6 000 + 2 400) / 90
        val summary = summarize(samples(0 to 100, 60 to 150, 181 to 80, 211 to 90), maxHeartRate = null)!!
        assertEquals(93, summary.averageBpm)
        assertEquals(150, summary.maxBpm)
        // Exactly two minutes still count
        val atTheLimit = listOf(HeartRateSample(0, 100), HeartRateSample(MAX_SAMPLE_GAP_MILLIS, 200))
        assertEquals(100, summarize(atTheLimit, maxHeartRate = null)!!.averageBpm)
    }

    @Test
    fun withoutAnyTimeTheAverageIsTheMean() {
        assertEquals(130, summarize(samples(5 to 130), maxHeartRate = null)!!.averageBpm)
        val gapOnly = listOf(HeartRateSample(0, 100), HeartRateSample(MAX_SAMPLE_GAP_MILLIS + 1, 200))
        assertEquals(HeartRateSummary(150, 200, listOf(0L, 0, 0, 0, 0)), summarize(gapOnly, maxHeartRate = 200))
    }

    @Test
    fun timeIsSplitIntoTheZonesOfTheMaximum() {
        // With 200 the zones start at 100, 120, 140, 160 and 180 bpm; each sample lasts 10 s but the last
        val bpms = listOf(99, 100, 119, 120, 140, 160, 179, 180, 210, 150)
        val summary = summarize(bpms.mapIndexed { index, bpm -> HeartRateSample(index * 10_000L, bpm) }, maxHeartRate = 200)!!
        // Under half of the maximum is in no zone, above it in the last
        assertEquals(listOf(20_000L, 10_000, 10_000, 20_000, 20_000), summary.zoneMillis)
    }

    @Test
    fun withoutAMaximumThereAreNoZones() {
        val samples = samples(0 to 120, 10 to 150)
        assertNull(summarize(samples, maxHeartRate = null)!!.zoneMillis)
        assertNull(summarize(samples, maxHeartRate = 0)!!.zoneMillis)
    }

    @Test
    fun noSamplesNoSummary() {
        assertNull(summarize(emptyList(), maxHeartRate = 190))
    }

    @Test
    fun samplesInAnyOrderAreSortedFirst() {
        val inOrder = samples(0 to 100, 10 to 160, 40 to 120, 45 to 130)
        assertEquals(summarize(inOrder, maxHeartRate = 200), summarize(inOrder.reversed(), maxHeartRate = 200))
        assertEquals(summarize(inOrder, maxHeartRate = 200), summarize(inOrder.shuffled(Random(1)), maxHeartRate = 200))
    }

    @Test
    fun tanakaEstimatesTheMaximum() {
        assertEquals(187, estimatedMaxHeartRate(30))
        // 190.5, 184.9 and 182.1
        assertEquals(191, estimatedMaxHeartRate(25))
        assertEquals(185, estimatedMaxHeartRate(33))
        assertEquals(182, estimatedMaxHeartRate(37))
    }

    @Test
    fun downsampleKeepsTheFirstSampleThenOneEveryFiveSecondsInTimeOrder() {
        val samples = listOf(30_000L, 7_000, 0, 4_999, 1_000, 10_500, 5_000, 11_000).map { HeartRateSample(it, 120) }
        assertEquals(listOf(0L, 5_000, 10_500, 30_000), samples.downsample { it.time }.map { it.time })
        assertEquals(listOf(0L, 10_500, 30_000), samples.downsample(stepMillis = 10_000) { it.time }.map { it.time })
        assertEquals(emptyList<HeartRateSample>(), emptyList<HeartRateSample>().downsample { it.time })
    }
}
