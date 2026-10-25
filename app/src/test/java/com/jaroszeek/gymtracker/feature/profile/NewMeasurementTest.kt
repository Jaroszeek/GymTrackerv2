package com.jaroszeek.gymtracker.feature.profile

import com.jaroszeek.gymtracker.domain.body.BodyMetric
import com.jaroszeek.gymtracker.domain.settings.WeightUnit
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class NewMeasurementTest {

    @Test
    fun aDraftSurvivesSerializationWithItsPickerClosed() {
        val draft = NewMeasurement(BodyMetric.SkeletalMuscle, 77.5, invalid = true, unit = WeightUnit.Lb, time = 1_760_000_000_000, timeMax = 1_760_000_100_000)
        assertEquals(draft.copy(timeMax = null), Json.decodeFromString<NewMeasurement>(Json.encodeToString(draft)))
        assertEquals(NewMeasurement(), Json.decodeFromString<NewMeasurement>(Json.encodeToString(NewMeasurement())))
    }
}
