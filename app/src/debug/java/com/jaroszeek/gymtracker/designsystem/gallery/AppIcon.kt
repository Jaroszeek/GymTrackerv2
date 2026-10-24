package com.jaroszeek.gymtracker.designsystem.gallery

import android.graphics.drawable.AdaptiveIconDrawable
import android.widget.ImageView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
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

/** The launcher icon "Pierścień" (D-080): full color, themed (monochrome) and small. */
@Composable
internal fun AppIcon() {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(GtSpacing.l), verticalAlignment = Alignment.CenterVertically) {
            LauncherIcon(R.drawable.ic_launcher_foreground, themed = false, size = 72.dp)
            LauncherIcon(R.drawable.ic_launcher_foreground, themed = true, size = 72.dp)
            LauncherIcon(R.drawable.ic_launcher_foreground, themed = false, size = 40.dp)
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
