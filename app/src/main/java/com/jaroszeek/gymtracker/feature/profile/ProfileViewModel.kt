package com.jaroszeek.gymtracker.feature.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.core.health.HealthConnect
import com.jaroszeek.gymtracker.core.health.HealthFeature
import com.jaroszeek.gymtracker.data.body.BodyRepository
import com.jaroszeek.gymtracker.data.health.HealthRepository
import com.jaroszeek.gymtracker.data.health.observeAge
import com.jaroszeek.gymtracker.designsystem.component.ChartPoint
import com.jaroszeek.gymtracker.designsystem.component.ChartRange
import com.jaroszeek.gymtracker.designsystem.component.within
import com.jaroszeek.gymtracker.domain.body.BodyMeasurement
import com.jaroszeek.gymtracker.domain.body.BodyMetric
import com.jaroszeek.gymtracker.domain.body.BodySummary
import com.jaroszeek.gymtracker.domain.body.bmiSeries
import com.jaroszeek.gymtracker.domain.body.bodySummary
import com.jaroszeek.gymtracker.domain.health.Calories
import com.jaroszeek.gymtracker.domain.health.calories
import com.jaroszeek.gymtracker.domain.settings.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.Year
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BodyChart { Weight, Bmi, BodyFat }

/** The sheet for a new value; [invalid] after saving a value outside the metric's range. */
/** [value] as entered: in [unit] for masses, centimetres for the height. */
data class NewMeasurement(
    val metric: BodyMetric = BodyMetric.Weight,
    val value: Double? = null,
    val invalid: Boolean = false,
    val unit: WeightUnit = WeightUnit.Kg,
)

/** The age sheet with the age as entered; saving it empty clears the age. */
data class AgeInput(val value: Int? = null)

data class ProfileUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    /** Oldest first, both sources, without duplicates. */
    val measurements: List<BodyMeasurement> = emptyList(),
    val summary: BodySummary = BodySummary(),
    val age: Int? = null,
    /** Null while Health Connect gives none: the card offers to connect. */
    val calories: Calories? = null,
    val chart: BodyChart = BodyChart.Weight,
    val range: ChartRange = ChartRange.ThreeMonths,
    /** The [chart] within the [range]; [chartEmpty] when the chart has no points at all, e.g. BMI without a height. */
    val chartPoints: List<ChartPoint> = emptyList(),
    val chartEmpty: Boolean = true,
    val adding: NewMeasurement? = null,
    val editingAge: AgeInput? = null,
    val confirmDelete: BodyMeasurement? = null,
    val showAll: Boolean = false,
)

private data class Local(
    val chart: BodyChart = BodyChart.Weight,
    val range: ChartRange = ChartRange.ThreeMonths,
    val adding: NewMeasurement? = null,
    val editingAge: AgeInput? = null,
    val confirmDelete: BodyMeasurement? = null,
    val showAll: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val body: BodyRepository,
    health: HealthRepository,
    private val preferences: AppPreferences,
    private val healthConnect: HealthConnect,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val zone = ZoneId.systemDefault()
    private val today = MutableStateFlow(today())
    private val activityPermitted = MutableStateFlow(false)
    private val local = MutableStateFlow(Local())
    private val unit = preferences.weightUnit.stateIn(viewModelScope, SharingStarted.Eagerly, WeightUnit.Kg)

    /** Null while Health Connect gives none: the card offers to connect. */
    private val todaysCalories = today.flatMapLatest { day ->
        combine(health.observeActivity(day.minusDays(CALORIE_DAYS), day), activityPermitted) { days, permitted ->
            calories(days, day).takeUnless { !permitted && it.isEmpty }
        }
    }

    val state: StateFlow<ProfileUiState> =
        combine(body.observeMeasurements(), body.observeLeanMass(), preferences.observeAge(clock), todaysCalories, local) { measurements, leanMass, age, calories, local ->
            val points = chartPoints(measurements, local.chart)
            ProfileUiState(
                loading = false,
                measurements = measurements,
                summary = bodySummary(measurements, leanMass),
                age = age,
                calories = calories,
                chart = local.chart,
                range = local.range,
                chartPoints = points.within(local.range, clock.instant(), zone),
                chartEmpty = points.isEmpty(),
                adding = local.adding,
                editingAge = local.editingAge,
                confirmDelete = local.confirmDelete,
                showAll = local.showAll,
            )
        }
            .catch { e ->
                Log.e(TAG, "Loading the profile failed", e)
                emit(ProfileUiState(loading = false, error = true))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ProfileUiState())

    /** On every return to the screen: a new day, or permissions changed in Health Connect. */
    fun onRefresh() {
        today.value = today()
        viewModelScope.launch {
            val granted = withContext(io) { healthConnect.grantedOrNone() }
            activityPermitted.value = HealthFeature.Activity.permissions.any { it in granted }
        }
    }

    /** The sheet starts at the current age. */
    fun onAgeEdit() = local.update { it.copy(editingAge = AgeInput(state.value.age)) }

    fun onAgeValue(age: Int?) = local.update { it.copy(editingAge = it.editingAge?.copy(value = age)) }

    fun onAgeDismiss() = local.update { it.copy(editingAge = null) }

    fun onAgeSave() {
        // Only from the open sheet: a second tap while it closes must not clear the age
        val input = local.value.editingAge ?: return
        onAgeDismiss()
        viewModelScope.launch { preferences.setBirthYear(input.value?.let { Year.now(clock.withZone(zone)).value - it }) }
    }

    fun onChart(chart: BodyChart) = local.update { it.copy(chart = chart) }

    fun onRange(range: ChartRange) = local.update { it.copy(range = range) }

    fun onShowAll() = local.update { it.copy(showAll = true) }

    /** [metric] is preselected, e.g. from the tile tapped; the field starts at its latest value. */
    fun onAdd(metric: BodyMetric = BodyMetric.Weight) = local.update { it.copy(adding = NewMeasurement(metric, latestInput(metric, unit.value), unit = unit.value)) }

    fun onAddMetric(metric: BodyMetric) =
        local.update { it.copy(adding = it.adding?.let { adding -> adding.copy(metric = metric, value = latestInput(metric, adding.unit), invalid = false) }) }

    private fun latestInput(metric: BodyMetric, unit: WeightUnit): Double? = state.value.summary[metric]?.let { metric.toInput(it.value, unit) }

    /** [value] as entered, see [NewMeasurement]. */
    fun onAddValue(value: Double?) = local.update { it.copy(adding = it.adding?.copy(value = value, invalid = false)) }

    fun onAddDismiss() = local.update { it.copy(adding = null) }

    fun onAddSave() {
        if (saving?.isActive == true) return
        val adding = local.value.adding ?: return
        val value = adding.value?.let { adding.metric.fromInput(it, adding.unit) } ?: return
        saving = viewModelScope.launch {
            val saved = try {
                body.add(adding.metric, value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Saving a measurement failed", e)
                false
            }
            local.update { if (saved) it.copy(adding = null) else it.copy(adding = it.adding?.copy(invalid = true)) }
        }
    }

    private var saving: Job? = null

    fun onDeleteRequest(measurement: BodyMeasurement) = local.update { it.copy(confirmDelete = measurement) }

    fun onDeleteDismiss() = local.update { it.copy(confirmDelete = null) }

    fun onDeleteConfirm() {
        val id = local.value.confirmDelete?.manualId ?: return
        local.update { it.copy(confirmDelete = null) }
        viewModelScope.launch {
            try {
                body.delete(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Deleting measurement $id failed", e)
            }
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(zone))

    private fun chartPoints(measurements: List<BodyMeasurement>, chart: BodyChart): List<ChartPoint> = when (chart) {
        BodyChart.Weight -> measurements.filter { it.metric == BodyMetric.Weight }.map { ChartPoint(it.time, it.value) }
        BodyChart.Bmi -> measurements.bmiSeries().map { (time, bmi) -> ChartPoint(time, bmi) }
        BodyChart.BodyFat -> measurements.filter { it.metric == BodyMetric.BodyFat }.map { ChartPoint(it.time, it.value) }
    }

    private companion object {
        const val TAG = "Profile"
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val CALORIE_DAYS = 7L
    }
}
