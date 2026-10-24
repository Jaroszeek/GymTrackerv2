package com.jaroszeek.gymtracker.designsystem.theme

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.jaroszeek.gymtracker.R

// Static weights on purpose: variable weight axes may not apply on device (D-007)
private val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)

private val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Geist with tabular figures, so digits never shift while weights and timers change. */
private fun geist(weight: FontWeight, size: Int, lineHeight: Int, letterSpacing: Float = 0f) = TextStyle(
    fontFamily = Geist,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.em,
    fontFeatureSettings = "tnum",
)

/**
 * A one-line label in a fixed slot (navbar, segments) shrinks to fit a large system font instead of losing
 * the end of the word; it never goes below [MIN_FIT_SP], which a 200% font still doubles.
 */
fun TextStyle.fitting(): TextAutoSize = TextAutoSize.StepBased(minFontSize = MIN_FIT_SP.sp, maxFontSize = fontSize)

private const val MIN_FIT_SP = 7

// Serif only for large display moments; it has no tabular figures, so never for numbers
private fun serif(size: Int, lineHeight: Int) = TextStyle(
    fontFamily = InstrumentSerif,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = (-0.01f).em,
)

@Immutable
data class GtTypography(
    val display: TextStyle = serif(44, 48),
    val displaySmall: TextStyle = serif(32, 36),
    val headline: TextStyle = geist(FontWeight.SemiBold, 24, 30, -0.01f),
    val title: TextStyle = geist(FontWeight.SemiBold, 18, 24),
    val body: TextStyle = geist(FontWeight.Normal, 16, 22),
    val bodySmall: TextStyle = geist(FontWeight.Normal, 14, 20),
    val label: TextStyle = geist(FontWeight.Medium, 14, 18),
    val labelSmall: TextStyle = geist(FontWeight.Medium, 12, 16, 0.01f),
    val numberXL: TextStyle = geist(FontWeight.SemiBold, 64, 68, -0.03f),
    val numberL: TextStyle = geist(FontWeight.SemiBold, 40, 44, -0.02f),
    val numberM: TextStyle = geist(FontWeight.Medium, 24, 28, -0.01f),
)

internal fun GtTypography.toMaterial() = Typography(
    displayLarge = display,
    displayMedium = displaySmall,
    displaySmall = displaySmall,
    headlineLarge = headline,
    headlineMedium = headline,
    headlineSmall = title,
    titleLarge = title,
    titleMedium = label,
    titleSmall = label,
    bodyLarge = body,
    bodyMedium = bodySmall,
    bodySmall = bodySmall,
    labelLarge = label,
    labelMedium = labelSmall,
    labelSmall = labelSmall,
)
