package com.jaroszeek.gymtracker.designsystem.gallery

import android.graphics.drawable.AdaptiveIconDrawable
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toDrawable
import com.jaroszeek.gymtracker.R
import com.jaroszeek.gymtracker.designsystem.component.GlassCard
import com.jaroszeek.gymtracker.designsystem.theme.GtSpacing
import com.jaroszeek.gymtracker.designsystem.theme.GtTheme

/** Launcher icon proposals for the owner to choose from (Stage 10): full color, themed (monochrome) and small. */
@Composable
internal fun IconProposals() {
    GlassCard(Modifier.fillMaxWidth()) {
        listOf(
            "A" to R.drawable.icon_proposal_ring,
            "B" to R.drawable.icon_proposal_bars,
            "C" to R.drawable.icon_proposal_dumbbell,
        ).forEach { (name, icon) ->
            Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.l), verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = GtTheme.typography.headline)
                LauncherIcon(icon, themed = false, size = 72.dp)
                LauncherIcon(icon, themed = true, size = 72.dp)
                LauncherIcon(icon, themed = false, size = 40.dp)
            }
        }
    }
}

/** Masked by the device's own icon shape, like the launcher does. */
@Composable
private fun LauncherIcon(foreground: Int, themed: Boolean, size: Dp) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                val front = context.getDrawable(foreground)!!.mutate()
                val back = if (themed) THEMED_BACKGROUND.toDrawable() else context.getDrawable(R.drawable.ic_launcher_background)!!
                if (themed) front.setTint(THEMED_FOREGROUND)
                setImageDrawable(AdaptiveIconDrawable(back, front))
            }
        },
        modifier = Modifier.size(size),
    )
}

// A light Material You themed icon (system tints the monochrome layer)
private const val THEMED_BACKGROUND = 0xFFDDE6C6.toInt()
private const val THEMED_FOREGROUND = 0xFF2C3A10.toInt()
