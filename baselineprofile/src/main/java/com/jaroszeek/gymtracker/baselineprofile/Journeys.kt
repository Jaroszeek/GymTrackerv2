package com.jaroszeek.gymtracker.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/** The app variant under test; `baselineprofile/build.gradle.kts` passes it (D-079). */
val targetAppId: String
    get() = requireNotNull(InstrumentationRegistry.getArguments().getString("targetAppId")) { "targetAppId argument missing" }

private const val TIMEOUT_MILLIS = 5_000L
private const val ATTEMPTS = 3

/** A text in either of the app's languages (D-105). */
private fun text(polish: String, english: String) = By.text(Pattern.compile("${Pattern.quote(polish)}|${Pattern.quote(english)}"))

/** First start: the notification prompt (D-047) goes away without granting anything. */
fun MacrobenchmarkScope.dismissFirstRunPrompt() {
    device.wait(Until.findObject(text("Nie teraz", "Not now")), TIMEOUT_MILLIS)?.click()
}

/** The everyday path: every tab and the lists people scroll most. */
fun MacrobenchmarkScope.visitTabs() {
    val tabs = listOf("Ćwiczenia" to "Exercises", "Trening" to "Workout", "Postępy" to "Progress", "Profil" to "Profile", "Start" to "Home")
    for ((polish, english) in tabs) {
        device.wait(Until.findObject(text(polish, english)), TIMEOUT_MILLIS)?.click() ?: continue
        device.waitForIdle()
        flingList(Direction.DOWN)
        flingList(Direction.UP)
    }
}

private fun MacrobenchmarkScope.flingList(direction: Direction) {
    // The list's node changes while it loads and scrolls; a stale one is looked up again
    repeat(ATTEMPTS) {
        val list = device.findObject(By.scrollable(true)) ?: return
        try {
            // Keep clear of the system gesture areas
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(direction)
            device.waitForIdle()
            return
        } catch (_: StaleObjectException) {
            device.waitForIdle()
        }
    }
}
