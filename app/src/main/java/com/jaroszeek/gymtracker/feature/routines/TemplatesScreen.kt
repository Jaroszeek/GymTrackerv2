package com.jaroszeek.gymtracker.feature.routines

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.data.routine.RoutineTemplate
import com.jaroszeek.gymtracker.data.routine.TemplateRepository
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TemplatesUiState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val templates: List<RoutineTemplate> = emptyList(),
    val adding: Boolean = false,
    /** The template's routines were added; the screen goes back to the routine list. */
    val added: Boolean = false,
)

@HiltViewModel
class TemplatesViewModel @Inject constructor(private val repository: TemplateRepository) : ViewModel() {
    private val _state = MutableStateFlow(TemplatesUiState())
    val state: StateFlow<TemplatesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                TemplatesUiState(loading = false, templates = repository.templates())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Loading templates failed", e)
                TemplatesUiState(loading = false, error = true)
            }
        }
    }

    fun onAdd(template: RoutineTemplate) {
        if (_state.value.adding) return
        _state.update { it.copy(adding = true) }
        viewModelScope.launch {
            repository.copy(template)
            _state.update { it.copy(adding = false, added = true) }
        }
    }

    private companion object {
        const val TAG = "Routines"
    }
}

@StringRes
private fun RoutineTemplate.description(): Int? = when (id) {
    "ppl" -> R.string.template_ppl_description
    "upper_lower" -> R.string.template_upper_lower_description
    "fbw" -> R.string.template_fbw_description
    else -> null
}

@Composable
fun TemplatesRoute(onBack: () -> Unit, viewModel: TemplatesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.added) { if (state.added) onBack() }
    TemplatesScreen(state, onBack, viewModel::onAdd)
}

@Composable
fun TemplatesScreen(state: TemplatesUiState, onBack: () -> Unit, onAdd: (RoutineTemplate) -> Unit, modifier: Modifier = Modifier) {
    val colors = GtTheme.colors
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal).asPaddingValues()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = GtSpacing.xl,
            end = GtSpacing.xl,
            top = insets.calculateTopPadding() + GtSpacing.s,
            bottom = navBarContentPadding().calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(GtSpacing.l),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                GlassIconButton(R.drawable.ic_arrow_back, stringResource(R.string.action_back), onClick = onBack)
                Text(stringResource(R.string.templates_title), Modifier.semantics { heading() }, style = GtTheme.typography.headline)
            }
        }
        if (state.error) {
            item(key = "error") {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.templates_error), color = colors.textSecondary, style = GtTheme.typography.body)
                }
            }
        }
        items(state.templates, key = { it.id }) { template ->
            GlassCard(Modifier.fillMaxWidth()) {
                Text(template.name, Modifier.semantics { heading() }, style = GtTheme.typography.headline)
                template.description()?.let { Text(stringResource(it), color = colors.textSecondary, style = GtTheme.typography.body) }
                template.routines.forEach { routine ->
                    Column(Modifier.padding(top = GtSpacing.s), verticalArrangement = Arrangement.spacedBy(GtSpacing.xxs)) {
                        Text(routine.name, style = GtTheme.typography.title)
                        Text(
                            routine.blocks.joinToString(", ") { block -> block.variants.joinToString(" / ") { it.exercise.name } },
                            color = colors.textTertiary,
                            style = GtTheme.typography.bodySmall,
                        )
                    }
                }
                GlassButton(
                    stringResource(R.string.template_add),
                    onClick = { onAdd(template) },
                    enabled = !state.adding,
                    icon = R.drawable.ic_add,
                    modifier = Modifier.padding(top = GtSpacing.s),
                )
            }
        }
    }
}
