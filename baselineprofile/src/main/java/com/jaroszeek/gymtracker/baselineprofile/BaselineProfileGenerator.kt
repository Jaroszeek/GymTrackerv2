package com.jaroszeek.gymtracker.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup and the main tabs, compiled ahead of time on install (A4 "Baseline Profile", D-079).
 *
 * `.\gradlew.bat :app:generateBaselineProfile` writes it to `app/src/release/generated/baselineProfiles`.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = targetAppId, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        dismissFirstRunPrompt()
        visitTabs()
    }
}
