package com.jaroszeek.gymtracker.feature.workout

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.core.datastore.AppPreferences
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NotificationPromptViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AppPreferences,
) : ViewModel() {

    /** On the first start, while the permission is missing (A7); once answered it does not come back. */
    val visible: StateFlow<Boolean> = preferences.notificationPromptShown
        .map { shown -> !shown && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun onAnswered() {
        viewModelScope.launch { preferences.setNotificationPromptShown() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Explains the workout notification before the system asks for the permission. */
@Composable
fun NotificationPrompt(viewModel: NotificationPromptViewModel = hiltViewModel()) {
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    GlassDialog(
        visible = visible,
        onDismissRequest = viewModel::onAnswered,
        title = stringResource(R.string.notification_prompt_title),
        text = stringResource(R.string.notification_prompt_text),
        confirmButton = {
            GlassButton(
                stringResource(R.string.notification_prompt_allow),
                onClick = {
                    viewModel.onAnswered()
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
        },
        dismissButton = { GlassButton(stringResource(R.string.notification_prompt_later), onClick = viewModel::onAnswered, style = GlassButtonStyle.Ghost) },
    )
}
