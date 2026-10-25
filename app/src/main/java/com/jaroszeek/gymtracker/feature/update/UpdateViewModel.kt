package com.jaroszeek.gymtracker.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.core.update.UpdateManager
import com.jaroszeek.gymtracker.core.update.UpdateState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UpdateUiState(
    /** False in builds without an update source (debug): the updater stays out of sight. */
    val enabled: Boolean = false,
    val update: UpdateState = UpdateState.Idle,
    val autoCheck: Boolean = true,
    val lastCheck: Long? = null,
)

/** The in-app updater (A5.11), for the dialog over the app and the card in Settings. */
@HiltViewModel
class UpdateViewModel @Inject constructor(private val updates: UpdateManager) : ViewModel() {

    val state: StateFlow<UpdateUiState> = combine(updates.state, updates.autoCheck, updates.lastCheck) { update, auto, last ->
        UpdateUiState(updates.enabled, update, auto, last)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), UpdateUiState(enabled = updates.enabled))

    fun onCheck() = updates.checkNow()

    fun onInstall() = updates.install()

    fun onPostpone() = updates.postpone()

    fun onAutoCheck(enabled: Boolean) {
        viewModelScope.launch { updates.setAutoCheck(enabled) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
