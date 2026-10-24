package com.jaroszeek.gymtracker.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtShapes
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Large tabular number with −/+ steppers, for quick one-handed edits of weight and reps.
 * Accepts both "," and "." as the decimal separator; [decimals] = 0 means whole numbers.
 */
@Composable
fun GlassNumberField(
    value: Double?,
    onValueChange: (Double?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    step: Double = 1.0,
    decimals: Int = 0,
    unit: String? = null,
    range: ClosedFloatingPointRange<Double> = 0.0..9999.0,
    /** Shown dimmed while the field is empty, e.g. "—" for no weight. */
    placeholder: String? = null,
) {
    val colors = GtTheme.colors
    val separator = remember { DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator }
    var text by remember { mutableStateOf(NumberInput.format(value, decimals, separator)) }
    // Follow external changes (steppers, state restore) without fighting the user's typing
    LaunchedEffect(value) {
        if (NumberInput.parse(text, decimals) != value) text = NumberInput.format(value, decimals, separator)
    }
    Column(
        modifier
            .clip(GtShapes.Medium)
            .background(colors.surfaceRaised)
            .specularEdge(GtShapes.Medium, colors)
            .padding(GtSpacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicTextField(
            value = text,
            onValueChange = { input ->
                if (NumberInput.isValidInput(input, decimals)) {
                    text = input
                    onValueChange(NumberInput.parse(input, decimals)?.coerceIn(range))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            textStyle = GtTheme.typography.numberL.copy(color = colors.textPrimary, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            singleLine = true,
            cursorBrush = SolidColor(colors.accent),
            // The label is inside the decoration box, so TalkBack reads it with the field (as in M3)
            decorationBox = { field ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, color = colors.textSecondary, style = GtTheme.typography.labelSmall)
                    Box(contentAlignment = Alignment.Center) {
                        if (text.isEmpty() && placeholder != null) {
                            Text(placeholder, color = colors.textTertiary, style = GtTheme.typography.numberL)
                        }
                        field()
                    }
                }
            },
        )
        // Steppers under the number: big thumb targets, and the field fits half the screen width
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(
                icon = R.drawable.ic_remove,
                contentDescription = stringResource(R.string.number_decrease, label),
                onClick = { onValueChange(NumberInput.step(value, -step, decimals, range)) },
                style = GlassButtonStyle.Ghost,
            )
            Text(
                unit.orEmpty(),
                Modifier.weight(1f),
                color = colors.textSecondary,
                style = GtTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
            GlassIconButton(
                icon = R.drawable.ic_add,
                contentDescription = stringResource(R.string.number_increase, label),
                onClick = { onValueChange(NumberInput.step(value, step, decimals, range)) },
                style = GlassButtonStyle.Ghost,
            )
        }
    }
}

/** Parsing and formatting behind [GlassNumberField]; pure, so it is unit tested. */
internal object NumberInput {
    private const val MAX_DIGITS = 5

    fun isValidInput(input: String, decimals: Int): Boolean {
        val pattern = if (decimals > 0) Regex("\\d{0,$MAX_DIGITS}([.,]\\d{0,$decimals})?") else Regex("\\d{0,$MAX_DIGITS}")
        return pattern.matches(input)
    }

    fun parse(input: String, decimals: Int): Double? =
        input.replace(',', '.').toDoubleOrNull()?.let { round(it, decimals) }

    fun format(value: Double?, decimals: Int, separator: Char): String {
        if (value == null) return ""
        val rounded = round(value, decimals)
        if (rounded == rounded.roundToLong().toDouble()) return rounded.roundToLong().toString()
        return "%.${decimals}f".format(Locale.ROOT, rounded).trimEnd('0').replace('.', separator)
    }

    fun step(value: Double?, delta: Double, decimals: Int, range: ClosedFloatingPointRange<Double>): Double =
        round((value ?: 0.0) + delta, decimals).coerceIn(range)

    // Steps like 2.5 kg must not accumulate floating point noise
    fun round(value: Double, decimals: Int): Double {
        val factor = 10.0.pow(decimals)
        return (value * factor).roundToLong() / factor
    }
}

@Preview
@Composable
private fun GlassNumberFieldPreview() {
    GymTrackerTheme {
        GlassScaffold {
            Row(Modifier.padding(GtSpacing.xl), horizontalArrangement = Arrangement.spacedBy(GtSpacing.m)) {
                GlassNumberField(82.5, {}, stringResource(R.string.label_weight), step = 2.5, decimals = 2, unit = stringResource(R.string.unit_kg))
            }
        }
    }
}
