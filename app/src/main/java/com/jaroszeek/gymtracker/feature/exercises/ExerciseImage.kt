package com.jaroszeek.gymtracker.feature.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.theme.GtSizes
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme

/**
 * Exercise photo over a dumbbell placeholder: offline, while loading or for exercises without
 * photos, the placeholder simply stays visible. Coil caches photos on disk.
 */
@Composable
fun ExerciseImage(
    url: String?,
    contentDescription: String?,
    shape: Shape,
    modifier: Modifier = Modifier,
    placeholderSize: Dp = GtSizes.Icon,
) {
    val colors = GtTheme.colors
    Box(
        modifier
            .clip(shape)
            .background(colors.surfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_fitness_center), contentDescription = null, Modifier.size(placeholderSize), tint = colors.textTertiary)
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
