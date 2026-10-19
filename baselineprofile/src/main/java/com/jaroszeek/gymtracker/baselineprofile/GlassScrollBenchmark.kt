package com.jaroszeek.gymtracker.baselineprofile

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scroll smoothness of the component gallery under the glass navbar and top bar (D-014),
 * with real backdrop blur and with "Ogranicz przezroczystość" as the baseline.
 *
 * `.\gradlew.bat :baselineprofile:connectedBenchmarkAndroidTest`
 */
@RunWith(AndroidJUnit4::class)
class GlassScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollWithBlur() = scroll(reduceTransparency = false)

    @Test
    fun scrollWithoutBlur() = scroll(reduceTransparency = true)

    private fun scroll(reduceTransparency: Boolean) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Full(),
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
        setupBlock = {
            startActivityAndWait(
                Intent()
                    .setComponent(ComponentName(PACKAGE, GALLERY))
                    .putExtra("mode", "dark")
                    .putExtra("reduceTransparency", reduceTransparency),
            )
        },
    ) {
        val list = device.wait(Until.findObject(By.res("gallery_list")), TIMEOUT_MILLIS)
        // Keep clear of the system gesture areas
        list.setGestureMargin(device.displayWidth / 5)
        repeat(FLINGS) {
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }
        repeat(FLINGS) {
            list.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE = "com.jaroszeek.gymtracker.benchmark"
        const val GALLERY = "com.jaroszeek.gymtracker.designsystem.gallery.GalleryActivity"
        const val ITERATIONS = 5
        const val FLINGS = 3
        const val TIMEOUT_MILLIS = 5_000L
    }
}
