package com.jaroszeek.gymtracker.designsystem.gallery

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.jaroszeek.gymtracker.data.debug.DebugData
import com.jaroszeek.gymtracker.designsystem.theme.GtPalette
import com.jaroszeek.gymtracker.designsystem.theme.GymTrackerTheme
import com.jaroszeek.gymtracker.designsystem.theme.ThemeMode
import com.jaroszeek.gymtracker.designsystem.theme.ThemeSettings
import kotlinx.coroutines.launch

/**
 * Debug-only component gallery. The theme can be preset for scripted screenshots:
 * `adb shell am start -n com.jaroszeek.gymtracker.debug/com.jaroszeek.gymtracker.designsystem.gallery.GalleryActivity
 *  --es palette onyx --es mode dark --ez amoled true --ez reduceTransparency true`
 * `--ei seedWorkouts 300` also adds test workouts ([DebugData]).
 */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val initial = intent.toThemeSettings()
        val seedWorkouts = intent.getIntExtra("seedWorkouts", 0)
        if (savedInstanceState == null && seedWorkouts > 0) lifecycleScope.launch { DebugData.seed(applicationContext, seedWorkouts) }
        setContent {
            var settings by rememberSaveable(stateSaver = ThemeSettingsSaver) { mutableStateOf(initial) }
            GymTrackerTheme(settings) {
                GalleryScreen(settings, onSettingsChange = { settings = it })
            }
        }
    }
}

private fun Intent.toThemeSettings() = ThemeSettings(
    mode = ThemeMode.entries.firstOrNull { it.name.equals(getStringExtra("mode"), ignoreCase = true) } ?: ThemeMode.System,
    palette = GtPalette.entries.firstOrNull { it.name.equals(getStringExtra("palette"), ignoreCase = true) } ?: GtPalette.Volt,
    amoled = getBooleanExtra("amoled", false),
    dynamicColor = getBooleanExtra("dynamic", false),
    reduceTransparency = getBooleanExtra("reduceTransparency", false),
)

private val ThemeSettingsSaver = androidx.compose.runtime.saveable.Saver<ThemeSettings, List<Any>>(
    save = { listOf(it.mode.name, it.palette.name, it.amoled, it.dynamicColor, it.reduceTransparency) },
    restore = {
        ThemeSettings(
            mode = ThemeMode.valueOf(it[0] as String),
            palette = GtPalette.valueOf(it[1] as String),
            amoled = it[2] as Boolean,
            dynamicColor = it[3] as Boolean,
            reduceTransparency = it[4] as Boolean,
        )
    },
)
