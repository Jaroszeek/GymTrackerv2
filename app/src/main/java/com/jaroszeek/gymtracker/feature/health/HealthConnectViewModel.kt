package com.jaroszeek.gymtracker.feature.health

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.core.health.HealthAvailability
import com.jaroszeek.gymtracker.core.health.HealthConnect
import com.jaroszeek.gymtracker.core.health.HealthFeature
import com.jaroszeek.gymtracker.data.health.HealthRepository
import com.jaroszeek.gymtracker.data.health.HealthSync
import com.jaroszeek.gymtracker.data.health.SyncStatus
import com.jaroszeek.gymtracker.data.health.observeAge
import com.jaroszeek.gymtracker.domain.health.ImportSummary
import com.jaroszeek.gymtracker.domain.health.resolveMaxHeartRate
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Year
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What Health Connect itself reports: availability, permissions and the optional features. */
data class HealthAccess(
    val availability: HealthAvailability = HealthAvailability.Available,
    val granted: Set<String> = emptySet(),
    val historySupported: Boolean = false,
    val backgroundSupported: Boolean = false,
    /** Reading the permissions failed. */
    val error: Boolean = false,
) {
    fun connected(feature: HealthFeature): Boolean = granted.containsAll(feature.permissions)

    fun partly(feature: HealthFeature): Boolean = !connected(feature) && feature.permissions.any { it in granted }

    /** At least one kind of data can be read. */
    val reads: Boolean get() = DATA_FEATURES.any { feature -> feature.permissions.any { it in granted } }
}

/** The features that read data; the others widen or add to them. */
val DATA_FEATURES = listOf(HealthFeature.Workouts, HealthFeature.Body, HealthFeature.Activity, HealthFeature.Recovery)

data class HealthConnectUiState(
    val loading: Boolean = true,
    val access: HealthAccess = HealthAccess(),
    val status: SyncStatus = SyncStatus(),
    val lastSync: Long? = null,
    val syncOnOpen: Boolean = true,
    val backgroundSync: Boolean = false,
    val writeWorkouts: Boolean = false,
    val summary: ImportSummary = ImportSummary(),
    val age: Int? = null,
    /** Entered by hand; null = estimated from the age. */
    val manualMaxHeartRate: Int? = null,
    /** Permissions to ask for now; the screen launches the request and reports back. */
    val request: HealthFeature? = null,
    /** Asked for and still missing: after two refusals Health Connect stops showing the dialog. */
    val denied: HealthFeature? = null,
    val confirmWrite: Boolean = false,
    val confirmDisconnect: Boolean = false,
    val disconnectFailed: Boolean = false,
) {
    /** What the zones use. */
    val maxHeartRate: Int? get() = resolveMaxHeartRate(manualMaxHeartRate, age)

    val estimatedMaxHeartRate: Int? get() = resolveMaxHeartRate(null, age)
}

@HiltViewModel
class HealthConnectViewModel @Inject constructor(
    private val health: HealthConnect,
    private val sync: HealthSync,
    private val preferences: AppPreferences,
    repository: HealthRepository,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val access = MutableStateFlow<HealthAccess?>(null)
    private val local = MutableStateFlow(Local())

    /** Screen-only state: the request on its way, the dialogs. */
    private data class Local(
        val request: HealthFeature? = null,
        /** Launched and not answered yet. */
        val pending: HealthFeature? = null,
        val denied: HealthFeature? = null,
        val confirmWrite: Boolean = false,
        val confirmDisconnect: Boolean = false,
        val disconnectFailed: Boolean = false,
    )

    private data class Settings(val onOpen: Boolean, val background: Boolean, val write: Boolean, val age: Int?, val maxHeartRate: Int?)

    private val settings = combine(
        preferences.healthSyncOnOpen,
        preferences.healthBackgroundSync,
        preferences.healthWriteWorkouts,
        preferences.observeAge(clock),
        preferences.maxHeartRate,
        ::Settings,
    )

    val state: StateFlow<HealthConnectUiState> =
        combine(access, local, sync.status, preferences.healthLastSync, combine(settings, repository.observeSummary(), ::Pair)) { access, local, status, lastSync, (settings, summary) ->
            HealthConnectUiState(
                loading = access == null,
                access = access ?: HealthAccess(),
                status = status,
                lastSync = lastSync,
                syncOnOpen = settings.onOpen,
                backgroundSync = settings.background && access?.connected(HealthFeature.Background) == true,
                writeWorkouts = settings.write && access?.connected(HealthFeature.WriteWorkouts) == true,
                summary = summary,
                age = settings.age,
                manualMaxHeartRate = settings.maxHeartRate,
                request = local.request,
                denied = local.denied,
                confirmWrite = local.confirmWrite,
                confirmDisconnect = local.confirmDisconnect,
                disconnectFailed = local.disconnectFailed,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HealthConnectUiState())

    /** Reads availability and permissions again, e.g. after coming back from Health Connect. */
    fun onRefresh() {
        viewModelScope.launch { refresh() }
    }

    fun onConnect(feature: HealthFeature) = local.update { it.copy(request = feature, denied = null) }

    fun onRequestLaunched() = local.update { it.copy(request = null, pending = it.request) }

    /** After the permission dialog: a granted toggle turns on; a missing permission gets a hint where to grant it. */
    fun onPermissionsResult() {
        val feature = local.value.pending ?: return
        viewModelScope.launch {
            val granted = refresh().connected(feature)
            if (granted) {
                when (feature) {
                    HealthFeature.Background -> setBackground(true)
                    HealthFeature.WriteWorkouts -> preferences.setHealthWriteWorkouts(true)
                    else -> Unit
                }
            }
            local.update { it.copy(pending = null, denied = feature.takeUnless { granted }) }
        }
    }

    fun onSyncNow() = sync.syncNow()

    fun onSyncOnOpen(enabled: Boolean) {
        viewModelScope.launch { preferences.setHealthSyncOnOpen(enabled) }
    }

    fun onBackgroundSync(enabled: Boolean) {
        if (enabled && access.value?.connected(HealthFeature.Background) != true) {
            onConnect(HealthFeature.Background)
        } else {
            viewModelScope.launch { setBackground(enabled) }
        }
    }

    /** Turning it on first warns about duplicates (A6). */
    fun onWriteWorkouts(enabled: Boolean) {
        if (enabled) {
            local.update { it.copy(confirmWrite = true) }
        } else {
            viewModelScope.launch { preferences.setHealthWriteWorkouts(false) }
        }
    }

    fun onWriteConfirm() {
        local.update { it.copy(confirmWrite = false) }
        if (access.value?.connected(HealthFeature.WriteWorkouts) == true) {
            viewModelScope.launch { preferences.setHealthWriteWorkouts(true) }
        } else {
            onConnect(HealthFeature.WriteWorkouts)
        }
    }

    fun onWriteDismiss() = local.update { it.copy(confirmWrite = false) }

    fun onAge(age: Int?) {
        viewModelScope.launch { preferences.setBirthYear(age?.let { year() - it }) }
    }

    fun onMaxHeartRate(bpm: Int?) {
        viewModelScope.launch { preferences.setMaxHeartRate(bpm) }
    }

    fun onDisconnectRequest() = local.update { it.copy(confirmDisconnect = true) }

    fun onDisconnectDismiss() = local.update { it.copy(confirmDisconnect = false) }

    fun onDisconnectConfirm() {
        local.update { it.copy(confirmDisconnect = false, disconnectFailed = false) }
        viewModelScope.launch {
            val failed = try {
                sync.disconnect()
                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Health Connect reports its failures as several exception types
                Log.e(TAG, "Disconnecting failed", e)
                true
            }
            local.update { it.copy(disconnectFailed = failed) }
            refresh()
        }
    }

    private suspend fun setBackground(enabled: Boolean) {
        preferences.setHealthBackgroundSync(enabled)
        sync.schedule(enabled)
    }

    /** New permissions, from the dialog or from Health Connect's own settings, bring their data in at once. */
    private suspend fun refresh(): HealthAccess {
        val before = access.value?.granted
        val now = readAccess()
        access.value = now
        if (before != null && now.reads && !before.containsAll(now.granted)) sync.syncNow()
        return now
    }

    private suspend fun readAccess(): HealthAccess = withContext(io) {
        val availability = health.availability()
        if (availability != HealthAvailability.Available) return@withContext HealthAccess(availability)
        try {
            HealthAccess(
                availability = availability,
                granted = health.granted(),
                historySupported = health.supports(HealthFeature.History),
                backgroundSupported = health.supports(HealthFeature.Background),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Reading permissions failed", e)
            HealthAccess(availability, error = true)
        }
    }

    private fun year(): Int = Year.now(clock.withZone(ZoneId.systemDefault())).value

    private companion object {
        const val TAG = "HealthConnect"
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
