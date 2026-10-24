package com.jaroszeek.gymtracker.feature.settings

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.core.di.IoDispatcher
import com.jaroszeek.gymtracker.designsystem.component.GlassButton
import com.jaroszeek.gymtracker.designsystem.component.GlassButtonStyle
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.component.GlassIconButton
import com.jaroszeek.gymtracker.designsystem.component.GlassScaffold
import com.jaroszeek.gymtracker.designsystem.component.navBarContentPadding
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What uses a license, with the license text in assets/licenses. */
data class LicenseGroup(@StringRes val license: Int, @StringRes val items: Int, val asset: String, @StringRes val note: Int? = null)

val Licenses = listOf(
    LicenseGroup(R.string.license_apache, R.string.license_apache_items, "licenses/Apache-2.0.txt"),
    LicenseGroup(R.string.license_ofl, R.string.license_geist, "licenses/OFL-Geist.txt"),
    LicenseGroup(R.string.license_ofl, R.string.license_instrument_serif, "licenses/OFL-InstrumentSerif.txt"),
    LicenseGroup(R.string.license_unlicense, R.string.license_free_exercise_db, "licenses/LICENSE-free-exercise-db.md", note = R.string.licenses_exercises),
)

/** [texts] by asset, read once; [expanded] indices of [Licenses] whose text is shown. */
data class LicensesUiState(val texts: Map<String, String> = emptyMap(), val expanded: Set<Int> = emptySet())

@HiltViewModel
class LicensesViewModel @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher io: CoroutineDispatcher,
) : ViewModel() {
    private val _state = MutableStateFlow(LicensesUiState())
    val state: StateFlow<LicensesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val texts = withContext(io) {
                Licenses.map { it.asset }.distinct().mapNotNull { asset ->
                    try {
                        asset to context.assets.open(asset).bufferedReader().use { it.readText() }
                    } catch (e: IOException) {
                        Log.e(TAG, "Missing $asset", e)
                        null
                    }
                }.toMap()
            }
            _state.update { it.copy(texts = texts) }
        }
    }

    fun onToggle(index: Int) = _state.update { it.copy(expanded = if (index in it.expanded) it.expanded - index else it.expanded + index) }

    private companion object {
        const val TAG = "Licenses"
    }
}

@Composable
fun LicensesRoute(viewModel: LicensesViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LicensesScreen(state, onBack, viewModel::onToggle)
}

@Composable
fun LicensesScreen(state: LicensesUiState, onBack: () -> Unit, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
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
                Text(stringResource(R.string.licenses_title), Modifier.semantics { heading() }, style = GtTheme.typography.headline)
            }
        }
        itemsIndexed(Licenses) { index, group ->
            val expanded = index in state.expanded
            GlassCard(Modifier.fillMaxWidth()) {
                Text(stringResource(group.items), style = GtTheme.typography.title)
                Text(stringResource(group.license), color = colors.textSecondary, style = GtTheme.typography.label)
                group.note?.let { Text(stringResource(it), color = colors.textSecondary, style = GtTheme.typography.bodySmall) }
                state.texts[group.asset]?.let { text ->
                    GlassButton(
                        stringResource(if (expanded) R.string.licenses_hide else R.string.licenses_show),
                        onClick = { onToggle(index) },
                        style = GlassButtonStyle.Ghost,
                    )
                    if (expanded) Text(text, color = colors.textSecondary, style = GtTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Preview
@Composable
private fun LicensesScreenPreview() {
    GymTrackerTheme {
        GlassScaffold { LicensesScreen(LicensesUiState(texts = mapOf(Licenses.first().asset to "Apache License\nVersion 2.0")), onBack = {}, onToggle = {}) }
    }
}
