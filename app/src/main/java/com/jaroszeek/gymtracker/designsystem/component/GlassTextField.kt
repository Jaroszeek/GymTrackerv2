package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme

/** Matte text field with a label above; [minLines] > 1 makes it a multi-line note field. */
@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    minLines: Int = 1,
) {
    val colors = GtTheme.colors
    val singleLine = minLines == 1
    // The label and the error are inside the decoration box: the field's node carries them for TalkBack, as in M3
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = GtTheme.typography.body.copy(color = colors.textPrimary),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        singleLine = singleLine,
        minLines = minLines,
        cursorBrush = SolidColor(colors.accent),
        decorationBox = { field ->
            Column(verticalArrangement = Arrangement.spacedBy(GtSpacing.xs)) {
                Text(label, color = colors.textSecondary, style = GtTheme.typography.labelSmall)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = GtSizes.MinTouch)
                        .clip(GtShapes.Medium)
                        .background(colors.surfaceRaised)
                        .specularEdge(GtShapes.Medium, colors)
                        .padding(horizontal = GtSpacing.l, vertical = GtSpacing.m),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(placeholder, color = colors.textTertiary, style = GtTheme.typography.body)
                    }
                    field()
                }
                if (error != null) Text(error, color = colors.danger, style = GtTheme.typography.labelSmall)
            }
        },
    )
}

/** Pill search field with a clear button. */
@Composable
fun GlassSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = GtTheme.colors
    val focus = LocalFocusManager.current
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(GtSizes.MinTouch)
            .clip(GtShapes.Pill)
            .background(colors.surfaceRaised)
            .specularEdge(GtShapes.Pill, colors),
        textStyle = GtTheme.typography.body.copy(color = colors.textPrimary),
        // Exercise names are English; Polish autocorrect would rewrite them
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        decorationBox = { field ->
            Row(
                Modifier.padding(start = GtSpacing.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GtSpacing.s),
            ) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, Modifier.size(GtSizes.IconSmall), tint = colors.textSecondary)
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(placeholder, color = colors.textTertiary, style = GtTheme.typography.body, maxLines = 1)
                    field()
                }
                if (query.isNotEmpty()) {
                    GlassIconButton(
                        icon = R.drawable.ic_close,
                        contentDescription = stringResource(R.string.action_clear),
                        onClick = { onQueryChange("") },
                        style = GlassButtonStyle.Ghost,
                    )
                }
            }
        },
    )
}

@Preview
@Composable
private fun GlassTextFieldPreview() {
    GymTrackerTheme {
        GlassScaffold {
            Column(Modifier.padding(GtSpacing.xl), verticalArrangement = Arrangement.spacedBy(GtSpacing.l)) {
                GlassSearchField(query = "", onQueryChange = {}, placeholder = stringResource(R.string.exercises_search))
                GlassTextField(value = "", onValueChange = {}, label = stringResource(R.string.label_name))
            }
        }
    }
}
