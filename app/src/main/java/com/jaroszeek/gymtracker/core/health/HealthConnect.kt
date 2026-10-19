package com.jaroszeek.gymtracker.core.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

enum class HealthAvailability { Available, UpdateRequired, Unavailable }

/**
 * What the app does with Health Connect, each asked for on its own with an explanation (A6), so
 * declining one leaves the others. [History] and [Background] widen the reads of the others.
 */
enum class HealthFeature(val permissions: Set<String>) {
    Workouts(reads(ExerciseSessionRecord::class, HeartRateRecord::class, ActiveCaloriesBurnedRecord::class, TotalCaloriesBurnedRecord::class)),
    Body(reads(WeightRecord::class, HeightRecord::class, BodyFatRecord::class, LeanBodyMassRecord::class, BoneMassRecord::class, BodyWaterMassRecord::class)),
    Activity(reads(StepsRecord::class, DistanceRecord::class, ActiveCaloriesBurnedRecord::class, TotalCaloriesBurnedRecord::class)),
    Recovery(reads(SleepSessionRecord::class, RestingHeartRateRecord::class, HeartRateVariabilityRmssdRecord::class)),
    History(setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)),
    Background(setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)),
    WriteWorkouts(setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class))),
}

private fun reads(vararg types: KClass<out Record>) = types.mapTo(mutableSetOf()) { HealthPermission.getReadPermission(it) }

/** Entry to Health Connect; the app works without it, so every use checks [client] first (A6). */
@Singleton
class HealthConnect @Inject constructor(@ApplicationContext private val context: Context) {

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.Available
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UpdateRequired
        else -> HealthAvailability.Unavailable
    }

    /** Null while Health Connect is missing or needs an update. */
    fun client(): HealthConnectClient? = if (availability() == HealthAvailability.Available) HealthConnectClient.getOrCreate(context) else null

    /** Empty without Health Connect. Throws when its service fails, like every other call. */
    suspend fun granted(): Set<String> = client()?.permissionController?.getGrantedPermissions().orEmpty()

    /** History and background reads need a newer Health Connect than the rest. */
    fun supports(feature: HealthFeature): Boolean {
        val flag = when (feature) {
            HealthFeature.History -> HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY
            HealthFeature.Background -> HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
            else -> return client() != null
        }
        return client()?.features?.getFeatureStatus(flag) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }

    companion object {
        val permissionContract = PermissionController.createRequestPermissionResultContract()
    }
}

/** Health Connect's own screen with the data and the permissions of this app. */
fun Context.openHealthConnect() {
    val intent = HealthConnectClient.getHealthConnectManageDataIntent(this)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("HealthConnect", "No Health Connect screen for ${intent.action}", e)
    }
}
