package com.jaroszeek.gymtracker.feature.health

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import androidx.health.connect.client.records.ExerciseSessionRecord
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.domain.health.HealthSession
import kotlin.math.roundToInt

/** Polish name of a Health Connect exercise type (A5.6). */
@StringRes
fun exerciseTypeLabel(type: Int): Int = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> R.string.exercise_type_badminton
    ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL -> R.string.exercise_type_baseball
    ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> R.string.exercise_type_basketball
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> R.string.exercise_type_biking
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> R.string.exercise_type_biking_stationary
    ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP -> R.string.exercise_type_boot_camp
    ExerciseSessionRecord.EXERCISE_TYPE_BOXING -> R.string.exercise_type_boxing
    ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> R.string.exercise_type_calisthenics
    ExerciseSessionRecord.EXERCISE_TYPE_CRICKET -> R.string.exercise_type_cricket
    ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> R.string.exercise_type_dancing
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> R.string.exercise_type_elliptical
    ExerciseSessionRecord.EXERCISE_TYPE_EXERCISE_CLASS -> R.string.exercise_type_exercise_class
    ExerciseSessionRecord.EXERCISE_TYPE_FENCING -> R.string.exercise_type_fencing
    ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN -> R.string.exercise_type_football_american
    ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN -> R.string.exercise_type_football_australian
    ExerciseSessionRecord.EXERCISE_TYPE_FRISBEE_DISC -> R.string.exercise_type_frisbee_disc
    ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> R.string.exercise_type_golf
    ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING -> R.string.exercise_type_guided_breathing
    ExerciseSessionRecord.EXERCISE_TYPE_GYMNASTICS -> R.string.exercise_type_gymnastics
    ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL -> R.string.exercise_type_handball
    ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> R.string.exercise_type_hiit
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> R.string.exercise_type_hiking
    ExerciseSessionRecord.EXERCISE_TYPE_ICE_HOCKEY -> R.string.exercise_type_ice_hockey
    ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING -> R.string.exercise_type_ice_skating
    ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> R.string.exercise_type_martial_arts
    ExerciseSessionRecord.EXERCISE_TYPE_PADDLING -> R.string.exercise_type_paddling
    ExerciseSessionRecord.EXERCISE_TYPE_PARAGLIDING -> R.string.exercise_type_paragliding
    ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> R.string.exercise_type_pilates
    ExerciseSessionRecord.EXERCISE_TYPE_RACQUETBALL -> R.string.exercise_type_racquetball
    ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING -> R.string.exercise_type_rock_climbing
    ExerciseSessionRecord.EXERCISE_TYPE_ROLLER_HOCKEY -> R.string.exercise_type_roller_hockey
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> R.string.exercise_type_rowing
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> R.string.exercise_type_rowing_machine
    ExerciseSessionRecord.EXERCISE_TYPE_RUGBY -> R.string.exercise_type_rugby
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> R.string.exercise_type_running
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> R.string.exercise_type_running_treadmill
    ExerciseSessionRecord.EXERCISE_TYPE_SAILING -> R.string.exercise_type_sailing
    ExerciseSessionRecord.EXERCISE_TYPE_SCUBA_DIVING -> R.string.exercise_type_scuba_diving
    ExerciseSessionRecord.EXERCISE_TYPE_SKATING -> R.string.exercise_type_skating
    ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> R.string.exercise_type_skiing
    ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING -> R.string.exercise_type_snowboarding
    ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING -> R.string.exercise_type_snowshoeing
    ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> R.string.exercise_type_soccer
    ExerciseSessionRecord.EXERCISE_TYPE_SOFTBALL -> R.string.exercise_type_softball
    ExerciseSessionRecord.EXERCISE_TYPE_SQUASH -> R.string.exercise_type_squash
    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> R.string.exercise_type_stair_climbing
    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> R.string.exercise_type_stair_climbing_machine
    ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> R.string.exercise_type_strength_training
    ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> R.string.exercise_type_stretching
    ExerciseSessionRecord.EXERCISE_TYPE_SURFING -> R.string.exercise_type_surfing
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> R.string.exercise_type_swimming_open_water
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> R.string.exercise_type_swimming_pool
    ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS -> R.string.exercise_type_table_tennis
    ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> R.string.exercise_type_tennis
    ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL -> R.string.exercise_type_volleyball
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> R.string.exercise_type_walking
    ExerciseSessionRecord.EXERCISE_TYPE_WATER_POLO -> R.string.exercise_type_water_polo
    ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> R.string.exercise_type_weightlifting
    ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR -> R.string.exercise_type_wheelchair
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> R.string.exercise_type_yoga
    else -> R.string.exercise_type_other
}

// Apps known to write to Health Connect; package visibility hides other apps' labels
private val SOURCES = mapOf(
    "com.sec.android.app.shealth" to R.string.source_samsung_health,
    "com.google.android.apps.fitness" to R.string.source_google_fit,
    // Entered by hand in Health Connect
    "com.google.android.apps.healthdata" to R.string.source_health_connect,
    "com.android.healthconnect.controller" to R.string.source_health_connect,
    "com.google.android.healthconnect.controller" to R.string.source_health_connect,
    "com.fitbit.FitbitMobile" to R.string.source_fitbit,
    "com.garmin.android.apps.connectmobile" to R.string.source_garmin,
    "com.strava" to R.string.source_strava,
    "fi.polar.polarflow" to R.string.source_polar,
    "com.withings.wiscale2" to R.string.source_withings,
    "com.huawei.health" to R.string.source_huawei,
    "com.mi.health" to R.string.source_mi_fitness,
    "com.xiaomi.wearable" to R.string.source_mi_fitness,
    "com.huami.watch.hmwatchmanager" to R.string.source_zepp,
    "com.ouraring.oura" to R.string.source_oura,
)

/** The app that wrote the data; its package when unknown. */
@Composable
@ReadOnlyComposable
fun sourceLabel(origin: String): String = SOURCES[origin]?.let { stringResource(it) } ?: origin

/** The type's name; the app's own title under it when it says more. */
@Composable
@ReadOnlyComposable
fun HealthSession.label(): String = stringResource(exerciseTypeLabel(exerciseType))

@Composable
@ReadOnlyComposable
fun HealthSession.subtitle(): String? = title?.trim()?.takeIf { it.isNotEmpty() && !it.equals(label(), ignoreCase = true) }

@Composable
@ReadOnlyComposable
fun kcalLabel(kcal: Double): String = stringResource(R.string.value_kcal, kcal.roundToInt())

@Composable
@ReadOnlyComposable
fun bpmLabel(bpm: Int): String = stringResource(R.string.value_bpm, bpm)
